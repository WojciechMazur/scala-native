package java.net

import scala.scalanative.unsigned._
import scala.scalanative.unsafe._
import scala.scalanative.libc._
import scala.scalanative.runtime.ByteArray
import java.io.{FileDescriptor, IOException, OutputStream, InputStream}
import scala.scalanative.windows.{WinSocket, WinSocketApi}
import scala.scalanative.posix.sys.{socket => unixSocket}

private[net] class WindowsPlainSocket extends GenericPlainSocket {
  import WinSocketApi._
  import WinSocket._

  override def create(streaming: Boolean): Unit = {
    WinSocket.init()
    val socket = WSASocketA(
      addressFamily = unixSocket.AF_INET,
      socketType = unixSocket.SOCK_STREAM,
      protocol = 0, // choosed by provider
      protocalInfo = null,
      group = 0.toUInt,
      flags = 0.toUInt
    )
    if (socket == InvalidSocket) {
      throw new IOException(s"Couldn't create a socket: ${WSAGetLastError()}")
    }
    fd = new FileDescriptor(socket)
  }

  protected def tryPoll(fd: FileDescriptor,
                        pollout: Boolean,
                        timeout: CInt): (CInt, CShort) = {
    val nAlloc = 1.toUInt
    val pollFd = stackalloc[WSAPollFd](nAlloc)

    pollFd.socket = fd.handle
    pollFd.events = 0
    pollFd.events = POLLIN
    if (pollout) {
      pollFd.events = (pollFd.events | POLLOUT).toShort
    }

    WSAPoll(pollFd, nAlloc, timeout) -> pollFd.revents
  }

  protected def setSocketFdBlocking(fd: FileDescriptor,
                                    blocking: Boolean): Unit = {
    val mode = stackalloc[Int]
    if (blocking)
      !mode = 1
    else
      !mode = 0
    ioctlSocket(fd.handle, FIONBIO, mode)
  }

}
