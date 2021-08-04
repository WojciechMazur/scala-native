package java.nio.file

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.posix.errno._
import scala.scalanative.windows._
import java.io.IOException
import java.nio.charset.StandardCharsets
import scala.scalanative.windows.ErrorHandlingApi._
import scala.scalanative.windows.WinBaseApi._
import java.nio.file._
import scalanative.libc.{string, errno => stdErrno}

trait WindowsException extends Exception
object WindowsException {
  def apply(msg: String): WindowsException = {
    val err = GetLastError()
    new IOException(s"$msg - ${errorMessage(err)} ($err)") with WindowsException
  }

  def onPath(file: String): IOException = {
    import ErrorCodes._
    lazy val e = stdErrno.errno
    val winError = GetLastError()
    winError match {
      case _ if e == ENOTDIR   => new NotDirectoryException(file)
      case ERROR_ACCESS_DENIED => new AccessDeniedException(file)
      case ERROR_FILE_NOT_FOUND | ERROR_PATH_NOT_FOUND =>
        new NoSuchFileException(file)
      case ERROR_ALREADY_EXISTS => new FileAlreadyExistsException(file)
      case e =>
        new IOException(s"$file - ${errorMessage(winError)} ($winError)")
    }
  }

  private def errorMessage(errCode: DWord): String = Zone { implicit z =>
    import WinBaseApi._
    import WinBaseApiExt._

    val msgBuffer = stackalloc[CWString]
    FormatMessageW(
      flags = FORMAT_MESSAGE_ALLOCATE_BUFFER |
        FORMAT_MESSAGE_FROM_SYSTEM |
        FORMAT_MESSAGE_IGNORE_INSERTS,
      source = null,
      messageId = errCode,
      languageId = DefaultLanguageId,
      buffer = msgBuffer,
      size = 0.toUInt,
      arguments = null
    )
    fromCWideString(!msgBuffer, StandardCharsets.UTF_16LE)
      .stripSuffix(System.lineSeparator())
  }
}
