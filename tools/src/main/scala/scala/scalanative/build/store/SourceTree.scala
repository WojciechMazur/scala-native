package scala.scalanative
package build
package store

import java.nio.file.{Files, Path}

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

  def apply(root: Path, include: Path => Boolean): SourceTree = {
    val files = mutable.ArrayBuffer.empty[(String, Digest)]
    if (Files.isDirectory(root)) {
      val stream = Files.walk(root)
      try
        stream.forEach { path =>
          if (Files.isRegularFile(path) && include(path)) {
            val relative = root.relativize(path).toString.replace('\\', '/')
            files += relative -> Digest.of(path)
          }
        }
      finally stream.close()
    }
    SourceTree(root, files.sortBy(_._1).toSeq)
  }
}
