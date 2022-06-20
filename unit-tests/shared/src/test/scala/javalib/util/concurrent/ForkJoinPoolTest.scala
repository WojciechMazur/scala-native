/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package javalib.util.concurrent

import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.NANOSECONDS

// import java.util.ArrayList
// import java.util.Collection
// import java.util.Collections
// import java.util.List
import java.util.concurrent._

import org.junit.Test
import org.junit.Assert._
import scala.scalanative.junit.utils.AssertThrows.assertThrows
import scala.util.Using

object ForkJoinPoolTest {
  // static class MyError extends Error {}

  //  // to test handlers
  //  static class FailingFJWSubclass extends ForkJoinWorkerThread {
  //      public FailingFJWSubclass(ForkJoinPool p) { super(p)  }
  //      protected void onStart() { super.onStart() throw new MyError() }
  //  }

  //  static class FailingThreadFactory
  //          implements ForkJoinPool.ForkJoinWorkerThreadFactory {
  //      final AtomicInteger calls = new AtomicInteger(0)
  //      public ForkJoinWorkerThread newThread(ForkJoinPool p) {
  //          if (calls.incrementAndGet() > 1) return null
  //          return new FailingFJWSubclass(p)
  //      }
  //  }

  //  static class SubFJP extends ForkJoinPool { // to expose protected
  //      SubFJP() { super(1) }
  //      public int drainTasksTo(Collection<? super ForkJoinTask<?>> c) {
  //          return super.drainTasksTo(c)
  //      }
  //      public ForkJoinTask<?> pollSubmission() {
  //          return super.pollSubmission()
  //      }
  //  }

  //  static class ManagedLocker implements ForkJoinPool.ManagedBlocker {
  //      final ReentrantLock lock
  //      boolean hasLock = false
  //      ManagedLocker(ReentrantLock lock) { this.lock = lock }
  //      public boolean block() {
  //          if (!hasLock)
  //              lock.lock()
  //          return true
  //      }
  //      public boolean isReleasable() {
  //          return hasLock || (hasLock = lock.tryLock())
  //      }
  //  }

  //  // A simple recursive task for testing
  //  static final class FibTask extends RecursiveTask<Integer> {
  //      final int number
  //      FibTask(int n) { number = n }
  //      protected Integer compute() {
  //          int n = number
  //          if (n <= 1)
  //              return n
  //          FibTask f1 = new FibTask(n - 1)
  //          f1.fork()
  //          return new FibTask(n - 2).compute() + f1.join()
  //      }
  //  }

  //  // A failing task for testing
  //  static final class FailingTask extends ForkJoinTask<Void> {
  //      public final Void getRawResult() { return null }
  //      protected final void setRawResult(Void mustBeNull) { }
  //      protected final boolean exec() { throw new Error() }
  //      FailingTask() {}
  //  }

  //  // Fib needlessly using locking to test ManagedBlockers
  //  static final class LockingFibTask extends RecursiveTask<Integer> {
  //      final int number
  //      final ManagedLocker locker
  //      final ReentrantLock lock
  //      LockingFibTask(int n, ManagedLocker locker, ReentrantLock lock) {
  //          number = n
  //          this.locker = locker
  //          this.lock = lock
  //      }
  //      protected Integer compute() {
  //          int n
  //          LockingFibTask f1 = null
  //          LockingFibTask f2 = null
  //          locker.block()
  //          n = number
  //          if (n > 1) {
  //              f1 = new LockingFibTask(n - 1, locker, lock)
  //              f2 = new LockingFibTask(n - 2, locker, lock)
  //          }
  //          lock.unlock()
  //          if (n <= 1)
  //              return n
  //          else {
  //              f1.fork()
  //              return f2.compute() + f1.join()
  //          }
  //      }
  //  }
}

class ForkJoinPoolTest extends JSR166Test {
  import JSR166Test._
  /*
   * Testing coverage notes:
   *
   * 1. shutdown and related methods are tested via super.joinPool.
   *
   * 2. newTaskFor and adapters are tested in submit/invoke tests
   *
   * 3. We cannot portably test monitoring methods such as
   * getStealCount() since they rely ultimately on random task
   * stealing that may cause tasks not to be stolen/propagated
   * across threads, especially on uniprocessors.
   *
   * 4. There are no independently testable ForkJoinWorkerThread
   * methods, but they are covered here and in task tests.
   */

