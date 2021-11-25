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

object AdaptLazyVals extends PluginPhase {
  val phaseName = "scalanative-adaptLazyValy"

  override val runsAfter = Set(LazyVals.name)
  override val runsBefore = Set(MoveStatics.name)

  private val curOffsetTrees = ScopedVar(Map.empty[Symbol, Tree])

  def defn(using Context): Definitions = ctx.definitions
  def defnNir(using Context) = NirDefinitions.defnNir

  private def isLazyFieldOffset(name: Name) =
    name.startsWith(nme.LAZY_FIELD_OFFSET.toString)

  override def transformTypeDef(td: TypeDef)(using Context): Tree = {
    val sym = td.symbol
    val hasLazyFields =
      sym.denot.info.fields.exists(f => isLazyFieldOffset(f.name))

    if (!hasLazyFields) td
    else {
      val TypeDef(
        _,
        templ @ Template(
          constructor,
          parents,
          self,
          body: List[Tree @unchecked]
        )
      ) = td

      // Collect definitions of LazyVals OFFSET fields, replace their usages
      // with inlined method invocation - it would be replaced with constant value
      // in Lower phase of SN linker
      val transforemedBodies = {
        val offsetTrees = body.collect {
          case vd: ValDef if isLazyFieldOffset(vd.name) =>
            (vd.symbol, vd.rhs)
        }
        ScopedVar.withScopedVars(
          curOffsetTrees := offsetTrees.toMap
        ) {
          body.map(transformAllDeep)
        }
      }

      // Create dummy methods used to initialize LazyVals bitmap fields
      // It is needed as we don't support static class constructors.
      // Make sure tht method would not be optimized by SN since there is
      // no other direct usage of this fields (access happends only via field offset)
      val dummyInitBitmpatFieldsDef = {
        val methodSym = newSymbol(
          owner = td.symbol,
          name = termName("$scalanative$setupDummyBitmapFields"),
          flags = Synthetic | Method | JavaStatic,
          info = MethodType(Nil, defn.UnitType),
          privateWithin = td.symbol,
          coord = td.symbol.coord
        )
        methodSym.addAnnotation(defnNir.NoOptimizeClass)
        val dummyAssigns = body.collect {
          case vd: ValDef if vd.name.is(NameKinds.LazyBitMapName) =>
            val sym = vd.symbol
            val selfIdent = Ident(sym.namedType)
            cpy.Assign(constructor)(lhs = selfIdent, rhs = selfIdent)
        }
        val res = DefDef(
          methodSym.asTerm,
          cpy.Block(constructor)(dummyAssigns, Literal(Constant(())))
        )
        res
      }

      // Update class constructor to call dummy bitmap init method
      val DefDef(_, _, _, (b: Block @unchecked)) = constructor
      val newConstructor = cpy.DefDef(constructor)(rhs =
        cpy.Block(b)(
          stats = {
            b.stats :+
              cpy.Apply(b)(
                fun = Select(
                  This(td.symbol.asClass),
                  dummyInitBitmpatFieldsDef.namedType
                ),
                args = Nil
              )
          },
          expr = b.expr
        )
      )

      // Create new template with new methods and updated bodies
      val newTemplate =
        cpy.Template(templ)(
          constr = newConstructor,
          body = transforemedBodies :+ dummyInitBitmpatFieldsDef
        )

      cpy.TypeDef(td)(td.name, newTemplate)
    }
  }

  override def transformIdent(ident: Ident)(using Context): Tree = {
    // curOffsetTrees would be empty if transformTypeDef was not entered yet
    if (!isLazyFieldOffset(ident.name) || curOffsetTrees.get.isEmpty)
      ident
    else
      curOffsetTrees.get(ident.symbol)
  }

}
