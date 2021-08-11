package scala.scalanative.nscplugin

import dotty.tools._
import dotc._
import plugins._
import core._

object NirPhase extends PluginPhase:
  val phaseName = "nir"
  
  override val runsAfter = Set(transform.Mixin.name)
  override val runsBefore = Set(transform.LambdaLift.name, backend.jvm.GenBCode.name)
    

