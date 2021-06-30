/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent

import java.io.ObjectStreamField
import java.security.AccessControlContext
import java.util.Random
import java.util.Spliterator
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.function.DoubleConsumer
import java.util.function.IntConsumer
import java.util.function.LongConsumer
import java.util.stream.DoubleStream
import java.util.stream.IntStream
import java.util.stream.LongStream
import java.util.stream.StreamSupport

/**
 * A random number generator isolated to the current thread.  Like the
 * global {@link java.util.Random} generator used by the {@link
 * java.lang.Math} class, a {@code ThreadLocalRandom} is initialized
 * with an internally generated seed that may not otherwise be
 * modified. When applicable, use of {@code ThreadLocalRandom} rather
 * than shared {@code Random} objects in concurrent programs will
 * typically encounter much less overhead and contention.  Use of
 * {@code ThreadLocalRandom} is particularly appropriate when multiple
 * tasks (for example, each a {@link ForkJoinTask}) use random numbers
 * in parallel in thread pools.
 *
 * <p>Usages of this class should typically be of the form:
 * {@code ThreadLocalRandom.current().nextX(...)} (where
 * {@code X} is {@code Int}, {@code Long}, etc).
 * When all usages are of this form, it is never possible to
 * accidentally share a {@code ThreadLocalRandom} across multiple threads.
 *
 * <p>This class also provides additional commonly used bounded random
 * generation methods.
 *
 * <p>Instances of {@code ThreadLocalRandom} are not cryptographically
 * secure.  Consider instead using {@link java.security.SecureRandom}
 * in security-sensitive applications. Additionally,
 * default-constructed instances do not use a cryptographically random
 * seed unless the {@linkplain System#getProperty system property}
 * {@code java.util.secureRandomSeed} is set to {@code true}.
 *
 * @since 1.7
 * @author Doug Lea
 */ @SerialVersionUID(-5851777807851030925L)
object ThreadLocalRandom {
  private def mix64(z0: Long) = {
    var z = z0
    z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL
    z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L
    z ^ (z >>> 33)
  }

