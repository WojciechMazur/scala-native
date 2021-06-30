/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.atomic.AtomicInteger
import scala.scalanative.annotation._
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

/**
 * A cancellable asynchronous computation.  This class provides a base
 * implementation of {@link Future}, with methods to start and cancel
 * a computation, query to see if the computation is complete, and
 * retrieve the result of the computation.  The result can only be
 * retrieved when the computation has completed; the {@code get}
 * methods will block if the computation has not yet completed.  Once
 * the computation has completed, the computation cannot be restarted
 * or cancelled (unless the computation is invoked using
 * {@link #runAndReset}).
 *
 * <p>A {@code FutureTask} can be used to wrap a {@link Callable} or
 * {@link Runnable} object.  Because {@code FutureTask} implements
 * {@code Runnable}, a {@code FutureTask} can be submitted to an
 * {@link Executor} for execution.
 *
 * <p>In addition to serving as a standalone class, this class provides
 * {@code protected} functionality that may be useful when creating
 * customized task classes.
 *
 * @since 1.5
 * @author Doug Lea
 * @param <V> The result type returned by this FutureTask's {@code get} methods
 */
object FutureTask {
  private val NEW          = 0
  private val COMPLETING   = 1
  private val NORMAL       = 2
  private val EXCEPTIONAL  = 3
  private val CANCELLED    = 4
  private val INTERRUPTING = 5
  private val INTERRUPTED  = 6

  /**
   * Simple linked list nodes to record waiting threads in a Treiber
   * stack.  See other classes such as Phaser and SynchronousQueue
   * for more detailed explanation.
   */
  final private[concurrent] class WaitNode private[concurrent] () {
    private[concurrent] var thread         = Thread.currentThread()
    private[concurrent] var next: WaitNode = null
  }
}

class FutureTask[V] private () extends RunnableFuture[V] {
  import FutureTask._

  /**
   * The run state of this task, initially NEW.  The run state
   * transitions to a terminal state only in methods set,
   * setException, and cancel.  During completion, state may take on
   * transient values of COMPLETING (while outcome is being set) or
   * INTERRUPTING (only while interrupting the runner to satisfy a
   * cancel(true)). Transitions from these intermediate to final
   * states use cheaper ordered/lazy writes because values are unique
   * and cannot be further modified.
   *
   * Possible state transitions:
   * NEW -> COMPLETING -> NORMAL
   * NEW -> COMPLETING -> EXCEPTIONAL
   * NEW -> CANCELLED
   * NEW -> INTERRUPTING -> INTERRUPTED
   */
  private val atomicState                 = new AtomicInteger(NEW)
  @alwaysinline def state: Int            = atomicState.getPlain()
  @alwaysinline def state_=(v: Int): Unit = atomicState.setPlain(v)

  /** The underlying callable; nulled out after running */
  private var callable: Callable[V] = _

  /** The result to return or exception to throw from get() */
  private var outcome: Any = null

  /** The thread running the callable; CASed during run() */
  private val atomicRunner                    = new AtomicReference[Thread]()
  @alwaysinline def runner: Thread            = atomicRunner.get()
  @alwaysinline def runner_=(v: Thread): Unit = atomicRunner.set(v)

  /** Treiber stack of waiting threads */
  @volatile private var atomicWaiters            = new AtomicReference[WaitNode]()
  @alwaysinline def waiters: WaitNode            = atomicWaiters.get()
  @alwaysinline def waiters_=(v: WaitNode): Unit = atomicWaiters.set(v)

  def this(callable: Callable[V]) = {
    this()
    this.callable = callable
  }

  def this(runnable: Runnable, result: V) = {
    this()
    this.callable = Executors.callable(runnable, result)
  }

  /**
   * Returns result or throws exception for completed task.
   *
   * @param s completed state value
   */
  @SuppressWarnings(Array("unchecked")) @throws[ExecutionException]
  private def report(s: Int): V = {
    val x = outcome
    if (s == NORMAL) return x.asInstanceOf[V]
    if (s >= CANCELLED) throw new CancellationException
    throw new ExecutionException(x.asInstanceOf[Throwable])
  }

  override def isCancelled(): Boolean = state >= CANCELLED
  override def isDone(): Boolean      = state != NEW
  override def cancel(mayInterruptIfRunning: Boolean): Boolean = {
    if (!(state == NEW && atomicState.compareAndSet(
          NEW,
          if (mayInterruptIfRunning) INTERRUPTING else CANCELLED))) {
      return false
    }
    try { // in case call to interrupt throws exception
      if (mayInterruptIfRunning) try {
        val t = runner
        if (t != null) t.interrupt()
      } finally {
        // final state
        atomicState.setRelease(INTERRUPTED)
      }
    } finally finishCompletion()
    true
  }

