package scala.scalanative.nscplugin

import dotty.tools._
import dotc._
import plugins._
import core._
import Contexts._

object GenNIR extends PluginPhase {
  val phaseName = "nir"

  override val runsAfter = Set(transform.LambdaLift.name)
  override val runsBefore = Set(transform.MoveStatics.name, backend.jvm.GenBCode.name)

  override def run(using Context): Unit = {
    println(s"Producing NIR for `${ctx.compilationUnit.source.file}`")
    NirCodeGen().run()
  }
}
