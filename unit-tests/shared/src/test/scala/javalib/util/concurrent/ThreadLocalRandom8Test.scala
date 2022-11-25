/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package org.scalanative.testsuite.javalib.util.concurrent

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder

import JSR166Test._

import org.junit.{Test, Ignore}
import org.junit.Assert._
import scala.scalanative.junit.utils.AssertThrows.assertThrows

object ThreadLocalRandom8Test {
  // max sampled int bound
  val MAX_INT_BOUND: Int = 1 << 26
  // max sampled long bound
  val MAX_LONG_BOUND: Long = 1L << 42
  // Number of replications for other checks
  val REPS: Int = Integer.getInteger("ThreadLocalRandom8Test.reps", 4)
}
class ThreadLocalRandom8Test extends JSR166Test {

  /** Invoking sized ints, long, doubles, with negative sizes throws
   *  IllegalArgumentException
   */
  @Ignore("Not implemented Java Streams")
  @Test def testBadStreamSize(): Unit = {
    // val r = ThreadLocalRandom.current
    // Seq(
    //   () => r.ints(-1L),
    //   () => r.ints(-1L, 2, 3),
    //   () => r.longs(-1L),
    //   () => r.longs(-1L, -1L, 1L),
    //   () => r.doubles(-1L),
    //   () => r.doubles(-1L, .5, .6)
    // ).foreach(t => assertThrows(classOf[IllegalArgumentException], t()))
  }

  /** Invoking bounded ints, long, doubles, with illegal bounds throws
   *  IllegalArgumentException
   */
  @Ignore("Not implemented Java Streams")
  @Test def testBadStreamBounds(): Unit = {
    // val r = ThreadLocalRandom.current
    // Seq(
    //   () => r.ints(2, 1),
    //   () => r.ints(10, 42, 42),
    //   () => r.longs(-1L, -1L),
    //   () => r.longs(10, 1L, -2L),
    //   () => r.doubles(0.0, 0.0),
    //   () => r.doubles(10, .5, .4)
    // ).foreach(t => assertThrows(classOf[IllegalArgumentException], t()))
  }

  /** A parallel sized stream of ints generates the given number of values
   */
  @Ignore("Not implemented Java Streams")
  @Test def testIntsCount(): Unit = {
    // val counter = new LongAdder
    // val r = ThreadLocalRandom.current
    // var size = 0
    // for (reps <- 0 until ThreadLocalRandom8Test.REPS) {
    //   counter.reset()
    //   r.ints(size).parallel.forEach((x: Int) => counter.increment())
    //   assertEquals(size, counter.sum)
    //   size += 524959
    // }
  }

  /** A parallel sized stream of longs generates the given number of values
   */
  @Ignore("Not implemented Java Streams")
  @Test def testLongsCount(): Unit = {
    // val counter = new LongAdder
    // val r = ThreadLocalRandom.current
    // var size = 0
    // for (reps <- 0 until ThreadLocalRandom8Test.REPS) {
    //   counter.reset()
    //   r.longs(size).parallel.forEach((x: Long) => counter.increment())
    //   assertEquals(size, counter.sum)
    //   size += 524959
    // }
  }

  /** A parallel sized stream of doubles generates the given number of values
   */
  @Ignore("Not implemented Java Streams")
  @Test def testDoublesCount(): Unit = {
    // val counter = new LongAdder
    // val r = ThreadLocalRandom.current
    // var size = 0
    // for (reps <- 0 until ThreadLocalRandom8Test.REPS) {
    //   counter.reset()
    //   r.doubles(size).parallel.forEach((x: Double) => counter.increment())
    //   assertEquals(size, counter.sum)
    //   size += 524959
    // }
  }

  /** Each of a parallel sized stream of bounded ints is within bounds
   */
  @Ignore("Not implemented Java Streams")
  @Test def testBoundedInts(): Unit = {
    // val fails = new AtomicInteger(0)
    // val r = ThreadLocalRandom.current
    // val size = 12345L
    // var least = -15485867
    // while (least < ThreadLocalRandom8Test.MAX_INT_BOUND) {
    //   var bound = least + 2
    //   while ({
    //     bound > least && bound < ThreadLocalRandom8Test.MAX_INT_BOUND
    //   }) {
    //     val lo = least
    //     val hi = bound
    //     r.ints(size, lo, hi)
    //       .parallel
    //       .forEach((x: Int) => {
    //         def foo(x: Int) = if (x < lo || x >= hi) fails.getAndIncrement
    //         foo(x)
    //       })

    //     bound += 67867967
    //   }

    //   least += 524959
    // }
    // assertEquals(0, fails.get)
  }

