package scala.scalanative
package build

import java.nio.file.{Files, Path, StandardCopyOption}

import scala.scalanative.build.store.{ActionKey, BuildCache, Digest, DiskStore}

/** What a build of a config depends on, known before running it.
 *
 *  @param outputKey
 *    digest of every input of the artifact: the content of the classpath, the
 *    compiler configuration, the identity of the compilers and the Scala Native
 *    version. Builds with the same key produce the same artifact, so a build
 *    tool can use it as the input hash of its link task and skip the toolchain
 *    when it did not change. It does not depend on the build directory or on
 *    file timestamps. Executables linked against shared libraries depend on the
 *    directory of these libraries, see `BuildCache.libraryDir`.
 *  @param inputs
 *    the named digests the key is made of, to tell why it changed
 *  @param libraries
 *    for executables linked against shared libraries, the record of the
 *    libraries the artifact of this key needs, written by the build
 */
final class BuildPlan private[build] (
    val outputKey: String,
    val inputs: Seq[(String, String)],
    libraries: Option[Path]
) {

  /** Whether an artifact of this key, kept by a build tool, works without
   *  running the build: an executable linked against shared libraries needs
   *  them, they may have been collected or never built on this machine.
   */
  def restorable: Boolean = libraries.forall { record =>
    try {
      val names = new String(Files.readAllBytes(record), "UTF-8")
        .split('\n')
        .filter(_.nonEmpty)
      // Records are in a child of the library directory
      val dir = record.getParent().getParent()
      names.forall(name => Files.isRegularFile(dir.resolve(name)))
    } catch { case _: java.io.IOException => false }
  }

  /** Records the libraries the artifact needs, see `restorable`. */
  private[build] def recordLibraries(names: Seq[Path]): Unit =
    libraries.foreach { record =>
      val content = names.map(_.getFileName.toString).mkString("\n")
      Files.createDirectories(record.getParent())
      val tmp = Files.createTempFile(record.getParent(), ".record", ".tmp")
      IO.write(tmp, content)
      Files.move(tmp, record, StandardCopyOption.REPLACE_EXISTING)
    }

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
      // Executables refer to the absolute location of the libraries
      val libraryDir =
        if (nativeConfig.useDynamicLibraries && Build.usesUnits(config))
          Some(BuildCache.libraryDir(config).toAbsolutePath())
        else None
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
      ) ++ libraryDir.map(dir => "libraries" -> Digest.of(dir.toString))
      val key = inputs
        .foldLeft(ActionKey.builder("artifact", version = 1)) {
          case (builder, (name, digest)) => builder.add(name, digest)
        }
        .result()
      new BuildPlan(
        key.hash,
        inputs.map { case (n, d) => n -> d.hash },
        libraryDir.map(
          _.resolve(DiskStore.ProgramsDir).resolve(key.hash)
        )
      )
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
