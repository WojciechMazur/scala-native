package java.net

import scala.scalanative.unsigned._
import scala.scalanative.unsafe._
import scala.scalanative.libc._
import scala.scalanative.runtime.ByteArray
import scala.scalanative.posix.errno._
import scala.scalanative.posix.fcntl._
import java.io.{FileDescriptor, IOException, OutputStream, InputStream}

private[net] class UnixPlainSocket extends GenericPlainSocket {

  @inline
  protected def getSocketFdOpts(fdFd: Int): Int = {
    val opts = fcntl(fdFd, F_GETFL, 0)

    if (opts == -1) {
      throw new ConnectException(
        "connect failed, fcntl F_GETFL" +
          s", errno: ${errno.errno}")
    }

    opts
  }

  @inline
  protected def setSocketFdOpts(fdFd: Int, opts: Int): Unit = {
    val ret = fcntl(fdFd, F_SETFL, opts)

    if (ret == -1) {
      throw new ConnectException(
        "connect failed, " +
          s"fcntl F_SETFL for opts: ${opts}" +
          s", errno: ${errno.errno}")
    }
  }

  @inline
  private def updateSocketFdOpts(fdFd: Int)(mapping: CInt => CInt): Int = {
    val oldOpts = getSocketFdOpts(fdFd)
    setSocketFdOpts(fdFd, mapping(oldOpts))
    oldOpts
  }

  protected def setSocketFdBlocking(fd: FileDescriptor,
                                    blocking: Boolean): CInt = {
    updateSocketFdOpts(fd.fd) { oldOpts =>
      if (blocking) oldOpts & ~O_NONBLOCK
      else oldOpts | O_NONBLOCK
    }
  }

}
