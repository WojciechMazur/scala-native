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
 *  logged and never fail the build, the action then simply runs. Without a
 *  store, outputs are only reused from `workDir`.
 *
 *  `workDir/action-index` remembers the action that produced each output and
 *  the digests of hashed inputs, so a build with nothing to do neither rehashes
 *  inputs nor reads the store.
 */
private[scalanative] final class BuildCache private (
    storeOption: Option[DiskStore],
    workDir: Path,
    logger: Logger,
    maxSizeBytes: Long
) {
  import BuildCache._

  private val indexPath = workDir.resolve("action-index")
  private val previous: Map[String, IndexEntry] = readIndex(indexPath)
  private val current = TrieMap.empty[String, IndexEntry]
  private val buildStart = System.currentTimeMillis()

  /** Digests of the outputs this build restored or stored, known without
   *  hashing them.
   */
  private val outputDigests = TrieMap.empty[String, InputEntry]

  /** The digest of `file`, reusing the one recorded by an earlier build if the
   *  file has not been modified since.
   */
  def digest(file: Path): Digest = {
    val path = file.toAbsolutePath().toString
    val size = Files.size(file)
    val mtime = Files.getLastModifiedTime(file).toMillis()
    val produced = outputDigests.get(path).collect {
      case input if input.size == size && input.mtime == mtime => input.digest
    }
    val known =
      produced.orElse(current.get(path).orElse(previous.get(path)).collect {
        case input: InputEntry
            if input.size == size && input.mtime == mtime &&
              input.isTrustworthy =>
          input.digest
      })
    known.getOrElse {
      val digest = Digest.of(file)
      current(path) = InputEntry(size, mtime, digest, recordedAt = buildStart)
      digest
    }
  }

  /** Makes the named `outputs` of the action `key` exist, running `execute`
   *  only if neither `workDir` nor the store has them.
   *
   *  @param exclusive
   *    whether builds in other processes wait for this action instead of
   *    running it at the same time, for actions taking long or much memory: the
   *    waiting ones restore its outputs afterwards
   */
  def run(
      key: ActionKey,
      outputs: Seq[(String, Path)],
      exclusive: Boolean = false
  )(
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
      // Outputs in use are the last to be evicted
      withStore(s"touch action $key")(())(_.touchAction(key))
      UpToDate
    } else
      withLock(key, exclusive) {
        val restored =
          withStore(s"read action $key")(Option.empty[ActionResult]) { store =>
            store.getAction(key).filter { result =>
              outputs.forall {
                case (name, path) =>
                  result.output(name).exists(store.getBlob(_, path))
              }
            }
          }
        restored.foreach { result =>
          outputs.foreach {
            case (name, path) =>
              recordOutput(key, path)
              result.output(name).foreach(recordDigest(path, _))
          }
        }
        if (restored.isDefined) Restored
        else {
          // Restored outputs may be links to stored blobs, never written to
          outputs.foreach { case (_, path) => Files.deleteIfExists(path) }
          execute
          withStore(s"store action $key")(()) { store =>
            val result = ActionResult(outputs.map {
              case (name, path) =>
                val digest = store.putBlob(path)
                recordDigest(path, digest)
                name -> digest
            })
            store.putAction(key, result)
          }
          outputs.foreach { case (_, path) => recordOutput(key, path) }
          Executed
        }
      }
  }

  /** Where shared libraries of library units are placed: in the store, or in
   *  `workDir` without one.
   */
  def libraryDir: Path =
    storeOption.fold(workDir.resolve("lib"))(_.libraries)

  /** Where the store keeps the sources of virtual locations, see
   *  `DiskStore.sourcesRoot`.
   */
  def sourcesRoot: Option[Path] = storeOption.map(_.sourcesRoot)

  /** The virtual location of `tree`, stored if not yet, see
   *  `DiskStore.sources`.
   */
  def sources(tree: SourceTree): Option[String] =
    withStore(s"store sources ${tree.root}")(Option.empty[String]) { store =>
      store.sources(tree)
      Some(DiskStore.virtualSources(tree.digest))
    }

  /** The unpacked sources of the jar `archive` and their virtual location, see
   *  `DiskStore.unpackedSources`.
   */
  def jarSources(archive: Path): Option[(Path, String)] =
    withStore(s"store sources of $archive")(Option.empty[(Path, String)]) {
      store =>
        val digest = this.digest(archive)
        Some(
          store.unpackedSources(archive, digest) ->
            DiskStore.virtualSources(digest)
        )
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
    withStore("collect garbage")(()) { store =>
      store.maybeCollectGarbage(maxSizeBytes).foreach { stats =>
        logger.debug(s"Collected garbage in ${store.root}: $stats")
      }
    }
  }

  private def recordDigest(output: Path, digest: Digest): Unit =
    outputDigests(output.toAbsolutePath().toString) = InputEntry(
      Files.size(output),
      Files.getLastModifiedTime(output).toMillis(),
      digest,
      recordedAt = buildStart
    )

  private def recordOutput(key: ActionKey, output: Path): Unit =
    current(output.toAbsolutePath().toString) = OutputEntry(
      key.hash,
      Files.size(output),
      Files.getLastModifiedTime(output).toMillis()
    )

  private def withStore[T](what: String)(fallback: => T)(
      body: DiskStore => T
  ): T = storeOption match {
    case None        => fallback
    case Some(store) =>
      try body(store)
      catch {
        case NonFatal(ex) =>
          logger.warn(s"Build cache in ${store.root}: cannot $what: $ex")
          fallback
      }
  }

  // Builds in this JVM running the same action wait for each other instead of
  // repeating it; across processes only exclusive actions do, other ones may
  // be repeated, which is safe
  private def withLock[T](key: ActionKey, exclusive: Boolean)(
      body: => T
  ): T = {
    val lockKey = s"${storeOption.fold(workDir)(_.root)}:${key.hash}"
    val lock = new Object
    val existing = locks.putIfAbsent(lockKey, lock)
    val owned = if (existing == null) lock else existing
    owned.synchronized {
      try {
        val processLock =
          if (!exclusive) None
          else
            withStore(s"lock action $key")(Option.empty[AutoCloseable]) {
              store =>
                Some(
                  store.lockAction(
                    key,
                    () =>
                      logger.info(
                        s"Waiting for another build running the same action in ${store.root}"
                      )
                  )
                )
            }
        try body
        finally processLock.foreach(_.close())
      } finally locks.remove(lockKey, lock)
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
          Some(new DiskStore(dir)),
          config.workDir,
          config.logger,
          Discover.buildCacheMaxSize()
        )
      }

  /** The library directory of the builds of `config`, see `libraryDir`. */
  def libraryDir(config: Config): Path =
    Discover
      .buildCacheDir()
      .filter(_ => config.compilerConfig.useGranularBuild)
      .fold(config.workDir.resolve("lib"))(new DiskStore(_).libraries)

  def apply(store: DiskStore, workDir: Path, logger: Logger): BuildCache =
    new BuildCache(Some(store), workDir, logger, DefaultMaxSizeBytes)

  /** A cache of `workDir` alone: outputs and input digests of earlier builds in
   *  the same directory.
   */
  def local(workDir: Path, logger: Logger): BuildCache =
    new BuildCache(None, workDir, logger, DefaultMaxSizeBytes)

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
