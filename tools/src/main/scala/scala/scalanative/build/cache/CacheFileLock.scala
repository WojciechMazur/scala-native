package scala.scalanative
package build
package cache

import java.nio.channels.FileChannel
import java.nio.file.{Files, Path, StandardOpenOption}

/** Exclusive lock for populating a cache directory (POSIX `flock` via FileLock). */
private[scalanative] object CacheFileLock {

  def withLock[A](lockFile: Path)(f: => A): A = {
    Files.createDirectories(lockFile.getParent)
    val channel =
      FileChannel.open(
        lockFile,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.APPEND
      )
    try {
      val fl = channel.lock()
      try f
      finally fl.release()
    } finally channel.close()
  }
}
