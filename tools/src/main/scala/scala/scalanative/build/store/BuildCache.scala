package scala.scalanative
package build
package store

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.ConcurrentHashMap

import scala.collection.concurrent.TrieMap
import scala.sys.process._
import scala.util.control.NonFatal

/** The action store as seen by one build in `workDir`.
 *
 *  Actions run through `run`: their outputs are reused when they are up to date
 *  in `workDir`, copied from the store when another build already ran the same
 *  action, and stored after running otherwise. Problems with the store are
 *  logged and never fail the build, the action then simply runs.
 *
 *  `workDir/action-index` remembers the action that produced each output and
 *  the digests of hashed inputs, so a build with nothing to do neither rehashes
 *  inputs nor reads the store.
 */
private[scalanative] final class BuildCache private (
    val store: DiskStore,
    workDir: Path,
    logger: Logger,
    maxSizeBytes: Long
) {
  import BuildCache._

  private val indexPath = workDir.resolve("action-index")
  private val previous: Map[String, IndexEntry] = readIndex(indexPath)
  private val current = TrieMap.empty[String, IndexEntry]
  private val buildStart = System.currentTimeMillis()

  /** The digest of `file`, reusing the one recorded by an earlier build if the
   *  file has not been modified since.
   */
  def digest(file: Path): Digest = {
    val path = file.toAbsolutePath().toString
    val size = Files.size(file)
    val mtime = Files.getLastModifiedTime(file).toMillis()
    val known = current.get(path).orElse(previous.get(path)).collect {
      case input: InputEntry
          if input.size == size && input.mtime == mtime &&
            input.isTrustworthy =>
        input.digest
    }
    known.getOrElse {
      val digest = Digest.of(file)
      current(path) = InputEntry(size, mtime, digest, recordedAt = buildStart)
      digest
    }
  }

  /** Makes the named `outputs` of the action `key` exist, running `execute`
   *  only if neither `workDir` nor the store has them.
   */
  def run(key: ActionKey, outputs: Seq[(String, Path)])(
      execute: => Unit
  ): Outcome = {
    def isUpToDate(output: Path): Boolean =
      previous.get(output.toAbsolutePath().toString).exists {
        case entry: OutputEntry =>
          entry.key == key.hash && entry.matches(output)
        case _ => false
      }

    if (outputs.forall { case (_, path) => isUpToDate(path) }) {
      outputs.foreach { case (_, path) => recordOutput(key, path) }
      UpToDate
    } else
      withLock(key) {
        val restored = tryStore(s"read action $key")(false) {
          store.getAction(key).exists { result =>
            outputs.forall {
              case (name, path) =>
                result.output(name).exists(store.getBlob(_, path))
            }
          }
        }
        if (restored) {
          outputs.foreach { case (_, path) => recordOutput(key, path) }
          Restored
        } else {
          execute
          tryStore(s"store action $key")(()) {
            val result = ActionResult(outputs.map {
              case (name, path) => name -> store.putBlob(path)
            })
            store.putAction(key, result)
          }
          outputs.foreach { case (_, path) => recordOutput(key, path) }
          Executed
        }
      }
  }

  /** The location of `tree` in the store, see `DiskStore.sources`. */
  def sources(tree: SourceTree): Option[Path] =
    tryStore(s"store sources ${tree.root}")(Option.empty[Path]) {
      Some(store.sources(tree))
    }

  /** Saves the index and occasionally collects garbage in the store. */
  def close(): Unit = {
    val entries = (previous ++ current).filter {
      case (path, _) => Files.exists(java.nio.file.Paths.get(path))
    }
    try writeIndex(indexPath, entries)
    catch {
      case ex: IOException =>
        logger.debug(s"Cannot write action index $indexPath: $ex")
    }
    tryStore("collect garbage")(()) {
      store.maybeCollectGarbage(maxSizeBytes).foreach { stats =>
        logger.debug(s"Collected garbage in ${store.root}: $stats")
      }
    }
  }

  private def recordOutput(key: ActionKey, output: Path): Unit =
    current(output.toAbsolutePath().toString) = OutputEntry(
      key.hash,
      Files.size(output),
      Files.getLastModifiedTime(output).toMillis()
    )

  private def tryStore[T](what: String)(fallback: => T)(body: => T): T =
    try body
    catch {
      case NonFatal(ex) =>
        logger.warn(s"Build cache in ${store.root}: cannot $what: $ex")
        fallback
    }

  // Builds in this JVM running the same action wait for each other instead of
  // repeating it; across processes the work may be repeated, which is safe
  private def withLock[T](key: ActionKey)(body: => T): T = {
    val lockKey = s"${store.root}:${key.hash}"
    val lock = new Object
    val existing = locks.putIfAbsent(lockKey, lock)
    val owned = if (existing == null) lock else existing
    owned.synchronized {
      try body
      finally locks.remove(lockKey, lock)
    }
  }
}

