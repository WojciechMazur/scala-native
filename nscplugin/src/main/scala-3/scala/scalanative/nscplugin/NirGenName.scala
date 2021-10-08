package scala.scalanative.nscplugin

import dotty.tools.dotc.ast.tpd._
import dotty.tools.dotc.core
import core.Contexts._
import core.Symbols._
import core.Flags._
import core.StdNames._

import scalanative.util.unreachable
import scalanative.nir

trait NirGenName(using Context) {
  self: NirCodeGen =>

  def genAnonName(owner: Symbol, anon: Symbol) =
    genName(owner).member(nir.Sig.Extern(anon.fullName.toString))

  def genName(sym: Symbol): nir.Global =
    if (sym.isType) {
      genTypeName(sym)
    } else if (sym.is(Method)) {
      genMethodName(sym)
    } else {
      genFieldName(sym)
    }

  def genTypeName(sym0: Symbol): nir.Global.Top = {
    val sym =
      if (sym0.isAllOf(ModuleClass | JavaDefined)) sym0.linkedClass
      else sym0
    if (sym == defn.ObjectClass) nir.Rt.Object.name.asInstanceOf[nir.Global.Top]
    else {
      val id = {
        val fullName = sym.fullName.toString
        NirGenName.MappedNames.getOrElse(fullName, fullName)
      }
      log(s"genTypeName: $id")
      nir.Global.Top(id)
    }
  }

  def genFieldName(sym: Symbol): nir.Global = {
    val owner = genTypeName(sym.owner)
    val id = nativeIdOf(sym)
    val scope = {
      /* TODO: Restore me - Variables are internally private, but with public setter/getter.
       * Removing this check would cause problems with reachability
       */
      if (sym.isPrivate) nir.Sig.Scope.Private(owner)
      else nir.Sig.Scope.Public
    }

    owner.member {
      if (sym.owner.isExternModule) {
        nir.Sig.Extern(id)
      } else {
        nir.Sig.Field(id, scope)
      }
    }
  }

  def genMethodName(sym: Symbol): nir.Global = {
    println(sym)
    val owner = genTypeName(sym.owner)
    val id = nativeIdOf(sym)
    val tpe = sym.typeRef.widen
    val scope =
      if (sym.isPrivate) nir.Sig.Scope.Private(owner)
      else nir.Sig.Scope.Public

    val paramTypes = sym.info.paramInfoss.flatten
      .map(fromType)
      .map(genType)

    if (sym == defn.`String_+`) 
      println(defn.String_+ -> defnNir.StringConcatMethod)
      genMethodName(defnNir.StringConcatMethod)
    else if (sym.owner.isExternModule) {
      if (sym.isSetter) {
        // Previously dropSetter was sued
        val id0 = sym.name.mangledString
        owner.member(nir.Sig.Extern(id0))
      } else {
        owner.member(nir.Sig.Extern(id))
      }
    } else if (sym.name == nme.CONSTRUCTOR) {
      owner.member(nir.Sig.Ctor(paramTypes))
    } else {
      val retType = genType(fromType(sym.info.resultType))
      owner.member(nir.Sig.Method(id, paramTypes :+ retType, scope))
    }
  }

  def genFuncPtrExternForwarderName(ownerSym: Symbol): nir.Global = {
    ???
    // val owner = genTypeName(ownerSym)
    // owner.member(nir.Sig.Generated("$extern$forwarder"))
  }

  private def nativeIdOf(sym: Symbol): String = {
    sym
      .getAnnotation(defnNir.NameClass)
      .flatMap(_.argumentConstantString(0))
      .getOrElse {
        val id: String = if (sym.isField) {
          sym.name.mangledString
        } else if (sym.is(Method)) {
          val name = sym.name.mangledString
          val isScalaHashOrEquals = name.startsWith("__scala_")
          if (sym.owner == defnNir.NObjectClass || isScalaHashOrEquals) {
            name.substring(2) // strip the __
          } else {
            name
          }
        } else {
          scalanative.util.unreachable
        }
        /*
         * Double quoted identifiers are not allowed in CLang.
         * We're replacing them with unicode to allow distinction between x / `x` and `"x"`.
         * It follows Scala JVM naming convention.
         */
        id.replace("\"", "$u0022")
      }
  }
}

object NirGenName {
  private val MappedNames = Map(
    "java.lang._String" -> "java.lang.String",
    "java.lang._Object" -> "java.lang.Object",
    "java.lang._Class" -> "java.lang.Class"
  )
}
