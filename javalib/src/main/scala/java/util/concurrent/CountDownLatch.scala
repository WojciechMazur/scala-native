package java.util.concurrent

import java.util.concurrent.locks.AbstractQueuedSynchronizer
import scala.annotation.tailrec
/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

/**
 * A synchronization aid that allows one or more threads to wait until
 * a set of operations being performed in other threads completes.
 *
 * <p>A {@code CountDownLatch} is initialized with a given <em>count</em>.
 * The {@link #await await} methods block until the current count reaches
 * zero due to invocations of the {@link #countDown} method, after which
 * all waiting threads are released and any subsequent invocations of
 * {@link #await await} return immediately.  This is a one-shot phenomenon
 * -- the count cannot be reset.  If you need a version that resets the
 * count, consider using a {@link CyclicBarrier}.
 *
 * <p>A {@code CountDownLatch} is a versatile synchronization tool
 * and can be used for a number of purposes.  A
 * {@code CountDownLatch} initialized with a count of one serves as a
 * simple on/off latch, or gate: all threads invoking {@link #await await}
 * wait at the gate until it is opened by a thread invoking {@link
 * #countDown}.  A {@code CountDownLatch} initialized to <em>N</em>
 * can be used to make one thread wait until <em>N</em> threads have
 * completed some action, or some action has been completed N times.
 *
 * <p>A useful property of a {@code CountDownLatch} is that it
 * doesn't require that threads calling {@code countDown} wait for
 * the count to reach zero before proceeding, it simply prevents any
 * thread from proceeding past an {@link #await await} until all
 * threads could pass.
 *
 * <p><b>Sample usage:</b> Here is a pair of classes in which a group
 * of worker threads use two countdown latches:
 * <ul>
 * <li>The first is a start signal that prevents any worker from proceeding
 * until the driver is ready for them to proceed;
 * <li>The second is a completion signal that allows the driver to wait
 * until all workers have completed.
 * </ul>
 *
 * <pre> {@code
 * class Driver { // ...
 *   void main() throws InterruptedException {
 *     CountDownLatch startSignal = new CountDownLatch(1);
 *     CountDownLatch doneSignal = new CountDownLatch(N);
 *
 *     for (int i = 0; i < N; ++i) // create and start threads
 *       new Thread(new Worker(startSignal, doneSignal)).start();
 *
 *     doSomethingElse();            // don't let run yet
 *     startSignal.countDown();      // let all threads proceed
 *     doSomethingElse();
 *     doneSignal.await();           // wait for all to finish
 *   }
 * }
 *
 * class Worker implements Runnable {
 *   private final CountDownLatch startSignal;
 *   private final CountDownLatch doneSignal;
 *   Worker(CountDownLatch startSignal, CountDownLatch doneSignal) {
 *     this.startSignal = startSignal;
 *     this.doneSignal = doneSignal;
 *   }
 *   public void run() {
 *     try {
 *       startSignal.await();
 *       doWork();
 *       doneSignal.countDown();
 *     } catch (InterruptedException ex) {} // return;
 *   }
 *
 *   void doWork() { ... }
 * }}</pre>
 *
 * <p>Another typical usage would be to divide a problem into N parts,
 * describe each part with a Runnable that executes that portion and
 * counts down on the latch, and queue all the Runnables to an
 * Executor.  When all sub-parts are complete, the coordinating thread
 * will be able to pass through await. (When threads must repeatedly
 * count down in this way, instead use a {@link CyclicBarrier}.)
 *
 * <pre> {@code
 * class Driver2 { // ...
 *   void main() throws InterruptedException {
 *     CountDownLatch doneSignal = new CountDownLatch(N);
 *     Executor e = ...;
 *
 *     for (int i = 0; i < N; ++i) // create and start threads
 *       e.execute(new WorkerRunnable(doneSignal, i));
 *
 *     doneSignal.await();           // wait for all to finish
 *   }
 * }
 *
 * class WorkerRunnable implements Runnable {
 *   private final CountDownLatch doneSignal;
 *   private final int i;
 *   WorkerRunnable(CountDownLatch doneSignal, int i) {
 *     this.doneSignal = doneSignal;
 *     this.i = i;
 *   }
 *   public void run() {
 *     doWork();
 *     doneSignal.countDown();
 *   }
 *
 *   void doWork() { ... }
 * }}</pre>
 *
 * <p>Memory consistency effects: Until the count reaches
 * zero, actions in a thread prior to calling
 * {@code countDown()}
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * actions following a successful return from a corresponding
 * {@code await()} in another thread.
 *
 * @since 1.5
 * @author Doug Lea
 */
object CountDownLatch {

