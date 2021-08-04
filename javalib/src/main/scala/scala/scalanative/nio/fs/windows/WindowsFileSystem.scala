package scala.scalanative.nio.fs.windows

import java.lang.Iterable
import java.nio.file.{
  FileStore,
  FileSystem,
  Path,
  PathMatcher,
  PathMatcherImpl,
  WatchService
}
import java.nio.file.spi.FileSystemProvider
import java.nio.file.attribute.UserPrincipalLookupService
import java.util.{LinkedList, Set}

import scala.scalanative.unsafe._
import scalanative.annotation.stub

class WindowsFileSystem(override val provider: WindowsFileSystemProvider)
    extends FileSystem {

  override def close(): Unit = throw new UnsupportedOperationException()

  @stub
  override def getFileStores(): Iterable[FileStore] = ???

  override def getPath(first: String, more: Array[String]): Path =
    WindowsPathParser((first +: more).mkString(getSeparator()))(this)

  override def getPathMatcher(syntaxAndPattern: String): PathMatcher =
    PathMatcherImpl(syntaxAndPattern)

  override def getRootDirectories(): Iterable[Path] = {
    val list = new LinkedList[Path]()
    ???
    list
  }
  def getUserPrincipalLookupService(): UserPrincipalLookupService =
    WindowsUserPrincipalLookupService

  override def getSeparator(): String = "\\"

  override def isOpen(): Boolean = true

  override def isReadOnly(): Boolean = false

  override def newWatchService(): WatchService =
    throw new UnsupportedOperationException()

  override def supportedFileAttributeViews(): Set[String] = {
    val set = new java.util.HashSet[String]()
    set.add("basic")
    set.add("owner")
    set.add("dos")
    set.add("acl")
    set
  }

}
