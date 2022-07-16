/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package org.scalanative.testsuite.javalib.util.concurrent.ThreadLocalRandom

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

import org.scalanative.testsuite.javalib.util.concurrent.JSR166Test
import JSR166Test._

import org.junit.{Test, Ignore}
import org.junit.Assert._

object ThreadLocalRandomTest {
  // max numbers of calls to detect getting stuck on one value
  /*
   * Testing coverage notes:
   *
   * We don't test randomness properties, but only that repeated
   * calls, up to NCALLS tries, produce at least one different
   * result.  For bounded versions, we sample various intervals
   * across multiples of primes.
   */
  val NCALLS = 10000
  // max sampled int bound
  val MAX_INT_BOUND: Int = 1 << 28
  // max sampled long bound
  val MAX_LONG_BOUND: Long = 1L << 42
  // Number of replications for other checks
  val REPS = 20
}
class ThreadLocalRandomTest extends JSR166Test {

  /** setSeed throws UnsupportedOperationException
   */
  @Test def testSetSeed(): Unit = {
    try {
      ThreadLocalRandom.current.setSeed(17)
      shouldThrow()
    } catch {
      case success: UnsupportedOperationException =>

    }
  }

  /** Repeated calls to next (only accessible via reflection) produce at least
   *  two distinct results, and repeated calls produce all possible values.
   */
  @throws[ReflectiveOperationException]
  @Ignore("Test needs reflective access to 'next' method")
  @Test def testNext(): Unit = {}

  /** Repeated calls to nextInt produce at least two distinct results
   */
  @Test def testNextInt(): Unit = {
    val f = ThreadLocalRandom.current.nextInt
    var i = 0
    while ({
      i < ThreadLocalRandomTest.NCALLS && ThreadLocalRandom.current.nextInt == f
    }) i += 1
    assertTrue(i < ThreadLocalRandomTest.NCALLS)
  }

  /** Repeated calls to nextLong produce at least two distinct results
   */
  @Test def testNextLong(): Unit = {
    val f = ThreadLocalRandom.current.nextLong
    var i = 0
    while ({
      i < ThreadLocalRandomTest.NCALLS && ThreadLocalRandom.current.nextLong == f
    }) i += 1
    assertTrue(i < ThreadLocalRandomTest.NCALLS)
  }

  /** Repeated calls to nextBoolean produce at least two distinct results
   */
  @Test def testNextBoolean(): Unit = {
    val f = ThreadLocalRandom.current.nextBoolean
    var i = 0
    while ({
      i < ThreadLocalRandomTest.NCALLS && ThreadLocalRandom.current.nextBoolean == f
    }) i += 1
    assertTrue(i < ThreadLocalRandomTest.NCALLS)
  }

  /** Repeated calls to nextFloat produce at least two distinct results
   */
  @Test def testNextFloat(): Unit = {
    val f = ThreadLocalRandom.current.nextFloat
    var i = 0
    while ({
      i < ThreadLocalRandomTest.NCALLS && ThreadLocalRandom.current.nextFloat == f
    }) i += 1
    assertTrue(i < ThreadLocalRandomTest.NCALLS)
  }

  /** Repeated calls to nextDouble produce at least two distinct results
   */
  @Test def testNextDouble(): Unit = {
    val f = ThreadLocalRandom.current.nextDouble
    var i = 0
    while ({
      i < ThreadLocalRandomTest.NCALLS && ThreadLocalRandom.current.nextDouble == f
    }) i += 1
    assertTrue(i < ThreadLocalRandomTest.NCALLS)
  }

  /** Repeated calls to nextGaussian produce at least two distinct results
   */
  @Test def testNextGaussian(): Unit = {
    val f = ThreadLocalRandom.current.nextGaussian
    var i = 0
    while ({
      i < ThreadLocalRandomTest.NCALLS && ThreadLocalRandom.current.nextGaussian == f
    }) i += 1
    assertTrue(i < ThreadLocalRandomTest.NCALLS)
  }

  /** nextInt(non-positive) throws IllegalArgumentException
   */
  @Test def testNextIntBoundNonPositive(): Unit = {
    val rnd = ThreadLocalRandom.current
    for (bound <- Array[Int](0, -17, Integer.MIN_VALUE)) {
      try {
        rnd.nextInt(bound)
        shouldThrow()
      } catch {
        case success: IllegalArgumentException =>

      }
    }
  }