  /**
   * Synchronization control For CountDownLatch.
   * Uses AQS state to represent count.
   */
  @SerialVersionUID(4982264981922014374L)
  final private class Sync private[concurrent] (val count: Int)
      extends AbstractQueuedSynchronizer {
    setState(count)

    private[concurrent] def getCount()() = getState()

    override protected def tryAcquireShared(acquires: Int): Int = {
      if (getState() == 0) 1
      else -1
    }

    override protected def tryReleaseShared(releases: Int): Boolean = { // Decrement count; signal when transition to zero
      @tailrec
      def loop(): Boolean = getState() match {
        case 0 => false
        case state =>
          val nextState = state - 1
          if (compareAndSetState(state, nextState)) {
            nextState == 0
          } else loop()
      }
      loop()
    }
  }
}

class CountDownLatch(sync: CountDownLatch.Sync) {
  def this(count: Int) {
    this(sync = {
      if (count < 0) throw new IllegalArgumentException("count < 0")
      new CountDownLatch.Sync(count)
    })
  }

  /**
   * Causes the current thread to wait until the latch has counted down to
   * zero, unless the thread is {@linkplain Thread#interrupt interrupted}.
   *
   * <p>If the current count is zero then this method returns immediately.
   *
   * <p>If the current count is greater than zero then the current
   * thread becomes disabled for thread scheduling purposes and lies
   * dormant until one of two things happen:
   * <ul>
   * <li>The count reaches zero due to invocations of the
   * {@link #countDown} method; or
   * <li>Some other thread {@linkplain Thread#interrupt interrupts}
   * the current thread.
   * </ul>
   *
   * <p>If the current thread:
   * <ul>
   * <li>has its interrupted status set on entry to this method; or
   * <li>is {@linkplain Thread#interrupt interrupted} while waiting,
   * </ul>
   * then {@link InterruptedException} is thrown and the current thread's
   * interrupted status is cleared.
   *
   * @throws InterruptedException if the current thread is interrupted
   *         while waiting
   */
  @throws[InterruptedException]
  def await(): Unit = {
    sync.acquireSharedInterruptibly(1)
  }

  /**
   * Causes the current thread to wait until the latch has counted down to
   * zero, unless the thread is {@linkplain Thread#interrupt interrupted},
   * or the specified waiting time elapses.
   *
   * <p>If the current count is zero then this method returns immediately
   * with the value {@code true}.
   *
   * <p>If the current count is greater than zero then the current
   * thread becomes disabled for thread scheduling purposes and lies
   * dormant until one of three things happen:
   * <ul>
   * <li>The count reaches zero due to invocations of the
   * {@link #countDown} method; or
   * <li>Some other thread {@linkplain Thread#interrupt interrupts}
   * the current thread; or
   * <li>The specified waiting time elapses.
   * </ul>
   *
   * <p>If the count reaches zero then the method returns with the
   * value {@code true}.
   *
   * <p>If the current thread:
   * <ul>
   * <li>has its interrupted status set on entry to this method; or
   * <li>is {@linkplain Thread#interrupt interrupted} while waiting,
   * </ul>
   * then {@link InterruptedException} is thrown and the current thread's
   * interrupted status is cleared.
   *
   * <p>If the specified waiting time elapses then the value {@code false}
   * is returned.  If the time is less than or equal to zero, the method
   * will not wait at all.
   *
   * @param timeout the maximum time to wait
   * @param unit the time unit of the {@code timeout} argument
   * @return {@code true} if the count reached zero and {@code false}
   *         if the waiting time elapsed before the count reached zero
   * @throws InterruptedException if the current thread is interrupted
   *         while waiting
   */
  @throws[InterruptedException]
  def await(timeout: Long, unit: TimeUnit): Boolean =
    sync.tryAcquireSharedNanos(1, unit.toNanos(timeout))

  /**
   * Decrements the count of the latch, releasing all waiting threads if
   * the count reaches zero.
   *
   * <p>If the current count is greater than zero then it is decremented.
   * If the new count is zero then all waiting threads are re-enabled for
   * thread scheduling purposes.
   *
   * <p>If the current count equals zero then nothing happens.
   */
  def countDown(): Unit = {
    sync.releaseShared(1)
  }

  /**
   * Returns the current count.
   *
   * <p>This method is typically used for debugging and testing purposes.
   *
   * @return the current count
   */
  def getCount(): Long = sync.getCount()

  /**
   * Returns a string identifying this latch, as well as its state.
   * The state, in brackets, includes the String {@code "Count ="}
   * followed by the current count.
   *
   * @return a string identifying this latch, as well as its state
   */
  override def toString(): String =
    super.toString + "[Count = " + sync.getCount() + "]"
}
