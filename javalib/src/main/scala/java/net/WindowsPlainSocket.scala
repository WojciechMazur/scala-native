package java.net

import scala.scalanative.unsigned._
import scala.scalanative.unsafe._
import scala.scalanative.libc._
import scala.scalanative.runtime.ByteArray
import java.io.{FileDescriptor, IOException, OutputStream, InputStream}
import scala.scalanative.windows.{WinSocket, WinSocketApi}
import WinSocketApi._

private[net] class WindowsPlainSocket extends GenericPlainSocket {
  override def create(streaming: Boolean): Unit = {
    WinSocket.init()
    super.create(streaming)
  }

  protected def setSocketFdBlocking(fd: FileDescriptor,
                                    blocking: Boolean): CInt = {
    val mode = stackalloc[Int]
    if (blocking)
      !mode = 1
    else
      !mode = 0
    ioctlSocket(fd.handle, FIONBIO, mode)
  }

}
