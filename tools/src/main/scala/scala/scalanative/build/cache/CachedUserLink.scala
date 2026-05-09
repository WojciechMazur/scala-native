package scala.scalanative
package build
package cache

import java.nio.file.Path

import scala.concurrent.{ExecutionContext, Future}

/** User-only NIR link / codegen against cached library headers (extension point).
  *
  * The production pipeline still runs monolithic reachability + codegen; this object
  * documents the intended split and holds helpers for future integration.
  */
private[scalanative] object CachedUserLink {

  /** When true, the toolchain should load `header.nir` + `header.sidecar` from the cache
    * and restrict LLVM codegen to user classpath tops only. Not yet enabled in `Build`.
    */
  final val splitCodegenEnabled: Boolean = false

  /** Reserved: return cached header paths that should be merged ahead of user IR. */
  def cachedHeaderPaths(config: Config): Seq[Path] = Nil

  /** Reserved: link + codegen only user code, then system-link against cached `.so` files. */
  def buildIfEnabled(
      config: Config
  )(implicit scope: scala.scalanative.util.Scope, ec: ExecutionContext): Option[Future[Path]] =
    None
}
