/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */
package org.scalanative.testsuite
package javalib.util.concurrent.atomic

import org.scalanative.testsuite.javalib.util.concurrent.JSR166Test
import JSR166Test._

import java.util.concurrent.atomic.AtomicReference

import org.junit.{Test, Ignore}
import org.junit.Assert._

class AtomicReferenceTest extends JSR166Test {

  /** constructor initializes to given value
   */
  @Test def testConstructor(): Unit = {
    val ai = new AtomicReference[Any](one)
    assertSame(one, ai.get)
  }

  /** default constructed initializes to null
   */
  @Test def testConstructor2(): Unit = {
    val ai = new AtomicReference[Any]
    assertNull(ai.get)
  }

  /** get returns the last value set
   */
  @Test def testGetSet(): Unit = {
    val ai = new AtomicReference[Any](one)
    assertSame(one, ai.get)
    ai.set(two)
    assertSame(two, ai.get)
    ai.set(m3)
    assertSame(m3, ai.get)
  }

  /** get returns the last value lazySet in same thread
   */
  @Test def testGetLazySet(): Unit = {
    val ai = new AtomicReference[Any](one)
    assertSame(one, ai.get)
    ai.lazySet(two)
    assertSame(two, ai.get)
    ai.lazySet(m3)
    assertSame(m3, ai.get)
  }

  /** compareAndSet succeeds in changing value if equal to expected else fails
   */
  @Test def testCompareAndSet(): Unit = {
    val ai = new AtomicReference[Any](one)
    assertTrue(ai.compareAndSet(one, two))
    assertTrue(ai.compareAndSet(two, m4))
    assertSame(m4, ai.get)
    assertFalse(ai.compareAndSet(m5, seven))
    assertSame(m4, ai.get)
    assertTrue(ai.compareAndSet(m4, seven))
    assertSame(seven, ai.get)
  }

  /** compareAndSet in one thread enables another waiting for value to succeed
   */
  @throws[Exception]
  @Test def testCompareAndSetInMultipleThreads(): Unit = {
    val ai = new AtomicReference[Any](one)
    val t = new Thread(new CheckedRunnable() {
      override def realRun(): Unit = {
        while ({ !ai.compareAndSet(two, three) }) Thread.`yield`()
      }
    })
    t.start()
    assertTrue(ai.compareAndSet(one, two))
    t.join(LONG_DELAY_MS)
    assertFalse(t.isAlive)
    assertSame(three, ai.get)
  }

  /** repeated weakCompareAndSet succeeds in changing value when equal to
   *  expected
   */
  @Test def testWeakCompareAndSet(): Unit = {
    val ai = new AtomicReference[Any](one)
    while (!ai.weakCompareAndSet(one, two)) ()
    while (!ai.weakCompareAndSet(two, m4)) ()
    assertSame(m4, ai.get)
    while (!ai.weakCompareAndSet(m4, seven)) ()
    assertSame(seven, ai.get)
  }

  /** getAndSet returns previous value and sets to given value
   */
  @Test def testGetAndSet(): Unit = {
    val ai = new AtomicReference[Any](one)
    assertSame(one, ai.getAndSet(zero))
    assertSame(zero, ai.getAndSet(m10))
    assertSame(m10, ai.getAndSet(one))
  }

  /** a deserialized/reserialized atomic holds same value
   */
  @throws[Exception]
  @Ignore("No ObjectInputStreams in Scala Native")
  @Test def testSerialization(): Unit = {
    //   val x = new AtomicReference[Any]
    //   val y = serialClone(x)
    //   assertNotSame(x, y)
    //   x.set(one)
    //   val z = serialClone(x)
    //   assertNotSame(y, z)
    //   assertEquals(one, x.get)
    //   assertNull(y.get)
    //   assertEquals(one, z.get)
  }

  /** toString returns current value.
   */
  @Test def testToString(): Unit = {
    val ai = new AtomicReference[Integer](one)
    assertEquals(one.toString, ai.toString)
    ai.set(two)
    assertEquals(two.toString, ai.toString)
  }
}
