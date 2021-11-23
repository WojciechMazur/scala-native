package scala.scalanative.nscplugin

import dotty.tools._
import dotc._
import dotc.ast.tpd._
import plugins._
import core.Flags._
import core.Contexts._
import core.Definitions
import core.NameKinds.{ExpandedName}

object PrepareGenNIR extends PluginPhase {
  val phaseName = "prepare-genNIR"

  override val runsBefore = Set(transform.LazyVals.name)

  def defn(using Context): Definitions = ctx.definitions

  override def transformDefDef(tree: DefDef)(using Context): Tree =
    transformLazyVal(tree)

  override def transformValDef(tree: ValDef)(using Context): Tree =
    transformLazyVal(tree)

  private def transformLazyVal(tree: ValOrDefDef)(using Context): Tree = {
    val sym = tree.symbol
    if (sym.is(Lazy, butNot = Module)) {
      sym.addAnnotation(defn.ThreadUnsafeAnnot)
    }
    tree
  }
}
