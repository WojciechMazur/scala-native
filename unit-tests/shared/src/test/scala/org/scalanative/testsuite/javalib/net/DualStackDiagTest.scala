package org.scalanative.testsuite.javalib.net

import java.net._

import org.junit.Test

class DualStackDiagTest {
  @Test def diag(): Unit = {
    val s = new ServerSocket(0)
    try {
      val c = new Socket(InetAddress.getByName("fd00:3630::1"), s.getLocalPort)
      println("SN_DIAG v6 connect ok (IPv6 stack) " + c.getLocalAddress)
      c.close()
    } catch {
      case e: Throwable => println("SN_DIAG v6 connect failed " + e)
    } finally s.close()
  }
}
