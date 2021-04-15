package scala.scalanative.nio.fs.windows

import scala.scalanative.unsafe._
import scala.scalanative.posix.errno._
import scala.scalanative.windows._
import java.io.IOException
import java.nio.file._
import scalanative.libc.{string, errno => stdErrno}

trait WindowsException extends Exception
object WindowsException {
  def apply(msg: String): WindowsException = {
    val err = ErrorHandling.getLastError()
    new IOException(msg + " - errorCode:" + err) with WindowsException
  }

  def onPath(file: String): IOException = {
    import ErrorCodes._
    lazy val e = stdErrno.errno
    ErrorHandling.getLastError().toInt match {
      case _ if e == ENOTDIR   => new NotDirectoryException(file)
      case ERROR_ACCESS_DENIED => new AccessDeniedException(file)
      case ERROR_FILE_NOT_FOUND | ERROR_PATH_NOT_FOUND =>
        new NoSuchFileException(file)
      case ERROR_ALREADY_EXISTS => new FileAlreadyExistsException(file)
      case e                    => new IOException(file + " - " + fromCString(string.strerror(e)))
    }
  }

}