  @throws[ExecutionException]
  override def get(): V = {
    var s = state
    if (s <= COMPLETING) s = awaitDone(false, 0L)
    report(s)
  }

  @throws[InterruptedException]
  @throws[ExecutionException]
  @throws[TimeoutException]
  override def get(timeout: Long, unit: TimeUnit): V = {
    if (unit == null) throw new NullPointerException
    var s = state
    if (s <= COMPLETING) {
      s = awaitDone(true, unit.toNanos(timeout))
      if (s <= COMPLETING) throw new TimeoutException
    }
    report(s)
  }

  /**
   * Protected method invoked when this task transitions to state
   * {@code isDone} (whether normally or via cancellation). The
   * default implementation does nothing.  Subclasses may override
   * this method to invoke completion callbacks or perform
   * bookkeeping. Note that you can query status inside the
   * implementation of this method to determine whether this task
   * has been cancelled.
   */
  protected def done(): Unit = {}

  /**
   * Sets the result of this future to the given value unless
   * this future has already been set or has been cancelled.
   *
   * <p>This method is invoked internally by the {@link #run} method
   * upon successful completion of the computation.
   *
   * @param v the value
   */
  protected def set(v: V): Unit = {
    if (atomicState.compareAndSet(NEW, COMPLETING)) {
      outcome = v
      atomicState.setRelease(NORMAL)
      finishCompletion()
    }
  }

  /**
   * Causes this future to report an {@link ExecutionException}
   * with the given throwable as its cause, unless this future has
   * already been set or has been cancelled.
   *
   * <p>This method is invoked internally by the {@link #run} method
   * upon failure of the computation.
   *
   * @param t the cause of failure
   */
  protected def setException(t: Throwable): Unit = {
    if (atomicState.compareAndSet(NEW, COMPLETING)) {
      outcome = t
      atomicState.setRelease(EXCEPTIONAL)
      finishCompletion()
    }
  }
  override def run(): Unit = {
    if (state != NEW || !atomicRunner.compareAndSet(null,
                                                    Thread.currentThread()))
      return
    try {
      val c = callable
      if (c != null && state == NEW) {
        try {
          set(c.call())
        } catch {
          case ex: Throwable =>
            setException(ex)
        }
      }
    } finally {
      // runner must be non-null until state is settled to
      // prevent concurrent calls to run()
      runner = null
      // state must be re-read after nulling runner to prevent
      // leaked interrupts
      val s = state
      if (s >= INTERRUPTING) handlePossibleCancellationInterrupt(s)
    }
  }

  /**
   * Executes the computation without setting its result, and then
   * resets this future to initial state, failing to do so if the
   * computation encounters an exception or is cancelled.  This is
   * designed for use with tasks that intrinsically execute more
   * than once.
   *
   * @return {@code true} if successfully run and reset
   */
  protected def runAndReset: Boolean = {
    if (state != NEW || !atomicRunner.compareAndSet(null,
                                                    Thread.currentThread()))
      return false

    var ran = false
    var s   = state
    try {
      val c = callable
      if (c != null && s == NEW) try {
        c.call() // don't set result
        ran = true
      } catch {
        case ex: Throwable =>
          setException(ex)
      }
    } finally {
      runner = null
      s = state
      if (s >= INTERRUPTING) handlePossibleCancellationInterrupt(s)
    }
    ran && s == NEW
  }

  /**
   * Ensures that any interrupt from a possible cancel(true) is only
   * delivered to a task while in run or runAndReset.
   */
  private def handlePossibleCancellationInterrupt(s: Int): Unit = {
    // It is possible for our interrupter to stall before getting a
    // chance to interrupt us.  Let's spin-wait patiently.
    if (s == INTERRUPTING) {
      while (state == INTERRUPTING) {
        Thread.`yield`() // wait out pending interrupt
      }
    }
    // assert state == INTERRUPTED;
    // We want to clear any interrupt we may have received from
    // cancel(true).  However, it is permissible to use interrupts
    // as an independent mechanism for a task to communicate with
    // its caller, and there is no way to clear only the
    // cancellation interrupt.
    //
    // Thread.interrupted();
  }

