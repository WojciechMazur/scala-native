package java.net

import scala.scalanative.unsigned._
import scala.scalanative.unsafe._
import scala.scalanative.libc._
import scala.scalanative.runtime.ByteArray
import scala.scalanative.posix.errno._
import scala.scalanative.posix.fcntl._
import scala.scalanative.posix.poll._
import scala.scalanative.posix.pollEvents._
import scala.scalanative.posix.pollOps._
import scala.scalanative.posix.sys.socket
import scala.scalanative.posix.sys.socketOps._

import java.io.{FileDescriptor, IOException, OutputStream, InputStream}

private[net] class UnixPlainSocket extends GenericPlainSocket {

  override def create(streaming: Boolean): Unit = {
    val sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM, 0)
    if (sock < 0) throw new IOException("Couldn't create a socket")
    fd = new FileDescriptor(sock)
  }

  protected def tryPoll(fd: FileDescriptor,
                        pollout: Boolean,
                        timeout: CInt): (CInt, CShort) = {
    val nAlloc = 1.toUInt
    val pollFd = stackalloc[struct_pollfd](nAlloc)

    pollFd.fd = fd.fd
    pollFd.revents = 0
    pollFd.events = POLLIN
    if (pollout) pollFd.events = (pollFd.events | POLLOUT).toShort

    poll(pollFd, nAlloc, timeout) -> pollFd.revents
  }

  @inline
  private def getSocketFdOpts(fdFd: Int): CInt = {
    val opts = fcntl(fdFd, F_GETFL, 0)

    if (opts == -1) {
      throw new ConnectException(
        "connect failed, fcntl F_GETFL" +
          s", errno: ${errno.errno}")
    }

    opts
  }

  @inline
  private def setSocketFdOpts(fdFd: Int, opts: Int): Unit = {
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
                                    blocking: Boolean): Unit = {
    updateSocketFdOpts(fd.fd) { oldOpts =>
      if (blocking) oldOpts & ~O_NONBLOCK
      else oldOpts | O_NONBLOCK
    }
  }

}
