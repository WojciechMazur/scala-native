// /*
//  * Written by Doug Lea and Martin Buchholz with assistance from
//  * members of JCP JSR-166 Expert Group and released to the public
//  * domain, as explained at
//  * http://creativecommons.org/publicdomain/zero/1.0/
//  * Other contributors include Andrew Wright, Jeffrey Hayes,
//  * Pat Fisher, Mike Judd.
//  */

// /*
//  * @test
//  * @summary JSR-166 tck tests, in a number of variations.
//  *          The first is the conformance testing variant,
//  *          while others also test implementation details.
//  * @build *
//  * @modules java.management
//  * @run junit/othervm/timeout=1000 JSR166TestCase
//  * @run junit/othervm/timeout=1000
//  *      --add-opens java.base/java.util.concurrent=ALL-UNNAMED
//  *      --add-opens java.base/java.lang=ALL-UNNAMED
//  *      -Djsr166.testImplementationDetails=true
//  *      JSR166TestCase
//  * @run junit/othervm/timeout=1000
//  *      --add-opens java.base/java.util.concurrent=ALL-UNNAMED
//  *      --add-opens java.base/java.lang=ALL-UNNAMED
//  *      -Djsr166.testImplementationDetails=true
//  *      -Djava.util.concurrent.ForkJoinPool.common.parallelism=0
//  *      JSR166TestCase
//  * @run junit/othervm/timeout=1000
//  *      --add-opens java.base/java.util.concurrent=ALL-UNNAMED
//  *      --add-opens java.base/java.lang=ALL-UNNAMED
//  *      -Djsr166.testImplementationDetails=true
//  *      -Djava.util.concurrent.ForkJoinPool.common.parallelism=1
//  *      -Djava.util.secureRandomSeed=true
//  *      JSR166TestCase
//  * @run junit/othervm/timeout=1000/policy=tck.policy
//  *      --add-opens java.base/java.util.concurrent=ALL-UNNAMED
//  *      --add-opens java.base/java.lang=ALL-UNNAMED
//  *      -Djsr166.testImplementationDetails=true
//  *      JSR166TestCase
//  */

package org.scalanative.testsuite.javalib.util.concurrent

import java.util.concurrent.TimeUnit._
import java.io._
import java.util._
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

import org.junit.Assert._
import scala.scalanative.junit.utils.AssertThrows.assertThrows

import scala.util.Using

/** Base class for JSR166 Junit TCK tests. Defines some constants, utility
 *  methods and classes, as well as a simple framework for helping to make sure
 *  that assertions failing in generated threads cause the associated test that
 *  generated them to itself fail (which JUnit does not otherwise arrange). The
 *  rules for creating such tests are:
 *
 *  <ol>
 *
 *  <li>All code not running in the main test thread (manually spawned threads
 *  or the common fork join pool) must be checked for failure (and completion!).
 *  Mechanisms that can be used to ensure this are: <ol> <li>Signalling via a
 *  synchronizer like AtomicInteger or CountDownLatch that the task completed
 *  normally, which is checked before returning from the test method in the main
 *  thread. <li>Using the forms {@link #threadFail}, {@link #threadAssertTrue},
 *  or {@link #threadAssertNull}, (not {@code fail}, {@code assertTrue}, etc.)
 *  Only the most typically used JUnit assertion methods are defined this way,
 *  but enough to live with. <li>Recording failure explicitly using {@link
 *  #threadUnexpectedException} or {@link #threadRecordFailure}. <li>Using a
 *  wrapper like CheckedRunnable that uses one the mechanisms above. </ol>
 *
 *  <li>If you override {@link #setUp} or {@link #tearDown}, make sure to invoke
 *  {@code super.setUp} and {@code super.tearDown} within them. These methods
 *  are used to clear and check for thread assertion failures.
 *
 *  <li>All delays and timeouts must use one of the constants {@code
 *  SHORT_DELAY_MS}, {@code SMALL_DELAY_MS}, {@code MEDIUM_DELAY_MS}, {@code
 *  LONG_DELAY_MS}. The idea here is that a SHORT is always discriminable from
 *  zero time, and always allows enough time for the small amounts of
 *  computation (creating a thread, calling a few methods, etc) needed to reach
 *  a timeout point. Similarly, a SMALL is always discriminable as larger than
 *  SHORT and smaller than MEDIUM. And so on. These constants are set to
 *  conservative values, but even so, if there is ever any doubt, they can all
 *  be increased in one spot to rerun tests on slower platforms.
 *
 *  <li>All threads generated must be joined inside each test case method (or
 *  {@code fail} to do so) before returning from the method. The {@code
 *  joinPool} method can be used to do this when using Executors.
 *
 *  </ol>
 *
 *  <p><b>Other notes</b> <ul>
 *
 *  <li>Usually, there is one testcase method per JSR166 method covering
 *  "normal" operation, and then as many exception-testing methods as there are
 *  exceptions the method can throw. Sometimes there are multiple tests per
 *  JSR166 method when the different "normal" behaviors differ significantly.
 *  And sometimes testcases cover multiple methods when they cannot be tested in
 *  isolation.
 *
 *  <li>The documentation style for testcases is to provide as javadoc a simple
 *  sentence or two describing the property that the testcase method purports to
 *  test. The javadocs do not say anything about how the property is tested. To
 *  find out, read the code.
 *
 *  <li>These tests are "conformance tests", and do not attempt to test
 *  throughput, latency, scalability or other performance factors (see the
 *  separate "jtreg" tests for a set intended to check these for the most
 *  central aspects of functionality.) So, most tests use the smallest sensible
 *  numbers of threads, collection sizes, etc needed to check basic conformance.
 *
 *  <li>The test classes currently do not declare inclusion in any particular
 *  package to simplify things for people integrating them in TCK test suites.
 *
 *  <li>As a convenience, the {@code main} of this class (JSR166TestCase) runs
 *  all JSR166 unit tests.
 *
 *  </ul>
 */
trait JSR166Test {
  import JSR166Test._

  /** Returns a random element from given choices.
   */
  def chooseRandomly[T](choices: List[T]): T =
    choices.get(ThreadLocalRandom.current().nextInt(choices.size()))

  /** Returns a random element from given choices.
   */
  def chooseRandomly[T](choices: Array[T]): T = {
    choices(ThreadLocalRandom.current().nextInt(choices.length))
  }

//     /**
//      * Returns the shortest timed delay. This can be scaled up for
//      * slow machines using the jsr166.delay.factor system property,
//      * or via jtreg's -timeoutFactor: flag.
//      * http://openjdk.java.net/jtreg/command-help.html
//      */
  protected def getShortDelay(): Long = SHORT_DELAY_MS

  /** Returns a new Date instance representing a time at least delayMillis
   *  milliseconds in the future.
   */
  def delayedDate(delayMillis: Long): Date = {
    // Add 1 because currentTimeMillis is known to round into the past.
    new Date(System.currentTimeMillis() + delayMillis + 1)
  }

  /** The first exception encountered if any threadAssertXXX method fails.
   */
  private final val threadFailure: AtomicReference[Throwable] =
    new AtomicReference(null)

  /** Records an exception so that it can be rethrown later in the test harness
   *  thread, triggering a test case failure. Only the first failure is recorded
   *  subsequent calls to this method from within the same test have no effect.
   */
  def threadRecordFailure(t: Throwable) = {
    System.err.println(t)
    if (threadFailure.compareAndSet(null, t)) () // dumpTestThreads()
  }

//     public void setUp() {
//         setDelays()
//     }

//     void tearDownFail(String format, Object... args) {
//         String msg = toString() + ": " + String.format(format, args)
//         System.err.println(msg)
//         dumpTestThreads()
//         throw new AssertionError(msg)
//     }

//     /**
//      * Extra checks that get done for all test cases.
//      *
//      * Triggers test case failure if any thread assertions have failed,
//      * by rethrowing, in the test harness thread, any exception recorded
//      * earlier by threadRecordFailure.
//      *
//      * Triggers test case failure if interrupt status is set in the main thread.
//      */
//     public void tearDown() throws Exception {
//         Throwable t = threadFailure.getAndSet(null)
//         if (t != null) {
//             if (t instanceof Error)
//                 throw (Error) t
//             else if (t instanceof RuntimeException)
//                 throw (RuntimeException) t
//             else if (t instanceof Exception)
//                 throw (Exception) t
//             else
//                 throw new AssertionError(t.toString(), t)
//         }

//         if (Thread.interrupted())
//             tearDownFail("interrupt status set in main thread")

//         checkForkJoinPoolThreadLeaks()
//     }

//     /**
//      * Finds missing PoolCleaners
//      */
//     void checkForkJoinPoolThreadLeaks() throws InterruptedException {
//         Thread[] survivors = new Thread[7]
//         int count = Thread.enumerate(survivors)
//         for (int i = 0 i < count i++) {
//             Thread thread = survivors[i]
//             String name = thread.getName()
//             if (name.startsWith("ForkJoinPool-")) {
//                 // give thread some time to terminate
//                 thread.join(LONG_DELAY_MS)
//                 if (thread.isAlive())
//                     tearDownFail("Found leaked ForkJoinPool thread thread=%s",
//                                  thread)
//             }
//         }

//         if (!ForkJoinPool.commonPool()
//             .awaitQuiescence(LONG_DELAY_MS, MILLISECONDS))
//             tearDownFail("ForkJoin common pool thread stuck")
//     }