  // Some classes to test extension and factory methods

  // /** Successfully constructed pool reports default factory, parallelism and
  //  *  async mode policies, no active threads or tasks, and quiescent running
  //  *  state.
  //  */
  // @Test def testDefaultInitialState(): Unit = {
  //   usingPoolCleaner(new ForkJoinPool(1)) { p =>
  //     assertSame(
  //       ForkJoinPool.defaultForkJoinWorkerThreadFactory,
  //       p.getFactory()
  //     )
  //     assertFalse(p.getAsyncMode())
  //     assertEquals(0, p.getActiveThreadCount())
  //     assertEquals(0, p.getStealCount())
  //     assertEquals(0, p.getQueuedTaskCount())
  //     assertEquals(0, p.getQueuedSubmissionCount())
  //     assertFalse(p.hasQueuedSubmissions())
  //     assertFalse(p.isShutdown())
  //     assertFalse(p.isTerminating())
  //     assertFalse(p.isTerminated())
  //     println("done 1")

  //   }
  // }

  // /** Constructor throws if size argument is less than zero
  //  */
  // @Test def testConstructor1(): Unit = assertThrows(
  //   classOf[IllegalArgumentException],
  //   new ForkJoinPool(-1)
  // )

  // /** Constructor throws if factory argument is null
  //  */
  // @Test def testConstructor2(): Unit = assertThrows(
  //   classOf[NullPointerException],
  //   new ForkJoinPool(1, null, null, false)
  // )

  // /** getParallelism returns size set in constructor
  //  */
  // @Test def testGetParallelism(): Unit = {
  //   usingPoolCleaner(new ForkJoinPool(1)) { p =>
  //     assertEquals(1, p.getParallelism())
  //   }
  // }

  /** getPoolSize returns number of started workers.
   */
  @Test def testGetPoolSize(): Unit = {
    val taskStarted = new CountDownLatch(1)
    val done = new CountDownLatch(1)
    val p = usingPoolCleaner(new ForkJoinPool(1)) { p =>
      assertEquals(0, p.getActiveThreadCount())
      val task: CheckedRunnable = () => {
        taskStarted.countDown()
        assertEquals(1, p.getPoolSize())
        assertEquals(1, p.getActiveThreadCount())
        await(done)
      }
      val future = p.submit(task)
      await(taskStarted)
      assertEquals(1, p.getPoolSize())
      assertEquals(1, p.getActiveThreadCount())
      done.countDown()
      println("done 3")
      p
    }
    assertEquals(0, p.getPoolSize())
    assertEquals(0, p.getActiveThreadCount())
    println("done 3b")
  }

  // /** awaitTermination on a non-shutdown pool times out
  //  */
  // @throws[InterruptedException]
  // @Test def testAwaitTermination_timesOut(): Unit = {
  //   usingPoolCleaner(new ForkJoinPool(1)) { p =>
  //     assertFalse(p.isTerminated())
  //     assertFalse(p.awaitTermination(java.lang.Long.MIN_VALUE, NANOSECONDS))
  //     assertFalse(p.awaitTermination(java.lang.Long.MIN_VALUE, MILLISECONDS))
  //     assertFalse(p.awaitTermination(-1L, NANOSECONDS))
  //     assertFalse(p.awaitTermination(-1L, MILLISECONDS))
  //     assertFalse(p.awaitTermination(randomExpiredTimeout(), randomTimeUnit()))
      
  //     locally {
  //       val timeoutNanos = 999999L
  //       val startTime = System.nanoTime()
  //       assertFalse(p.awaitTermination(timeoutNanos, NANOSECONDS))
  //       assertTrue(System.nanoTime() - startTime >= timeoutNanos)
  //       assertFalse(p.isTerminated())
  //     }
  //     locally {
  //       val startTime = System.nanoTime()
  //       val timeoutMillis = JSR166Test.timeoutMillis()
  //       assertFalse(p.awaitTermination(timeoutMillis, MILLISECONDS))
  //       assertTrue(millisElapsedSince(startTime) >= timeoutMillis)
  //     }
  //     assertFalse(p.isTerminated())
  //     p.shutdown()
  //     assertTrue(p.awaitTermination(LONG_DELAY_MS, MILLISECONDS))
  //     assertTrue(p.isTerminated())
  //     println("done 3")
  //   }
  // }