  /** nextInt(least >= bound) throws IllegalArgumentException
   */
  @Test def testNextIntBadBounds(): Unit = {
    val badBoundss = Array(
      Array(17, 2),
      Array(-42, -42),
      Array(Integer.MAX_VALUE, Integer.MIN_VALUE)
    )
    val rnd = ThreadLocalRandom.current
    for (badBounds <- badBoundss) {
      try {
        rnd.nextInt(badBounds(0), badBounds(1))
        shouldThrow()
      } catch {
        case success: IllegalArgumentException =>

      }
    }
  }

  /** nextInt(bound) returns 0 <= value < bound; repeated calls produce at least
   *  two distinct results
   */
  @Test def testNextIntBounded()
      : Unit = { // sample bound space across prime number increments
    var bound = 2
    while ({ bound < ThreadLocalRandomTest.MAX_INT_BOUND }) {
      val f = ThreadLocalRandom.current.nextInt(bound)
      assertTrue(0 <= f && f < bound)
      var i = 0
      var j = 0
      while (i < ThreadLocalRandomTest.NCALLS && {
            j = ThreadLocalRandom.current.nextInt(bound)
            j == f
          }) {
        assertTrue(0 <= j && j < bound)
        i += 1
      }
      assertTrue(i < ThreadLocalRandomTest.NCALLS)

      bound += 524959
    }
  }

  /** nextInt(least, bound) returns least <= value < bound; repeated calls
   *  produce at least two distinct results
   */
  @Test def testNextIntBounded2(): Unit = {
    var least = -15485863
    while ({ least < ThreadLocalRandomTest.MAX_INT_BOUND }) {
      var bound = least + 2
      while ({ bound > least && bound < ThreadLocalRandomTest.MAX_INT_BOUND }) {
        val f = ThreadLocalRandom.current.nextInt(least, bound)
        assertTrue(least <= f && f < bound)
        var i = 0
        var j = 0
        while (i < ThreadLocalRandomTest.NCALLS && {
              j = ThreadLocalRandom.current.nextInt(least, bound)
              j == f
            }) {
          assertTrue(least <= j && j < bound)
          i += 1
        }
        assertTrue(i < ThreadLocalRandomTest.NCALLS)

        bound += 49979687
      }

      least += 524959
    }
  }

  /** nextLong(non-positive) throws IllegalArgumentException
   */
  @Test def testNextLongBoundNonPositive(): Unit = {
    val rnd = ThreadLocalRandom.current
    for (bound <- Array[Long](0L, -17L, java.lang.Long.MIN_VALUE)) {
      try {
        rnd.nextLong(bound)
        shouldThrow()
      } catch {
        case success: IllegalArgumentException =>

      }
    }
  }

  /** nextLong(least >= bound) throws IllegalArgumentException
   */
  @Test def testNextLongBadBounds(): Unit = {
    val badBoundss = Array(
      Array(17L, 2L),
      Array(-42L, -42L),
      Array(java.lang.Long.MAX_VALUE, java.lang.Long.MIN_VALUE)
    )
    val rnd = ThreadLocalRandom.current
    for (badBounds <- badBoundss) {
      try {
        rnd.nextLong(badBounds(0), badBounds(1))
        shouldThrow()
      } catch {
        case success: IllegalArgumentException =>

      }
    }
  }

  /** nextLong(bound) returns 0 <= value < bound; repeated calls produce at
   *  least two distinct results
   */
  @Test def testNextLongBounded(): Unit = {
    var bound = 2L
    while (bound < ThreadLocalRandomTest.MAX_LONG_BOUND) {
      val f = ThreadLocalRandom.current.nextLong(bound)
      assertTrue(0 <= f && f < bound)
      var i = 0
      var j = 0L
      while (i < ThreadLocalRandomTest.NCALLS && {
            j = ThreadLocalRandom.current.nextLong(bound)
            j == f
          }) {
        assertTrue(0 <= j && j < bound)
        i += 1
      }
      assertTrue(i < ThreadLocalRandomTest.NCALLS)

      bound += 15485863
    }
  }