  /** Just like fail(reason), but additionally recording (using
   *  threadRecordFailure) any AssertionError thrown, so that the current
   *  testcase will fail.
   */
  def threadFail(reason: String): Unit = {
    try fail(reason)
    catch {
      case fail: AssertionError =>
        threadRecordFailure(fail)
        throw fail
    }
  }

  /** Just like assertTrue(b), but additionally recording (using
   *  threadRecordFailure) any AssertionError thrown, so that the current
   *  testcase will fail.
   */
  def threadAssertTrue(pred: => Boolean): Unit = {
    try assertTrue(pred)
    catch {
      case fail: AssertionError =>
        threadRecordFailure(fail)
        throw fail
    }
  }

  /** Just like assertFalse(b), but additionally recording (using
   *  threadRecordFailure) any AssertionError thrown, so that the current
   *  testcase will fail.
   */
  def threadAssertFalse(pred: => Boolean): Unit = {
    try assertFalse(pred)
    catch {
      case fail: AssertionError =>
        threadRecordFailure(fail)
        throw fail
    }
  }

//     /**
//      * Just like assertNull(x), but additionally recording (using
//      * threadRecordFailure) any AssertionError thrown, so that the
//      * current testcase will fail.
//      */
//     public void threadAssertNull(Object x) {
//         try {
//             assertNull(x)
//         } catch (AssertionError fail) {
//             threadRecordFailure(fail)
//             throw fail
//         }
//     }

//     /**
//      * Just like assertEquals(x, y), but additionally recording (using
//      * threadRecordFailure) any AssertionError thrown, so that the
//      * current testcase will fail.
//      */
//     public void threadAssertEquals(long x, long y) {
//         try {
//             assertEquals(x, y)
//         } catch (AssertionError fail) {
//             threadRecordFailure(fail)
//             throw fail
//         }
//     }

//     /**
//      * Just like assertEquals(x, y), but additionally recording (using
//      * threadRecordFailure) any AssertionError thrown, so that the
//      * current testcase will fail.
//      */
//     public void threadAssertEquals(Object x, Object y) {
//         try {
//             assertEquals(x, y)
//         } catch (AssertionError fail) {
//             threadRecordFailure(fail)
//             throw fail
//         } catch (Throwable fail) {
//             threadUnexpectedException(fail)
//         }
//     }

  /** Fails with message "should throw exception".
   */
  def shouldThrow(exceptionName: String = "exception"): Unit = fail(
    s"Should throw $exceptionName"
  )

//     /**
//      * Just like assertSame(x, y), but additionally recording (using
//      * threadRecordFailure) any AssertionError thrown, so that the
//      * current testcase will fail.
//      */
//     public void threadAssertSame(Object x, Object y) {
//         try {
//             assertSame(x, y)
//         } catch (AssertionError fail) {
//             threadRecordFailure(fail)
//             throw fail
//         }
//     }

  /** Calls threadFail with message "should throw exception".
   */
  def threadShouldThrow(): Unit = threadFail("should throw exception")

  /** Calls threadFail with message "should throw" + exceptionName.
   */
  def threadShouldThrow(exceptionName: String): Unit = threadFail(
    "should throw " + exceptionName
  )

  /** Records the given exception using {@link #threadRecordFailure}, then
   *  rethrows the exception, wrapping it in an AssertionError if necessary.
   */
  def threadUnexpectedException(t: Throwable): Unit = {
    threadRecordFailure(t)
    // t.printStackTrace()
    t match {
      case t: RuntimeException => throw t
      case t: Error            => throw t
      case t => throw new AssertionError(s"unexpected exception: $t", t)
    }
  }

  /** Allows use of try-with-resources with per-test thread pools.
   */
  class PoolCleaner(val pool: ExecutorService) extends AutoCloseable {
    def close(): Unit = joinPool(pool)
  }

  /** An extension of PoolCleaner that has an action to release the pool.
   */
  class PoolCleanerWithReleaser(pool: ExecutorService, releaser: Runnable)
      extends PoolCleaner(pool) {
    override def close(): Unit = {
      try releaser.run()
      finally super.close()
    }
  }

  def usingWrappedPoolCleaner[Executor <: ExecutorService, T](pool: Executor)(
      wrapper: Executor => PoolCleaner
  )(fn: Executor => T): T = usingPoolCleaner(pool, wrapper)(fn)

  def usingPoolCleaner[Executor <: ExecutorService, T](
      pool: Executor,
      wrapper: Executor => PoolCleaner = cleaner(_: ExecutorService)
  )(fn: Executor => T): T = {
    val cleaner = wrapper(pool)
    try fn(pool)
    catch {
      case t: Throwable =>
        fail(s"Pool cleanup failed: $t")
        null.asInstanceOf[T]
    } finally cleaner.close()
  }

  def cleaner(pool: ExecutorService): PoolCleaner = new PoolCleaner(pool)
  def cleaner(pool: ExecutorService, releaser: Runnable) =
    new PoolCleanerWithReleaser(pool, releaser)
  def cleaner(pool: ExecutorService, latch: CountDownLatch) =
    new PoolCleanerWithReleaser(pool, releaser(latch))
  def cleaner(pool: ExecutorService, flag: AtomicBoolean) =
    new PoolCleanerWithReleaser(pool, releaser(flag))

  def releaser(latch: CountDownLatch) = new Runnable() {
    def run(): Unit = while ({
      latch.countDown()
      latch.getCount() > 0
    }) ()
  }

  def releaser(flag: AtomicBoolean) = new Runnable() {
    def run(): Unit = flag.set(true)
  }

  /** Waits out termination of a thread pool or fails doing so.
   */
  def joinPool(pool: ExecutorService): Unit =
    try {
      pool.shutdown()
      if (!pool.awaitTermination(2 * LONG_DELAY_MS, MILLISECONDS)) {
        try {
          threadFail(
            s"ExecutorService $pool did not terminate in a timely manner"
          )
        } finally {
          // last resort, for the benefit of subsequent tests
          pool.shutdownNow()
          val res = pool.awaitTermination(MEDIUM_DELAY_MS, MILLISECONDS)
        }
      }
    } catch {
      case ok: SecurityException =>
        // Allowed in case test doesn't have privs
        ()
      case fail: InterruptedException =>
        threadFail("Unexpected InterruptedException")
    }

  /** Like Runnable, but with the freedom to throw anything. junit folks had the
   *  same idea:
   *  http://junit.org/junit5/docs/snapshot/api/org/junit/gen5/api/Executable.html
   */
  trait Action { def run(): Unit }

//     /**
//      * Runs all the given actions in parallel, failing if any fail.
//      * Useful for running multiple variants of tests that are
//      * necessarily individually slow because they must block.
//      */
//     void testInParallel(Action ... actions) {
//         ExecutorService pool = Executors.newCachedThreadPool()
//         try (PoolCleaner cleaner = cleaner(pool)) {
//             ArrayList<Future<?>> futures = new ArrayList<>(actions.length)
//             for (final Action action : actions)
//                 futures.add(pool.submit(new CheckedRunnable() {
//                     public void realRun() throws Throwable { action.run()}}))
//             for (Future<?> future : futures)
//                 try {
//                     assertNull(future.get(LONG_DELAY_MS, MILLISECONDS))
//                 } catch (ExecutionException ex) {
//                     threadUnexpectedException(ex.getCause())
//                 } catch (Exception ex) {
//                     threadUnexpectedException(ex)
//                 }
//         }
//     }

