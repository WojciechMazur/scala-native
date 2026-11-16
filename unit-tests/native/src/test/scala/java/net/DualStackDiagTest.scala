package java.net

import org.junit.Test

class DualStackDiagTest {
  @Test def diag(): Unit = {
    println("SN_DIAG useIPv4Stack=" + SocketHelpers.getUseIPv4Stack())
    val s = new ServerSocket(0)
    try {
      val c = new Socket(InetAddress.getByName("fd00:3630::1"), s.getLocalPort)
      println("SN_DIAG v6 connect ok " + c.getLocalAddress)
      c.close()
    } catch { case e: Throwable => println("SN_DIAG v6 connect failed " + e) }
    finally s.close()
  }
}
