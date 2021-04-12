package java.io

import scalanative.annotation.stub
import scalanative.unsigned._
import scalanative.unsafe._
import scalanative.libc._, stdlib._, stdio._, string._
import scalanative.nio.fs.unix.UnixException
import scalanative.posix.unistd, unistd.lseek
import scalanative.runtime
import scala.scalanative.windows
import scala.scalanative.windows.{
  FileApi,
  FileAccess,
  ErrorHandling,
  ErrorCodes
}
import scala.scalanative.windows.HelperMethods.withFile
import scala.scalanative.windows.File.FilePointerMoveMethods
import scala.scalanative.runtime.PlatformExt.isWindows
import scala.annotation.switch

class FileInputStream(fd: FileDescriptor, file: Option[File])
    extends InputStream {

  def this(fd: FileDescriptor) = this(fd, None)
  def this(file: File) = this(FileDescriptor.openReadOnly(file), Some(file))
  def this(str: String) = this(new File(str))

  override def available(): Int = {
    if (isWindows) {
      val currentPosition, lastPosition = stackalloc[windows.LargeInteger]
      FileApi.setFilePointerEx(fd.handle,
                               distanceToMove = 0,
                               newFilePointer = currentPosition,
                               moveMethod = FilePointerMoveMethods.Current)
      FileApi.setFilePointerEx(fd.handle,
                               distanceToMove = 0,
                               newFilePointer = lastPosition,
                               moveMethod = FilePointerMoveMethods.End)
      FileApi.setFilePointerEx(fd.handle,
                               distanceToMove = !currentPosition,
                               newFilePointer = null,
                               moveMethod = FilePointerMoveMethods.Begin)

      (!lastPosition - !currentPosition).toInt
    } else {
      val currentPosition = lseek(fd.fd, 0, SEEK_CUR)
      val lastPosition    = lseek(fd.fd, 0, SEEK_END)
      lseek(fd.fd, currentPosition, SEEK_SET)
      (lastPosition - currentPosition).toInt
    }
  }

  override def close(): Unit = fd.close()

  override protected def finalize(): Unit =
    close()

  final def getFD(): FileDescriptor =
    fd

  override def read(): Int = {
    val buffer = new Array[Byte](1)
    if (read(buffer) <= 0) -1
    else buffer(0).toUInt.toInt
  }

  override def read(buffer: Array[Byte]): Int = {
    if (buffer == null) {
      throw new NullPointerException
    }
    read(buffer, 0, buffer.length)
  }

  override def read(buffer: Array[Byte], offset: Int, count: Int): Int = {
    if (buffer == null) {
      throw new NullPointerException
    }
    if (offset < 0 || count < 0 || count > buffer.length - offset) {
      throw new IndexOutOfBoundsException
    }
    if (count == 0) {
      return 0
    }

    // we use the runtime knowledge of the array layout to avoid
    // intermediate buffer, and write straight into the array memory
    val buf = buffer.asInstanceOf[runtime.ByteArray].at(offset)
    if (isWindows) {
      val readBytes = stackalloc[windows.DWord]
      val hasSucceded =
        FileApi.readFile(fd.handle, buf, count.toUInt, readBytes, null)
      if (hasSucceded) (!readBytes).toInt
      else {
        (ErrorHandling.getLastError().toInt: @switch) match {
          case ErrorCodes.ERROR_HANDLE_EOF => -1
          case err                         =>
            // Todo proper Windows exceptions handling
            throw UnixException(file.fold("")(_.toString), err)
        }
      }
    } else {
      val readCount = unistd.read(fd.fd, buf, count.toUInt)
      if (readCount == 0) {
        // end of file
        -1
      } else if (readCount < 0) {
        // negative value (typically -1) indicates that read failed
        throw UnixException(file.fold("")(_.toString), errno.errno)
      } else {
        // successfully read readCount bytes
        readCount
      }
    }
  }

  override def skip(n: Long): Long =
    if (n < 0) {
      throw new IOException()
    } else {
      val bytesToSkip = Math.min(n, available())
      if (isWindows) {
        FileApi.setFilePointerEx(fd.handle,
                                 distanceToMove = bytesToSkip,
                                 newFilePointer = null,
                                 moveMethod = FilePointerMoveMethods.Current)
      } else
        lseek(fd.fd, bytesToSkip, SEEK_CUR)
      bytesToSkip
    }

  @stub
  def getChannel: java.nio.channels.FileChannel = ???
}