  /** Checks that thread eventually enters the expected blocked thread state.
   */
  def assertThreadBlocks(thread: Thread, expected: Thread.State): Unit = {
    // always sleep at least 1 ms, with high probability avoiding
    // transitory states
    for (retries <- LONG_DELAY_MS * 3 / 4 until 0 by -1) {
      try delay(1)
      catch {
        case fail: InterruptedException =>
          throw new AssertionError("Unexpected InterruptedException", fail)
      }
      val s = thread.getState()
      if (s == expected) return ()
      else if (s == Thread.State.TERMINATED)
        fail("Unexpected thread termination")
    }
    fail("timed out waiting for thread to enter thread state " + expected)
  }

//     /**
//      * Checks that future.get times out, with the default timeout of
//      * {@code timeoutMillis()}.
//      */
//     void assertFutureTimesOut(Future future) {
//         assertFutureTimesOut(future, timeoutMillis())
//     }

//     /**
//      * Checks that future.get times out, with the given millisecond timeout.
//      */
//     void assertFutureTimesOut(Future future, long timeoutMillis) {
//         long startTime = System.nanoTime()
//         try {
//             future.get(timeoutMillis, MILLISECONDS)
//             shouldThrow()
//         } catch (TimeoutException success) {
//         } catch (Exception fail) {
//             threadUnexpectedException(fail)
//         }
//         assertTrue(millisElapsedSince(startTime) >= timeoutMillis)
//         assertFalse(future.isDone())
//     }

//     /**
//      * Fails with message "should throw exception".
//      */
//     public void shouldThrow() {
//         fail("Should throw exception")
//     }

//     /**
//      * Fails with message "should throw " + exceptionName.
//      */
//     public void shouldThrow(String exceptionName) {
//         fail("Should throw " + exceptionName)
//     }

//     /**
//      * Runs Runnable r with a security policy that permits precisely
//      * the specified permissions.  If there is no current security
//      * manager, the runnable is run twice, both with and without a
//      * security manager.  We require that any security manager permit
//      * getPolicy/setPolicy.
//      */
//     public void runWithPermissions(Runnable r, Permission... permissions) {
//         SecurityManager sm = System.getSecurityManager()
//         if (sm == null) {
//             r.run()
//         }
//         runWithSecurityManagerWithPermissions(r, permissions)
//     }

//     /**
//      * Runs Runnable r with a security policy that permits precisely
//      * the specified permissions.  If there is no current security
//      * manager, a temporary one is set for the duration of the
//      * Runnable.  We require that any security manager permit
//      * getPolicy/setPolicy.
//      */
//     public void runWithSecurityManagerWithPermissions(Runnable r,
//                                                       Permission... permissions) {
//         SecurityManager sm = System.getSecurityManager()
//         if (sm == null) {
//             Policy savedPolicy = Policy.getPolicy()
//             try {
//                 Policy.setPolicy(permissivePolicy())
//                 System.setSecurityManager(new SecurityManager())
//                 runWithSecurityManagerWithPermissions(r, permissions)
//             } finally {
//                 System.setSecurityManager(null)
//                 Policy.setPolicy(savedPolicy)
//             }
//         } else {
//             Policy savedPolicy = Policy.getPolicy()
//             AdjustablePolicy policy = new AdjustablePolicy(permissions)
//             Policy.setPolicy(policy)

//             try {
//                 r.run()
//             } finally {
//                 policy.addPermission(new SecurityPermission("setPolicy"))
//                 Policy.setPolicy(savedPolicy)
//             }
//         }
//     }

//     /**
//      * Runs a runnable without any permissions.
//      */
//     public void runWithoutPermissions(Runnable r) {
//         runWithPermissions(r)
//     }

  /** Spin-waits up to the specified number of milliseconds for the given thread
   *  to enter a wait state: BLOCKED, WAITING, or TIMED_WAITING.
   *  @param waitingForGodot
   *    if non-null, an additional condition to satisfy
   */
  def waitForThreadToEnterWaitState(
      thread: Thread,
      timeoutMillis: Long,
      waitingForGodot: Callable[Boolean]
  ): Unit = {
    lazy val startTime = System.nanoTime()
    import Thread.State._
    while (true) {
      thread.getState() match {
        case BLOCKED | WAITING | TIMED_WAITING =>
          try {
            if (waitingForGodot == null || waitingForGodot.call()) return ()
          } catch { case fail: Throwable => threadUnexpectedException(fail) }
        case TERMINATED =>
          fail("Unexpected thread termination")
        case _ => ()
      }
      if (millisElapsedSince(startTime) > timeoutMillis) {
        assertTrue(thread.isAlive())
        if (waitingForGodot == null
            || thread.getState() == Thread.State.RUNNABLE)
          fail("timed out waiting for thread to enter wait state")
        else
          fail(
            s"timed out waiting for condition, thread state=${thread.getState()}"
          )
      }
      Thread.`yield`()
    }
  }

  /** Spin-waits up to the specified number of milliseconds for the given thread
   *  to enter a wait state: BLOCKED, WAITING, or TIMED_WAITING.
   */
  def waitForThreadToEnterWaitState(thread: Thread, timeoutMillis: Long): Unit =
    waitForThreadToEnterWaitState(thread, timeoutMillis, null)

  /** Spin-waits up to LONG_DELAY_MS milliseconds for the given thread to enter
   *  a wait state: BLOCKED, WAITING, or TIMED_WAITING.
   */
  def waitForThreadToEnterWaitState(thread: Thread): Unit =
    waitForThreadToEnterWaitState(thread, LONG_DELAY_MS, null)

  /** Spin-waits up to LONG_DELAY_MS milliseconds for the given thread to enter
   *  a wait state: BLOCKED, WAITING, or TIMED_WAITING, and additionally satisfy
   *  the given condition.
   */
  def waitForThreadToEnterWaitState(
      thread: Thread,
      waitingForGodot: Callable[Boolean]
  ): Unit =
    waitForThreadToEnterWaitState(thread, LONG_DELAY_MS, waitingForGodot)

  /** Spin-waits up to LONG_DELAY_MS milliseconds for the current thread to be
   *  interrupted. Clears the interrupt status before returning.
   */
  def awaitInterrupted(): Unit = {
    lazy val startTime = System.nanoTime()
    while (!Thread.interrupted()) {
      if (millisElapsedSince(startTime) > LONG_DELAY_MS)
        fail("timed out waiting for thread interrupt")
      Thread.`yield`()
    }
  }

//     /**
//      * Checks that timed f.get() returns the expected value, and does not
//      * wait for the timeout to elapse before returning.
//      */
//     <T> void checkTimedGet(Future<T> f, T expectedValue, long timeoutMillis) {
//         long startTime = System.nanoTime()
//         T actual = null
//         try {
//             actual = f.get(timeoutMillis, MILLISECONDS)
//         } catch (Throwable fail) { threadUnexpectedException(fail) }
//         assertEquals(expectedValue, actual)
//         if (millisElapsedSince(startTime) > timeoutMillis/2)
//             throw new AssertionError("timed get did not return promptly")
//     }

//     <T> void checkTimedGet(Future<T> f, T expectedValue) {
//         checkTimedGet(f, expectedValue, LONG_DELAY_MS)
//     }

  /** Returns a new started daemon Thread running the given runnable.
   */
  def newStartedThread(runnable: Runnable): Thread = {
    val t = new Thread(runnable)
    t.setDaemon(true)
    t.start()
    t
  }

//     /**
//      * Returns a new started daemon Thread running the given action,
//      * wrapped in a CheckedRunnable.
//      */
//     Thread newStartedThread(Action action) {
//         return newStartedThread(checkedRunnable(action))
//     }

  /** Waits for the specified time (in milliseconds) for the thread to terminate
   *  (using {@link Thread#join(long)}), else interrupts the thread (in the hope
   *  that it may terminate later) and fails.
   */
  def awaitTermination(thread: Thread, timeoutMillis: Long = LONG_DELAY_MS) = {
    try thread.join(timeoutMillis)
    catch { case fail: InterruptedException => threadUnexpectedException(fail) }
    if (thread.getState() != Thread.State.TERMINATED) {
      try
        threadFail(
          s"timed out waiting for thread to terminate, thread=$thread, state=${thread.getState()}"
        )
      // Interrupt thread __after__ having reported its stack trace
      finally thread.interrupt()
    }
  }

  // Some convenient Runnable classes

  abstract class CheckedRunnable extends Runnable {
    @throws[Throwable]
    protected def realRun(): Unit

    final def run(): Unit = {
      try realRun()
      catch {
        case fail: Throwable => threadUnexpectedException(fail)
      }
    }
  }

//     Runnable checkedRunnable(Action action) {
//         return new CheckedRunnable() {
//             public void realRun() throws Throwable {
//                 action.run()
//             }}
//     }

  abstract class ThreadShouldThrow[T](val exceptionClass: Class[T])
      extends Thread {
    protected def realRun(): Unit
    final override def run(): Unit = {
      try {
        realRun()
        threadShouldThrow(exceptionClass.getSimpleName())
      } catch {
        case t: Throwable =>
          if (!exceptionClass.isInstance(t)) threadUnexpectedException(t)
      }
    }
  }

  abstract class CheckedInterruptedRunnable extends Runnable {
    protected def realRun(): Unit

    final def run(): Unit = {
      try
        assertThrows(classOf[InterruptedException], realRun())
      catch {
        case success: InterruptedException =>
          threadAssertFalse(Thread.interrupted())
        case fail: Throwable => threadUnexpectedException(fail)
      }

    }
  }

