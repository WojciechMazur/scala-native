package scala.scalanative
package testinterface

import scalanative.meta.LinktimeInfo

import java.net.Socket
import signalhandling.SignalConfig

import scalanative.posix.sys.socket._
import scalanative.posix.netinet.in
import scalanative.posix.unistd
import scala.sys.exit
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

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

    if (LinktimeInfo.isEmscripten) {
      import emscripten._
      assert(emscripten_websocket_is_supported(), "unsupported")
      val bridgeSocket = emscripten_init_websocket_to_posix_socket_bridge(
        c"ws://localhost:8888"
      )
      assert(bridgeSocket != -1)
      val readyState = stackalloc[UShort]()
      !readyState = 0.toUShort
      while ({
        emscripten_websocket_get_ready_state(bridgeSocket, readyState)
        val state = !readyState
        state.toInt == 0
      }) Thread.sleep(100) // fail at runtime: emscripten_thread_sleep(100)
      println(s"connected to WebSocket proxy")
    } else {
      if (args.length != 1) {
        System.err.println(usage)
        throw new IllegalArgumentException("One argument expected")
      }
    }

    SignalConfig.setDefaultHandlers()

    val serverAddr =
      if (!LinktimeInfo.isFreeBSD) iPv4Loopback
      else getFreeBSDLoopbackAddr()

    println("Waiting for port number from stdin:")
    val serverPort = scala.io.StdIn.readInt()
    println(s"Connecting to $serverPort")

    val clientSocket = new Socket(serverAddr, serverPort)
    val nativeRPC = new NativeRPC(clientSocket)
    val bridge = new TestAdapterBridge(nativeRPC)

    bridge.start()
    println("bridge started")

    val exitCode = nativeRPC.loop()
    sys.exit(exitCode)
  }

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
