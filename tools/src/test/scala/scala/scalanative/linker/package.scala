package scala.scalanative

import scala.scalanative.nir._

package object linker {
  val linktimeInfoProperties = "scala.scalanative.meta.linktimeinfo"
  // Defaults values for linktime resolved properties
  val linktimeInfoDefaults: Map[String, Any] = {
    Map(
      s"$linktimeInfoProperties.isWindows" -> false,
      s"$linktimeInfoProperties.is32BitPlatform" -> false,
      s"$linktimeInfoProperties.sizeOfPtr" -> Val.Size(8),
      s"$linktimeInfoProperties.asanEnabled" -> false
    )
  }
}