  abstract class CheckedCallable[T] extends Callable[T] {
    protected def realCall(): T
    final def call(): T = {
      try return realCall()
      catch {
        case fail: Throwable =>
          threadUnexpectedException(fail)
          null.asInstanceOf[T]
      }
      throw new AssertionError("unreached")
    }
  }

  class NoOpRunnable extends Runnable {
    def run() = ()
  }

  class NoOpCallable extends Callable[Any] {
    def call(): Any = java.lang.Boolean.TRUE
  }

  final val TEST_STRING = "a test string"

  class StringTask(value: String = TEST_STRING) extends Callable[String] {
    def call() = value
  }

  def latchAwaitingStringTask(latch: CountDownLatch): Callable[String] =
    new CheckedCallable[String] {
      override protected def realCall(): String = {
        try latch.await()
        catch {
          case quittingTime: InterruptedException => ()
        }
        TEST_STRING
      }
    }

//     public Runnable countDowner(final CountDownLatch latch) {
//         return new CheckedRunnable() {
//             public void realRun() throws InterruptedException {
//                 latch.countDown()
//             }}
//     }

  object LatchAwaiter {
    final val NEW = 0
    final val RUNNING = 1
    final val DONE = 2
  }
  class LatchAwaiter(latch: CountDownLatch) extends CheckedRunnable {
    import LatchAwaiter._
    var state = NEW
    @throws[InterruptedException]
    def realRun(): Unit = {
      state = 1
      await(latch)
      state = 2
    }
  }

  def awaiter(latch: CountDownLatch) = new LatchAwaiter(latch)

  def await(latch: CountDownLatch, timeoutMillis: Long = LONG_DELAY_MS) = {
    util
      .Try(
        !latch.await(timeoutMillis, MILLISECONDS)
      )
      .fold(
        threadUnexpectedException(_), {
          if (_)
            fail(
              s"timed out waiting for CountDownLatch for ${timeoutMillis / 1000} sec"
            )
        }
      )
  }

//     public void await(Semaphore semaphore) {
//         boolean timedOut = false
//         try {
//             timedOut = !semaphore.tryAcquire(LONG_DELAY_MS, MILLISECONDS)
//         } catch (Throwable fail) {
//             threadUnexpectedException(fail)
//         }
//         if (timedOut)
//             fail("timed out waiting for Semaphore for "
//                  + (LONG_DELAY_MS/1000) + " sec")
//     }

//     public void await(CyclicBarrier barrier) {
//         try {
//             barrier.await(LONG_DELAY_MS, MILLISECONDS)
//         } catch (Throwable fail) {
//             threadUnexpectedException(fail)
//         }
//     }

// //     /**
// //      * Spin-waits up to LONG_DELAY_MS until flag becomes true.
// //      */
// //     public void await(AtomicBoolean flag) {
// //         await(flag, LONG_DELAY_MS)
// //     }

// //     /**
// //      * Spin-waits up to the specified timeout until flag becomes true.
// //      */
// //     public void await(AtomicBoolean flag, long timeoutMillis) {
// //         long startTime = System.nanoTime()
// //         while (!flag.get()) {
// //             if (millisElapsedSince(startTime) > timeoutMillis)
// //                 throw new AssertionError("timed out")
// //             Thread.yield()
// //         }
// //     }

  class NPETask extends Callable[String] {
    override def call(): String = throw new NullPointerException()
  }

  def possiblyInterruptedRunnable(timeoutMillis: Long): Runnable =
    new CheckedRunnable() {
      override protected def realRun() =
        try delay(timeoutMillis)
        catch { case ok: InterruptedException => () }
    }

  /** For use as ThreadFactory in constructors
   */
  class SimpleThreadFactory extends ThreadFactory {
    def newThread(r: Runnable): Thread = new Thread(r)
  }

  trait TrackedRunnable extends Runnable {
    def isDone: Boolean
  }

  class TrackedNoOpRunnable extends Runnable {
    @volatile var done = false
    def run(): Unit = {
      done = true
    }
  }

  /** Analog of CheckedRunnable for RecursiveAction
   */
  abstract class CheckedRecursiveAction extends RecursiveAction {
    protected def realCompute(): Unit

    override protected final def compute(): Unit =
      try realCompute()
      catch { case fail: Throwable => threadUnexpectedException(fail) }
  }

  /** Analog of CheckedCallable for RecursiveTask
   */
  abstract class CheckedRecursiveTask[T] extends RecursiveTask[T] {
    protected def realCompute(): T
    override final protected def compute(): T = {
      try {
        return realCompute()
      } catch {
        case fail: Throwable =>
          threadUnexpectedException(fail)
      }
      throw new AssertionError("unreached")
    }
  }

  /** For use as RejectedExecutionHandler in constructors
   */
  class NoOpREHandler extends RejectedExecutionHandler {
    def rejectedExecution(r: Runnable, executor: ThreadPoolExecutor): Unit = ()
  }

  /** A CyclicBarrier that uses timed await and fails with AssertionErrors
   *  instead of throwing checked exceptions.
   */
  class CheckedBarrier(parties: Int) extends CyclicBarrier(parties) {
    override def await(): Int = {
      try super.await(LONGER_DELAY_MS, MILLISECONDS)
      catch {
        case _: TimeoutException => throw new AssertionError("timed out")
        case fail: Exception =>
          throw new AssertionError("Unexpected exception: " + fail, fail)
      }
    }
  }

  def checkEmpty(q: BlockingQueue[_]): Unit = {
    try {
      assertTrue(q.isEmpty())
      assertEquals(0, q.size())
      assertNull(q.peek())
      assertNull(q.poll())
      assertNull(q.poll(randomExpiredTimeout(), randomTimeUnit()))
      assertEquals(q.toString(), "[]")
      assertTrue(Arrays.equals(q.toArray(), Array.empty[Any]))
      assertFalse(q.iterator().hasNext())
      try {
        q.element()
        shouldThrow()
      } catch { case _: NoSuchElementException => () }
      try {
        q.iterator().next()
        shouldThrow()
      } catch { case _: NoSuchElementException => () }
      try {
        q.remove()
        shouldThrow()
      } catch { case _: NoSuchElementException => () }
    } catch {
      case fail: InterruptedException => threadUnexpectedException(fail)
    }
  }

//     void assertSerialEquals(Object x, Object y) {
//         assertTrue(Arrays.equals(serialBytes(x), serialBytes(y)))
//     }

//     void assertNotSerialEquals(Object x, Object y) {
//         assertFalse(Arrays.equals(serialBytes(x), serialBytes(y)))
//     }

  def serialBytes(o: Object): Array[Byte] = {
    try {
      val bos = new ByteArrayOutputStream()
      val oos = new ObjectOutputStream(bos)
      oos.writeObject(o)
      oos.flush()
      oos.close()
      bos.toByteArray()
    } catch {
      case fail: Throwable =>
        threadUnexpectedException(fail)
        Array.empty
    }
  }

  def assertImmutable(o: Object): Unit = {
    o match {
      case c: Collection[Any] @unchecked =>
        assertThrows(
          classOf[UnsupportedOperationException],
          () => c.add(null: Any)
        )
      case _ => ()
    }
  }

  def serialClone[T <: AnyRef](o: T): T = {
    val clone =
      try {
        val ois = new ObjectInputStream(
          new ByteArrayInputStream(serialBytes(o))
        )
        ois.readObject().asInstanceOf[T]
      } catch {
        case fail: Throwable =>
          threadUnexpectedException(fail)
          null.asInstanceOf[T]
      }
    if (o == clone) assertImmutable(o)
    else assertSame(o.getClass(), clone.getClass())

    clone
  }

//     /**
//      * A version of serialClone that leaves error handling (for
//      * e.g. NotSerializableException) up to the caller.
//      */
//     @SuppressWarnings("unchecked")
//     <T> T serialClonePossiblyFailing(T o)
//         throws ReflectiveOperationException, java.io.IOException {
//         ByteArrayOutputStream bos = new ByteArrayOutputStream()
//         ObjectOutputStream oos = new ObjectOutputStream(bos)
//         oos.writeObject(o)
//         oos.flush()
//         oos.close()
//         ObjectInputStream ois = new ObjectInputStream
//             (new ByteArrayInputStream(bos.toByteArray()))
//         T clone = (T) ois.readObject()
//         if (o == clone) assertImmutable(o)
//         else assertSame(o.getClass(), clone.getClass())
//         return clone
//     }

//     /**
//      * If o implements Cloneable and has a public clone method,
//      * returns a clone of o, else null.
//      */
//     @SuppressWarnings("unchecked")
//     <T> T cloneableClone(T o) {
//         if (!(o instanceof Cloneable)) return null
//         final T clone
//         try {
//             clone = (T) o.getClass().getMethod("clone").invoke(o)
//         } catch (NoSuchMethodException ok) {
//             return null
//         } catch (ReflectiveOperationException unexpected) {
//             throw new Error(unexpected)
//         }
//         assertNotSame(o, clone) // not 100% guaranteed by spec
//         assertSame(o.getClass(), clone.getClass())
//         return clone
//     }

//     public void assertThrows(Class<? extends Throwable> expectedExceptionClass,
//                              Action... throwingActions) {
//         for (Action throwingAction : throwingActions) {
//             boolean threw = false
//             try { throwingAction.run() }
//             catch (Throwable t) {
//                 threw = true
//                 if (!expectedExceptionClass.isInstance(t))
//                     throw new AssertionError(
//                             "Expected " + expectedExceptionClass.getName() +
//                             ", got " + t.getClass().getName(),
//                             t)
//             }
//             if (!threw)
//                 shouldThrow(expectedExceptionClass.getName())
//         }
//     }

