package scala.scalanative
package nscplugin

import scala.tools.nsc._
import scala.collection.mutable

object NirPrimitives extends NirPrimitivesProvider

abstract class NirPrimitives {
  val global: Global

  type ThisNirGlobalAddons = NirGlobalAddons {
    val global: NirPrimitives.this.global.type
  }

  val nirAddons: ThisNirGlobalAddons

  import global._
  import definitions._
  import rootMirror._
  import scalaPrimitives._
  import nirAddons._
  import nirDefinitions._
  import NirPrimitives._

  def init(): Unit =
    initWithPrimitives(addPrimitive)

  def initPrepJSPrimitives(): Unit = {
    nirPrimitives.clear()
    initWithPrimitives(nirPrimitives.put)
  }

  def isNirPrimitive(sym: Symbol): Boolean =
    nirPrimitives.contains(sym)

  def isNirPrimitive(code: Int): Boolean = NirPrimitives.isNirPrimitive(code)
  def isRawPtrOp(code: Int): Boolean = NirPrimitives.isRawPtrOp(code)
  def isRawPtrLoadOp(code: Int): Boolean = NirPrimitives.isRawPtrLoadOp(code)
  def isRawPtrStoreOp(code: Int): Boolean = NirPrimitives.isRawPtrStoreOp(code)
  def isRawCastOp(code: Int): Boolean = NirPrimitives.isRawCastOp(code)

  private val nirPrimitives = mutable.Map.empty[Symbol, Int]

  private def initWithPrimitives(addPrimitive: (Symbol, Int) => Unit): Unit = {
    type CompatAddPrimitives = ((NirDefinitionsProvider.Compat#Symbol, Int) => Unit)
    NirPrimitives.addCommonPrimitives(
      nirDefinitions,
      addPrimitive.asInstanceOf[CompatAddPrimitives]
    )

    import scala.tools.nsc.settings._
    ScalaVersion.current match {
      case SpecificScalaVersion(2, 11, _, _) =>
        HashMethods.foreach(addPrimitive(_, HASH))
      case _ =>
    }
  }
}
