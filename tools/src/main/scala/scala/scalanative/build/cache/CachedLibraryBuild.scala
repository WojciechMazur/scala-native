package scala.scalanative
package build
package cache

import scala.concurrent.ExecutionContext

import scala.scalanative.util.Scope

/** Orchestrates optional pre-build of the runtime DSO and post-build cache maintenance. */
private[scalanative] object CachedLibraryBuild {

  def enabled(config: Config): Boolean =
    config.compilerConfig.useCachedLibraries &&
      !config.targetsWindows &&
      (config.targetsLinux || config.targetsMac)

  /** Nested `Build.build` for `libscala-native-runtime` must not re-enter [[RuntimeDsoBuilder]] while the populate lock is held. */
  private[cache] def isNestedRuntimeLibraryBuild(config: Config): Boolean =
    config.moduleName == "scala-native-runtime" &&
      config.compilerConfig.buildTarget == BuildTarget.LibraryDynamic

  /** Runs before link/codegen (may pre-build the runtime dynamic library). */
  def preBuild(config: Config)(implicit scope: Scope, ec: ExecutionContext): Unit =
    if (enabled(config) && !isNestedRuntimeLibraryBuild(config)) {
      val log = config.logger
      val cacheRoot =
        config.compilerConfig.cacheRoot.getOrElse(CachePaths.defaultCacheRoot).toAbsolutePath
      log.info(
        "[cached libraries] Mode active — cache root: " + cacheRoot +
          "; external stdlib may be linked into libscala-native-runtime (see following DSO messages)."
      )
      RuntimeDsoBuilder.ensureRuntimeDso(config)
      ()
    } else if (isNestedRuntimeLibraryBuild(config)) {
      config.logger.debug(
        "[cached libraries] Skipping runtime DSO pre-step (nested libscala-native-runtime build)."
      )
    }

  /** Fire-and-forget LRU pruning after a successful link. */
  def postBuild(config: Config): Unit = {
    val nc = config.compilerConfig
    if (nc.useCachedLibraries && nc.cacheMaxSizeBytes > 0) {
      val root = nc.cacheRoot.getOrElse(CachePaths.defaultCacheRoot)
      val log = config.logger
      ExecutionContext.global.execute(() =>
        try CacheLru.pruneIfNeeded(root, nc.cacheMaxSizeBytes, log)
        catch { case e: Exception => log.warn(s"Cache LRU: ${e.getMessage}") }
      )
    }
  }
}
