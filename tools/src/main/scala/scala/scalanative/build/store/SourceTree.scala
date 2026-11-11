package scala.scalanative
package build
package store

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{
  FileVisitOption, FileVisitResult, Files, NoSuchFileException, Path,
  SimpleFileVisitor
}

import scala.collection.mutable

/** Files under `root`, by path relative to it with `/` separators, and the
 *  digest of the whole tree: the hash of its sorted `path, file digest` list.
 *  Renaming, adding or editing any file changes the digest; the location of the
 *  root does not.
 */
private[scalanative] final case class SourceTree(
    root: Path,
    files: Seq[(String, Digest)]
) {
  lazy val digest: Digest = Digest.of(manifest)

  /** The hashed form: one `path<TAB>digest` line per file, sorted by path. */
  def manifest: String = {
    val out = new java.lang.StringBuilder
    files.foreach {
      case (path, digest) =>
        out.append(path).append('\t').append(digest.toString).append('\n')
    }
    out.toString
  }
}

private[scalanative] object SourceTree {

  def apply(
      root: Path,
      include: Path => Boolean,
      digest: Path => Digest = Digest.of(_: Path)
  ): SourceTree = {
    val files = mutable.ArrayBuffer.empty[(String, Digest)]
    if (Files.isDirectory(root)) {
      // Files may come and go while walking, e.g. when the build directory
      // is inside the tree: they are not part of it
      val visitor = new SimpleFileVisitor[Path] {
        override def visitFile(
            path: Path,
            attrs: BasicFileAttributes
        ): FileVisitResult = {
          if (attrs.isRegularFile() && include(path)) {
            val relative = root.relativize(path).toString.replace('\\', '/')
            try files += relative -> digest(path)
            catch { case _: NoSuchFileException => () }
          }
          FileVisitResult.CONTINUE
        }
        override def visitFileFailed(
            path: Path,
            ex: IOException
        ): FileVisitResult = ex match {
          case _: NoSuchFileException => FileVisitResult.CONTINUE
          case _                      => throw ex
        }
      }
      Files.walkFileTree(
        root,
        java.util.EnumSet.of(FileVisitOption.FOLLOW_LINKS),
        Int.MaxValue,
        visitor
      )
    }
    SourceTree(root, files.sortBy(_._1).toSeq)
  }
}
