package scala.scalanative
package build
package cache

import java.nio.file.{Path, Paths}

private[scalanative] object CachePaths {

  def defaultCacheRoot: Path =
    Paths.get(System.getProperty("user.home"), ".cache", "scala-native")

  /** Version segment for cache paths; set `SCALANATIVE_VERSION` when buildinfo is unavailable (e.g. minimal link tests). */
  def scalaNativeVersion: String =
    sys.env.getOrElse("SCALANATIVE_VERSION", "unknown")
}
