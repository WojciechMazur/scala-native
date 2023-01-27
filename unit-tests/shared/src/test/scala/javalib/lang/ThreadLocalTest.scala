/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */
package org.scalanative.testsuite.javalib.lang

import org.scalanative.testsuite.javalib.util.concurrent.JSR166Test
import JSR166Test._

import org.junit.{Test, Ignore}
import org.junit.Assert._

object ThreadLocalTest {
  val tl = new ThreadLocal[Integer]() {
    override def initialValue: Integer = one
  }
  val itl = new InheritableThreadLocal[Integer]() {
    override def initialValue: Integer = zero
    override def childValue(parentValue: Integer): Integer = parentValue + 1
  }
}
class ThreadLocalTest extends JSR166Test {

  /** remove causes next access to return initial value
   */
  @Test def testRemove(): Unit = {
    assertSame(ThreadLocalTest.tl.get, one)
    ThreadLocalTest.tl.set(two)
    assertSame(ThreadLocalTest.tl.get, two)
    ThreadLocalTest.tl.remove()
    assertSame(ThreadLocalTest.tl.get, one)
  }

  /** remove in InheritableThreadLocal causes next access to return initial
   *  value
   */
  @Test def testRemoveITL(): Unit = {
    assertSame(ThreadLocalTest.itl.get, zero)
    ThreadLocalTest.itl.set(two)
    assertSame(ThreadLocalTest.itl.get, two)
    ThreadLocalTest.itl.remove()
    assertSame(ThreadLocalTest.itl.get, zero)
  }

  private class ITLThread(val x: Array[Int]) extends Thread {
    override def run(): Unit = {
      var child: ITLThread = null
      if (ThreadLocalTest.itl.get.intValue < x.length - 1) {
        child = new ITLThread(x)
        child.start()
      }
      Thread.`yield`()
      val threadId = ThreadLocalTest.itl.get.intValue
      for (j <- 0 until threadId) {
        x(threadId) += 1
        Thread.`yield`()
      }
      if (child != null) { // Wait for child (if any)
        try child.join()
        catch { case e: InterruptedException => threadUnexpectedException(e) }
      }
    }
  }

  /** InheritableThreadLocal propagates generic values.
   */
  @Test def testGenericITL(): Unit = {
    val threadCount = 10
    val x = new Array[Int](threadCount)
    val progenitor = new ITLThread(x)
    progenitor.start()
    progenitor.join()
    for (i <- 0 until threadCount) { assertEquals(i, x(i)) }
  }
}
