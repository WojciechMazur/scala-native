package scala.scalanative
package build
package cache

import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import java.nio.file.attribute.BasicFileAttributes
import java.util.Comparator

/** Best-effort LRU pruning under a cache root: removes oldest library cache directories
  * (directories that directly contain `header.sidecar`) until estimated total size <= maxBytes.
  */
private[scalanative] object CacheLru {

  def pruneIfNeeded(cacheRoot: Path, maxBytes: Long, logger: build.Logger): Unit = {
    if (maxBytes <= 0 || Files.notExists(cacheRoot)) ()
    else {
      val total = treeSize(cacheRoot)
      if (total <= maxBytes) ()
      else {
        val libDirs = findLibraryCacheDirs(cacheRoot)
        var remaining = total
        val target = (maxBytes * 0.9).toLong
        libDirs.sortBy(_._2).foreach { case (dir, _) =>
          if (remaining > target) {
            try {
              val sz = treeSize(dir)
              IOUtil.deleteRecursive(dir)
              remaining -= sz
              logger.debug(s"Cache LRU pruned $dir ($sz bytes)")
            } catch {
              case e: Exception =>
                logger.warn(s"Cache LRU: could not prune $dir: ${e.getMessage}")
            }
          }
        }
      }
    }
  }

  private def findLibraryCacheDirs(root: Path): Seq[(Path, Long)] = {
    val buf = scala.collection.mutable.ArrayBuffer.empty[(Path, Long)]
    try {
      Files.walkFileTree(
        root,
        new SimpleFileVisitor[Path] {
          override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
            if (file.getFileName != null && file.getFileName.toString == "header.sidecar") {
              val parent = file.getParent
              if (parent != null)
                buf += ((parent, Files.getLastModifiedTime(parent).toMillis))
            }
            FileVisitResult.CONTINUE
          }
        }
      )
    } catch {
      case _: Exception => ()
    }
    buf.distinct.toSeq
  }

  private def treeSize(root: Path): Long = {
    var sum = 0L
    try {
      Files.walkFileTree(
        root,
        new SimpleFileVisitor[Path] {
          override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
            if (attrs.isRegularFile) sum += attrs.size()
            FileVisitResult.CONTINUE
          }
        }
      )
    } catch { case _: Exception => () }
    sum
  }
}

private object IOUtil {
  def deleteRecursive(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        val paths = stream.sorted(Comparator.reverseOrder()).iterator()
        while (paths.hasNext) {
          val p = paths.next()
          try Files.deleteIfExists(p)
          catch { case _: Exception => () }
        }
      } finally stream.close()
    }
}
