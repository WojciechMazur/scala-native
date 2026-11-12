package scala.scalanative
package build
package units

import java.nio.file.{Files, Path}
import java.util.concurrent.CompletableFuture

import scala.scalanative.build.store.{ActionKey, BuildCache, Digest, SourceTree}
import scala.scalanative.util.Scope

/** Libraries of the classpath, derived as stored actions. */
private[scalanative] object Libraries {

  /** Opens the library of every entry of the classpath of `config`, deriving
   *  those not in `workDir` or the store. Libraries are derived in parallel and
   *  closed with `scope`.
   */
  def open(config: Config, cache: BuildCache)(implicit
      scope: Scope
  ): Seq[(Path, Library.Reader)] = {
    val dir = config.workDir.resolve("libraries")
    val derivations = config.classPath.map { entry =>
      CompletableFuture.supplyAsync[(Path, Path)](() =>
        entry -> derive(entry, dir, cache, config.logger)
      )
    }
    val libraries = derivations.map(_.join())
    val used = libraries.map(_._2.getFileName().toString).toSet
    if (Files.isDirectory(dir)) {
      val stream = Files.list(dir)
      try
        stream.forEach { file =>
          if (!used.contains(file.getFileName().toString))
            Files.deleteIfExists(file)
        }
      finally stream.close()
    }
    libraries.map {
      case (entry, file) =>
        val reader = Library.Reader
          .open(file, origin = entry)
          .getOrElse(
            throw new BuildException(s"Unreadable library $file of $entry")
          )
        scope.acquire(reader)
        entry -> reader
    }
  }

  /** The digest of the content of classpath `entry` that libraries are derived
   *  from: the jar itself, or the NIR files and service declarations of a
   *  directory.
   */
  def digest(entry: Path, cache: BuildCache): Digest =
    if (Files.isDirectory(entry))
      SourceTree(
        entry,
        include = DeriveLibrary.isLibraryFile(entry, _),
        digest = cache.digest(_)
      ).digest
    else cache.digest(entry)

  private def derive(
      entry: Path,
      dir: Path,
      cache: BuildCache,
      logger: Logger
  ): Path = {
    val entryDigest = digest(entry, cache)
    val key = ActionKey
      .builder("derive-library", Library.FormatVersion)
      .add("scala-native", nir.Versions.current)
      .add("entry", entryDigest)
      .result()
    val target = dir.resolve(s"${entryDigest.hash}.snl")
    cache.run(key, Seq("library" -> target), exclusive = true) {
      Scope { implicit in =>
        DeriveLibrary(entry, entryDigest, target, logger)
      }
    }
    target
  }
}