  def assertIteratorExhausted(it: Iterator[_]): Unit = {
    try {
      it.next()
      shouldThrow()
    } catch { case _: NoSuchElementException => () }
    assertFalse(it.hasNext())
  }

//     public <T> Callable<T> callableThrowing(final Exception ex) {
//         return new Callable<T>() { public T call() throws Exception { throw ex }}
//     }

//     public Runnable runnableThrowing(final RuntimeException ex) {
//         return new Runnable() { public void run() { throw ex }}
//     }

//     /** A reusable thread pool to be shared by tests. */
//     static final ExecutorService cachedThreadPool =
//         new ThreadPoolExecutor(0, Integer.MAX_VALUE,
//                                1000L, MILLISECONDS,
//                                new SynchronousQueue<Runnable>())

  def shuffle[T](array: Array[T]) = {
    Collections.shuffle(Arrays.asList(array), ThreadLocalRandom.current())
  }

//     /**
//      * Returns the same String as would be returned by {@link
//      * Object#toString}, whether or not the given object's class
//      * overrides toString().
//      *
//      * @see System#identityHashCode
//      */
  def identityString(x: AnyRef): String = {
    x.getClass().getName() + "@" +
      Integer.toHexString(System.identityHashCode(x))
  }

//     // --- Shared assertions for Executor tests ---

//     }

  // @SuppressWarnings("FutureReturnValueIgnored")
  def assertNullTaskSubmissionThrowsNullPointerException(e: Executor): Unit = {
    val nullRunnable: Runnable = null
    val nullCallable: Callable[Any] = null
    try {
      e.execute(nullRunnable)
      shouldThrow()
    } catch { case success: NullPointerException => () }

    if (!e.isInstanceOf[ExecutorService]) return ()

    val es = e.asInstanceOf[ExecutorService]
    try {
      es.submit(nullRunnable)
      shouldThrow()
    } catch { case _: NullPointerException => () }

    try {
      es.submit(nullRunnable, java.lang.Boolean.TRUE)
      shouldThrow()
    } catch { case sucess: NullPointerException => () }
    try {
      es.submit(nullCallable)
      shouldThrow()
    } catch { case sucess: NullPointerException => () }

    if (!e.isInstanceOf[ScheduledExecutorService]) return ()
    val ses = e.asInstanceOf[ScheduledExecutorService]
    try {
      ses.schedule(nullRunnable, randomTimeout(), randomTimeUnit())
      shouldThrow()
    } catch { case sucess: NullPointerException => () }
    try {
      ses.schedule(nullCallable, randomTimeout(), randomTimeUnit())
      shouldThrow()
    } catch { case sucess: NullPointerException => () }
    try {
      ses.scheduleAtFixedRate(
        nullRunnable,
        randomTimeout(),
        LONG_DELAY_MS,
        MILLISECONDS
      )
      shouldThrow()
    } catch { case sucess: NullPointerException => () }
    try {
      ses.scheduleWithFixedDelay(
        nullRunnable,
        randomTimeout(),
        LONG_DELAY_MS,
        MILLISECONDS
      )
      shouldThrow()
    } catch { case sucess: NullPointerException => () }
  }

  def setRejectedExecutionHandler(
      p: ThreadPoolExecutor,
      handler: RejectedExecutionHandler
  ): Unit = {
    p.setRejectedExecutionHandler(handler)
    assertSame(handler, p.getRejectedExecutionHandler())
  }

  def assertTaskSubmissionsAreRejected(p: ThreadPoolExecutor): Unit = {
    val savedHandler = p.getRejectedExecutionHandler()
    val savedTaskCount = p.getTaskCount()
    val savedCompletedTaskCount = p.getCompletedTaskCount()
    val savedQueueSize = p.getQueue().size()
    val stock = true // (p.getClass().getClassLoader() == null)

    val r: Runnable = () => {}
    val c: Callable[Boolean] = () => java.lang.Boolean.TRUE

    class Recorder extends RejectedExecutionHandler {
      @volatile var r: Runnable = _
      @volatile var p: ThreadPoolExecutor = _
      def reset(): Unit = { r = null; p = null }
      def rejectedExecution(r: Runnable, p: ThreadPoolExecutor): Unit = {
        assertNull(this.r)
        assertNull(this.p)
        this.r = r
        this.p = p
      }
    }

    // check custom handler is invoked exactly once per task
    val recorder = new Recorder()
    setRejectedExecutionHandler(p, recorder)
    (2 to 0 by -1).foreach { i =>
      recorder.reset()
      p.execute(r)
      if (stock && p.getClass() == classOf[ThreadPoolExecutor])
        assertSame(r, recorder.r)
      assertSame(p, recorder.p)

      recorder.reset()
      assertFalse(p.submit(r).isDone())
      if (stock) assertTrue(!(recorder.r.asInstanceOf[FutureTask[_]]).isDone())
      assertSame(p, recorder.p)

      recorder.reset()
      assertFalse(p.submit(r, java.lang.Boolean.TRUE).isDone())
      if (stock) assertTrue(!(recorder.r.asInstanceOf[FutureTask[_]]).isDone())
      assertSame(p, recorder.p)

      recorder.reset()
      assertFalse(p.submit(c).isDone())
      if (stock) assertTrue(!(recorder.r.asInstanceOf[FutureTask[_]]).isDone())
      assertSame(p, recorder.p)

      p match {
        case s: ScheduledExecutorService =>
          var future: ScheduledFuture[_] = null

          recorder.reset()
          future = s.schedule(r, randomTimeout(), randomTimeUnit())
          assertFalse(future.isDone())
          if (stock)
            assertTrue(!(recorder.r.asInstanceOf[FutureTask[_]]).isDone())
          assertSame(p, recorder.p)

          recorder.reset()
          future = s.schedule(c, randomTimeout(), randomTimeUnit())
          assertFalse(future.isDone())
          if (stock)
            assertTrue(!(recorder.r.asInstanceOf[FutureTask[_]]).isDone())
          assertSame(p, recorder.p)

          recorder.reset()
          future = s.scheduleAtFixedRate(
            r,
            randomTimeout(),
            LONG_DELAY_MS,
            MILLISECONDS
          )
          assertFalse(future.isDone())
          if (stock)
            assertTrue(!(recorder.r.asInstanceOf[FutureTask[_]]).isDone())
          assertSame(p, recorder.p)

          recorder.reset()
          future = s.scheduleWithFixedDelay(
            r,
            randomTimeout(),
            LONG_DELAY_MS,
            MILLISECONDS
          )
          assertFalse(future.isDone())
          if (stock)
            assertTrue(!(recorder.r.asInstanceOf[FutureTask[_]]).isDone())
          assertSame(p, recorder.p)

        case _ => ()
      }
    }

    // Checking our custom handler above should be sufficient, but
    // we add some integration tests of standard handlers.
    val thread = new AtomicReference[Thread]()
    val setThread: Runnable = () => thread.set(Thread.currentThread())

    setRejectedExecutionHandler(p, new ThreadPoolExecutor.AbortPolicy())
    try {
      p.execute(setThread)
      shouldThrow()
    } catch { case _: RejectedExecutionException => () }
    assertNull(thread.get())

    setRejectedExecutionHandler(p, new ThreadPoolExecutor.DiscardPolicy())
    p.execute(setThread)
    assertNull(thread.get())

    setRejectedExecutionHandler(p, new ThreadPoolExecutor.CallerRunsPolicy())
    p.execute(setThread)
    if (p.isShutdown())
      assertNull(thread.get())
    else
      assertSame(Thread.currentThread(), thread.get())

    setRejectedExecutionHandler(p, savedHandler)

    // check that pool was not perturbed by handlers
    assertEquals(savedTaskCount, p.getTaskCount())
    assertEquals(savedCompletedTaskCount, p.getCompletedTaskCount())
    assertEquals(savedQueueSize, p.getQueue().size())
  }

//     void assertCollectionsEquals(Collection<?> x, Collection<?> y) {
//         assertEquals(x, y)
//         assertEquals(y, x)
//         assertEquals(x.isEmpty(), y.isEmpty())
//         assertEquals(x.size(), y.size())
//         if (x instanceof List) {
//             assertEquals(x.toString(), y.toString())
//         }
//         if (x instanceof List || x instanceof Set) {
//             assertEquals(x.hashCode(), y.hashCode())
//         }
//         if (x instanceof List || x instanceof Deque) {
//             assertTrue(Arrays.equals(x.toArray(), y.toArray()))
//             assertTrue(Arrays.equals(x.toArray(new Object[0]),
//                                      y.toArray(new Object[0])))
//         }
//     }

//     /**
//      * A weaker form of assertCollectionsEquals which does not insist
//      * that the two collections satisfy Object#equals(Object), since
//      * they may use identity semantics as Deques do.
//      */
//     void assertCollectionsEquivalent(Collection<?> x, Collection<?> y) {
//         if (x instanceof List || x instanceof Set)
//             assertCollectionsEquals(x, y)
//         else {
//             assertEquals(x.isEmpty(), y.isEmpty())
//             assertEquals(x.size(), y.size())
//             assertEquals(new HashSet(x), new HashSet(y))
//             if (x instanceof Deque) {
//                 assertTrue(Arrays.equals(x.toArray(), y.toArray()))
//                 assertTrue(Arrays.equals(x.toArray(new Object[0]),
//                                          y.toArray(new Object[0])))
//             }
//         }
//     }
}