  private def mix32(z0: Long) = {
    var z = z0
    z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL
    (((z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L) >>> 32).toInt
  }

  /**
   * Initialize Thread fields for the current thread.  Called only
   * when Thread.threadLocalRandomProbe is zero, indicating that a
   * thread local seed value needs to be generated. Note that even
   * though the initialization is purely thread-local, we need to
   * rely on (static) atomic generators to initialize the values.
   */
  private[concurrent] def localInit(): Unit = {
    val p = probeGenerator.addAndGet(PROBE_INCREMENT)
    val probe =
      if (p == 0) 1
      else p // skip 0
    val seed = mix64(seeder.getAndAdd(SEEDER_INCREMENT))
    val t    = Thread.currentThread()
    t.threadLocalRandomSeed = seed
    t.threadLocalRandomProbe = probe
  }

  /**
   * Returns the current thread's {@code ThreadLocalRandom}.
   *
   * @return the current thread's {@code ThreadLocalRandom}
   */
  def current(): ThreadLocalRandom = {
    if (Thread.currentThread().threadLocalRandomProbe == 0)
      localInit()
    instance
  }

  /**
   * Spliterator for int streams.  We multiplex the four int
   * versions into one class by treating a bound less than origin as
   * unbounded, and also by treating "infinite" as equivalent to
   * Long.MAX_VALUE. For splits, it uses the standard divide-by-two
   * approach. The long and double versions of this class are
   * identical except for types.
   */
  final private class RandomIntsSpliterator private[concurrent] (
      var index: Long,
      fence: Long,
      origin: Int,
      bound: Int)
      extends Spliterator.OfInt {
    override def trySplit(): ThreadLocalRandom.RandomIntsSpliterator = {
      val i = index
      val m = (i + fence) >>> 1
      if (m <= i) null
      else {
        index = m
        new ThreadLocalRandom.RandomIntsSpliterator(i, m, origin, bound)
      }
    }

    override def estimateSize(): Long = fence - index

    override def characteristics(): Int = {
      Spliterator.SIZED |
        Spliterator.SUBSIZED |
        Spliterator.NONNULL |
        Spliterator.IMMUTABLE
    }

    override def tryAdvance(consumer: IntConsumer): Boolean = {
      if (consumer == null)
        throw new NullPointerException

      if (index < fence) {
        consumer.accept(
          ThreadLocalRandom.current().internalNextInt(origin, bound))
        index += 1
        return true
      }
      false
    }

    override def forEachRemaining(consumer: IntConsumer): Unit = {
      if (consumer == null)
        throw new NullPointerException

      if (index < fence) {
        var i = index

        index = fence
        val rng = ThreadLocalRandom.current()

        while ({
          consumer.accept(rng.internalNextInt(origin, bound))
          i += 1
          i < fence
        }) ()
      }
    }
  }

  /**
   * Spliterator for long streams.
   */
  final private class RandomLongsSpliterator private[concurrent] (
      var index: Long,
      fence: Long,
      origin: Long,
      bound: Long)
      extends Spliterator.OfLong {

    override def trySplit(): ThreadLocalRandom.RandomLongsSpliterator = {
      val i = index
      val m = (i + fence) >>> 1
      if (m <= index) null
      else {
        index = m
        new ThreadLocalRandom.RandomLongsSpliterator(i, m, origin, bound)
      }
    }

    override def estimateSize(): Long = fence - index
    override def characteristics(): Int = {
      Spliterator.SIZED |
        Spliterator.SUBSIZED |
        Spliterator.NONNULL |
        Spliterator.IMMUTABLE
    }

    override def tryAdvance(consumer: LongConsumer): Boolean = {
      if (consumer == null)
        throw new NullPointerException

      if (index < fence) {
        consumer.accept(
          ThreadLocalRandom.current().internalNextLong(origin, bound))
        index += 1
        return true
      }
      false
    }

    override def forEachRemaining(consumer: LongConsumer): Unit = {
      if (consumer == null)
        throw new NullPointerException

      if (index < fence) {
        val rng = ThreadLocalRandom.current()

        var i = index
        index = fence
        while ({
          consumer.accept(rng.internalNextLong(origin, bound))
          i += 1
          i < fence
        }) ()
      }
    }
  }

  /**
   * Spliterator for double streams.
   */
  final private class RandomDoublesSpliterator private[concurrent] (
      var index: Long,
      fence: Long,
      origin: Double,
      bound: Double)
      extends Spliterator.OfDouble {

    override def trySplit(): ThreadLocalRandom.RandomDoublesSpliterator = {
      val m = (index + fence) >>> 1
      if (m <= index) null
      else {
        val i = index
        index = m
        new ThreadLocalRandom.RandomDoublesSpliterator(i, m, origin, bound)
      }
    }
    override def estimateSize(): Long = fence - index
    override def characteristics(): Int = {
      Spliterator.SIZED |
        Spliterator.SUBSIZED |
        Spliterator.NONNULL |
        Spliterator.IMMUTABLE
    }
    override def tryAdvance(consumer: DoubleConsumer): Boolean = {
      if (consumer == null)
        throw new NullPointerException

      if (index < fence) {
        consumer.accept(
          ThreadLocalRandom.current().internalNextDouble()(origin, bound))
        index += 1
        return true
      }
      false
    }
    override def forEachRemaining(consumer: DoubleConsumer): Unit = {
      if (consumer == null)
        throw new NullPointerException

      var i = index
      if (index < fence) {
        val rng = ThreadLocalRandom.current()
        var i   = index
        index = fence
        while ({
          rng.internalNextDouble()(origin, bound)
          i += 1
          i < fence
        }) ()
      }
    }
  }

  /**
   * Returns the probe value for the current thread without forcing
   * initialization. Note that invoking ThreadLocalRandom.current()
   * can be used to force initialization on zero return.
   */
  private[concurrent] def getProbe(): Int =
    Thread.currentThread().threadLocalRandomProbe

  /**
   * Pseudo-randomly advances and records the given probe value for the
   * given thread.
   */
  private[concurrent] def advanceProbe(probe0: Int) = {
    var probe = probe0
    probe ^= probe << 13 // xorshift
    probe ^= probe >>> 17
    probe ^= probe << 5
    Thread.currentThread().threadLocalRandomProbe = probe
    probe
  }

  /**
   * Returns the pseudo-randomly initialized or updated secondary seed.
   */
  private[concurrent] def nextSecondarySeed = {
    val t      = Thread.currentThread()
    var r: Int = t.threadLocalRandomSecondarySeed
    if (r != 0) {
      r ^= r << 13
      r ^= r >>> 17
      r ^= r << 5
    } else {
      r = mix32(seeder.getAndAdd(SEEDER_INCREMENT))
      if (r == 0) r = 1 //avoid zero
    }
    //U.putInt(t, SECONDARY, r)
    t.threadLocalRandomSecondarySeed = r
    r
  }

  /**
   * Erases ThreadLocals by nulling out Thread maps.
   */
  private[concurrent] def eraseThreadLocals(thread: Thread): Unit = {
    ???
    // thread.localValues = null
    // thread.inheritableValues = null
  }

  private[concurrent] def setInheritedAccessControlContext(
      thread: Thread,
      acc: AccessControlContext): Unit = {
//    U.putObjectRelease(thread, INHERITEDACCESSCONTROLCONTEXT, acc)
  }

  /**
   * The seed increment.
   */
  private val GAMMA = 0x9e3779b97f4a7c15L

  /**
   * The increment for generating probe values.
   */
  private val PROBE_INCREMENT = 0x9e3779b9

  /**
   * The increment of seeder per new instance.
   */
  private val SEEDER_INCREMENT = 0xbb67ae8584caa73bL

  /**
   * The least non-zero value returned by nextDouble(). This value
   * is scaled by a random value of 53 bits to produce a result.
   */
  private val DOUBLE_UNIT = 1.0 / (1L << 53)
  private val FLOAT_UNIT  = 1.0f / (1 << 24)

  // IllegalArgumentException messages
  private[concurrent] val BAD_BOUND = "bound must be positive"
  private[concurrent] val BAD_RANGE = "bound must be greater than origin"
  private[concurrent] val BAD_SIZE  = "size must be non-negative"

  /** Rarely-used holder for the second of a pair of Gaussians */
  private val nextLocalGaussian = new ThreadLocal[Double]

  /** Generates per-thread initialization/probe field */
  private val probeGenerator = new AtomicInteger

  /** The common ThreadLocalRandom */
  private[concurrent] val instance = new ThreadLocalRandom

  /**
   * The next seed for default constructors.
   */
  private val seeder = new AtomicLong(
    mix64(System.currentTimeMillis()) ^ mix64(System.nanoTime()))

//  try {
//    val sec: String = VM.getSavedProperty("java.util.secureRandomSeed")
//    if (Boolean.parseBoolean(sec)) {
//      val seedBytes = java.security.SecureRandom.getSeed(8)
//      var s         = seedBytes(0).toLong & 0xffL
//      for (i <- 1 until 8) { s = (s << 8) | (seedBytes(i).toLong & 0xffL) }
//      seeder.set(s)
//    }
//  }
}

@SerialVersionUID(-5851777807851030925L)
class ThreadLocalRandom private () /** Constructor used only for static singleton */
    extends Random {

  /**
   * Field used only during singleton initialization.
   * True when constructor completes.
   */
  private[concurrent] var initialized = true

  /**
   * Throws {@code UnsupportedOperationException}.  Setting seeds in
   * this generator is not supported.
   *
   * @throws UnsupportedOperationException always
   */
  override def setSeed(seed: Long): Unit = { // only allow call from super() constructor
    if (initialized)
      throw new UnsupportedOperationException
  }
  final private[concurrent] def nextSeed(): Long = {
    val t = Thread.currentThread()
    t.threadLocalRandomSeed += ThreadLocalRandom.GAMMA // read and update per-thread seed
    t.threadLocalRandomSeed
  }

  /**
   * Generates a pseudorandom number with the indicated number of
   * low-order bits.  Because this class has no subclasses, this
   * method cannot be invoked or overridden.
   *
   * @param  bits random bits
   * @return the next pseudorandom value from this random number
   *         generator's sequence
   */
  override protected def next(bits: Int): Int = nextInt() >>> (32 - bits)

  /**
   * The form of nextLong used by LongStream Spliterators.  If
   * origin is greater than bound, acts as unbounded form of
   * nextLong, else as bounded form.
   *
   * @param origin the least value, unless greater than bound
   * @param bound the upper bound (exclusive), must not equal origin
   * @return a pseudorandom value
   */
  final private[concurrent] def internalNextLong(origin: Long, bound: Long) = {
    var r = ThreadLocalRandom.mix64(nextSeed())
    if (origin < bound) {
      val n = bound - origin
      val m = n - 1
      if ((n & m) == 0L) { // power of two
        r = (r & m) + origin
      } else if (n > 0L) { // reject over-represented candidates
        var u = r >>> 1 // ensure nonnegative
        r = u % n
        while ((u + m - r) < 0L) { // rejection check
          // retry
          u = ThreadLocalRandom.mix64(nextSeed()) >>> 1
        }
        r += origin
      } else { // range not representable as long
        while ({ r < origin || r >= bound }) {
          r = ThreadLocalRandom.mix64(nextSeed())
        }
      }
    }
    r
  }

  /**
   * The form of nextInt used by IntStream Spliterators.
   * Exactly the same as long version, except for types.
   *
   * @param origin the least value, unless greater than bound
   * @param bound the upper bound (exclusive), must not equal origin
   * @return a pseudorandom value
   */
  final private[concurrent] def internalNextInt(origin: Int, bound: Int) = {
    var r = ThreadLocalRandom.mix32(nextSeed())
    if (origin < bound) {
      val n = bound - origin
      val m = n - 1
      if ((n & m) == 0) r = (r & m) + origin
      else if (n > 0) {
        var u = r >>> 1
        r = u % n
        while ((u + m - r) < 0)
          u = ThreadLocalRandom.mix32(nextSeed()) >>> 1
        r += origin
      } else
        while ({ r < origin || r >= bound }) {
          r = ThreadLocalRandom.mix32(nextSeed())
        }
    }
    r
  }

  /**
   * The form of nextDouble() used by DoubleStream Spliterators.
   *
   * @param origin the least value, unless greater than bound
   * @param bound the upper bound (exclusive), must not equal origin
   * @return a pseudorandom value
   */
  final private[concurrent] def internalNextDouble()(origin: Double,
                                                     bound: Double) = {
    var r = (nextLong() >>> 11) * ThreadLocalRandom.DOUBLE_UNIT
    if (origin < bound) {
      r = r * (bound - origin) + origin
      if (r >= bound) { // correct for rounding
        r = java.lang.Double.longBitsToDouble(
          java.lang.Double.doubleToLongBits(bound) - 1
        )
      }
    }
    r
  }

  /**
   * Returns a pseudorandom {@code int} value.
   *
   * @return a pseudorandom {@code int} value
   */
  override def nextInt(): Int = ThreadLocalRandom.mix32(nextSeed())

  /**
   * Returns a pseudorandom {@code int} value between zero (inclusive)
   * and the specified bound (exclusive).
   *
   * @param bound the upper bound (exclusive).  Must be positive.
   * @return a pseudorandom {@code int} value between zero
   *         (inclusive) and the bound (exclusive)
   * @throws IllegalArgumentException if {@code bound} is not positive
   */
  override def nextInt(bound: Int): Int = {
    if (bound <= 0)
      throw new IllegalArgumentException(ThreadLocalRandom.BAD_BOUND)
    var r = ThreadLocalRandom.mix32(nextSeed())
    val m = bound - 1
    if ((bound & m) == 0) r &= m
    else {
      var u = r >>> 1
      r = u & bound
      while ((u + m - r) < 0) {
        u = ThreadLocalRandom.mix32(nextSeed()) >>> 1
      }
    }
    r
  }

  /**
   * Returns a pseudorandom {@code int} value between the specified
   * origin (inclusive) and the specified bound (exclusive).
   *
   * @param origin the least value returned
   * @param bound the upper bound (exclusive)
   * @return a pseudorandom {@code int} value between the origin
   *         (inclusive) and the bound (exclusive)
   * @throws IllegalArgumentException if {@code origin} is greater than
   *         or equal to {@code bound}
   */
  def nextInt(origin: Int, bound: Int): Int = {
    if (origin >= bound)
      throw new IllegalArgumentException(ThreadLocalRandom.BAD_RANGE)
    internalNextInt(origin, bound)
  }

  /**
   * Returns a pseudorandom {@code long} value.
   *
   * @return a pseudorandom {@code long} value
   */
  override def nextLong(): Long = ThreadLocalRandom.mix64(nextSeed())

  /**
   * Returns a pseudorandom {@code long} value between zero (inclusive)
   * and the specified bound (exclusive).
   *
   * @param bound the upper bound (exclusive).  Must be positive.
   * @return a pseudorandom {@code long} value between zero
   *         (inclusive) and the bound (exclusive)
   * @throws IllegalArgumentException if {@code bound} is not positive
   */
  def nextLong(bound: Long): Long = {
    if (bound <= 0)
      throw new IllegalArgumentException(ThreadLocalRandom.BAD_BOUND)
    var r = ThreadLocalRandom.mix64(nextSeed())
    val m = bound - 1
    if ((bound & m) == 0L) r &= m
    else {
      var u = r >>> 1
      r = u % bound
      while ((u + m - r) < 0L)
        u = ThreadLocalRandom.mix64(nextSeed()) >>> 1
    }
    r
  }

  /**
   * Returns a pseudorandom {@code long} value between the specified
   * origin (inclusive) and the specified bound (exclusive).
   *
   * @param origin the least value returned
   * @param bound the upper bound (exclusive)
   * @return a pseudorandom {@code long} value between the origin
   *         (inclusive) and the bound (exclusive)
   * @throws IllegalArgumentException if {@code origin} is greater than
   *         or equal to {@code bound}
   */
  def nextLong(origin: Long, bound: Long): Long = {
    if (origin >= bound)
      throw new IllegalArgumentException(ThreadLocalRandom.BAD_RANGE)
    internalNextLong(origin, bound)
  }

  /**
   * Returns a pseudorandom {@code double} value between zero
   * (inclusive) and one (exclusive).
   *
   * @return a pseudorandom {@code double} value between zero
   *         (inclusive) and one (exclusive)
   */
  override def nextDouble(): Double =
    (ThreadLocalRandom.mix64(nextSeed()) >>> 11) * ThreadLocalRandom.DOUBLE_UNIT

  /**
   * Returns a pseudorandom {@code double} value between 0.0
   * (inclusive) and the specified bound (exclusive).
   *
   * @param bound the upper bound (exclusive).  Must be positive.
   * @return a pseudorandom {@code double} value between zero
   *         (inclusive) and the bound (exclusive)
   * @throws IllegalArgumentException if {@code bound} is not positive
   */
  def nextDouble(bound: Double): Double = {
    if (!(bound > 0.0))
      throw new IllegalArgumentException(ThreadLocalRandom.BAD_BOUND)
    val result =
      (ThreadLocalRandom.mix64(nextSeed()) >>> 11) * ThreadLocalRandom.DOUBLE_UNIT * bound
    if (result < bound) result
    else
      java.lang.Double
        .longBitsToDouble(java.lang.Double.doubleToLongBits(bound) - 1)
  }

  /**
   * Returns a pseudorandom {@code double} value between the specified
   * origin (inclusive) and bound (exclusive).
   *
   * @param origin the least value returned
   * @param bound the upper bound (exclusive)
   * @return a pseudorandom {@code double} value between the origin
   *         (inclusive) and the bound (exclusive)
   * @throws IllegalArgumentException if {@code origin} is greater than
   *         or equal to {@code bound}
   */
  def nextDouble(origin: Double, bound: Double): Double = {
    if (!(origin < bound))
      throw new IllegalArgumentException(ThreadLocalRandom.BAD_RANGE)
    internalNextDouble()(origin, bound)
  }

  /**
   * Returns a pseudorandom {@code boolean} value.
   *
   * @return a pseudorandom {@code boolean} value
   */
  override def nextBoolean(): Boolean = ThreadLocalRandom.mix32(nextSeed()) < 0

  /**
   * Returns a pseudorandom {@code float} value between zero
   * (inclusive) and one (exclusive).
   *
   * @return a pseudorandom {@code float} value between zero
   *         (inclusive) and one (exclusive)
   */
  override def nextFloat(): Float =
    (ThreadLocalRandom.mix32(nextSeed()) >>> 8) * ThreadLocalRandom.FLOAT_UNIT
  override def nextGaussian()
      : Double = { // Use nextLocalGaussian instead of nextGaussian field
    val d =
      ThreadLocalRandom.nextLocalGaussian.get().asInstanceOf[java.lang.Double]
    if (d != null) {
      ThreadLocalRandom.nextLocalGaussian.set(null.asInstanceOf[Double])
      return d.doubleValue()
    }
    var v1 = .0
    var v2 = .0
    var s  = .0
    do {
      v1 = 2 * nextDouble() - 1 // between -1 and 1

      v2 = 2 * nextDouble() - 1
      s = v1 * v1 + v2 * v2
    } while ({ s >= 1 || s == 0 })

    val multiplier = Math.sqrt(-2 * Math.log(s) / s)
    ThreadLocalRandom.nextLocalGaussian.set(
      java.lang.Double.valueOf(v2 * multiplier).doubleValue()
    )
    v1 * multiplier
  }

  /**
   * Returns a stream producing the given {@code streamSize} number of
   * pseudorandom {@code int} values.
   *
   * @param streamSize the number of values to generate
   * @return a stream of pseudorandom {@code int} values
   * @throws IllegalArgumentException if {@code streamSize} is
   *         less than zero
   * @since 1.8
   */
  def ints(streamSize: Long): IntStream = {
    if (streamSize < 0L)
      throw new IllegalArgumentException(ThreadLocalRandom.BAD_SIZE)
    StreamSupport.intStream(
      new ThreadLocalRandom.RandomIntsSpliterator(0L,
                                                  streamSize,
                                                  Integer.MAX_VALUE,
                                                  0),
      false)
  }

  /**
   * Returns an effectively unlimited stream of pseudorandom {@code int}
   * values.
   *
   * @implNote This method is implemented to be equivalent to {@code
   * ints(Long.MAX_VALUE)}.
   *
   * @return a stream of pseudorandom {@code int} values
   * @since 1.8
   */
  def ints(): IntStream =
    StreamSupport.intStream(
      new ThreadLocalRandom.RandomIntsSpliterator(0L,
                                                  java.lang.Long.MAX_VALUE,
                                                  Integer.MAX_VALUE,
                                                  0),
      false)

  /**
   * Returns a stream producing the given {@code streamSize} number
   * of pseudorandom {@code int} values, each conforming to the given
   * origin (inclusive) and bound (exclusive).
   *
   * @param streamSize the number of values to generate
   * @param randomNumberOrigin the origin (inclusive) of each random value
   * @param randomNumberBound the bound (exclusive) of each random value
   * @return a stream of pseudorandom {@code int} values,
   *         each with the given origin (inclusive) and bound (exclusive)
   * @throws IllegalArgumentException if {@code streamSize} is
   *         less than zero, or {@code randomNumberOrigin}
   *         is greater than or equal to {@code randomNumberBound}
   * @since 1.8
   */
  def ints(streamSize: Long,
           randomNumberOrigin: Int,
           randomNumberBound: Int): IntStream = {
    if (streamSize < 0L)
      throw new IllegalArgumentException(ThreadLocalRandom.BAD_SIZE)
    if (randomNumberOrigin >= randomNumberBound)
      throw new IllegalArgumentException(ThreadLocalRandom.BAD_RANGE)
    StreamSupport.intStream(
      new ThreadLocalRandom.RandomIntsSpliterator(0L,
                                                  streamSize,
                                                  randomNumberOrigin,
                                                  randomNumberBound),
      false)
  }

  /**
   * Returns an effectively unlimited stream of pseudorandom {@code
   * int} values, each conforming to the given origin (inclusive) and bound
   * (exclusive).
   *
   * @implNote This method is implemented to be equivalent to {@code
   * ints(Long.MAX_VALUE, randomNumberOrigin, randomNumberBound)}.
   *
   * @param randomNumberOrigin the origin (inclusive) of each random value
   * @param randomNumberBound the bound (exclusive) of each random value
   * @return a stream of pseudorandom {@code int} values,
   *         each with the given origin (inclusive) and bound (exclusive)
   * @throws IllegalArgumentException if {@code randomNumberOrigin}
   *         is greater than or equal to {@code randomNumberBound}
   * @since 1.8
   */
  def ints(randomNumberOrigin: Int, randomNumberBound: Int): IntStream = {
    if (randomNumberOrigin >= randomNumberBound)
      throw new IllegalArgumentException(ThreadLocalRandom.BAD_RANGE)
    StreamSupport.intStream(
      new ThreadLocalRandom.RandomIntsSpliterator(0L,
                                                  java.lang.Long.MAX_VALUE,
                                                  randomNumberOrigin,
                                                  randomNumberBound),
      false)
  }

  /**
   * Returns a stream producing the given {@code streamSize} number of
   * pseudorandom {@code long} values.
   *
   * @param streamSize the number of values to generate
   * @return a stream of pseudorandom {@code long} values
   * @throws IllegalArgumentException if {@code streamSize} is
   *         less than zero
   * @since 1.8
   */
  def longs(streamSize: Long): LongStream = {
    if (streamSize < 0L)
      throw new IllegalArgumentException(ThreadLocalRandom.BAD_SIZE)
    StreamSupport.longStream(
      new ThreadLocalRandom.RandomLongsSpliterator(0L,
                                                   streamSize,
                                                   java.lang.Long.MAX_VALUE,
                                                   0L),
      false)
  }

  /**
   * Returns an effectively unlimited stream of pseudorandom {@code long}
   * values.
   *
   * @implNote This method is implemented to be equivalent to {@code
   * longs(Long.MAX_VALUE)}.
   *
   * @return a stream of pseudorandom {@code long} values
   * @since 1.8
   */
  def longs(): LongStream =
    StreamSupport.longStream(
      new ThreadLocalRandom.RandomLongsSpliterator(0L,
                                                   java.lang.Long.MAX_VALUE,
                                                   java.lang.Long.MAX_VALUE,
                                                   0L),
      false)

  /**
   * Returns a stream producing the given {@code streamSize} number of
   * pseudorandom {@code long}, each conforming to the given origin
   * (inclusive) and bound (exclusive).
   *
   * @param streamSize the number of values to generate
   * @param randomNumberOrigin the origin (inclusive) of each random value
   * @param randomNumberBound the bound (exclusive) of each random value
   * @return a stream of pseudorandom {@code long} values,
   *         each with the given origin (inclusive) and bound (exclusive)
   * @throws IllegalArgumentException if {@code streamSize} is
   *         less than zero, or {@code randomNumberOrigin}
   *         is greater than or equal to {@code randomNumberBound}
   * @since 1.8
   */
  def longs(streamSize: Long,
            randomNumberOrigin: Long,
            randomNumberBound: Long): LongStream = {
    if (streamSize < 0L)
      throw new IllegalArgumentException(ThreadLocalRandom.BAD_SIZE)
    if (randomNumberOrigin >= randomNumberBound)
      throw new IllegalArgumentException(ThreadLocalRandom.BAD_RANGE)
    StreamSupport.longStream(
      new ThreadLocalRandom.RandomLongsSpliterator(0L,
                                                   streamSize,
                                                   randomNumberOrigin,
                                                   randomNumberBound),
      false)
  }

  /**
   * Returns an effectively unlimited stream of pseudorandom {@code
   * long} values, each conforming to the given origin (inclusive) and bound
   * (exclusive).
   *
   * @implNote This method is implemented to be equivalent to {@code
   * longs(Long.MAX_VALUE, randomNumberOrigin, randomNumberBound)}.
   *
   * @param randomNumberOrigin the origin (inclusive) of each random value
   * @param randomNumberBound the bound (exclusive) of each random value
   * @return a stream of pseudorandom {@code long} values,
   *         each with the given origin (inclusive) and bound (exclusive)
   * @throws IllegalArgumentException if {@code randomNumberOrigin}
   *         is greater than or equal to {@code randomNumberBound}
   * @since 1.8
   */
  def longs(randomNumberOrigin: Long, randomNumberBound: Long): LongStream = {
    if (randomNumberOrigin >= randomNumberBound)
      throw new IllegalArgumentException(ThreadLocalRandom.BAD_RANGE)
    StreamSupport.longStream(
      new ThreadLocalRandom.RandomLongsSpliterator(0L,
                                                   java.lang.Long.MAX_VALUE,
                                                   randomNumberOrigin,
                                                   randomNumberBound),
      false)
  }

  /**
   * Returns a stream producing the given {@code streamSize} number of
   * pseudorandom {@code double} values, each between zero
   * (inclusive) and one (exclusive).
   *
   * @param streamSize the number of values to generate
   * @return a stream of {@code double} values
   * @throws IllegalArgumentException if {@code streamSize} is
   *         less than zero
   * @since 1.8
   */
  def doubles(streamSize: Long): DoubleStream = {
    if (streamSize < 0L)
      throw new IllegalArgumentException(ThreadLocalRandom.BAD_SIZE)
    StreamSupport.doubleStream(
      new ThreadLocalRandom.RandomDoublesSpliterator(0L,
                                                     streamSize,
                                                     java.lang.Double.MAX_VALUE,
                                                     0.0),
      false)
  }

  /**
   * Returns an effectively unlimited stream of pseudorandom {@code
   * double} values, each between zero (inclusive) and one
   * (exclusive).
   *
   * @implNote This method is implemented to be equivalent to {@code
   * doubles(Long.MAX_VALUE)}.
   *
   * @return a stream of pseudorandom {@code double} values
   * @since 1.8
   */
  def doubles(): DoubleStream =
    StreamSupport.doubleStream(
      new ThreadLocalRandom.RandomDoublesSpliterator(0L,
                                                     java.lang.Long.MAX_VALUE,
                                                     java.lang.Double.MAX_VALUE,
                                                     0.0),
      false)

  /**
   * Returns a stream producing the given {@code streamSize} number of
   * pseudorandom {@code double} values, each conforming to the given origin
   * (inclusive) and bound (exclusive).
   *
   * @param streamSize the number of values to generate
   * @param randomNumberOrigin the origin (inclusive) of each random value
   * @param randomNumberBound the bound (exclusive) of each random value
   * @return a stream of pseudorandom {@code double} values,
   *         each with the given origin (inclusive) and bound (exclusive)
   * @throws IllegalArgumentException if {@code streamSize} is
   *         less than zero, or {@code randomNumberOrigin}
   *         is greater than or equal to {@code randomNumberBound}
   * @since 1.8
   */
  def doubles(streamSize: Long,
              randomNumberOrigin: Double,
              randomNumberBound: Double): DoubleStream = {
    if (streamSize < 0L)
      throw new IllegalArgumentException(ThreadLocalRandom.BAD_SIZE)

    if (!(randomNumberOrigin < randomNumberBound))
      throw new IllegalArgumentException(ThreadLocalRandom.BAD_RANGE)

    StreamSupport.doubleStream(
      new ThreadLocalRandom.RandomDoublesSpliterator(0L,
                                                     streamSize,
                                                     randomNumberOrigin,
                                                     randomNumberBound),
      false)
  }

  /**
   * Returns an effectively unlimited stream of pseudorandom {@code
   * double} values, each conforming to the given origin (inclusive) and bound
   * (exclusive).
   *
   * @implNote This method is implemented to be equivalent to {@code
   * doubles(Long.MAX_VALUE, randomNumberOrigin, randomNumberBound)}.
   *
   * @param randomNumberOrigin the origin (inclusive) of each random value
   * @param randomNumberBound the bound (exclusive) of each random value
   * @return a stream of pseudorandom {@code double} values,
   *         each with the given origin (inclusive) and bound (exclusive)
   * @throws IllegalArgumentException if {@code randomNumberOrigin}
   *         is greater than or equal to {@code randomNumberBound}
   * @since 1.8
   */
  def doubles(randomNumberOrigin: Double,
              randomNumberBound: Double): DoubleStream = {
    if (!(randomNumberOrigin < randomNumberBound))
      throw new IllegalArgumentException(ThreadLocalRandom.BAD_RANGE)
    StreamSupport.doubleStream(
      new ThreadLocalRandom.RandomDoublesSpliterator(0L,
                                                     java.lang.Long.MAX_VALUE,
                                                     randomNumberOrigin,
                                                     randomNumberBound),
      false)
  }

}