private[scalanative] object BuildCache {
  sealed abstract class Outcome
  case object UpToDate extends Outcome
  case object Restored extends Outcome
  case object Executed extends Outcome

  final val DefaultMaxSizeBytes: Long = 10L * 1024 * 1024 * 1024
  // Mtime resolution of common file systems, and then some
  private final val RacyIntervalMillis = 2000L

  private val locks = new ConcurrentHashMap[String, Object]()

  /** The store used by `config`, if any: granular builds use the store at
   *  `Discover.buildCacheDir()`.
   */
  def open(config: Config): Option[BuildCache] =
    if (!config.compilerConfig.useGranularBuild) None
    else
      Discover.buildCacheDir().map { dir =>
        new BuildCache(
          new DiskStore(dir),
          config.workDir,
          config.logger,
          Discover.buildCacheMaxSize()
        )
      }

  def apply(store: DiskStore, workDir: Path, logger: Logger): BuildCache =
    new BuildCache(store, workDir, logger, DefaultMaxSizeBytes)

  private val toolchains = TrieMap.empty[(String, Long, Long), Digest]

  /** Identifies the compiler at `path`: its version, default target and the
   *  Scala Native version, without the machine-specific install location.
   */
  def toolchain(path: Path): Digest = {
    val file = path.toFile()
    toolchains.getOrElseUpdate(
      (file.getAbsolutePath(), file.lastModified(), file.length()), {
        val version = Process(Seq(file.getAbsolutePath(), "--version")).!!
        val identity = version.linesIterator
          .filterNot(_.startsWith("InstalledDir:"))
          .mkString("\n")
        Digest.of(s"scala-native ${nir.Versions.current}\n$identity")
      }
    )
  }

  private sealed abstract class IndexEntry
  private final case class OutputEntry(key: String, size: Long, mtime: Long)
      extends IndexEntry {
    def matches(path: Path): Boolean =
      try
        Files.size(path) == size &&
          Files.getLastModifiedTime(path).toMillis() == mtime
      catch { case _: IOException => false }
  }
  private final case class InputEntry(
      size: Long,
      mtime: Long,
      digest: Digest,
      recordedAt: Long
  ) extends IndexEntry {
    // A file modified right before it was hashed may be modified again
    // without changing its mtime, its digest is only reused once hashed later
    def isTrustworthy: Boolean = mtime < recordedAt - RacyIntervalMillis
  }

  private def readIndex(path: Path): Map[String, IndexEntry] =
    try {
      if (!Files.exists(path)) Map.empty
      else {
        val text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
        text
          .split('\n')
          .iterator
          .flatMap { line =>
            line.split('\t') match {
              case Array("output", file, key, size, mtime) =>
                Some(file -> OutputEntry(key, size.toLong, mtime.toLong))
              case Array("input", file, size, mtime, digest, recordedAt) =>
                Digest.parse(digest).map { digest =>
                  file -> InputEntry(
                    size.toLong,
                    mtime.toLong,
                    digest,
                    recordedAt.toLong
                  )
                }
              case _ => None
            }
          }
          .toMap
      }
    } catch {
      case _: IOException | _: NumberFormatException => Map.empty
    }

  private def writeIndex(path: Path, entries: Map[String, IndexEntry]): Unit = {
    val out = new java.lang.StringBuilder
    entries.toSeq.sortBy(_._1).foreach {
      case (file, OutputEntry(key, size, mtime)) =>
        out.append(s"output\t$file\t$key\t$size\t$mtime\n")
      case (file, InputEntry(size, mtime, digest, recordedAt)) =>
        out.append(s"input\t$file\t$size\t$mtime\t$digest\t$recordedAt\n")
    }
    IO.write(path, out.toString)
  }
}
