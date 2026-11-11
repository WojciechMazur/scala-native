package scala.scalanative
package build

import java.nio.file.Path

import scala.scalanative.build.store.{ActionKey, BuildCache, Digest}

/** What a build of a config depends on, known before running it.
 *
 *  @param outputKey
 *    digest of every input of the artifact: the content of the classpath, the
 *    compiler configuration, the identity of the compilers and the Scala Native
 *    version. Builds with the same key produce the same artifact, so a build
 *    tool can use it as the input hash of its link task and skip the toolchain
 *    when it did not change. It does not depend on the build directory or on
 *    file timestamps.
 *  @param inputs
 *    the named digests the key is made of, to tell why it changed
 */
final class BuildPlan private[build] (
    val outputKey: String,
    val inputs: Seq[(String, String)]
) {
  override def toString: String =
    inputs
      .map { case (name, digest) => s"  $name: $digest" }
      .mkString(s"BuildPlan($outputKey\n", "\n", ")")
}

private[build] object BuildPlan {

  def apply(config: Config): BuildPlan = {
    val cache = BuildCache.local(config.workDir, config.logger)
    try {
      val nativeConfig = config.compilerConfig
      def entries(paths: Seq[Path]): Seq[(Path, Digest)] =
        NativeLib.filterClasspath(paths).map { entry =>
          entry -> units.Libraries.digest(entry, cache)
        }
      val classpath = entries(config.classPath)
      // Sources only end up in the artifact as debug information
      val sources =
        if (nativeConfig.sourceLevelDebuggingConfig.enabled)
          entries(config.sourcesClassPath)
        else Nil
      val inputs = Seq(
        "scala-native" -> Digest.of(nir.Versions.current),
        "clang" -> BuildCache.toolchain(nativeConfig.clang),
        "clang++" -> BuildCache.toolchain(nativeConfig.clangPP),
        "config" -> Digest.of(canonical(nativeConfig)),
        "artifact" -> Digest.of(
          canonical(
            Seq(config.mainClass, config.testConfig, config.baseName)
          )
        ),
        "classpath" -> Digest.of(classpath.map(_._2.toString).mkString("\n")),
        "sources" -> Digest.of(sources.map(_._2.toString).mkString("\n"))
      )
      val key = inputs
        .foldLeft(ActionKey.builder("artifact", version = 1)) {
          case (builder, (name, digest)) => builder.add(name, digest)
        }
        .result()
      new BuildPlan(key.hash, inputs.map { case (n, d) => n -> d.hash })
    } finally cache.close()
  }

  /** A rendering of configuration values that is equal for equal values: maps
   *  and sets are sorted, case classes are shown field by field.
   */
  private[build] def canonical(value: Any): String = value match {
    // Before Iterable: a path is an Iterable of its components
    case path: Path                      => s"Path(${path.toString})"
    case map: scala.collection.Map[_, _] =>
      map.toSeq
        .map { case (k, v) => s"${canonical(k)}->${canonical(v)}" }
        .sorted
        .mkString("Map(", ",", ")")
    case set: scala.collection.Set[_] =>
      set.toSeq.map(canonical).sorted.mkString("Set(", ",", ")")
    case seq: Iterable[_] => seq.map(canonical).mkString("Seq(", ",", ")")
    case Some(v)          => s"Some(${canonical(v)})"
    case None             => "None"
    case product: Product if product.productArity > 0 =>
      product.productIterator
        .map(canonical)
        .mkString(product.productPrefix + "(", ",", ")")
    case other => other.toString
  }
}
