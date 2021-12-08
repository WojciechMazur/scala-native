package scala.scalanative.nscplugin

import dotty.tools._
import dotc._
import dotc.transform.{LazyVals, MoveStatics}
import dotc.ast.tpd._
import plugins._
import core.Flags._
import core.Contexts._
import core.Definitions
import core.Names._
import core.Symbols._
import core.Types._
import core.DenotTransformers._
import core.SymDenotations.SymDenotation
import core.StdNames._
import core.NameKinds
import core.Constants.Constant
import NoContext.given_Context
import dotty.tools.backend.sjs.ScopedVar
import dotty.tools.dotc.ast.Trees
import scala.annotation.{threadUnsafe => tu}

object AdaptLazyVals extends PluginPhase {
  val phaseName = "scalanative-adaptLazyVals"

  override val runsAfter = Set(LazyVals.name, MoveStatics.name)
  override val runsBefore = Set(GenNIR.phaseName)

  def defn(using Context): Definitions = ctx.definitions
  def defnNir(using Context) = NirDefinitions.defnNir
  def defnLazyVals(using Context) = LazyValsDefns.get

  private def isLazyFieldOffset(name: Name) =
    name.startsWith(nme.LAZY_FIELD_OFFSET.toString)

  // Map of field symbols for LazyVals offsets and literals
  // with the name of referenced bitmap fields within given TypeDef
  private val bitmapFieldNames = collection.mutable.Map.empty[Symbol, Literal]

  override def prepareForUnit(tree: Tree)(using Context): Context = {
    bitmapFieldNames.clear()
    super.prepareForUnit(tree)
  }

  // Collect informations about offset fields
  override def prepareForTypeDef(td: TypeDef)(using Context): Context = {
    val sym = td.symbol
    val hasLazyFields = sym.denot.info.fields
      .exists(f => isLazyFieldOffset(f.name))

    if (hasLazyFields) {
      val template @ Template(_, _, _, _) = td.rhs
      bitmapFieldNames ++= template.body.collect {
        case vd: ValDef if isLazyFieldOffset(vd.name) =>
          val Apply(_, List(cls: Literal, fieldname: Literal)) = vd.rhs
          vd.symbol -> fieldname
      }.toMap
    }

    ctx
  }

  // Replace all usages of all unsuportted LazyVals methods with their
  // Scala Native specific implementation (taking Ptr intead of object + offset)
  override def transformApply(tree: Apply)(using Context): Tree = {
    // Create call to SN intrinsic methods returning pointer to bitmap field
    def classFieldPtr(target: Tree, fieldRef: Tree): Tree = {
      val fieldName = bitmapFieldNames(fieldRef.symbol)
      cpy.Apply(tree)(
        fun = ref(defnNir.RuntimePackage_fromRawPtr),
        args = List(
          cpy.Apply(tree)(
            fun = ref(defnNir.Intrinsics_classFieldRawPtr),
            args = List(target, fieldName)
          )
        )
      )
    }

    val Apply(fun, args) = tree
    val sym = fun.symbol

    if bitmapFieldNames.isEmpty then tree // No LazyVals in TypeDef, fast path
    else if sym == defnLazyVals.LazyVals_get then
      val List(target, fieldRef) = args
      cpy.Apply(tree)(
        fun = ref(defnLazyVals.NativeLazyVals_get),
        args = List(classFieldPtr(target, fieldRef))
      )
    else if sym == defnLazyVals.LazyVals_setFlag then
      val List(target, fieldRef, value, ord) = args
      cpy.Apply(tree)(
        fun = ref(defnLazyVals.NativeLazyVals_setFlag),
        args = List(classFieldPtr(target, fieldRef), value, ord)
      )
    else if sym == defnLazyVals.LazyVals_CAS then
      val List(target, fieldRef, expected, value, ord) = args
      cpy.Apply(tree)(
        fun = ref(defnLazyVals.NativeLazyVals_CAS),
        args = List(classFieldPtr(target, fieldRef), expected, value, ord)
      )
    else if sym == defnLazyVals.LazyVals_wait4Notification then
      val List(target, fieldRef, value, ord) = args
      cpy.Apply(tree)(
        fun = ref(defnLazyVals.NativeLazyVals_wait4Notification),
        args = List(classFieldPtr(target, fieldRef), value, ord)
      )
    else tree
  }

  object LazyValsDefns {
    var lastCtx: Option[Context] = None
    var lastDefns: LazyValsDefns = _

    def get(using Context): LazyValsDefns = {
      if (!lastCtx.contains(ctx)) {
        lastDefns = LazyValsDefns()
        lastCtx = Some(ctx)
      }
      lastDefns
    }
  }
  class LazyValsDefns(using Context) {
    @tu lazy val NativeLazyValsModule = requiredModule(
      "scala.scalanative.runtime.LazyVals"
    )
    @tu lazy val NativeLazyVals_get = NativeLazyValsModule.requiredMethod("get")
    @tu lazy val NativeLazyVals_setFlag =
      NativeLazyValsModule.requiredMethod("setFlag")
    @tu lazy val NativeLazyVals_CAS = NativeLazyValsModule.requiredMethod("CAS")
    @tu lazy val NativeLazyVals_wait4Notification =
      NativeLazyValsModule.requiredMethod("wait4Notification")

    @tu lazy val LazyValsModule = requiredModule("scala.runtime.LazyVals")
    @tu lazy val LazyVals_get = LazyValsModule.requiredMethod("get")
    @tu lazy val LazyVals_setFlag = LazyValsModule.requiredMethod("setFlag")
    @tu lazy val LazyVals_CAS = LazyValsModule.requiredMethod("CAS")
    @tu lazy val LazyVals_wait4Notification =
      LazyValsModule.requiredMethod("wait4Notification")
  }

}
