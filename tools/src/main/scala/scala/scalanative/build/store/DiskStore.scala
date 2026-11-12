package scala.scalanative
package build
package store

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.FileTime
import java.nio.file.{
  AtomicMoveNotSupportedException, FileSystemException, Files,
  NoSuchFileException, Path, StandardCopyOption, StandardOpenOption
}
import java.security.DigestOutputStream
import java.util.UUID

import scala.collection.mutable

/** Action and blob store in a local directory, shared by every build on the
 *  machine:
 *
 *  {{{
 *  <root>/ac/<2 hex>/<action key>    action results
 *  <root>/cas/<2 hex>/<sha256>       blobs
 *  <root>/sources/<tree digest>/     source trees referenced by debug info
 *  <root>/lib/                       shared libraries programs load, linked
 *                                    to their blobs
 *  <root>/tmp/                       staging area for atomic publishing
 *  <root>/locks/<2 hex>/<action key> locks of long actions, see `lockAction`
 *  }}}
 *
 *  Every entry is written to `tmp` and moved into place, so readers see either
 *  nothing or a complete entry and never need a lock. Concurrent writers of the
 *  same entry produce the same content, the last move wins. Reads verify blob
 *  contents, a corrupt blob is deleted and reported as missing.
 *
 *  Except on Windows, blobs are read-only and restored as hard links: outputs
 *  take no space of their own, and stay real files, which loaders resolving
 *  paths relative to a library need. Outputs must be replaced, never written in
 *  place.
 */
