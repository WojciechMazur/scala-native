package java.io

import scala.scalanative.unsigned._
import scala.scalanative.unsafe._
import scala.scalanative.posix.{fcntl, unistd}
import scala.scalanative.windows.{HandleApi, ConsoleExt}
import scala.scalanative.windows.{FileApi, FileAccess, FileSharing, FileDisposition, FileFlags}
import scala.scalanative.annotation.alwaysinline
import scala.scalanative.runtime.PlatformExt.isWindows
import scala.scalanative.runtime.{Intrinsics, toRawPtr, fromRawPtr}
import FileDescriptor._

final class FileDescriptor(fileHandle: FileHandle, readOnly: Boolean = false) {

  def this() =
    this {
      if (isWindows) FileHandle(HandleApi.InvalidHandleValue)
      else FileHandle(-1)
    }

  /* Unix file descriptor underlying value */
  @alwaysinline
  private[java] def fd: Int = Intrinsics.castRawPtrToInt(toRawPtr(fileHandle))

  /* Windows file handler underlying value */
  @alwaysinline
  private[java] def handle: HandleApi.Handle = fileHandle

  // ScalaNative private construcors
  private[java] def this(fd: Int) =
    this(FileHandle(fd))

  private[java] def this(fd: Int, readOnly: Boolean) =
    this(FileHandle(fd), readOnly)

  def sync(): Unit = {
    if (isStdFileDescriptor) throwSyncFailed()
    else {
      if (!readOnly) {
        val hasSucceded =
          if (isWindows) FileApi.flushFileBuffers(handle)
          else unistd.fsync(fd) == 0

        if (!hasSucceded) {
          throwSyncFailed()
        }
      }
    }
  }

  def valid(): Boolean =
    if (isWindows) {
      handle != HandleApi.InvalidHandleValue
    } else {
      // inspired by Apache Harmony including filedesc.c
      fcntl.fcntl(fd, fcntl.F_GETFD, 0) != -1
    }

  def close(): Unit =
    if (isWindows) HandleApi.closeHandle(handle)
    else unistd.close(fd)
    
  private def throwSyncFailed(): Unit =
    throw new SyncFailedException("sync failed")

  private def isStdFileDescriptor(): Boolean = {
    if (isWindows()) {
      this == FileDescriptor.in ||
      this == FileDescriptor.out ||
      this == FileDescriptor.err
    } else fd <= 2
  }

}

object FileDescriptor {
  type FileHandle = Ptr[Byte]
  object FileHandle {
    def apply(handle: HandleApi.Handle): FileHandle = handle
    def apply(unixFd: Int): FileHandle = fromRawPtr[Byte] {
      Intrinsics.castIntToRawPtr(unixFd)
    }
  }

  val in: FileDescriptor = {
    val handle =
      if (isWindows()) FileHandle(ConsoleExt.stdIn)
      else FileHandle(unistd.STDIN_FILENO)
    new FileDescriptor(handle)
  }

  val out: FileDescriptor = {
    val handle =
      if (isWindows()) FileHandle(ConsoleExt.stdOut)
      else FileHandle(unistd.STDOUT_FILENO)
    new FileDescriptor(handle)
  }

  val err: FileDescriptor = {
    val handle =
      if (isWindows()) FileHandle(ConsoleExt.stdErr)
      else FileHandle(unistd.STDERR_FILENO)
    new FileDescriptor(handle)
  }

  private[io] def openReadOnly(file: File): FileDescriptor =
    Zone { implicit z =>
      val filename = toCString(file.getPath())
      def fail() =
        throw new FileNotFoundException("No such file " + file.getPath())

      val fileHandle = if (isWindows) {
        assert(
          file.getPath().size < FileApi.maxAsciiPathSize,
          s"Paths on Windows are limmited to ${FileApi.maxAsciiPathSize}," +
            " Unicode version of file opening function is not supported yet "
        )
        val handle = FileApi.createFile(filename,
                                        FileAccess.FILE_GENERIC_READ.toUInt,
                                        FileSharing.NotShared.toUInt,
                                        null,
                                        FileDisposition.OpenExisting.toUInt,
                                        0.toUInt,
                                        null)
        if (handle == HandleApi.InvalidHandleValue) {
          fail()
        }
        FileHandle(handle)
      } else {
        val fd = fcntl.open(filename, fcntl.O_RDONLY, 0.toUInt)
        if (fd == -1) {
          fail()
        }
        FileHandle(fd)
      }

      new FileDescriptor(fileHandle, true)
    }
}
