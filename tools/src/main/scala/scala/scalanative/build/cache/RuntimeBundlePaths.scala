package scala.scalanative
package build
package cache

import java.nio.file.{Files, Path}

/** Locates the on-disk cache directory for the bundled scala-native-runtime dynamic library. */
private[cache] object RuntimeBundlePaths {

  /** Runtime jars and cache directory (independent of whether the dylib is already built). */
  def layout(config: Config): Option[(Path, Seq[Path])] = {
    if (config.targetsWindows) None
    else {
      val rts = ClasspathPartition.runtimeJars(config.classPath)
      if (rts.isEmpty || !config.compilerConfig.prebuildScalaNativeRuntimeDso) None
      else {
        val native = config.compilerConfig
        val cacheRoot = native.cacheRoot.getOrElse(CachePaths.defaultCacheRoot)
        val ghash = CacheKey.globalConfigHash(native)
        val content = rts.map(ClasspathPartition.jarContentSha256).sorted.mkString("|")
        // Bump prefix when DSO build inputs / toolchain semantics change (e.g. visibility).
        val libKey = CacheKey.sha256Hex("RUNTIME|v4-dso-entry-allowlist|" + content)
        val dir =
          cacheRoot
            .resolve(CachePaths.scalaNativeVersion)
            .resolve(ghash)
            .resolve("scala-native")
            .resolve("runtime")
            .resolve(libKey)
        Some((dir, rts))
      }
    }
  }

  def dylibFileName(config: Config): String =
    if (config.targetsMac) "libscala-native-runtime.dylib"
    else "libscala-native-runtime.so"

  /** Dynamic library produced by nested `Build.build` lives under `cacheDir/work/` (see [[Config.buildPath]]). */
  def dylibPath(cacheDir: Path, config: Config): Path =
    cacheDir.resolve("work").resolve(dylibFileName(config))

  def isComplete(cacheDir: Path, config: Config): Boolean = {
    val dylib = dylibPath(cacheDir, config)
    Files.exists(cacheDir.resolve("COMPLETE")) && Files.exists(dylib)
  }
}