object JSR166Test {
//     protected static final boolean expensiveTests =
//         Boolean.getBoolean("jsr166.expensiveTests")

  /** If true, also run tests that are not part of the official tck because they
   *  test unspecified implementation details.
   */
  final val testImplementationDetails =
    java.lang.Boolean.getBoolean("jsr166.testImplementationDetails")

//     /**
//      * If true, report on stdout all "slow" tests, that is, ones that
//      * take more than profileThreshold milliseconds to execute.
//      */
//     private static final boolean profileTests =
//         Boolean.getBoolean("jsr166.profileTests")

//     /**
//      * The number of milliseconds that tests are permitted for
//      * execution without being reported, when profileTests is set.
//      */
//     private static final long profileThreshold =
//         Long.getLong("jsr166.profileThreshold", 100)

//     /**
//      * The number of repetitions per test (for tickling rare bugs).
//      */
//     private static final int runsPerTest =
//         Integer.getInteger("jsr166.runsPerTest", 1)

//     /**
//      * The number of repetitions of the test suite (for finding leaks?).
//      */
//     private static final int suiteRuns =
//         Integer.getInteger("jsr166.suiteRuns", 1)

//     /**
//      * Returns the value of the system property, or NaN if not defined.
//      */
//     private static float systemPropertyValue(String name) {
//         String floatString = System.getProperty(name)
//         if (floatString == null)
//             return Float.NaN
//         try {
//             return Float.parseFloat(floatString)
//         } catch (NumberFormatException ex) {
//             throw new IllegalArgumentException(
//                 String.format("Bad float value in system property %s=%s",
//                               name, floatString))
//         }
//     }

//     private static final ThreadMXBean THREAD_MXBEAN
//         = ManagementFactory.getThreadMXBean()

//     /**
//      * The scaling factor to apply to standard delays used in tests.
//      * May be initialized from any of:
//      * - the "jsr166.delay.factor" system property
//      * - the "test.timeout.factor" system property (as used by jtreg)
//      *   See: http://openjdk.java.net/jtreg/tag-spec.html
//      * - hard-coded fuzz factor when using a known slowpoke VM
//      */
  private lazy val delayFactor = {
//         float x
//         if (!Float.isNaN(x = systemPropertyValue("jsr166.delay.factor")))
//             return x
//         if (!Float.isNaN(x = systemPropertyValue("test.timeout.factor")))
//             return x
//         String prop = System.getProperty("java.vm.version")
//         if (prop != null && prop.matches(".*debug.*"))
//             return 4.0f // How much slower is fastdebug than product?!
    1.0f
  }

//     /**
//          * A filter for tests to run, matching strings of the form
//          * methodName(className), e.g. "testInvokeAll5(ForkJoinPoolTest)"
//          * Usefully combined with jsr166.runsPerTest.
//          */
//         private static final Pattern methodFilter = methodFilter()

//         private static Pattern methodFilter() {
//             String regex = System.getProperty("jsr166.methodFilter")
//             return (regex == null) ? null : Pattern.compile(regex)
//         }

//         // Instrumentation to debug very rare, but very annoying hung test runs.
//         static volatile TestCase currentTestCase
//         // static volatile int currentRun = 0
//         static {
//             Runnable wedgedTestDetector = new Runnable() { public void run() {
//                 // Avoid spurious reports with enormous runsPerTest.
//                 // A single test case run should never take more than 1 second.
//                 // But let's cap it at the high end too ...
//                 final int timeoutMinutesMin = Math.max(runsPerTest / 60, 1)
//                     * Math.max((int) delayFactor, 1)
//                 final int timeoutMinutes = Math.min(15, timeoutMinutesMin)
//                 for (TestCase lastTestCase = currentTestCase) {
//                     try { MINUTES.sleep(timeoutMinutes) }
//                     catch (InterruptedException unexpected) { break }
//                     if (lastTestCase == currentTestCase) {
//                         System.err.printf(
//                             "Looks like we're stuck running test: %s%n",
//                             lastTestCase)
//     //                     System.err.printf(
//     //                         "Looks like we're stuck running test: %s (%d/%d)%n",
//     //                         lastTestCase, currentRun, runsPerTest)
//     //                     System.err.println("availableProcessors=" +
//     //                         Runtime.getRuntime().availableProcessors())
//     //                     System.err.printf("cpu model = %s%n", cpuModel())
//                         dumpTestThreads()
//                         // one stack dump is probably enough more would be spam
//                         break
//                     }
//                     lastTestCase = currentTestCase
//                 }}}
//             Thread thread = new Thread(wedgedTestDetector, "WedgedTestDetector")
//             thread.setDaemon(true)
//             thread.start()
//         }

//     //     public static String cpuModel() {
//     //         try {
//     //             java.util.regex.Matcher matcher
//     //               = Pattern.compile("model name\\s*: (.*)")
//     //                 .matcher(new String(
//     //                     java.nio.file.Files.readAllBytes(
//     //                         java.nio.file.Paths.get("/proc/cpuinfo")), "UTF-8"))
//     //             matcher.find()
//     //             return matcher.group(1)
//     //         } catch (Exception ex) { return null }
//     //     }

//     /**
//       * Runs all JSR166 unit tests using junit.textui.TestRunner.
//       */
//      public static void main(String[] args) {
//          main(suite(), args)
//      }

//      static class PithyResultPrinter extends junit.textui.ResultPrinter {
//          PithyResultPrinter(java.io.PrintStream writer) { super(writer) }
//          long runTime
//          public void startTest(Test test) {}
//          protected void printHeader(long runTime) {
//              this.runTime = runTime // defer printing for later
//          }
//          protected void printFooter(TestResult result) {
//              if (result.wasSuccessful()) {
//                  getWriter().println("OK (" + result.runCount() + " tests)"
//                      + "  Time: " + elapsedTimeAsString(runTime))
//              } else {
//                  getWriter().println("Time: " + elapsedTimeAsString(runTime))
//                  super.printFooter(result)
//              }
//          }
//      }

//      /**
//       * Returns a TestRunner that doesn't bother with unnecessary
//       * fluff, like printing a "." for each test case.
//       */
//      static junit.textui.TestRunner newPithyTestRunner() {
//          junit.textui.TestRunner runner = new junit.textui.TestRunner()
//          runner.setPrinter(new PithyResultPrinter(System.out))
//          return runner
//      }

//      /**
//       * Runs all unit tests in the given test suite.
//       * Actual behavior influenced by jsr166.* system properties.
//       */
//      static void main(Test suite, String[] args) {
//          if (useSecurityManager) {
//              System.err.println("Setting a permissive security manager")
//              Policy.setPolicy(permissivePolicy())
//              System.setSecurityManager(new SecurityManager())
//          }
//          for (int i = 0 i < suiteRuns i++) {
//              TestResult result = newPithyTestRunner().doRun(suite)
//              if (!result.wasSuccessful())
//                  System.exit(1)
//              System.gc()
//              System.runFinalization()
//          }
//      }

//      public static TestSuite newTestSuite(Object... suiteOrClasses) {
//          TestSuite suite = new TestSuite()
//          for (Object suiteOrClass : suiteOrClasses) {
//              if (suiteOrClass instanceof TestSuite)
//                  suite.addTest((TestSuite) suiteOrClass)
//              else if (suiteOrClass instanceof Class)
//                  suite.addTest(new TestSuite((Class<?>) suiteOrClass))
//              else
//                  throw new ClassCastException("not a test suite or class")
//          }
//          return suite
//      }

//      public static void addNamedTestClasses(TestSuite suite,
//                                             String... testClassNames) {
//          for (String testClassName : testClassNames) {
//              try {
//                  Class<?> testClass = Class.forName(testClassName)
//                  Method m = testClass.getDeclaredMethod("suite")
//                  suite.addTest(newTestSuite((Test)m.invoke(null)))
//              } catch (ReflectiveOperationException e) {
//                  throw new AssertionError("Missing test class", e)
//              }
//          }
//      }

//      public static final double JAVA_CLASS_VERSION
//      public static final String JAVA_SPECIFICATION_VERSION
//      static {
//          try {
//              JAVA_CLASS_VERSION = java.security.AccessController.doPrivileged(
//                  new java.security.PrivilegedAction<Double>() {
//                  public Double run() {
//                      return Double.valueOf(System.getProperty("java.class.version"))}})
//              JAVA_SPECIFICATION_VERSION = java.security.AccessController.doPrivileged(
//                  new java.security.PrivilegedAction<String>() {
//                  public String run() {
//                      return System.getProperty("java.specification.version")}})
//          } catch (Throwable t) {
//              throw new Error(t)
//          }
//      }

//      public static boolean atLeastJava6()  { return JAVA_CLASS_VERSION >= 50.0 }
//      public static boolean atLeastJava7()  { return JAVA_CLASS_VERSION >= 51.0 }
//      public static boolean atLeastJava8()  { return JAVA_CLASS_VERSION >= 52.0 }
//      public static boolean atLeastJava9()  { return JAVA_CLASS_VERSION >= 53.0 }
//      public static boolean atLeastJava10() { return JAVA_CLASS_VERSION >= 54.0 }
//      public static boolean atLeastJava11() { return JAVA_CLASS_VERSION >= 55.0 }
//      public static boolean atLeastJava12() { return JAVA_CLASS_VERSION >= 56.0 }
//      public static boolean atLeastJava13() { return JAVA_CLASS_VERSION >= 57.0 }
//      public static boolean atLeastJava14() { return JAVA_CLASS_VERSION >= 58.0 }
//      public static boolean atLeastJava15() { return JAVA_CLASS_VERSION >= 59.0 }
//      public static boolean atLeastJava16() { return JAVA_CLASS_VERSION >= 60.0 }
//      public static boolean atLeastJava17() { return JAVA_CLASS_VERSION >= 61.0 }

//      /**
//       * Collects all JSR166 unit tests as one suite.
//       */
//      public static Test suite() {
//          // Java7+ test classes
//          TestSuite suite = newTestSuite(
//              ForkJoinPoolTest.suite(),
//              ForkJoinTaskTest.suite(),
//              RecursiveActionTest.suite(),
//              RecursiveTaskTest.suite(),
//              LinkedTransferQueueTest.suite(),
//              PhaserTest.suite(),
//              ThreadLocalRandomTest.suite(),
//              AbstractExecutorServiceTest.suite(),
//              AbstractQueueTest.suite(),
//              AbstractQueuedSynchronizerTest.suite(),
//              AbstractQueuedLongSynchronizerTest.suite(),
//              ArrayBlockingQueueTest.suite(),
//              ArrayDequeTest.suite(),
//              ArrayListTest.suite(),
//              AtomicBooleanTest.suite(),
//              AtomicIntegerArrayTest.suite(),
//              AtomicIntegerFieldUpdaterTest.suite(),
//              AtomicIntegerTest.suite(),
//              AtomicLongArrayTest.suite(),
//              AtomicLongFieldUpdaterTest.suite(),
//              AtomicLongTest.suite(),
//              AtomicMarkableReferenceTest.suite(),
//              AtomicReferenceArrayTest.suite(),
//              AtomicReferenceFieldUpdaterTest.suite(),
//              AtomicReferenceTest.suite(),
//              AtomicStampedReferenceTest.suite(),
//              ConcurrentHashMapTest.suite(),
//              ConcurrentLinkedDequeTest.suite(),
//              ConcurrentLinkedQueueTest.suite(),
//              ConcurrentSkipListMapTest.suite(),
//              ConcurrentSkipListSubMapTest.suite(),
//              ConcurrentSkipListSetTest.suite(),
//              ConcurrentSkipListSubSetTest.suite(),
//              CopyOnWriteArrayListTest.suite(),
//              CopyOnWriteArraySetTest.suite(),
//              CountDownLatchTest.suite(),
//              CountedCompleterTest.suite(),
//              CyclicBarrierTest.suite(),
//              DelayQueueTest.suite(),
//              EntryTest.suite(),
//              ExchangerTest.suite(),
//              ExecutorsTest.suite(),
//              ExecutorCompletionServiceTest.suite(),
//              FutureTaskTest.suite(),
//              HashtableTest.suite(),
//              LinkedBlockingDequeTest.suite(),
//              LinkedBlockingQueueTest.suite(),
//              LinkedListTest.suite(),
//              LockSupportTest.suite(),
//              PriorityBlockingQueueTest.suite(),
//              PriorityQueueTest.suite(),
//              ReentrantLockTest.suite(),
//              ReentrantReadWriteLockTest.suite(),
//              ScheduledExecutorTest.suite(),
//              ScheduledExecutorSubclassTest.suite(),
//              SemaphoreTest.suite(),
//              SynchronousQueueTest.suite(),
//              SystemTest.suite(),
//              ThreadLocalTest.suite(),
//              ThreadPoolExecutorTest.suite(),
//              ThreadPoolExecutorSubclassTest.suite(),
//              ThreadTest.suite(),
//              TimeUnitTest.suite(),
//              TreeMapTest.suite(),
//              TreeSetTest.suite(),
//              TreeSubMapTest.suite(),
//              TreeSubSetTest.suite(),
//              VectorTest.suite())

//          // Java8+ test classes
//          if (atLeastJava8()) {
//              String[] java8TestClassNames = {
//                  "ArrayDeque8Test",
//                  "Atomic8Test",
//                  "CompletableFutureTest",
//                  "ConcurrentHashMap8Test",
//                  "CountedCompleter8Test",
//                  "DoubleAccumulatorTest",
//                  "DoubleAdderTest",
//                  "ForkJoinPool8Test",
//                  "ForkJoinTask8Test",
//                  "HashMapTest",
//                  "LinkedBlockingDeque8Test",
//                  "LinkedBlockingQueue8Test",
//                  "LinkedHashMapTest",
//                  "LongAccumulatorTest",
//                  "LongAdderTest",
//                  "SplittableRandomTest",
//                  "StampedLockTest",
//                  "SubmissionPublisherTest",
//                  "ThreadLocalRandom8Test",
//                  "TimeUnit8Test",
//              }
//              addNamedTestClasses(suite, java8TestClassNames)
//          }

//          // Java9+ test classes
//          if (atLeastJava9()) {
//              String[] java9TestClassNames = {
//                  "AtomicBoolean9Test",
//                  "AtomicInteger9Test",
//                  "AtomicIntegerArray9Test",
//                  "AtomicLong9Test",
//                  "AtomicLongArray9Test",
//                  "AtomicReference9Test",
//                  "AtomicReferenceArray9Test",
//                  "ExecutorCompletionService9Test",
//                  "ForkJoinPool9Test",
//              }
//              addNamedTestClasses(suite, java9TestClassNames)
//          }

//          return suite
//      }

//      /** Returns list of junit-style test method names in given class. */
//      public static ArrayList<String> testMethodNames(Class<?> testClass) {
//          Method[] methods = testClass.getDeclaredMethods()
//          ArrayList<String> names = new ArrayList<>(methods.length)
//          for (Method method : methods) {
//              if (method.getName().startsWith("test")
//                  && Modifier.isPublic(method.getModifiers())
//                  // method.getParameterCount() requires jdk8+
//                  && method.getParameterTypes().length == 0) {
//                  names.add(method.getName())
//              }
//          }
//          return names
//      }

//      /**
//       * Returns junit-style testSuite for the given test class, but
//       * parameterized by passing extra data to each test.
//       */
//      public static <ExtraData> Test parameterizedTestSuite
//          (Class<? extends JSR166TestCase> testClass,
//           Class<ExtraData> dataClass,
//           ExtraData data) {
//          try {
//              TestSuite suite = new TestSuite()
//              Constructor c =
//                  testClass.getDeclaredConstructor(dataClass, String.class)
//              for (String methodName : testMethodNames(testClass))
//                  suite.addTest((Test) c.newInstance(data, methodName))
//              return suite
//          } catch (ReflectiveOperationException e) {
//              throw new AssertionError(e)
//          }
//      }

//      /**
//       * Returns junit-style testSuite for the jdk8 extension of the
//       * given test class, but parameterized by passing extra data to
//       * each test.  Uses reflection to allow compilation in jdk7.
//       */
//      public static <ExtraData> Test jdk8ParameterizedTestSuite
//          (Class<? extends JSR166TestCase> testClass,
//           Class<ExtraData> dataClass,
//           ExtraData data) {
//          if (atLeastJava8()) {
//              String name = testClass.getName()
//              String name8 = name.replaceAll("Test$", "8Test")
//              if (name.equals(name8)) throw new AssertionError(name)
//              try {
//                  return (Test)
//                      Class.forName(name8)
//                      .getMethod("testSuite", dataClass)
//                      .invoke(null, data)
//              } catch (ReflectiveOperationException e) {
//                  throw new AssertionError(e)
//              }
//          } else {
//              return new TestSuite()
//          }
//      }

