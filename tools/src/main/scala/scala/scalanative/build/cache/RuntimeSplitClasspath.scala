package scala.scalanative
package build
package cache

import java.nio.file.Files

import scala.scalanative.build.{BuildTarget, Config}
import scala.scalanative.util.Scope

/** Replaces stdlib JAR NIR with stripped headers and wires the final link against `libscala-native-runtime`. */
private[scalanative] object RuntimeSplitClasspath {

  def rewriteIfApplicable(config: Config)(implicit scope: Scope): Config = {
    if (!CachedLibraryBuild.enabled(config)) config
    else if (CachedLibraryBuild.isNestedRuntimeLibraryBuild(config)) config
    else if (!config.compilerConfig.prebuildScalaNativeRuntimeDso) config
    else if (config.compilerConfig.buildTarget != BuildTarget.Application) config
    else if (config.targetsWindows) config
    else {
      RuntimeBundlePaths.layout(config) match {
        case None => config
        case Some((dir, rts)) =>
          val dylib = RuntimeBundlePaths.dylibPath(dir, config)
          if (!Files.exists(dylib)) {
            config.logger.warn(
              "[cached libraries] Prebuilt runtime dylib not found; skipping NIR classpath split (run preBuild or enable prebuildScalaNativeRuntimeDso)."
            )
            config
          } else {
            RuntimeNirHeaders.ensure(dir, rts, config.logger)
            val hdr = RuntimeNirHeaders.root(dir)
            val rtSet = rts.toSet
            val rest = config.classPath.filterNot(rtSet.contains)
            val newCp = hdr +: rest
            val libDir = dylib.getParent.toAbsolutePath.normalize().toString
            val linkExtra = Seq(
              "-L" + libDir,
              "-Wl,-rpath," + libDir,
              "-lscala-native-runtime"
            )
            config.logger.info(
              "[cached libraries] Split NIR classpath: stripped stdlib under\n" +
                s"  ${hdr.toAbsolutePath}\n" +
                s"  (${rts.size} runtime JARs removed from NIR loading; C sources from those JARs are not re-unpacked.)\n" +
                s"  Final link flags include: ${linkExtra.mkString(" ")}\n" +
                "  Interflow optimization is disabled for this link (stripped external NIR is not yet supported by the optimizer)."
            )
            config
              .withClassPath(newCp)
              .withClasspathForMetaScan(rts)
              .withLinkApplicationAgainstPrebuiltRuntimeDylib(true)
              .withCompilerConfig(nc =>
                nc.withLinkingOptions(linkExtra ++ nc.linkingOptions)
                  .withOptimize(false)
              )
          }
      }
    }
  }
}
