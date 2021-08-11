package scala.scalanative.nscplugin

import scala.collection.mutable
import dotty.tools.backend.jvm.DottyPrimitives
import dotty.tools.dotc.ast.tpd._
import dotty.tools.dotc.util.ReadOnlyMap
import dotty.tools.dotc.core._
import Names.TermName
import StdNames._
import Types._
import Contexts._
import Symbols._
import scala.scalanative.nscplugin.NirPrimitives

object NirPrimitives extends NirPrimitivesProvider

class NirPrimitives(ctx: Context) extends DottyPrimitives(ctx) {
  private lazy val nirPrimitives: ReadOnlyMap[Symbol, Int] = initNirPrimitives(using ctx)

  override def getPrimitive(sym: Symbol): Int =
    nirPrimitives.getOrElse(sym, super.getPrimitive(sym))

  override def getPrimitive(app: Apply, tpe: Type)(using Context): Int =
    nirPrimitives.getOrElse(app.fun.symbol, super.getPrimitive(app, tpe))

  override def isPrimitive(sym: Symbol): Boolean =
    nirPrimitives.contains(sym) || super.isPrimitive(sym)

  private def initNirPrimitives(using Context): ReadOnlyMap[Symbol, Int] = {
    val primitives = MutableSymbolMap[Int]()

    def addPrimitive(s: Symbol, code: Int) = {
      assert(!(primitives contains s), "Duplicate primitive " + s)
      primitives(s) = code
    }

    type CompatAddPrimitives =
      ((NirDefinitionsProvider.Compat#Symbol, Int) => Unit)
    NirPrimitives.addCommonPrimitives(
      ctx.definitions.asInstanceOf[NirDefinitionsProvider],
      addPrimitive.asInstanceOf[CompatAddPrimitives]
    )

    primitives
  }
}