  // Delays for timing-dependent tests, in milliseconds.
  final val SHORT_DELAY_MS = (50 * delayFactor).toLong
  final val SMALL_DELAY_MS = SHORT_DELAY_MS * 5
  final val MEDIUM_DELAY_MS = SHORT_DELAY_MS * 10
  final val LONG_DELAY_MS = SHORT_DELAY_MS * 200

  /** A delay significantly longer than LONG_DELAY_MS. Use this in a thread that
   *  is waited for via awaitTermination(Thread).
   */
  final val LONGER_DELAY_MS = LONG_DELAY_MS * 2

  // SN note: We define this variables as functions for source-compatibility with JSR 166 tests for easier porting of tests
  final val (randomTimeout, randomExpiredTimeout, randomTimeUnit) = {
    val rnd = ThreadLocalRandom.current()
    val timeouts =
      Array(java.lang.Long.MIN_VALUE, -1, 0, 1, java.lang.Long.MAX_VALUE)
    val timeUnits = TimeUnit.values()

    val timeout = timeouts(rnd.nextInt(timeouts.length))
    val expired = timeouts(rnd.nextInt(3))
    val timeUnit = timeUnits(rnd.nextInt(timeUnits.length))
    (() => timeout, () => expired, () => timeUnit)
  }

  /** Returns a random boolean a "coin flip".
   */
  def randomBoolean(): Boolean = ThreadLocalRandom.current().nextBoolean()