  // /**
  //  * setUncaughtExceptionHandler changes handler for uncaught exceptions.
  //  *
  //  * Additionally tests: Overriding ForkJoinWorkerThread.onStart
  //  * performs its defined action
  //  */
  // @throws[InterruptedException]
// @Test def testSetUncaughtExceptionHandler(): Unit = {
//   //     final CountDownLatch uehInvoked = new CountDownLatch(1)
//   //     final Thread.UncaughtExceptionHandler ueh =
//   //         new Thread.UncaughtExceptionHandler() {
//   //             public void uncaughtException(Thread t, Throwable e) {
//   //                 threadAssertTrue(e instanceof MyError)
//   //                 threadAssertTrue(t instanceof FailingFJWSubclass)
//   //                 uehInvoked.countDown()
//   //             }}
//   //     ForkJoinPool p = new ForkJoinPool(1, new FailingThreadFactory(),
//   //                                       ueh, false)
//   //     try (PoolCleaner cleaner = cleaner(p)) {
//   //         assertSame(ueh, p.getUncaughtExceptionHandler())
//   //         try {
//   //             p.execute(new FibTask(8))
//   //             await(uehInvoked)
//   //         } finally {
//   //             p.shutdownNow() // failure might have prevented processing task
//   //         }
//   //     }
//   // }

//   // /**
//   //  * After invoking a single task, isQuiescent eventually becomes
//   //  * true, at which time queues are empty, threads are not active,
//   //  * the task has completed successfully, and construction
//   //  * parameters continue to hold
//   //  */
//   // @throws[Exception]
// @Test def testIsQuiescent(): Unit = {
//   //     ForkJoinPool p = new ForkJoinPool(2)
//   //     try (PoolCleaner cleaner = cleaner(p)) {
//   //         assertTrue(p.isQuiescent())
//   //         long startTime = System.nanoTime()
//   //         FibTask f = new FibTask(20)
//   //         p.invoke(f)
//   //         assertSame(ForkJoinPool.defaultForkJoinWorkerThreadFactory,
//   //                    p.getFactory())
//   //         while (! p.isQuiescent()) {
//   //             if (millisElapsedSince(startTime) > LONG_DELAY_MS)
//   //                 throw new AssertionError("timed out")
//   //             assertFalse(p.getAsyncMode())
//   //             assertFalse(p.isShutdown())
//   //             assertFalse(p.isTerminating())
//   //             assertFalse(p.isTerminated())
//   //             Thread.yield()
//   //         }

//   //         assertTrue(p.isQuiescent())
//   //         assertFalse(p.getAsyncMode())
//   //         assertEquals(0, p.getQueuedTaskCount())
//   //         assertEquals(0, p.getQueuedSubmissionCount())
//   //         assertFalse(p.hasQueuedSubmissions())
//   //         while (p.getActiveThreadCount() != 0
//   //                && millisElapsedSince(startTime) < LONG_DELAY_MS)
//   //             Thread.yield()
//   //         assertFalse(p.isShutdown())
//   //         assertFalse(p.isTerminating())
//   //         assertFalse(p.isTerminated())
//   //         assertTrue(f.isDone())
//   //         assertEquals(6765, (int) f.get())
//   //         assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS)
//   //     }
//   // }

//   // /**
//   //  * Completed submit(ForkJoinTask) returns result
//   //  */
//   // @throws[Throwable]
// @Test def testSubmitForkJoinTask(): Unit = {
//   //     ForkJoinPool p = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(p)) {
//   //         ForkJoinTask<Integer> f = p.submit(new FibTask(8))
//   //         assertEquals(21, (int) f.get())
//   //     }
//   // }

//   // /**
//   //  * A task submitted after shutdown is rejected
//   //  */
//   // @Test def testSubmitAfterShutdown(): Unit = {
//   //     ForkJoinPool p = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(p)) {
//   //         p.shutdown()
//   //         assertTrue(p.isShutdown())
//   //         try {
//   //             ForkJoinTask<Integer> unused = p.submit(new FibTask(8))
//   //             shouldThrow()
//   //         } catch (RejectedExecutionException success) {}
//   //     }
//   // }

//   // /**
//   //  * Pool maintains parallelism when using ManagedBlocker
//   //  */
//   // @throws[Throwable]
// @Test def testBlockingForkJoinTask(): Unit = {
//   //     ForkJoinPool p = new ForkJoinPool(4)
//   //     try {
//   //         ReentrantLock lock = new ReentrantLock()
//   //         ManagedLocker locker = new ManagedLocker(lock)
//   //         ForkJoinTask<Integer> f = new LockingFibTask(20, locker, lock)
//   //         p.execute(f)
//   //         assertEquals(6765, (int) f.get())
//   //     } finally {
//   //         p.shutdownNow() // don't wait out shutdown
//   //     }
//   // }

//   // /**
//   //  * pollSubmission returns unexecuted submitted task, if present
//   //  */
//   // @Test def testPollSubmission(): Unit = {
//   //     final CountDownLatch done = new CountDownLatch(1)
//   //     SubFJP p = new SubFJP()
//   //     try (PoolCleaner cleaner = cleaner(p)) {
//   //         ForkJoinTask a = p.submit(awaiter(done))
//   //         ForkJoinTask b = p.submit(awaiter(done))
//   //         ForkJoinTask c = p.submit(awaiter(done))
//   //         ForkJoinTask r = p.pollSubmission()
//   //         assertTrue(r == a || r == b || r == c)
//   //         assertFalse(r.isDone())
//   //         done.countDown()
//   //     }
//   // }

//   // /**
//   //  * drainTasksTo transfers unexecuted submitted tasks, if present
//   //  */
//   // @Test def testDrainTasksTo(): Unit = {
//   //     final CountDownLatch done = new CountDownLatch(1)
//   //     SubFJP p = new SubFJP()
//   //     try (PoolCleaner cleaner = cleaner(p)) {
//   //         ForkJoinTask a = p.submit(awaiter(done))
//   //         ForkJoinTask b = p.submit(awaiter(done))
//   //         ForkJoinTask c = p.submit(awaiter(done))
//   //         ArrayList<ForkJoinTask> al = new ArrayList()
//   //         p.drainTasksTo(al)
//   //         assertTrue(al.size() > 0)
//   //         for (ForkJoinTask r : al) {
//   //             assertTrue(r == a || r == b || r == c)
//   //             assertFalse(r.isDone())
//   //         }
//   //         done.countDown()
//   //     }
//   // }

//   // // FJ Versions of AbstractExecutorService tests

//   // /**
//   //  * execute(runnable) runs it to completion
//   //  */
//   // @throws[Throwable]
// @Test def testExecuteRunnable(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         final AtomicBoolean done = new AtomicBoolean(false)
//   //         Future<?> future = e.submit(new CheckedRunnable() {
//   //             public void realRun() {
//   //                 done.set(true)
//   //             }})
//   //         assertNull(future.get())
//   //         assertNull(future.get(randomExpiredTimeout(), randomTimeUnit()))
//   //         assertTrue(done.get())
//   //         assertTrue(future.isDone())
//   //         assertFalse(future.isCancelled())
//   //     }
//   // }

//   // /**
//   //  * Completed submit(callable) returns result
//   //  */
//   // @throws[Throwable]
// @Test def testSubmitCallable(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         Future<String> future = e.submit(new StringTask())
//   //         assertSame(TEST_STRING, future.get())
//   //         assertTrue(future.isDone())
//   //         assertFalse(future.isCancelled())
//   //     }
//   // }

//   // /**
//   //  * Completed submit(runnable) returns successfully
//   //  */
//   // @throws[Throwable]
// @Test def testSubmitRunnable(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         Future<?> future = e.submit(new NoOpRunnable())
//   //         assertNull(future.get())
//   //         assertTrue(future.isDone())
//   //         assertFalse(future.isCancelled())
//   //     }
//   // }

//   // /**
//   //  * Completed submit(runnable, result) returns result
//   //  */
//   // @throws[Throwable]
// @Test def testSubmitRunnable2(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         Future<String> future = e.submit(new NoOpRunnable(), TEST_STRING)
//   //         assertSame(TEST_STRING, future.get())
//   //         assertTrue(future.isDone())
//   //         assertFalse(future.isCancelled())
//   //     }
//   // }

//   // /**
//   //  * A submitted privileged action runs to completion
//   //  */
//   // @throws[Exception]
// @Test def testSubmitPrivilegedAction(): Unit = {
//   //     final Callable callable = Executors.callable(new PrivilegedAction() {
//   //             public Object run() { return TEST_STRING }})
//   //     Runnable r = new CheckedRunnable() {
//   //     public void realRun() throws Exception {
//   //         ExecutorService e = new ForkJoinPool(1)
//   //         try (PoolCleaner cleaner = cleaner(e)) {
//   //             Future future = e.submit(callable)
//   //             assertSame(TEST_STRING, future.get())
//   //         }
//   //     }}

//   //     runWithPermissions(r, new RuntimePermission("modifyThread"))
//   // }

//   // /**
//   //  * A submitted privileged exception action runs to completion
//   //  */
//   // @throws[Exception]
// @Test def testSubmitPrivilegedExceptionAction(): Unit = {
//   //     final Callable callable =
//   //         Executors.callable(new PrivilegedExceptionAction() {
//   //             public Object run() { return TEST_STRING }})
//   //     Runnable r = new CheckedRunnable() {
//   //     public void realRun() throws Exception {
//   //         ExecutorService e = new ForkJoinPool(1)
//   //         try (PoolCleaner cleaner = cleaner(e)) {
//   //             Future future = e.submit(callable)
//   //             assertSame(TEST_STRING, future.get())
//   //         }
//   //     }}

//   //     runWithPermissions(r, new RuntimePermission("modifyThread"))
//   // }

//   // /**
//   //  * A submitted failed privileged exception action reports exception
//   //  */
//   // @throws[Exception]
// @Test def testSubmitFailedPrivilegedExceptionAction(): Unit = {
//   //     final Callable callable =
//   //         Executors.callable(new PrivilegedExceptionAction() {
//   //             public Object run() { throw new IndexOutOfBoundsException() }})
//   //     Runnable r = new CheckedRunnable() {
//   //     public void realRun() throws Exception {
//   //         ExecutorService e = new ForkJoinPool(1)
//   //         try (PoolCleaner cleaner = cleaner(e)) {
//   //             Future future = e.submit(callable)
//   //             try {
//   //                 future.get()
//   //                 shouldThrow()
//   //             } catch (ExecutionException success) {
//   //                 assertTrue(success.getCause() instanceof IndexOutOfBoundsException)
//   //             }
//   //         }
//   //     }}

//   //     runWithPermissions(r, new RuntimePermission("modifyThread"))
//   // }

//   // /**
//   //  * execute(null runnable) throws NullPointerException
//   //  */
//   // @Test def testExecuteNullRunnable(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         try {
//   //             Future<?> unused = e.submit((Runnable) null)
//   //             shouldThrow()
//   //         } catch (NullPointerException success) {}
//   //     }
//   // }

//   // /**
//   //  * submit(null callable) throws NullPointerException
//   //  */
//   // @Test def testSubmitNullCallable(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         try {
//   //             Future<String> unused = e.submit((Callable) null)
//   //             shouldThrow()
//   //         } catch (NullPointerException success) {}
//   //     }
//   // }

//   // /**
//   //  * submit(callable).get() throws InterruptedException if interrupted
//   //  */
//   // @throws[InterruptedException]
// @Test def testInterruptedSubmit(): Unit = {
//   //     final CountDownLatch submitted    = new CountDownLatch(1)
//   //     final CountDownLatch quittingTime = new CountDownLatch(1)
//   //     final Callable<Void> awaiter = new CheckedCallable<Void>() {
//   //         public Void realCall() throws InterruptedException {
//   //             assertTrue(quittingTime.await(2*LONG_DELAY_MS, MILLISECONDS))
//   //             return null
//   //         }}
//   //     final ExecutorService p = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(p, quittingTime)) {
//   //         Thread t = new Thread(new CheckedInterruptedRunnable() {
//   //             public void realRun() throws Exception {
//   //                 Future<Void> future = p.submit(awaiter)
//   //                 submitted.countDown()
//   //                 future.get()
//   //             }})
//   //         t.start()
//   //         await(submitted)
//   //         t.interrupt()
//   //         awaitTermination(t)
//   //     }
//   // }

//   // /**
//   //  * get of submit(callable) throws ExecutionException if callable
//   //  * throws exception
//   //  */
//   // @throws[Throwable]
// @Test def testSubmitEE(): Unit = {
//   //     ForkJoinPool p = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(p)) {
//   //         try {
//   //             p.submit(new Callable() {
//   //                     public Object call() { throw new ArithmeticException() }})
//   //                 .get()
//   //             shouldThrow()
//   //         } catch (ExecutionException success) {
//   //             assertTrue(success.getCause() instanceof ArithmeticException)
//   //         }
//   //     }
//   // }

//   // /**
//   //  * invokeAny(null) throws NullPointerException
//   //  */
//   // @throws[Throwable]
// @Test def testInvokeAny1(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         try {
//   //             e.invokeAny(null)
//   //             shouldThrow()
//   //         } catch (NullPointerException success) {}
//   //     }
//   // }

//   // /**
//   //  * invokeAny(empty collection) throws IllegalArgumentException
//   //  */
//   // @throws[Throwable]
// @Test def testInvokeAny2(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         try {
//   //             e.invokeAny(new ArrayList<Callable<String>>())
//   //             shouldThrow()
//   //         } catch (IllegalArgumentException success) {}
//   //     }
//   // }

//   // /**
//   //  * invokeAny(c) throws NullPointerException if c has a single null element
//   //  */
//   // @throws[Throwable]
// @Test def testInvokeAny3(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         List<Callable<String>> l = new ArrayList<>()
//   //         l.add(null)
//   //         try {
//   //             e.invokeAny(l)
//   //             shouldThrow()
//   //         } catch (NullPointerException success) {}
//   //     }
//   // }

//   // /**
//   //  * invokeAny(c) throws NullPointerException if c has null elements
//   //  */
//   // @throws[Throwable]
// @Test def testInvokeAny4(): Unit = {
//   //     CountDownLatch latch = new CountDownLatch(1)
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         List<Callable<String>> l = new ArrayList<>()
//   //         l.add(latchAwaitingStringTask(latch))
//   //         l.add(null)
//   //         try {
//   //             e.invokeAny(l)
//   //             shouldThrow()
//   //         } catch (NullPointerException success) {}
//   //         latch.countDown()
//   //     }
//   // }

//   // /**
//   //  * invokeAny(c) throws ExecutionException if no task in c completes
//   //  */
//   // @throws[Throwable]
// @Test def testInvokeAny5(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         List<Callable<String>> l = new ArrayList<>()
//   //         l.add(new NPETask())
//   //         try {
//   //             e.invokeAny(l)
//   //             shouldThrow()
//   //         } catch (ExecutionException success) {
//   //             assertTrue(success.getCause() instanceof NullPointerException)
//   //         }
//   //     }
//   // }

//   // /**
//   //  * invokeAny(c) returns result of some task in c if at least one completes
//   //  */
//   // @throws[Throwable]
// @Test def testInvokeAny6(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         List<Callable<String>> l = new ArrayList<>()
//   //         l.add(new StringTask())
//   //         l.add(new StringTask())
//   //         String result = e.invokeAny(l)
//   //         assertSame(TEST_STRING, result)
//   //     }
//   // }

//   // /**
//   //  * invokeAll(null) throws NullPointerException
//   //  */
//   // @throws[Throwable]
// @Test def testInvokeAll1(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         try {
//   //             e.invokeAll(null)
//   //             shouldThrow()
//   //         } catch (NullPointerException success) {}
//   //     }
//   // }

//   // /**
//   //  * invokeAll(empty collection) returns empty list
//   //  */
//   // @throws[InterruptedException]
// @Test def testInvokeAll2(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     final Collection<Callable<String>> emptyCollection
//   //         = Collections.emptyList()
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         List<Future<String>> r = e.invokeAll(emptyCollection)
//   //         assertTrue(r.isEmpty())
//   //     }
//   // }

//   // /**
//   //  * invokeAll(c) throws NullPointerException if c has null elements
//   //  */
//   // @throws[InterruptedException]
// @Test def testInvokeAll3(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         List<Callable<String>> l = new ArrayList<>()
//   //         l.add(new StringTask())
//   //         l.add(null)
//   //         try {
//   //             e.invokeAll(l)
//   //             shouldThrow()
//   //         } catch (NullPointerException success) {}
//   //     }
//   // }

//   // /**
//   //  * get of returned element of invokeAll(c) throws
//   //  * ExecutionException on failed task
//   //  */
//   // @throws[Throwable]
// @Test def testInvokeAll4(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         List<Callable<String>> l = new ArrayList<>()
//   //         l.add(new NPETask())
//   //         List<Future<String>> futures = e.invokeAll(l)
//   //         assertEquals(1, futures.size())
//   //         try {
//   //             futures.get(0).get()
//   //             shouldThrow()
//   //         } catch (ExecutionException success) {
//   //             assertTrue(success.getCause() instanceof NullPointerException)
//   //         }
//   //     }
//   // }

//   // /**
//   //  * invokeAll(c) returns results of all completed tasks in c
//   //  */
//   // @throws[Throwable]
// @Test def testInvokeAll5(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         List<Callable<String>> l = new ArrayList<>()
//   //         l.add(new StringTask())
//   //         l.add(new StringTask())
//   //         List<Future<String>> futures = e.invokeAll(l)
//   //         assertEquals(2, futures.size())
//   //         for (Future<String> future : futures)
//   //             assertSame(TEST_STRING, future.get())
//   //     }
//   // }

//   // /**
//   //  * timed invokeAny(null) throws NullPointerException
//   //  */
//   // @throws[Throwable]
// @Test def testTimedInvokeAny1(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         try {
//   //             e.invokeAny(null, randomTimeout(), randomTimeUnit())
//   //             shouldThrow()
//   //         } catch (NullPointerException success) {}
//   //     }
//   // }

//   // /**
//   //  * timed invokeAny(null time unit) throws NullPointerException
//   //  */
//   // @throws[Throwable]
// @Test def testTimedInvokeAnyNullTimeUnit(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         List<Callable<String>> l = new ArrayList<>()
//   //         l.add(new StringTask())
//   //         try {
//   //             e.invokeAny(l, randomTimeout(), null)
//   //             shouldThrow()
//   //         } catch (NullPointerException success) {}
//   //     }
//   // }

//   // /**
//   //  * timed invokeAny(empty collection) throws IllegalArgumentException
//   //  */
//   // @throws[Throwable]
// @Test def testTimedInvokeAny2(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         try {
//   //             e.invokeAny(new ArrayList<Callable<String>>(),
//   //                         randomTimeout(), randomTimeUnit())
//   //             shouldThrow()
//   //         } catch (IllegalArgumentException success) {}
//   //     }
//   // }

//   // /**
//   //  * timed invokeAny(c) throws NullPointerException if c has null elements
//   //  */
//   // @throws[Throwable]
// @Test def testTimedInvokeAny3(): Unit = {
//   //     CountDownLatch latch = new CountDownLatch(1)
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         List<Callable<String>> l = new ArrayList<>()
//   //         l.add(latchAwaitingStringTask(latch))
//   //         l.add(null)
//   //         try {
//   //             e.invokeAny(l, randomTimeout(), randomTimeUnit())
//   //             shouldThrow()
//   //         } catch (NullPointerException success) {}
//   //         latch.countDown()
//   //     }
//   // }

//   // /**
//   //  * timed invokeAny(c) throws ExecutionException if no task completes
//   //  */
//   // @throws[Throwable]
// @Test def testTimedInvokeAny4(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         long startTime = System.nanoTime()
//   //         List<Callable<String>> l = new ArrayList<>()
//   //         l.add(new NPETask())
//   //         try {
//   //             e.invokeAny(l, LONG_DELAY_MS, MILLISECONDS)
//   //             shouldThrow()
//   //         } catch (ExecutionException success) {
//   //             assertTrue(success.getCause() instanceof NullPointerException)
//   //         }
//   //         assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS)
//   //     }
//   // }

//   // /**
//   //  * timed invokeAny(c) returns result of some task in c
//   //  */
//   // @throws[Throwable]
// @Test def testTimedInvokeAny5(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         long startTime = System.nanoTime()
//   //         List<Callable<String>> l = new ArrayList<>()
//   //         l.add(new StringTask())
//   //         l.add(new StringTask())
//   //         String result = e.invokeAny(l, LONG_DELAY_MS, MILLISECONDS)
//   //         assertSame(TEST_STRING, result)
//   //         assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS)
//   //     }
//   // }

//   // /**
//   //  * timed invokeAll(null) throws NullPointerException
//   //  */
//   // @throws[Throwable]
// @Test def testTimedInvokeAll1(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         try {
//   //             e.invokeAll(null, randomTimeout(), randomTimeUnit())
//   //             shouldThrow()
//   //         } catch (NullPointerException success) {}
//   //     }
//   // }

//   // /**
//   //  * timed invokeAll(null time unit) throws NullPointerException
//   //  */
//   // @throws[Throwable]
// @Test def testTimedInvokeAllNullTimeUnit(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         List<Callable<String>> l = new ArrayList<>()
//   //         l.add(new StringTask())
//   //         try {
//   //             e.invokeAll(l, randomTimeout(), null)
//   //             shouldThrow()
//   //         } catch (NullPointerException success) {}
//   //     }
//   // }

//   // /**
//   //  * timed invokeAll(empty collection) returns empty list
//   //  */
//   // @throws[InterruptedException]
// @Test def testTimedInvokeAll2(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     final Collection<Callable<String>> emptyCollection
//   //         = Collections.emptyList()
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         List<Future<String>> r
//   //             = e.invokeAll(emptyCollection,
//   //                           randomTimeout(), randomTimeUnit())
//   //         assertTrue(r.isEmpty())
//   //     }
//   // }

//   // /**
//   //  * timed invokeAll(c) throws NullPointerException if c has null elements
//   //  */
//   // @throws[InterruptedException]
// @Test def testTimedInvokeAll3(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         List<Callable<String>> l = new ArrayList<>()
//   //         l.add(new StringTask())
//   //         l.add(null)
//   //         try {
//   //             e.invokeAll(l, randomTimeout(), randomTimeUnit())
//   //             shouldThrow()
//   //         } catch (NullPointerException success) {}
//   //     }
//   // }

//   // /**
//   //  * get of returned element of invokeAll(c) throws exception on failed task
//   //  */
//   // @throws[Throwable]
// @Test def testTimedInvokeAll4(): Unit = {
//   //     ExecutorService e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         List<Callable<String>> l = new ArrayList<>()
//   //         l.add(new NPETask())
//   //         List<Future<String>> futures
//   //             = e.invokeAll(l, LONG_DELAY_MS, MILLISECONDS)
//   //         assertEquals(1, futures.size())
//   //         try {
//   //             futures.get(0).get()
//   //             shouldThrow()
//   //         } catch (ExecutionException success) {
//   //             assertTrue(success.getCause() instanceof NullPointerException)
//   //         }
//   //     }
//   // }

//   // /**
//   //  * timed invokeAll(c) returns results of all completed tasks in c
//   //  */
//   // @throws[Throwable]
// @Test def testTimedInvokeAll5(): Unit = {
//   //     ForkJoinPool e = new ForkJoinPool(1)
//   //     try (PoolCleaner cleaner = cleaner(e)) {
//   //         List<Callable<String>> l = new ArrayList<>()
//   //         l.add(new StringTask())
//   //         l.add(new StringTask())
//   //         List<Future<String>> futures
//   //             = e.invokeAll(l, LONG_DELAY_MS, MILLISECONDS)
//   //         assertEquals(2, futures.size())
//   //         for (Future<String> future : futures)
//   //             assertSame(TEST_STRING, future.get())
//   //     }
//   // }

}