  /** Each of a parallel sized stream of bounded longs is within bounds
   */
  @Ignore("Not implemented Java Streams")
  @Test def testBoundedLongs(): Unit = {
    // val fails = new AtomicInteger(0)
    // val r = ThreadLocalRandom.current
    // val size = 123L
    // var least: Long = -86028121
    // while ({ least < ThreadLocalRandom8Test.MAX_LONG_BOUND }) {
    //   var bound = least + 2
    //   while ({
    //     bound > least && bound < ThreadLocalRandom8Test.MAX_LONG_BOUND
    //   }) {
    //     val lo = least
    //     val hi = bound
    //     r.longs(size, lo, hi)
    //       .parallel
    //       .forEach((x: Long) => {
    //         def foo(x: Long) = if (x < lo || x >= hi) fails.getAndIncrement
    //         foo(x)
    //       })

    //     bound += Math.abs(bound * 7919)
    //   }

    //   least += 1982451653L
    // }
    // assertEquals(0, fails.get)
  }

  /** Each of a parallel sized stream of bounded doubles is within bounds
   */
  @Ignore("Not implemented Java Streams")
  @Test def testBoundedDoubles(): Unit = {
    // val fails = new AtomicInteger(0)
    // val r = ThreadLocalRandom.current
    // val size = 456
    // var least = 0.00011
    // while ({ least < 1.0e20 }) {
    //   var bound = least * 1.0011
    //   while ({ bound < 1.0e20 }) {
    //     val lo = least
    //     val hi = bound
    //     r.doubles(size, lo, hi)
    //       .parallel
    //       .forEach((x: Double) => {
    //         def foo(x: Double) = if (x < lo || x >= hi) fails.getAndIncrement
    //         foo(x)
    //       })

    //     bound *= 17
    //   }

    //   least *= 9
    // }
    // assertEquals(0, fails.get)
  }

  /** A parallel unsized stream of ints generates at least 100 values
   */
  @Ignore("Not implemented Java Streams")
  @Test def testUnsizedIntsCount(): Unit = {
    // val counter = new LongAdder
    // val r = ThreadLocalRandom.current
    // val size = 100
    // r.ints.limit(size).parallel.forEach((x: Int) => counter.increment())
    // assertEquals(size, counter.sum)
  }

  /** A parallel unsized stream of longs generates at least 100 values
   */
  @Ignore("Not implemented Java Streams")
  @Test def testUnsizedLongsCount(): Unit = {
    // val counter = new LongAdder
    // val r = ThreadLocalRandom.current
    // val size = 100
    // r.longs.limit(size).parallel.forEach((x: Long) => counter.increment())
    // assertEquals(size, counter.sum)
  }

  /** A parallel unsized stream of doubles generates at least 100 values
   */
  @Ignore("Not implemented Java Streams")
  @Test def testUnsizedDoublesCount(): Unit = {
    // val counter = new LongAdder
    // val r = ThreadLocalRandom.current
    // val size = 100
    // r.doubles.limit(size).parallel.forEach((x: Double) => counter.increment())
    // assertEquals(size, counter.sum)
  }

  /** A sequential unsized stream of ints generates at least 100 values
   */
  @Ignore("Not implemented Java Streams")
  @Test def testUnsizedIntsCountSeq(): Unit = {
    // val counter = new LongAdder
    // val r = ThreadLocalRandom.current
    // val size = 100
    // r.ints.limit(size).forEach((x: Int) => counter.increment())
    // assertEquals(size, counter.sum)
  }

  /** A sequential unsized stream of longs generates at least 100 values
   */
  @Ignore("Not implemented Java Streams")
  @Test def testUnsizedLongsCountSeq(): Unit = {
    // val counter = new LongAdder
    // val r = ThreadLocalRandom.current
    // val size = 100
    // r.longs.limit(size).forEach((x: Long) => counter.increment())
    // assertEquals(size, counter.sum)
  }

  /** A sequential unsized stream of doubles generates at least 100 values
   */
  @Ignore("Not implemented Java Streams")
  @Test def testUnsizedDoublesCountSeq(): Unit = {
    // val counter = new LongAdder
    // val r = ThreadLocalRandom.current
    // val size = 100
    // r.doubles.limit(size).forEach((x: Double) => counter.increment())
    // assertEquals(size, counter.sum)
  }

  /** A deserialized/reserialized ThreadLocalRandom is always identical to
   *  ThreadLocalRandom.current()
   */
  @Ignore("No ObjectInputStreams in Scala Native")
  @Test def testSerialization(): Unit = {
    // assertSame(
    //   ThreadLocalRandom.current,
    //   serialClone(ThreadLocalRandom.current)
    // )
    // // In the current implementation, there is exactly one shared instance
    // if (testImplementationDetails)
    //   assertSame(
    //     ThreadLocalRandom.current,
    //     java.util.concurrent.CompletableFuture
    //       .supplyAsync(() => serialClone(ThreadLocalRandom.current))
    //       .join
    //   )
  }
}