  private final lazy val TIMEOUT_DELAY_MS =
    (12.0 * Math.cbrt(delayFactor)).toLong

  /** Returns a timeout in milliseconds to be used in tests that verify that
   *  operations block or time out. We want this to be longer than the OS
   *  scheduling quantum, but not too long, so don't scale linearly with
   *  delayFactor we use "crazy" cube root instead.
   */
  def timeoutMillis(): Long = TIMEOUT_DELAY_MS

  /** Delays, via Thread.sleep, for the given millisecond delay, but if the
   *  sleep is shorter than specified, may re-sleep or yield until time elapses.
   *  Ensures that the given time, as measured by System.nanoTime(), has
   *  elapsed.
   */
  def delay(ms: Long) = {
    var millis = ms
    var nanos = millis * (1000 * 1000)
    var wakeupTime = System.nanoTime() + nanos
    while ({
      if (millis > 0L) Thread.sleep(millis)
      else Thread.`yield`() // too short to sleep
      nanos = wakeupTime - System.nanoTime()
      millis = nanos / (1000 * 1000)
      nanos >= 0L
    }) ()
  }

//     /** Returns true if thread info might be useful in a thread dump. */
//        static boolean threadOfInterest(ThreadInfo info) {
//            final String name = info.getThreadName()
//            String lockName
//            if (name == null)
//                return true
//            if (name.equals("Signal Dispatcher")
//                || name.equals("WedgedTestDetector"))
//                return false
//            if (name.equals("Reference Handler")) {
//                // Reference Handler stacktrace changed in JDK-8156500
//                StackTraceElement[] stackTrace String methodName
//                if ((stackTrace = info.getStackTrace()) != null
//                    && stackTrace.length > 0
//                    && (methodName = stackTrace[0].getMethodName()) != null
//                    && methodName.equals("waitForReferencePendingList"))
//                    return false
//                // jdk8 Reference Handler stacktrace
//                if ((lockName = info.getLockName()) != null
//                    && lockName.startsWith("java.lang.ref"))
//                    return false
//            }
//            if ((name.equals("Finalizer") || name.equals("Common-Cleaner"))
//                && (lockName = info.getLockName()) != null
//                && lockName.startsWith("java.lang.ref"))
//                return false
//            if (name.startsWith("ForkJoinPool.commonPool-worker")
//                && (lockName = info.getLockName()) != null
//                && lockName.startsWith("java.util.concurrent.ForkJoinPool"))
//                return false
//            return true
//        }

//        /**
//         * A debugging tool to print stack traces of most threads, as jstack does.
//         * Uninteresting threads are filtered out.
//         */
//        static void dumpTestThreads() {
//            SecurityManager sm = System.getSecurityManager()
//            if (sm != null) {
//                try {
//                    System.setSecurityManager(null)
//                } catch (SecurityException giveUp) {
//                    return
//                }
//            }

//            System.err.println("------ stacktrace dump start ------")
//            for (ThreadInfo info : THREAD_MXBEAN.dumpAllThreads(true, true))
//                if (threadOfInterest(info))
//                    System.err.print(info)
//            System.err.println("------ stacktrace dump end ------")

//            if (sm != null) System.setSecurityManager(sm)
//        }

// /**
//    * A security policy where new permissions can be dynamically added
//    * or all cleared.
//    */
//   public static class AdjustablePolicy extends java.security.Policy {
//       Permissions perms = new Permissions()
//       AdjustablePolicy(Permission... permissions) {
//           for (Permission permission : permissions)
//               perms.add(permission)
//       }
//       void addPermission(Permission perm) { perms.add(perm) }
//       void clearPermissions() { perms = new Permissions() }
//       public PermissionCollection getPermissions(CodeSource cs) {
//           return perms
//       }
//       public PermissionCollection getPermissions(ProtectionDomain pd) {
//           return perms
//       }
//       public boolean implies(ProtectionDomain pd, Permission p) {
//           return perms.implies(p)
//       }
//       public void refresh() {}
//       public String toString() {
//           List<Permission> ps = new ArrayList<>()
//           for (Enumeration<Permission> e = perms.elements() e.hasMoreElements())
//               ps.add(e.nextElement())
//           return "AdjustablePolicy with permissions " + ps
//       }
//   }

//   /**
//    * Returns a policy containing all the permissions we ever need.
//    */
//   public static Policy permissivePolicy() {
//       return new AdjustablePolicy
//           // Permissions j.u.c. needs directly
//           (new RuntimePermission("modifyThread"),
//            new RuntimePermission("getClassLoader"),
//            new RuntimePermission("setContextClassLoader"),
//            // Permissions needed to change permissions!
//            new SecurityPermission("getPolicy"),
//            new SecurityPermission("setPolicy"),
//            new RuntimePermission("setSecurityManager"),
//            // Permissions needed by the junit test harness
//            new RuntimePermission("accessDeclaredMembers"),
//            new PropertyPermission("*", "read"),
//            new java.io.FilePermission("<<ALL FILES>>", "read"))
//   }

//   /**
//    * Sleeps until the given time has elapsed.
//    * Throws AssertionError if interrupted.
//    */
//   static void sleep(long millis) {
//       try {
//           delay(millis)
//       } catch (InterruptedException fail) {
//           throw new AssertionError("Unexpected InterruptedException", fail)
//       }
//   }

  /** The maximum number of consecutive spurious wakeups we should tolerate
   *  (from APIs like LockSupport.park) before failing a test.
   */
  final val MAX_SPURIOUS_WAKEUPS = 10

  /** The number of elements to place in collections, arrays, etc.
   */
  final val SIZE = 20

  // Some convenient Integer constants
  final val zero = new Integer(0)
  final val one = new Integer(1)
  final val two = new Integer(2)
  final val three = new Integer(3)
  final val four = new Integer(4)
  final val five = new Integer(5)
  final val six = new Integer(6)
  final val seven = new Integer(7)
  final val eight = new Integer(8)
  final val nine = new Integer(9)
  final val m1 = new Integer(-1)
  final val m2 = new Integer(-2)
  final val m3 = new Integer(-3)
  final val m4 = new Integer(-4)
  final val m5 = new Integer(-5)
  final val m6 = new Integer(-6)
  final val m10 = new Integer(-10)

  /** Returns the number of milliseconds since time given by startNanoTime,
   *  which must have been previously returned from a call to {@link
   *  System#nanoTime()}.
   */
  def millisElapsedSince(startNanoTime: Long): Long = {
    NANOSECONDS.toMillis(System.nanoTime() - startNanoTime)
  }

  /** Returns maximum number of tasks that can be submitted to given pool (with
   *  bounded queue) before saturation (when submission throws
   *  RejectedExecutionException).
   */
  def saturatedSize(pool: ThreadPoolExecutor): Int = {
    val q = pool.getQueue()
    pool.getMaximumPoolSize() + q.size() + q.remainingCapacity()
  }
}
