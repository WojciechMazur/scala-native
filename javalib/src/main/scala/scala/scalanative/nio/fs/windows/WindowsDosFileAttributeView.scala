package scala.scalanative.nio.fs.windows

import java.util.{HashMap => JHashMap}
import java.util.concurrent.TimeUnit
import java.nio.file.{LinkOption, Path}
import java.nio.file.attribute._
import java.lang.{Boolean => JBoolean}
import niocharset.StandardCharsets
import scalanative.unsigned._
import scalanative.unsafe._
import scalanative.libc._
import scala.scalanative.windows.MinWinBase.{FileTime => WinFileTime}
import scala.scalanative.windows.{File => WinFile}
import scala.scalanative.windows.{FileAttributes => WinFileAttributes, _}
import scala.scalanative.windows.HelperMethods._
import scala.scalanative.annotation.alwaysinline

final class WindowsDosFileAttributeView(path: Path, options: Array[LinkOption])
    extends DosFileAttributeView {
  def name(): String = "dos"

  private val followLinks = !options.contains(LinkOption.NOFOLLOW_LINKS)
  private val fileOpeningFlags = {
    FileFlags.FILE_FLAG_BACKUP_SEMANTICS | {
      if (followLinks) 0.toUInt
      else FileFlags.FILE_FLAG_OPEN_REPARSE_POINT
    }
  }

  override def setAttribute(name: String, value: Object): Unit =
    (name, value) match {
      case ("lastModifiedTime", time: FileTime) => setTimes(time, null, null)
      case ("lastAccessTime", time: FileTime)   => setTimes(null, time, null)
      case ("creationTime", time: FileTime)     => setTimes(null, null, time)
      case ("readonly", v: JBoolean)            => setReadOnly(v)
      case ("hidden", v: JBoolean)              => setHidden(v)
      case ("system", v: JBoolean)              => setSystem(v)
      case ("archive", v: JBoolean)             => setArchive(v)
      case _                                    => super.setAttribute(name, value)
    }

  override def asMap: JHashMap[String, Object] = {
    val map = new JHashMap[String, Object]()
    map.put("lastModifiedTime", attributes.lastModifiedTime())
    map.put("lastAccessTime", attributes.lastAccessTime())
    map.put("creationTime", attributes.creationTime())
    map.put("fileKey", attributes.fileKey())
    map.put("size", Long.box(attributes.size()))
    map.put("isRegularFile", Boolean.box(attributes.isRegularFile))
    map.put("isDirectory", Boolean.box(attributes.isDirectory))
    map.put("isSymbolicLink", Boolean.box(attributes.isSymbolicLink))
    map.put("isOther", Boolean.box(attributes.isOther))
    map.put("readonly", Boolean.box(attributes.isReadOnly))
    map.put("hidden", Boolean.box(attributes.isHidden))
    map.put("system", Boolean.box(attributes.isSystem))
    map.put("archive", Boolean.box(attributes.isArchive))
    map
  }

  def setArchive(value: Boolean): Unit =
    setWinAttribute(WinFileAttributes.Archive, value)

  def setHidden(value: Boolean): Unit =
    setWinAttribute(WinFileAttributes.Hidden, value)

  def setReadOnly(value: Boolean): Unit =
    setWinAttribute(WinFileAttributes.ReadOnly, value)

  def setSystem(value: Boolean): Unit =
    setWinAttribute(WinFileAttributes.System, value)

  def setTimes(lastModifiedTime: FileTime,
               lastAccessTime: FileTime,
               createTime: FileTime): Unit = Zone { implicit z =>
    def setOrNull(ref: Ptr[WinFileTime], value: FileTime): Ptr[WinFileTime] = {
      if (value == null) null
      else {
        !ref = toWindowsFileTime(value)
        ref
      }
    }

    withFileOpen(pathAbs,
                 access = FileAccess.FILE_GENERIC_WRITE,
                 attributes = fileOpeningFlags) { handle =>
      val create, access, write = stackalloc[WinFileTime]
      if (!FileApi.SetFileTime(
            handle,
            creationTime = setOrNull(create, createTime),
            lastAccessTime = setOrNull(access, lastAccessTime),
            lastWriteTime = setOrNull(write, lastModifiedTime))) {
        throw WindowsException("Failed to set file times")
      }
    }
  }

  def readAttributes(): DosFileAttributes = attributes

  private lazy val attributes = Zone { implicit z =>
    import WinFile.ByHandleFileInformationOps
    val fileInfo = alloc[WinFile.ByHandleFileInformation]
    withFileOpen(pathAbs,
                 access = FileAccess.FILE_READ_ATTRIBUTES,
                 attributes = fileOpeningFlags) {
      FileApi.GetFileInformationByHandle(_, fileInfo)
    }

    new DosFileAttributes {
      class DosFileKey(volumeId: DWord, fileIndex: ULargeInteger)

      import WinFileAttributes._
      private val attrs      = fileInfo.fileAttributes
      private val createdAt  = toFileTime(fileInfo.creationTime)
      private val accessedAt = toFileTime(fileInfo.lastAccessTime)
      private val modifiedAt = toFileTime(fileInfo.lastWriteTime)
      private val fileSize   = fileInfo.fileSize
      private val dosFileKey =
        new DosFileKey(volumeId = fileInfo.volumeSerialNumber,
                       fileIndex = fileInfo.fileIndex)

      def creationTime(): FileTime     = createdAt
      def lastAccessTime(): FileTime   = accessedAt
      def lastModifiedTime(): FileTime = modifiedAt
      def fileKey(): Object            = dosFileKey
      def size(): Long                 = fileSize.toLong

      //to replace with checking reparse tag
      def isSymbolicLink(): Boolean = hasAttrSet(ReparsePoint)
      def isDirectory(): Boolean    = hasAttrSet(Directory)
      def isOther(): Boolean        = hasAttrSet(ReparsePoint | Device)
      def isRegularFile(): Boolean = {
        !isSymbolicLink() &&
        !isDirectory() &&
        !isOther()
      }

      def isArchive(): Boolean  = hasAttrSet(Archive)
      def isHidden(): Boolean   = hasAttrSet(Hidden)
      def isReadOnly(): Boolean = hasAttrSet(ReadOnly)
      def isSystem(): Boolean   = hasAttrSet(System)

      private def hasAttrSet(attr: DWord): Boolean =
        (attrs & attr) != 0.toUInt
    }
  }

  private def setWinAttribute(attribute: DWord, enabled: Boolean): Unit = Zone {
    implicit z =>
      val filename      = toCWideStringUTF16LE(pathAbs)
      val previousAttrs = FileApi.GetFileAttributesW(filename)
      def setNewAttrs(): Boolean = {
        val newAttributes =
          if (enabled) previousAttrs | attribute
          else previousAttrs & ~attribute
        FileApi.SetFileAttributesW(filename, newAttributes)
      }

      if (previousAttrs == FileApi.InvalidFileAttributes || !setNewAttrs()) {
        throw WindowsException("Failed to set file attributes")
      }
  }

  @alwaysinline
  private lazy val pathAbs = path.toAbsolutePath.toString

  private def toFileTime(winFileTime: WinFileTime): FileTime = {
    import WinFileTime._
    try {
      val withEpochAdjustment = winFileTime.toLong - UnixEpochDifference
      val windowsNanos        = Math.multiplyExact(withEpochAdjustment, EpochInterval)
      FileTime.from(windowsNanos, TimeUnit.NANOSECONDS)
    } catch {
      case _: ArithmeticException =>
        val seconds = toUnixEpochMillis(winFileTime)
        FileTime.from(seconds, TimeUnit.MILLISECONDS)
    }
  }

  private def toWindowsFileTime(fileTime: FileTime): WinFileTime = {
    import WinFileTime._
    val asWindowsEpoch = fileTime.to(TimeUnit.NANOSECONDS) / EpochInterval
    (asWindowsEpoch + UnixEpochDifference).toULong
  }
}