private[scalanative] final class DiskStore(val root: Path) extends ActionStore {
  import DiskStore.{LockPollMillis, linksBlobs}

  private val acDir = root.resolve("ac")
  private val casDir = root.resolve("cas")
  private val sourcesDir = root.resolve("sources")
  private val tmpDir = root.resolve("tmp")
  private val locksDir = root.resolve("locks")

  /** Where shared libraries of library units are placed, see
   *  `units.UnitCompile`.
   */
  val libraries: Path = root.resolve("lib")

  private def acPath(key: ActionKey): Path =
    acDir.resolve(key.hash.substring(0, 2)).resolve(key.hash)

  private[store] def casPath(digest: Digest): Path =
    casDir.resolve(digest.hash.substring(0, 2)).resolve(digest.hash)

  private def hasBlob(digest: Digest): Boolean = {
    val path = casPath(digest)
    try Files.size(path) == digest.sizeBytes
    catch { case _: IOException => false }
  }

  def getAction(key: ActionKey): Option[ActionResult] = {
    val path = acPath(key)
    val text =
      try Some(new String(Files.readAllBytes(path), StandardCharsets.UTF_8))
      catch { case _: IOException => None }
    text.flatMap { text =>
      ActionResult.parse(text) match {
        case None =>
          deleteQuietly(path)
          None
        case Some(result) if result.outputs.forall(o => hasBlob(o._2)) =>
          // Recently used results are the last to be evicted
          touch(path)
          Some(result)
        case Some(_) => None
      }
    }
  }

  /** Marks the result of `key` as recently used. */
  def touchAction(key: ActionKey): Unit = touch(acPath(key))

  def putAction(key: ActionKey, result: ActionResult): Unit = {
    result.outputs.foreach {
      case (name, digest) =>
        if (!hasBlob(digest))
          throw new IllegalStateException(
            s"Output $name of action $key is not stored: $digest"
          )
    }
    val bytes = result.serialize.getBytes(StandardCharsets.UTF_8)
    publish(acPath(key)) { tmp => Files.write(tmp, bytes) }
  }

  def putBlob(file: Path): Digest = {
    val tmp = newTempFile()
    try {
      // Hash what is stored rather than what was read before, the file may
      // change in between
      val md = Digest.newMessageDigest()
      val out = new DigestOutputStream(Files.newOutputStream(tmp), md)
      try Files.copy(file, out)
      finally out.close()
      val digest = Digest(Digest.hex(md.digest()), Files.size(tmp))
      if (!hasBlob(digest)) {
        makeReadOnly(tmp)
        moveIntoPlace(tmp, casPath(digest))
      }
      // The output becomes a link as when restored
      if (linksBlobs) {
        val parent = file.toAbsolutePath().getParent()
        val linked = parent.resolve(s".sn-blob-${UUID.randomUUID()}.tmp")
        try if (link(casPath(digest), linked)) moveIntoPlace(linked, file)
        finally deleteQuietly(linked)
      }
      digest
    } finally deleteQuietly(tmp)
  }

  def getBlob(digest: Digest, target: Path): Boolean = {
    val blob = casPath(digest)
    if (!hasBlob(digest)) false
    else {
      val parent = target.toAbsolutePath().getParent()
      Files.createDirectories(parent)
      val tmp = parent.resolve(s".sn-blob-${UUID.randomUUID()}.tmp")
      try {
        val restored =
          if (linksBlobs && link(blob, tmp)) hashOf(blob) == digest.hash
          else copy(blob, tmp) == digest.hash
        if (!restored) {
          deleteQuietly(blob)
          false
        } else {
          moveIntoPlace(tmp, target)
          true
        }
      } catch {
        case _: NoSuchFileException => false // evicted concurrently
      } finally deleteQuietly(tmp)
    }
  }

  // Hard links need the same file system, outputs are copied otherwise
  private def link(blob: Path, target: Path): Boolean =
    try {
      makeReadOnly(blob) // blobs stored by earlier versions
      Files.createLink(target, blob)
      true
    } catch {
      case _: UnsupportedOperationException | _: FileSystemException => false
    }

  // Copies `blob` to `target`, returns the hash of what was copied
  private def copy(blob: Path, target: Path): String = {
    val md = Digest.newMessageDigest()
    val out = new DigestOutputStream(Files.newOutputStream(target), md)
    try Files.copy(blob, out)
    finally out.close()
    Digest.hex(md.digest())
  }

  private def hashOf(file: Path): String = {
    val md = Digest.newMessageDigest()
    val in = Files.newInputStream(file)
    try {
      val buffer = new Array[Byte](1 << 16)
      var read = in.read(buffer)
      while (read >= 0) {
        md.update(buffer, 0, read)
        read = in.read(buffer)
      }
    } finally in.close()
    Digest.hex(md.digest())
  }

  private def makeReadOnly(path: Path): Unit =
    if (linksBlobs) path.toFile().setWritable(false, false)

  /** Locks the action `key` against other processes using this store, waiting
   *  while another one holds it, so that builds running at once do not all run
   *  the same long action: the ones waiting find its result afterwards. Locks
   *  of a process are released when it exits. The lock of one action must be
   *  taken once per process: file locks do not exclude threads.
   */
  def lockAction(key: ActionKey, onWait: () => Unit): AutoCloseable = {
    val path = locksDir.resolve(key.hash.substring(0, 2)).resolve(key.hash)
    Files.createDirectories(path.getParent())
    val channel = FileChannel.open(
      path,
      StandardOpenOption.CREATE,
      StandardOpenOption.WRITE
    )
    try {
      // Polling: a blocking lock fails when the kernel sees a cycle between
      // processes, each holding a lock in one thread and waiting in another,
      // although every thread would get its lock eventually
      var lock = channel.tryLock()
      if (lock == null) {
        onWait()
        while (lock == null) {
          Thread.sleep(LockPollMillis)
          lock = channel.tryLock()
        }
      }
      val held = lock
      new AutoCloseable {
        def close(): Unit =
          try held.release()
          finally channel.close()
      }
    } catch {
      case ex: Throwable =>
        channel.close()
        throw ex
    }
  }

  /** The files referred to by the virtual paths of debug information, see
   *  `DiskStore.virtualSources`.
   */
  def sourcesRoot: Path = sourcesDir

  /** A copy of `tree`, made if not stored yet. Object files built from the tree
   *  refer to it through `DiskStore.virtualSources(tree.digest)`.
   */
  def sources(tree: SourceTree): Path =
    publishSources(tree.digest) { tmp =>
      tree.files.foreach {
        case (relative, _) =>
          val target = tmp.resolve(relative)
          Files.createDirectories(target.getParent())
          Files.copy(tree.root.resolve(relative), target)
      }
    }

  /** The sources of the jar `archive`, whose digest is `digest`, unpacked if
   *  not stored yet. Code generated from them refers to them through
   *  `DiskStore.virtualSources(digest)`.
   */
  def unpackedSources(archive: Path, digest: Digest): Path =
    publishSources(digest)(IO.unzip(archive, _))

  private def publishSources(digest: Digest)(fill: Path => Unit): Path = {
    val dir = sourcesDir.resolve(digest.hash)
    if (Files.isDirectory(dir)) touch(dir)
    else {
      Files.createDirectories(tmpDir)
      val tmp = Files.createTempDirectory(tmpDir, "sources-")
      try {
        fill(tmp)
        Files.createDirectories(sourcesDir)
        try Files.move(tmp, dir, StandardCopyOption.ATOMIC_MOVE)
        catch {
          // Published concurrently: the failure is a plain
          // FileSystemException on macOS
          case _: FileSystemException if Files.isDirectory(dir) => ()
        }
      } finally if (Files.exists(tmp)) IO.deleteRecursive(tmp)
    }
    dir
  }

  /** Evicts least recently used action results until the store holds at most
   *  `maxSizeBytes`, then deletes blobs and source trees no remaining result
   *  refers to. Entries younger than `grace` are kept: a concurrent build may
   *  have stored blobs whose result is not published yet.
   */
  def collectGarbage(
      maxSizeBytes: Long,
      grace: Long = DiskStore.DefaultGraceMillis
  ): DiskStore.GcStats = {
    val now = System.currentTimeMillis()
    case class Entry(path: Path, lastUsed: Long, result: ActionResult)
    val entries = mutable.ArrayBuffer.empty[Entry]
    forEachFile(acDir) { path =>
      val result =
        try
          ActionResult.parse(
            new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
          )
        catch { case _: IOException => None }
      result match {
        case Some(result) =>
          entries += Entry(path, lastModified(path), result)
        case None if now - lastModified(path) > grace => deleteQuietly(path)
        case None                                     => ()
      }
    }

    val references = mutable.HashMap.empty[String, Int]
    var liveBytes = 0L
    entries.foreach { entry =>
      liveBytes += 1024 // an action result and its directory entry
      entry.result.outputs.foreach {
        case (_, digest) =>
          val count = references.getOrElse(digest.hash, 0)
          if (count == 0) liveBytes += digest.sizeBytes
          references(digest.hash) = count + 1
      }
    }

    // Evict down to a low watermark, so that collections stay rare
    var evicted = 0
    var evictedUntil = Long.MinValue
    if (liveBytes > maxSizeBytes) {
      val lowWatermark = maxSizeBytes / 10 * 8
      val byLastUse = entries.sortBy(_.lastUsed).iterator
      while (liveBytes > lowWatermark && byLastUse.hasNext) {
        val entry = byLastUse.next()
        if (now - entry.lastUsed > grace) {
          deleteQuietly(entry.path)
          evicted += 1
          evictedUntil = entry.lastUsed
          liveBytes -= 1024
          entry.result.outputs.foreach {
            case (_, digest) =>
              val count = references(digest.hash) - 1
              if (count == 0) {
                references.remove(digest.hash)
                liveBytes -= digest.sizeBytes
              } else references(digest.hash) = count
          }
        }
      }
    }

    var deletedBlobs = 0
    var freedBytes = 0L
    forEachFile(casDir) { path =>
      val hash = path.getFileName().toString
      if (!references.contains(hash) && now - lastModified(path) > grace) {
        val size =
          try Files.size(path)
          catch { case _: IOException => 0L }
        if (deleteQuietly(path)) {
          deletedBlobs += 1
          freedBytes += size
        }
      }
    }
    // Source trees are used together with the objects built from them
    listDir(sourcesDir).foreach { dir =>
      if (lastModified(dir) <= evictedUntil) IO.deleteRecursive(dir)
    }
    // Libraries whose blob is gone, and programs needing them
    listDir(libraries).foreach { path =>
      if (Files.isRegularFile(path) && linkCount(path) == 1 &&
          now - lastModified(path) > grace)
        deleteQuietly(path)
    }
    listDir(libraries.resolve(DiskStore.ProgramsDir)).foreach { record =>
      val needed =
        try Files.readAllLines(record).toArray.toSeq.map(_.toString)
        catch { case _: IOException => Nil }
      if (!needed.forall(name => Files.exists(libraries.resolve(name))))
        deleteQuietly(record)
    }
    listDir(tmpDir).foreach { path =>
      if (now - lastModified(path) > grace) IO.deleteRecursive(path)
    }
    DiskStore.GcStats(
      actions = entries.size - evicted,
      evictedActions = evicted,
      deletedBlobs = deletedBlobs,
      freedBytes = freedBytes,
      liveBytes = liveBytes
    )
  }

  /** Runs `collectGarbage` if the last collection is older than `interval`. The
   *  stamp is updated first, so concurrent builds do not both collect.
   */
  def maybeCollectGarbage(
      maxSizeBytes: Long,
      interval: Long = DiskStore.DefaultGcIntervalMillis
  ): Option[DiskStore.GcStats] = {
    val stamp = root.resolve("gc-stamp")
    val now = System.currentTimeMillis()
    if (Files.exists(stamp) && now - lastModified(stamp) < interval) None
    else {
      Files.createDirectories(root)
      if (!Files.exists(stamp)) IO.write(stamp, Array.emptyByteArray)
      touch(stamp)
      Some(collectGarbage(maxSizeBytes))
    }
  }

  private def newTempFile(): Path = {
    Files.createDirectories(tmpDir)
    Files.createTempFile(tmpDir, "blob-", ".tmp")
  }

  /** Writes a new file in the staging area and moves it to `target`. */
  private def publish(target: Path)(write: Path => Unit): Unit = {
    val tmp = newTempFile()
    try {
      write(tmp)
      moveIntoPlace(tmp, target)
    } finally deleteQuietly(tmp)
  }

  private def moveIntoPlace(tmp: Path, target: Path): Unit = {
    Files.createDirectories(target.getParent())
    try
      Files.move(
        tmp,
        target,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING
      )
    catch {
      case _: AtomicMoveNotSupportedException =>
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private def touch(path: Path): Unit =
    try
      Files.setLastModifiedTime(
        path,
        FileTime.fromMillis(System.currentTimeMillis())
      )
    catch { case _: IOException => () }

  private def linkCount(path: Path): Int =
    try Files.getAttribute(path, "unix:nlink").asInstanceOf[Int]
    catch { case _: Exception => 0 }

  private def lastModified(path: Path): Long =
    try Files.getLastModifiedTime(path).toMillis()
    catch { case _: IOException => 0L }

  private def deleteQuietly(path: Path): Boolean =
    try Files.deleteIfExists(path)
    catch { case _: IOException => false }

  private def listDir(dir: Path): Seq[Path] =
    if (!Files.isDirectory(dir)) Nil
    else {
      val out = mutable.ArrayBuffer.empty[Path]
      val stream = Files.list(dir)
      try stream.forEach(out += _)
      finally stream.close()
      out.toSeq
    }

  // Files of a two-level fan-out directory
  private def forEachFile(dir: Path)(f: Path => Unit): Unit =
    listDir(dir).foreach { shard =>
      if (Files.isDirectory(shard)) listDir(shard).foreach(f)
    }
}

private[scalanative] object DiskStore {
  final val DefaultGraceMillis: Long = 60L * 60 * 1000
  final val DefaultGcIntervalMillis: Long = 24L * 60 * 60 * 1000

  /** Records of the libraries each program needs, in the library directory, see
   *  `BuildPlan`.
   */
  final val ProgramsDir = "programs"

  private final val LockPollMillis = 25L

  private val linksBlobs = !scala.util.Properties.isWin

  /** Where debug information of stored code says its sources are: the same on
   *  every machine, so that stored code does not depend on the location of the
   *  store. Debuggers map it to `sourcesRoot`.
   */
  final val VirtualSourceRoot = "/scala-native-sources"

  /** The virtual location of the sources with `digest`. */
  def virtualSources(digest: Digest): String =
    s"$VirtualSourceRoot/${digest.hash}"

  final case class GcStats(
      actions: Int,
      evictedActions: Int,
      deletedBlobs: Int,
      freedBytes: Long,
      liveBytes: Long
  )
}
