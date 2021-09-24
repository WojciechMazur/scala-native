package scala.scalanative
package nscplugin

import scala.tools.nsc._
import scala.language.implicitConversions

trait NirDefinitions {
  val global: Global
  import global._

  object nirDefinitions extends NirDefinitionsProvider(Scala2Compat)
  object Scala2Compat extends NirDefinitionsProvider.Compat[global.Symbol, global.Name] {
    def getRequiredClass(cls: String): Symbol = rootMirror.getRequiredClass(cls)
    def getRequiredModule(cls: String): Symbol =
      rootMirror.getRequiredModule(cls)
    def getPackageObject(cls: String): Symbol = rootMirror.getPackageObject(cls)

    def getMember(sym: Symbol, name: Name): Symbol =
      definitions.getMember(sym, name)
    def getMemberMethod(sym: Symbol, name: Name): Symbol =
      definitions.getMemberMethod(sym, name)
    def getDecl(sym: Symbol, name: Name): Symbol =
      definitions.getDecl(sym, name)

    def TermName(name: String): Name = global.TermName(name)

    def symbolAlternatives(sym: Symbol): Seq[Symbol] = sym.alternatives
    def symbolCompanion(sym: Symbol): Symbol = sym.companion

    final val BoxesRunTimeModule: Symbol = definitions.BoxesRunTimeModule
    final val ScalaRunTimeModule: Symbol = definitions.ScalaRunTimeModule
    final val StringClass: Symbol = definitions.StringClass
    final val BoxedUnit_UNIT: Symbol = definitions.BoxedUnit_UNIT
    final val Array_clone: Symbol = definitions.Array_clone
  }
}
