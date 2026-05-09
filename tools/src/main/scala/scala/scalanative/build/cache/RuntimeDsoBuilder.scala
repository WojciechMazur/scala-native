package scala.scalanative
package build
package cache

import java.nio.file.Files

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration

import scala.scalanative.util.Scope

/** Builds and caches a dynamic library containing the Scala Native runtime classpath slice. */
private[scalanative] object RuntimeDsoBuilder {

  private val LogPrefix = "[cached libraries] scala-native-runtime DSO"

  /** @return path to `libscala-native-runtime.{so,dylib}` when built or already cached */
  def ensureRuntimeDso(config: Config)(implicit
      scope: Scope,
      ec: ExecutionContext
  ): Option[java.nio.file.Path] = {
    val log = config.logger
    if (config.targetsWindows) {
      log.debug(s"$LogPrefix: skipped (Windows is not supported for this cache path).")
      None
    } else {
      val rts = ClasspathPartition.runtimeJars(config.classPath)
      if (rts.isEmpty) {
        log.debug(
          s"$LogPrefix: skipped (no runtime jars matched on classpath; expected nativelib, javalib, scalalib, …)."
        )
        None
      } else {
        val native = config.compilerConfig
        if (!native.prebuildScalaNativeRuntimeDso) {
          log.info(
            s"$LogPrefix: not produced (`NativeConfig.prebuildScalaNativeRuntimeDso` is false). " +
              "No separate shared library is written to the cache; the application link still embeds the runtime as usual."
          )
          None
        } else {
          RuntimeBundlePaths.layout(config) match {
            case None => None
            case Some((dir, jars)) =>
              val dylib = RuntimeBundlePaths.dylibPath(dir, config)
              val jarSummary = summarizeJarNames(jars)
              if (RuntimeBundlePaths.isComplete(dir, config)) {
                RuntimeNirHeaders.ensure(dir, jars, log)
                log.info(
                  s"$LogPrefix: CACHE HIT — reusing external shared library\n" +
                    s"  path: ${dylib.toAbsolutePath}\n" +
                    s"  bundled classpath (${jars.size} jars): $jarSummary"
                )
                Some(dylib)
              } else {
                CacheFileLock.withLock(dir.resolve("populate.lock")) {
                  if (RuntimeBundlePaths.isComplete(dir, config)) {
                    RuntimeNirHeaders.ensure(dir, jars, log)
                    log.info(
                      s"$LogPrefix: CACHE HIT (after lock) — reusing external shared library\n" +
                        s"  path: ${dylib.toAbsolutePath}\n" +
                        s"  bundled classpath (${jars.size} jars): $jarSummary"
                    )
                    Some(dylib)
                  } else {
                    log.info(
                      s"$LogPrefix: CACHE MISS — linking a standalone shared library from the stdlib classpath\n" +
                        s"  output: ${dylib.toAbsolutePath}\n" +
                        s"  inputs (${jars.size} jars → one .so/.dylib): $jarSummary\n" +
                        "  (This link is separate from your application; later log lines for Linking native code include this library, then your app.)"
                    )
                    Files.createDirectories(dir)
                    val workBase = dir.resolve("work")
                    val rtConfig = config
                      .withClassPath(jars)
                      .withBaseDir(workBase)
                      .withModuleName("scala-native-runtime")
                      .withMainClass(None)
                      .withCompilerConfig(
                        native
                          .withBaseName("scala-native-runtime")
                          .withBuildTarget(BuildTarget.LibraryDynamic)
                          .withOptimize(false)
                          // Wide DSO reach can touch javalib VirtualThread stubs; treat as undefined
                          // at link time instead of failing on UnsupportedFeature.virtualThreads.
                          .withCheckFeatures(false)
                      )
                    Await.result(Build.build(rtConfig), Duration.Inf)
                    Files.writeString(dir.resolve("COMPLETE"), dylib.toAbsolutePath.toString)
                    Sidecar.write(
                      dir.resolve("header.sidecar"),
                      Sidecar.empty
                    )
                    RuntimeNirHeaders.ensure(dir, jars, log)
                    log.info(
                      s"$LogPrefix: CACHE POPULATED — external shared library installed\n" +
                        s"  path: ${dylib.toAbsolutePath}"
                    )
                    Some(dylib)
                  }
                }
              }
          }
        }
      }
    }
  }

  private def summarizeJarNames(paths: Seq[java.nio.file.Path]): String = {
    val names = paths.map(_.getFileName.toString).sorted
    if (names.lengthCompare(8) <= 0) names.mkString(", ")
    else names.take(8).mkString(", ") + s", … (+${names.length - 8} more)"
  }
}
