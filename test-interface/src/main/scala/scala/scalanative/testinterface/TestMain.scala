package scala.scalanative
package testinterface

import scalanative.meta.LinktimeInfo

import java.net.Socket
import signalhandling.SignalConfig

import scala.sys.exit
import scala.scalanative.posix.sys.socket._
import scala.scalanative.posix.netinet.in
import scala.scalanative.posix.unistd
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.posix.errno._

import java.io._
object TestMain {

  private val usage: String = {
    """Usage: test-main <server_port>
      |
      |arguments:
      |  server_port             -  the sbt test server port to use (required)
      |
      |*** Warning - dragons ahead ***
      |
      |This binary is meant to be executed by the Scala Native
      |testing framework and not standalone.
      |
      |Execute at your own risk!
      |""".stripMargin
  }

  final val iPv4Loopback = "127.0.0.1"
  final val iPv6Loopback = "::1"

  private def getFreeBSDLoopbackAddr(): String = {
    /* Standard out-of-the-box FreeBSD differs from Linux & macOS in
     * not allowing IPv4 mapped IPv6 addresses, such as :FFFF:127.0.0.1
     * or ::ffff:7f00:1.  These can be enabled by setting the line
     * ipv6_ipv4mapping="YES" in /etc/rc.conf (and rebooting).
     *
     * FreeBSD TestMain initial connections should succeed on both IPv4
     * and IPv6 systems without requiring arcane and non-standard system
     * configuration.  This method checks the protocol that Java connect()
     * is likely used and returns the corresponding loopback address.
     * Tests which use IPv4 addresses, either through hard-coding or
     * bind resolution, on IPv6 systems will still fail. This allows to
     * run the vast majority of tests which do not have this characteristic.
     *
     * Networking is complex, especially on j-random systems: full of
     * joy & lamentation.
     */

    if (!LinktimeInfo.isFreeBSD) iPv4Loopback // should never happen
    else {
      // These are the effective imports
      import scalanative.posix.sys.socket._
      import scalanative.posix.netinet.in
      import scalanative.posix.unistd

      /* These are to get this code to compile on Windows.
       * Including all of them is cargo cult programming. Someone
       * more patient or more knowledgeable about Windows may
       * be able to reduce the set.
       */
      import scala.scalanative.windows._
      import scala.scalanative.windows.WinSocketApi._
      import scala.scalanative.windows.WinSocketApiExt._
      import scala.scalanative.windows.WinSocketApiOps._
      import scala.scalanative.windows.ErrorHandlingApi._

      /* The keen observer will note that this scheme could be used
       * for Linux and macOS. That change is not introduced at this time
       * in order to preserve historical behavior.
       */
      val sd = socket(AF_INET6, SOCK_STREAM, in.IPPROTO_TCP)
      if (sd == -1) iPv4Loopback
      else {
        unistd.close(sd)
        iPv6Loopback
      }
    }
  }

  /** Main method of the test runner. */
  def main(args: Array[String]): Unit = {
    // TODO:  Works (kinda) only in browser, Node.js has no support for websockets

    println("Hello world")
    // int preopen_fd = getenv("DEBUGGER_FD") ? 4 : 3;

    // val serverPort = if (LinktimeInfo.isEmscripten) {
    //   import emscripten._
    //   assert(emscripten_websocket_is_supported(), "unsupported")
    //   val bridgeSocket = emscripten_init_websocket_to_posix_socket_bridge(
    //     c"ws://localhost:8888"
    //   )
    //   assert(bridgeSocket != -1)
    //   val readyState = stackalloc[UShort]()
    //   !readyState = 0.toUShort
    //   while ({
    //     emscripten_websocket_get_ready_state(bridgeSocket, readyState)
    //     val state = !readyState
    //     state.toInt == 0
    //   }) Thread.sleep(100) // fail at runtime: emscripten_thread_sleep(100)
    //   println(s"connected to WebSocket proxy")

    //   println("Waiting for port number from stdin:")
    //   scala.io.StdIn.readInt()
    // } else {
    //   if (args.length != 1) {
    //     System.err.println(usage)
    //     throw new IllegalArgumentException("One argument expected")
    //   }
    //   args(0).toInt
    // }

    // SignalConfig.setDefaultHandlers()

    // val serverAddr =
    //   if (!LinktimeInfo.isFreeBSD) iPv4Loopback
    //   else getFreeBSDLoopbackAddr()

    // println(s"Connecting to $serverPort")
    val socket = new SimpleSocket(fd = {
      val preOpenFd = 3
      val addrOut = ??? //stackalloc[sockaddr]()
      val addrLen = ??? //stackalloc[socklen_t]()
      var fd = -1
      while (fd == -1) {
        fd = accept4(preOpenFd, addrOut, addrLen, 0)
        // int new_connection_fd = accept4(preopen_fd, &addr_out_ignored, &addr_len_out_ignored, SOCK_NONBLOCK);
        Thread.sleep(1)
      }
      println(s"Connected on socketFd=$preOpenFd, fd=$fd")
      fd
    })
    val nativeRPC =
      new NativeRPC(socket.getInputStream(), socket.getOutputStream())
    val bridge = new TestAdapterBridge(nativeRPC)

    bridge.start()
    println("bridge started")

    val exitCode = nativeRPC.loop()
    sys.exit(exitCode)
  }

}

