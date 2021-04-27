package java.io

import scala.scalanative.unsigned._
import scala.scalanative.unsafe._
import scala.scalanative.posix.{fcntl, unistd}
import scala.scalanative.windows.{HandleApi, ConsoleExt}
import scala.scalanative.windows.{
  DWord,
  FileApi,
  FileAccess,
  FileSharing,
  FileDisposition,
  FileFlags
}
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

  override def toString(): String =
    s"FileDescriptor(${fd}, readonly=$readOnly"
  /* Unix file descriptor underlying value */
  @alwaysinline
  private[java] def fd: Int = fileHandle.toInt

  /* Windows file handler underlying value */
  @alwaysinline
  private[java] def handle: HandleApi.Handle = fileHandle

  // ScalaNative private construcors
  private[java] def this(fd: Int) =
    this(FileHandle(fd))

  private[java] def this(fd: Int, readOnly: Boolean) =
    this(FileHandle(fd), readOnly)

  def sync(): Unit = {
    def throwSyncFailed(): Unit = {
      throw new SyncFailedException("sync failed")
    }
    def isStdFileDescriptor(): Boolean = {
      if (isWindows) {
        this == FileDescriptor.in ||
        this == FileDescriptor.out ||
        this == FileDescriptor.err
      } else fd <= 2
    }

    if (isStdFileDescriptor) throwSyncFailed()
    else {
      if (!readOnly) {
        val hasSucceded =
          if (isWindows) FileApi.FlushFileBuffers(handle)
          else unistd.fsync(fd) == 0

        if (!hasSucceded) {
          throwSyncFailed()
        }
      }
    }
  }

  def valid(): Boolean =
    if (isWindows) {
      val flags = stackalloc[DWord]
      handle != HandleApi.InvalidHandleValue &&
      HandleApi.GetHandleInformation(handle, flags)
    } else {
      // inspired by Apache Harmony including filedesc.c
      fcntl.fcntl(fd, fcntl.F_GETFD, 0) != -1
    }

  def close(): Unit = {
    if (isWindows) HandleApi.CloseHandle(handle)
    else unistd.close(fd)
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
      if (isWindows) FileHandle(ConsoleExt.stdIn)
      else FileHandle(unistd.STDIN_FILENO)
    new FileDescriptor(handle)
  }

  val out: FileDescriptor = {
    val handle =
      if (isWindows) FileHandle(ConsoleExt.stdOut)
      else FileHandle(unistd.STDOUT_FILENO)
    new FileDescriptor(handle)
  }

  val err: FileDescriptor = {
    val handle =
      if (isWindows) FileHandle(ConsoleExt.stdErr)
      else FileHandle(unistd.STDERR_FILENO)
    new FileDescriptor(handle)
  }

  private[io] def openReadOnly(file: File): FileDescriptor =
    Zone { implicit z =>
      def fail() =
        throw new FileNotFoundException("No such file " + file.getPath())

      val fileHandle = if (isWindows) {
        val handle = FileApi.CreateFileW(
          filename = toCWideStringUTF16LE(file.getPath),
          desiredAccess = FileAccess.FILE_GENERIC_READ,
          shareMode = FileSharing.ShareRead | FileSharing.ShareWrite,
          securityAttributes = null,
          creationDisposition = FileDisposition.OpenExisting,
          flagsAndAttributes = 0.toUInt,
          templateFile = null
        )
        if (handle == HandleApi.InvalidHandleValue) {
          fail()
        }
        FileHandle(handle)
      } else {
        val fd = fcntl.open(toCString(file.getPath()), fcntl.O_RDONLY, 0.toUInt)
        if (fd == -1) {
          fail()
        }
        FileHandle(fd)
      }

      new FileDescriptor(fileHandle, true)
    }
}