  /** nextLong(least, bound) returns least <= value < bound; repeated calls
   *  produce at least two distinct results
   */
  @Test def testNextLongBounded2(): Unit = {
    var least: Long = -86028121
    while (least < ThreadLocalRandomTest.MAX_LONG_BOUND) {
      var bound = least + 2
      while (bound > least && bound < ThreadLocalRandomTest.MAX_LONG_BOUND) {
        val f = ThreadLocalRandom.current.nextLong(least, bound)
        assertTrue(least <= f && f < bound)
        var i = 0
        var j = 0L
        while (i < ThreadLocalRandomTest.NCALLS && {
              j = ThreadLocalRandom.current.nextLong(least, bound)
              j == f
            }) {
          assertTrue(least <= j && j < bound)
          i += 1
        }
        assertTrue(i < ThreadLocalRandomTest.NCALLS)

        bound += Math.abs(bound * 7919)
      }

      least += 982451653L
    }
  }

  /** nextDouble(non-positive) throws IllegalArgumentException
   */
  @Test def testNextDoubleBoundNonPositive(): Unit = {
    val rnd = ThreadLocalRandom.current
    val badBounds = Array(
      0.0d,
      -17.0d,
      -java.lang.Double.MIN_VALUE,
      java.lang.Double.NEGATIVE_INFINITY,
      java.lang.Double.NaN
    )
    for (bound <- badBounds) {
      try {
        rnd.nextDouble(bound)
        shouldThrow()
      } catch {
        case success: IllegalArgumentException =>

      }
    }
  }

  /** nextDouble(least, bound) returns least <= value < bound; repeated calls
   *  produce at least two distinct results
   */
  @Test def testNextDoubleBounded2(): Unit = {
    var least = 0.0001
    while ({ least < 1.0e20 }) {
      var bound = least * 1.001
      while ({ bound < 1.0e20 }) {
        val f = ThreadLocalRandom.current.nextDouble(least, bound)
        assertTrue(least <= f && f < bound)
        var i = 0
        var j = .0
        while (i < ThreadLocalRandomTest.NCALLS && {
              j = ThreadLocalRandom.current.nextDouble(least, bound)
              j == f
            }) {
          assertTrue(least <= j && j < bound)
          i += 1
        }
        assertTrue(i < ThreadLocalRandomTest.NCALLS)

        bound *= 16
      }

      least *= 8
    }
  }

  /** Different threads produce different pseudo-random sequences
   */
  @Test def testDifferentSequences()
      : Unit = { // Don't use main thread's ThreadLocalRandom - it is likely to
    // be polluted by previous tests.
    val threadLocalRandom =
      new AtomicReference[ThreadLocalRandom]
    val rand = new AtomicLong
    var firstRand = 0L
    var firstThreadLocalRandom: ThreadLocalRandom = null
    val getRandomState = new CheckedRunnable() {
      override def realRun(): Unit = {
        val current = ThreadLocalRandom.current
        assertSame(current, ThreadLocalRandom.current)
        // test bug: the following is not guaranteed and not true in JDK8
        //                assertNotSame(current, threadLocalRandom.get());
        rand.set(current.nextLong)
        threadLocalRandom.set(current)
      }
    }
    val first = newStartedThread(getRandomState)
    awaitTermination(first)
    firstRand = rand.get
    firstThreadLocalRandom = threadLocalRandom.get
    for (i <- 0 until ThreadLocalRandomTest.NCALLS) {
      val t = newStartedThread(getRandomState)
      awaitTermination(t)
      if (firstRand != rand.get) return
    }
    fail("all threads generate the same pseudo-random sequence")
  }

  /** Repeated calls to nextBytes produce at least values of different signs for
   *  every byte
   */
  @Test def testNextBytes(): Unit = {
    import scala.util.control.Breaks._
    val rnd = ThreadLocalRandom.current
    val n = rnd.nextInt(1, 20)
    val bytes = new Array[Byte](n)
    breakable {
      for (i <- 0 until n) {
        var tries = ThreadLocalRandomTest.NCALLS
        while ({ { tries -= 1; tries + 1 } > 0 }) {
          val before = bytes(i)
          rnd.nextBytes(bytes)
          val after = bytes(i)
          if (after * before < 0) break()
        }
        fail("not enough variation in random bytes")
      }
    }
  }

  /** Filling an empty array with random bytes succeeds without effect.
   */
  @Test def testNextBytes_emptyArray(): Unit = {
    ThreadLocalRandom.current.nextBytes(new Array[Byte](0))
  }
  @Test def testNextBytes_nullArray(): Unit = {
    try {
      ThreadLocalRandom.current.nextBytes(null)
      shouldThrow()
    } catch {
      case success: NullPointerException =>

    }
  }
}