class SimpleSocket(fd: Int) extends Socket() {
  private def socket = this
  var closed = false
  override def isClosed() = closed
  override def getOutputStream: OutputStream = new OutputStream {
    override def close(): Unit = {
      socket.close()
      closed = true
    }

    override def write(b: Array[Byte]) = socket.write(b, 0, b.length)

    override def write(b: Array[Byte], off: Int, len: Int) = {
      if (b == null)
        throw new NullPointerException("Buffer parameter is null")

      if (off < 0 || off > b.length || len < 0 || len > b.length - off)
        throw new ArrayIndexOutOfBoundsException("Invalid length or offset")

      socket.write(b, off, len)
    }

    override def write(b: Int) = {
      socket.write(Array[Byte](b.toByte), 0, 1)
    }
  }

  override def getInputStream: InputStream = {
    // if (isClosed) {
    //   throw new SocketException("Socket is closed")
    // }
    // if (shutInput) {
    //   throw new SocketException("Socket input is shutdown")
    // }
    new InputStream {

      override def close(): Unit = socket.close()

      override def available(): Int = socket.available()

      override def read(): Int = {
        val buffer = new Array[Byte](1)
        socket.read(buffer, 0, 1) match {
          case -1 => -1
          case _ => buffer(0) & 0xff // Convert to Int with _no_ sign extension.
        }
      }

      override def read(buffer: Array[Byte]) = read(buffer, 0, buffer.length)

      override def read(buffer: Array[Byte], offset: Int, count: Int): Int = {
        if (buffer == null) throw new NullPointerException("Buffer is null")

        if (count == 0) return 0

        if (offset < 0 || offset >= buffer.length) {
          throw new ArrayIndexOutOfBoundsException(
            "Offset out of bounds: " + offset
          )
        }

        if (count < 0 || offset + count > buffer.length) {
          throw new ArrayIndexOutOfBoundsException(
            "Reading would result in buffer overflow"
          )
        }

        socket.read(buffer, offset, count)
      }
    }
  }

  override def shutdownOutput(): Unit = {
    println("shutdown output")
    shutdown(fd, 1) match {
      case 0 =>//  shutOutput = true
      case _ => ???
        // throw new SocketException("Error while shutting down socket's output")
    }
  }

  override def shutdownInput(): Unit = {
    println("shutdown input")
    shutdown(fd, 0) match {
      case 0 =>// shutInput = true
      case _ => ???
        // throw new SocketException("Error while shutting down socket's input")
    }
  }

  def write(buffer: Array[Byte], offset: Int, count: Int): Int = {
    // if (shutOutput) {
    //   throw new IOException("Trying to write to a shut down socket")
    // } else 
    if (isClosed) {
      0
    } else {
      val cArr = buffer.at(offset)
      var sent = 0
      while (sent < count) {
        val ret = send(fd, cArr + sent, (count - sent).toUInt, 0).toInt
        if (ret < 0) {
          throw new IOException("Could not send the packet to the client")
        }
        sent += ret
      }
      sent
    }
  }

  def read(buffer: Array[Byte], offset: Int, count: Int): Int = {
    // if (shutInput) -1
    // else {
      val bytesNum = recv(fd, buffer.at(offset), count.toUInt, 0)
        .toInt

      def timeoutDetected = errno == EAGAIN || errno == EWOULDBLOCK

      bytesNum match {
        case _ if (bytesNum > 0) => bytesNum

        case 0 => if (count == 0) 0 else -1

        case b if timeoutDetected =>
          println(s"timeout - $errno, bytes = $b")
          throw new java.net.SocketTimeoutException("Socket timeout while reading data")

        case _ => ???
          // throw new SocketException(s"read failed, errno: ${lastError()}")
      }
    }
  // }

  def available(): Int = {
    import scala.scalanative.posix.sys.ioctl._
    // if (shutInput) {
      // 0
    // } else {
      val bytesAvailable = stackalloc[CInt]()
      ioctl(fd, FIONREAD, bytesAvailable.asInstanceOf[Ptr[Byte]])
      !bytesAvailable match {
        case -1 =>
          throw new IOException(
            "Error while trying to estimate available bytes to read"
          )
        case x => x
      }
    }
  // }
}

@extern object emscripten {
  type EMSCRIPTEN_WEBSOCKET_T = CInt

  def emscripten_init_websocket_to_posix_socket_bridge(
      proxyUrl: CString
  ): EMSCRIPTEN_WEBSOCKET_T = extern

  def emscripten_websocket_get_ready_state(
      webSocket: EMSCRIPTEN_WEBSOCKET_T,
      state: Ptr[UShort]
  ): Int = extern
  def emscripten_thread_sleep(millis: CInt): Unit = extern
  def emscripten_websocket_is_supported(): Boolean = extern
}
