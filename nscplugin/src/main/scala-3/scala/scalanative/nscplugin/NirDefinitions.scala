package scala.scalanative.nscplugin

import scala.collection.mutable
import dotty.tools.dotc._
import core._
import Names.TermName
import StdNames._
import Types._
import Contexts._
import Symbols._
import dotty.tools.backend.jvm.DottyPrimitives
import dotty.tools.dotc.util.ReadOnlyMap
import scala.scalanative.nscplugin.NirPrimitives

class NirDefinitions()(using ctx: Context) extends DottyPrimitives(ctx) {
  import NirPrimitives._

  object nirDefinitions extends NirDefinitionsProvider(Scala3Compat) {}

  object Scala3Compat extends NirDefinitionsProvider.Compat {
    type Symbol = core.Symbols.Symbol
    type Name = core.Names.Name

    def getRequiredClass(cls: String): Symbol = requiredClass(cls)
    def getRequiredModule(cls: String): Symbol = requiredModule(cls)
    def getPackageObject(cls: String): Symbol = requiredPackage(cls)

    def getMember(sym: Symbol, name: Name): Symbol = requiredModuleRef(sym.fullName).member(name).symbol
    def getMemberMethod(sym: Symbol, name: Name): Symbol = getMember(sym, name)
    def getDecl(sym: Symbol, name: Name): Symbol =  requiredClassRef(sym.fullName).decl(name).symbol

    def TermName(name: String): Name = Names.termName(name)

    def symbolAlternatives(sym: Symbol): Seq[Symbol] = sym.alternatives.map(_.symbol)
    def symbolCompanion(sym: Symbol): Symbol = sym.companionModule

    final val BoxesRunTimeModule: Symbol = ctx.definitions.BoxesRunTimeModule
    final val ScalaRunTimeModule: Symbol = ctx.definitions.ScalaRuntimeModule
    final val StringClass: Symbol = ctx.definitions.StringClass
    final val BoxedUnit_UNIT: Symbol = ctx.definitions.BoxedUnit_UNIT
    final val Array_clone: Symbol = ctx.definitions.Array_clone
  }
}