  /**
   * Removes and signals all waiting threads, invokes done(), and
   * nulls out callable.
   */
  private def finishCompletion(): Unit = { // assert state > COMPLETING;
    @tailrec
    def loop(): Unit = {
      var q = waiters
      if (q == null) ()
      else if (atomicWaiters.weakCompareAndSetVolatile(q, null)) {
        while (true) {
          q.thread match {
            case null => ()
            case t =>
              q.thread = null
              LockSupport.unpark(t)
          }
          q.next match {
            case null => return
            case next =>
              q.next = null // unlink to help gc
              q = next
          }
        }
      } else loop()
    }

    loop()
    done()
    callable = null // to reduce footprint

  }

  /**
   * Awaits completion or aborts on interrupt or timeout.
   *
   * @param timed true if use timed waits
   * @param nanos time to wait, if timed
   * @return state upon completion or at timeout
   */
  @throws[InterruptedException]
  private def awaitDone(timed: Boolean, nanos: Long): Int = {
    // The code below is very delicate, to achieve these goals:
    // - call nanoTime exactly once for each call to park
    // - if nanos <= 0L, return promptly without allocation or nanoTime
    // - if nanos == Long.MIN_VALUE, don't underflow
    // - if nanos == Long.MAX_VALUE, and nanoTime is non-monotonic
    //   and we suffer a spurious wakeup, we will do no worse than
    //   to park-spin for a while
    var startTime: Option[Long] = None
    var q: WaitNode             = null
    var queued                  = false

    while (true) {
      val s = state
      if (s > COMPLETING) {
        if (q != null) q.thread = null
        return s
      } else if (s == COMPLETING) {
        // We may have already promised (via isDone) that we are done
        // so never return empty-handed or throw InterruptedException
        Thread.`yield`()
      } else if (Thread.interrupted()) {
        removeWaiter(q)
        throw new InterruptedException
      } else if (q == null) {
        if (timed && nanos <= 0L) return s
        q = new WaitNode
      } else if (!queued) {
        q.next = waiters
        queued = atomicWaiters.weakCompareAndSetVolatile(q.next, q)
      } else if (timed) {
        val parkNanos = if (startTime.isEmpty) { // first time
          startTime = Some(System.nanoTime())
          nanos
        } else {
          val elapsed = System.nanoTime() - startTime.getOrElse(0L)
          if (elapsed >= nanos) {
            removeWaiter(q)
            return state
          }
          nanos - elapsed
        }
        // nanoTime may be slow; recheck before parking
        if (state < COMPLETING)
          LockSupport.parkNanos(this, parkNanos)
      } else LockSupport.park(this)
    }
    state
  }

  /**
   * Tries to unlink a timed-out or interrupted wait node to avoid
   * accumulating garbage.  Internal nodes are simply unspliced
   * without CAS since it is harmless if they are traversed anyway
   * by releasers.  To avoid effects of unsplicing from already
   * removed nodes, the list is retraversed in case of an apparent
   * race.  This is slow when there are a lot of nodes, but we don't
   * expect lists to be long enough to outweigh higher-overhead
   * schemes.
   */
  @tailrec
  private def removeWaiter(node: WaitNode): Unit = {
    @tailrec
    def tryRemove(q: WaitNode, pred: WaitNode): Boolean = {
      if (q == null) false
      else {
        val s = q.next
        if (q.thread != null) tryRemove(q = s, pred = q)
        else if (pred != null) {
          pred.next = s
          if (pred.thread == null) { // check for race
            true                     //retry
          } else tryRemove(q, pred)
        } else if (!atomicWaiters.compareAndSet(q, s)) true // retry
        else tryRemove(s, pred)
      }
    }

    if (node != null) {
      node.thread = null
      val retry = tryRemove(waiters, pred = null)
      if (retry) removeWaiter(node)
    }
  }

  /**
   * Returns a string representation of this
   *
   * @implSpec
   * The default implementation returns a string identifying this
   * FutureTask, as well as its completion state.  The state, in
   * brackets, contains one of the strings {@code "Completed Normally"},
   * {@code "Completed Exceptionally"}, {@code "Cancelled"}, or {@code
   * "Not completed"}.
   *
   * @return a string representation of this FutureTask
   */
  override def toString: String = {
    val status = state match {
      case NORMAL                                 => "[Completed normally]"
      case EXCEPTIONAL                            => "[Completed exceptionally: " + outcome + "]"
      case CANCELLED | INTERRUPTING | INTERRUPTED => "[Cancelled]"
      case _ =>
        if (callable == null) "[Not completed]"
        else "[Not completed, task = " + callable + "]"
    }
    super.toString + status
  }
}
