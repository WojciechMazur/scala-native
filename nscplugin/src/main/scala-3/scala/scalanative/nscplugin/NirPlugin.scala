package scala.scalanative.nscplugin.NirPlugin

import dotty.tools.dotc.plugins._

class NirPlugin extends StandardPlugin:
  val name: String = "NirPlugin"
  val description: String = "Scala Native compiler plugin"

  def init(options: List[String]): List[PluginPhase] = Nil
