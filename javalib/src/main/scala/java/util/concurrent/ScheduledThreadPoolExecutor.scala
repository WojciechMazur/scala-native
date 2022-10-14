/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent

import java.util.concurrent.TimeUnit._
import java.util
import java.util._
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks._
import scala.annotation.tailrec

/** A {@link ThreadPoolExecutor} that can additionally schedule commands to run
 *  after a given delay, or to execute periodically. This class is preferable to
 *  {@link java.util.Timer} when multiple worker threads are needed, or when the
 *  additional flexibility or capabilities of {@link ThreadPoolExecutor} (which
 *  this class extends) are required.
 *
 *  <p>Delayed tasks execute no sooner than they are enabled, but without any
 *  real-time guarantees about when, after they are enabled, they will commence.
 *  Tasks scheduled for exactly the same execution time are enabled in
 *  first-in-first-out (FIFO) order of submission.
 *
 *  <p>When a submitted task is cancelled before it is run, execution is
 *  suppressed. By default, such a cancelled task is not automatically removed
 *  from the work queue until its delay elapses. While this enables further
 *  inspection and monitoring, it may also cause unbounded retention of
 *  cancelled tasks. To avoid this, use {@link #setRemoveOnCancelPolicy} to
 *  cause tasks to be immediately removed from the work queue at time of
 *  cancellation.
 *
 *  <p>Successive executions of a periodic task scheduled via {@link
 *  #scheduleAtFixedRate scheduleAtFixedRate} or {@link #scheduleWithFixedDelay
 *  scheduleWithFixedDelay} do not overlap. While different executions may be
 *  performed by different threads, the effects of prior executions <a
 *  href="package-summary.html#MemoryVisibility"><i>happen-before</i></a> those
 *  of subsequent ones.
 *
 *  <p>While this class inherits from {@link ThreadPoolExecutor}, a few of the
 *  inherited tuning methods are not useful for it. In particular, because it
 *  acts as a fixed-sized pool using {@code corePoolSize} threads and an
 *  unbounded queue, adjustments to {@code maximumPoolSize} have no useful
 *  effect. Additionally, it is almost never a good idea to set {@code
 *  corePoolSize} to zero or use {@code allowCoreThreadTimeOut} because this may
 *  leave the pool without threads to handle tasks once they become eligible to
 *  run.
 *
 *  <p>As with {@code ThreadPoolExecutor}, if not otherwise specified, this
 *  class uses {@link Executors#defaultThreadFactory} as the default thread
 *  factory, and {@link ThreadPoolExecutor.AbortPolicy} as the default rejected
 *  execution handler.
 *
 *  <p><b>Extension notes:</b> This class overrides the {@link
 *  ThreadPoolExecutor#execute(Runnable) execute} and {@link
 *  AbstractExecutorService#submit(Runnable) submit} methods to generate
 *  internal {@link ScheduledFuture} objects to control per-task delays and
 *  scheduling. To preserve functionality, any further overrides of these
 *  methods in subclasses must invoke superclass versions, which effectively
 *  disables additional task customization. However, this class provides
 *  alternative protected extension method {@code decorateTask} (one version
 *  each for {@code Runnable} and {@code Callable}) that can be used to
 *  customize the concrete task types used to execute commands entered via
 *  {@code execute}, {@code submit}, {@code schedule}, {@code
 *  scheduleAtFixedRate}, and {@code scheduleWithFixedDelay}. By default, a
 *  {@code ScheduledThreadPoolExecutor} uses a task type extending {@link
 *  FutureTask}. However, this may be modified or replaced using subclasses of
 *  the form:
 *
 *  <pre> {@code public class CustomScheduledExecutor extends
 *  ScheduledThreadPoolExecutor {
 *
 *  static class CustomTask<V> implements RunnableScheduledFuture<V> { ... }
 *
 *  protected <V> RunnableScheduledFuture<V> decorateTask( Runnable r,
 *  RunnableScheduledFuture<V> task) { return new CustomTask<V>(r, task); }
 *
 *  protected <V> RunnableScheduledFuture<V> decorateTask( Callable<V> c,
 *  RunnableScheduledFuture<V> task) { return new CustomTask<V>(c, task); } //
 *  ... add constructors, etc. }}</pre>
 *
 *  @since 1.5
 *  @author
 *    Doug Lea
 */
object ScheduledThreadPoolExecutor {

  /** Sequence number to break scheduling ties, and in turn to guarantee FIFO
   *  order among tied entries.
   */
  private val sequencer = new AtomicLong

  /** The default keep-alive time for pool threads.
   *
   *  Normally, this value is unused because all pool threads will be core
   *  threads, but if a user creates a pool with a corePoolSize of zero (against
   *  our advice), we keep a thread alive as long as there are queued tasks. If
   *  the keep alive time is zero (the historic value), we end up hot-spinning
   *  in getTask, wasting a CPU. But on the other hand, if we set the value too
   *  high, and users create a one-shot pool which they don't cleanly shutdown,
   *  the pool's non-daemon threads will prevent JVM termination. A small but
   *  non-zero value (relative to a JVM's lifetime) seems best.
   */
  private val DEFAULT_KEEPALIVE_MILLIS = 10L

  /** Specialized delay queue. To mesh with TPE declarations, this class must be
   *  declared as a BlockingQueue<Runnable> even though it can only hold
   *  RunnableScheduledFutures.
   */
  private[concurrent] object DelayedWorkQueue {
    private val INITIAL_CAPACITY = 16

    /** Sets f's heapIndex if it is a ScheduledFutureTask.
     */
    private def setIndex(f: RunnableScheduledFuture[AnyRef], idx: Int): Unit =
      f match {
        case f: ScheduledThreadPoolExecutor#ScheduledFutureTask[_] =>
          f.heapIndex = idx
        case _ => ()
      }
  }
  private[concurrent] class DelayedWorkQueue
      extends util.AbstractQueue[Runnable]
      with BlockingQueue[Runnable] {
    private var queue =
      new Array[RunnableScheduledFuture[AnyRef]](
        DelayedWorkQueue.INITIAL_CAPACITY
      )
    final private val lock = new ReentrantLock
    private var _size = 0

    /** Thread designated to wait for the task at the head of the queue. This
     *  variant of the Leader-Follower pattern
     *  (http://www.cs.wustl.edu/~schmidt/POSA/POSA2/) serves to minimize
     *  unnecessary timed waiting. When a thread becomes the leader, it waits
     *  only for the next delay to elapse, but other threads await indefinitely.
     *  The leader thread must signal some other thread before returning from
     *  take() or poll(...), unless some other thread becomes leader in the
     *  interim. Whenever the head of the queue is replaced with a task with an
     *  earlier expiration time, the leader field is invalidated by being reset
     *  to null, and some waiting thread, but not necessarily the current
     *  leader, is signalled. So waiting threads must be prepared to acquire and
     *  lose leadership while waiting.
     */
    private var leader: Thread = null

    /** Condition signalled when a newer task becomes available at the head of
     *  the queue or a new thread may need to become leader.
     */
    final private val available = lock.newCondition()

    /** Sifts element added at bottom up to its heap-ordered spot. Call only
     *  when holding lock.
     */
    private def siftUp(_k: Int, key: RunnableScheduledFuture[AnyRef]): Unit = {
      var k = _k
      var break = false
      while (!break && k > 0) {
        val parent = (k - 1) >>> 1
        val e = queue(parent)
        if (key.compareTo(e) >= 0) break = true
        else {
          queue(k) = e
          DelayedWorkQueue.setIndex(e, k)
          k = parent
        }
      }
      queue(k) = key
      DelayedWorkQueue.setIndex(key, k)
    }

    /** Sifts element added at top down to its heap-ordered spot. Call only when
     *  holding lock.
     */
    private def siftDown(
        _k: Int,
        key: RunnableScheduledFuture[AnyRef]
    ): Unit = {
      var k = _k
      val half = _size >>> 1
      var break = false
      while (!break && k < half) {
        var child = (k << 1) + 1
        var c = queue(child)
        val right = child + 1
        if (right < _size && c.compareTo(queue(right)) > 0) {
          child = right
          c = queue(child)
        }
        if (key.compareTo(c) <= 0) break = true
        else {
          queue(k) = c
          DelayedWorkQueue.setIndex(c, k)
          k = child
        }
      }
      queue(k) = key
      DelayedWorkQueue.setIndex(key, k)
    }

    /** Resizes the heap array. Call only when holding lock.
     */
    private def grow(): Unit = {
      val oldCapacity = queue.length
      var newCapacity = oldCapacity + (oldCapacity >> 1) // grow 50%
      if (newCapacity < 0) { // overflow
        newCapacity = Integer.MAX_VALUE
      }
      queue = util.Arrays.copyOf(queue, newCapacity)
    }

    /** Finds index of given object, or -1 if absent.
     */
    private def indexOf(x: Any): Int = x match {
      case null => -1
      case t: ScheduledThreadPoolExecutor#ScheduledFutureTask[_] =>
        val i = t.heapIndex
        // Sanity check; x could conceivably be a
        // ScheduledFutureTask from some other pool.
        if (i >= 0 && i < _size && (queue(i) == x)) i
        else -1
      case _ =>
        for (i <- 0 until _size) if (x == queue(i)) return i
        -1
    }

    override def contains(x: Any): Boolean = {
      val lock = this.lock
      lock.lock()
      try indexOf(x) != -1
      finally lock.unlock()
    }

    override def remove(x: Any): Boolean = {
      val lock = this.lock
      lock.lock()
      try {
        val i = indexOf(x)
        if (i < 0) return false
        DelayedWorkQueue.setIndex(queue(i), -1)
        _size -= 1
        val s = _size
        val replacement = queue(s)
        queue(s) = null
        if (s != i) {
          siftDown(i, replacement)
          if (queue(i) eq replacement) siftUp(i, replacement)
        }
        true
      } finally lock.unlock()
    }

    override def size(): Int = {
      val lock = this.lock
      lock.lock()
      try this._size
      finally lock.unlock()
    }

    override def isEmpty(): Boolean = _size == 0
    override def remainingCapacity(): Int = Integer.MAX_VALUE
    override def peek(): RunnableScheduledFuture[AnyRef] = {
      val lock = this.lock
      lock.lock()
      try queue(0)
      finally lock.unlock()
    }

    override def offer(x: Runnable): Boolean = {
      if (x == null) throw new NullPointerException
      val e = x.asInstanceOf[RunnableScheduledFuture[AnyRef]]
      val lock = this.lock
      lock.lock()
      try {
        val i = _size
        if (i >= queue.length) grow()
        _size = i + 1
        if (i == 0) {
          queue(0) = e
          DelayedWorkQueue.setIndex(e, 0)
        } else siftUp(i, e)
        if (queue(0) eq e) {
          leader = null
          available.signal()
        }
      } finally lock.unlock()
      true
    }

    override def put(e: Runnable): Unit = { offer(e) }
    override def add(e: Runnable): Boolean = offer(e)
    override def offer(e: Runnable, timeout: Long, unit: TimeUnit): Boolean =
      offer(e)

    /** Performs common bookkeeping for poll and take: Replaces first element
     *  with last and sifts it down. Call only when holding lock.
     *  @param f
     *    the task to remove and return
     */
    private def finishPoll(f: RunnableScheduledFuture[AnyRef]) = {
      val s = { _size -= 1; _size }
      val x = queue(s)
      queue(s) = null
      if (s != 0) siftDown(0, x)
      DelayedWorkQueue.setIndex(f, -1)
      f
    }

    override def poll(): RunnableScheduledFuture[AnyRef] = {
      val lock = this.lock
      lock.lock()
      try {
        val first = queue(0)
        if (first == null || first.getDelay(NANOSECONDS) > 0) null
        else finishPoll(first)
      } finally lock.unlock()
    }

    @throws[InterruptedException]
    override def take(): RunnableScheduledFuture[AnyRef] = {
      @tailrec def loop(): RunnableScheduledFuture[AnyRef] = {
        var first = queue(0)
        if (first == null) {
          available.await()
          loop()
        } else {
          val delay = first.getDelay(NANOSECONDS)
          if (delay <= 0L) finishPoll(first)
          else {
            first = null // don't retain ref while waiting
            if (leader != null) available.await()
            else {
              val thisThread = Thread.currentThread()
              leader = thisThread
              try available.awaitNanos(delay)
              finally if (leader eq thisThread) leader = null
            }
            loop()
          }
        }
      }

      val lock = this.lock
      lock.lockInterruptibly()
      try loop()
      finally {
        if (leader == null && queue(0) != null) available.signal()
        lock.unlock()
      }
    }

    @throws[InterruptedException]
    override def poll(
        timeout: Long,
        unit: TimeUnit
    ): RunnableScheduledFuture[AnyRef] = {
      @tailrec def loop(nanos: Long): RunnableScheduledFuture[AnyRef] = {
        var first = queue(0)
        if (first == null)
          if (nanos <= 0L) null
          else loop(available.awaitNanos(nanos))
        else {
          val delay = first.getDelay(NANOSECONDS)
          if (delay <= 0L) finishPoll(first)
          else if (nanos <= 0L) null
          else {
            first = null
            if (nanos < delay || leader != null)
              loop(available.awaitNanos(nanos))
            else {
              val thisThread = Thread.currentThread()
              leader = thisThread
              loop(try {
                val timeLeft = available.awaitNanos(delay)
                nanos - (delay - timeLeft)
              } finally if (leader eq thisThread) leader = null)
            }
          }
        }
      }

      val lock = this.lock
      lock.lockInterruptibly()
      try loop(unit.toNanos(timeout))
      finally {
        if (leader == null && queue(0) != null) available.signal()
        lock.unlock()
      }
    }

    override def clear(): Unit = {
      val lock = this.lock
      lock.lock()
      try {
        for (i <- 0 until _size) {
          val t = queue(i)
          if (t != null) {
            queue(i) = null
            DelayedWorkQueue.setIndex(t, -1)
          }
        }
        _size = 0
      } finally lock.unlock()
    }

    override def drainTo(c: util.Collection[_ >: Runnable]): Int =
      drainTo(c, Integer.MAX_VALUE)

    override def drainTo(
        c: util.Collection[_ >: Runnable],
        maxElements: Int
    ): Int = {
      Objects.requireNonNull(c)
      if (c eq this) throw new IllegalArgumentException
      if (maxElements <= 0) return 0
      val lock = this.lock
      lock.lock()
      try {
        var n = 0
        var first: RunnableScheduledFuture[AnyRef] = null
        while ({
          n < maxElements && { first = queue(0); first != null } &&
          first.getDelay(NANOSECONDS) <= 0
        }) {
          c.add(first) // In this order, in case add() throws.

          finishPoll(first)
          n += 1
        }
        n
      } finally lock.unlock()
    }

    override def toArray(): Array[AnyRef] = {
      val lock = this.lock
      lock.lock()
      try util.Arrays.copyOf(queue, _size, classOf[Array[AnyRef]])
      finally lock.unlock()
    }

    @SuppressWarnings(Array("unchecked"))
    override def toArray[T <: AnyRef](a: Array[T]): Array[T] = {
      val lock = this.lock
      lock.lock()
      try {
        if (a.length < _size)
          return util.Arrays
            .copyOf(queue, _size)
            .asInstanceOf[Array[T]]
        System.arraycopy(queue, 0, a, 0, _size)
        if (a.length > _size) a(_size) = null.asInstanceOf[T]
        a
      } finally lock.unlock()
    }
    override def iterator(): util.Iterator[Runnable] = {
      val lock = this.lock
      lock.lock()
      try
        new Itr(util.Arrays.copyOf(queue, _size))
      finally lock.unlock()
    }

    /** Snapshot iterator that works off copy of underlying q array.
     */
    private[concurrent] class Itr private[concurrent] (
        val array: Array[RunnableScheduledFuture[AnyRef]]
    ) extends util.Iterator[Runnable] {

      // index of next element to return; initially 0
      private[concurrent] var cursor = 0

      // index of last element returned; -1 if no such
      private[concurrent] var lastRet = -1

      override def hasNext(): Boolean = cursor < array.length

      override def next(): Runnable = {
        if (cursor >= array.length) throw new NoSuchElementException
        lastRet = cursor
        cursor += 1
        array(lastRet)
      }

      override def remove(): Unit = {
        if (lastRet < 0) throw new IllegalStateException
        DelayedWorkQueue.this.remove(array(lastRet))
        lastRet = -1
      }
    }
  }
}

class ScheduledThreadPoolExecutor(
    corePoolSize: Int,
    threadFactory: ThreadFactory,
    handler: RejectedExecutionHandler
) extends ThreadPoolExecutor(
      corePoolSize,
      Integer.MAX_VALUE,
      ScheduledThreadPoolExecutor.DEFAULT_KEEPALIVE_MILLIS,
      MILLISECONDS,
      new ScheduledThreadPoolExecutor.DelayedWorkQueue,
      threadFactory,
      handler
    )
    with ScheduledExecutorService {

  /** False if should cancel/suppress periodic tasks on shutdown.
   */
  private var continueExistingPeriodicTasksAfterShutdown = false

  /** False if should cancel non-periodic not-yet-expired tasks on shutdown.
   */
  private var executeExistingDelayedTasksAfterShutdown = true

  /** True if ScheduledFutureTask.cancel should remove from queue.
   */
  private[concurrent] var removeOnCancel = false

  private sealed trait ScheduledFutureTask[V <: AnyRef]
      extends RunnableScheduledFuture[V] { self: FutureTask[V] =>

    /** The nanoTime-based time when the task is enabled to execute. */
    protected var time: Long

    /** Period for repeating tasks, in nanoseconds. A positive value indicates
     *  fixed-rate execution. A negative value indicates fixed-delay execution.
     *  A value of 0 indicates a non-repeating (one-shot) task.
     */
    protected var period: Long

    /** Sequence number to break ties FIFO */
    protected def sequenceNumber: Long

    /** The actual task to be re-enqueued by reExecutePeriodic */
    private[concurrent] var outerTask: RunnableScheduledFuture[V] = this

    /** Index into delay queue, to support faster cancellation.
     */
    private[concurrent] var heapIndex: Int = 0

    override def getDelay(unit: TimeUnit): Long =
      unit.convert(time - System.nanoTime(), NANOSECONDS)
    override def compareTo(other: Delayed): Int = {
      if (other eq this) { // compare zero if same object
        return 0
      }
      if (other
            .isInstanceOf[ScheduledFutureTask[_]]) {
        val x =
          other.asInstanceOf[ScheduledFutureTask[_]]
        val diff = time - x.time
        if (diff < 0) return -1
        else if (diff > 0) return 1
        else if (sequenceNumber < x.sequenceNumber) return -1
        else return 1
      }
      val diff = getDelay(NANOSECONDS) - other.getDelay(NANOSECONDS)
      if (diff < 0) -1
      else if (diff > 0) 1
      else 0
    }

    /** Returns {@code true} if this is a periodic (not a one-shot) action.
     *
     *  @return
     *    {@code true} if periodic
     */
    override def isPeriodic(): Boolean = period != 0

    /** Sets the next time to run for a periodic task.
     */
    protected def setNextRunTime(): Unit = {
      val p = period
      if (p > 0) time += p
      else time = triggerTime(-p)
    }

  }

  private class ScheduledFutureRunableTask[V <: AnyRef](
      runnable: Runnable,
      result: V,
      protected var time: Long,
      protected var period: Long,
      protected val sequenceNumber: Long
  ) extends FutureTask(runnable, result)
      with ScheduledFutureTask[V] {
    def this(
        runnable: Runnable,
        result: V,
        time: Long,
        sequenceNumber: Long
    ) = this(
      runnable,
      result,
      time = time,
      period = 0,
      sequenceNumber = sequenceNumber
    )

    override def cancel(mayInterruptIfRunning: Boolean): Boolean = {
      // The racy read of heapIndex below is benign:
      // if heapIndex < 0, then OOTA guarantees that we have surely
      // been removed; else we recheck under lock in remove()
      val cancelled = super.cancel(mayInterruptIfRunning)
      if (cancelled && removeOnCancel && heapIndex >= 0) remove(this)
      cancelled
    }

    /** Overrides FutureTask version so as to reset/requeue if periodic.
     */
    override def run(): Unit = {
      if (!canRunInCurrentRunState(this)) cancel(false)
      else if (!isPeriodic()) super.run()
      else if (runAndReset()) {
        setNextRunTime()
        reExecutePeriodic(outerTask)
      }
    }
  }

  private class ScheduledFutureCallableTask[V <: AnyRef](
      callable: Callable[V],
      protected var time: Long,
      protected val sequenceNumber: Long
  ) extends FutureTask(callable)
      with ScheduledFutureTask[V] {
    protected var period: Long = 0

    override def cancel(mayInterruptIfRunning: Boolean): Boolean = {
      // The racy read of heapIndex below is benign:
      // if heapIndex < 0, then OOTA guarantees that we have surely
      // been removed; else we recheck under lock in remove()
      val cancelled = super.cancel(mayInterruptIfRunning)
      if (cancelled && removeOnCancel && heapIndex >= 0) remove(this)
      cancelled
    }

    /** Overrides FutureTask version so as to reset/requeue if periodic.
     */
    override def run(): Unit = {
      if (!canRunInCurrentRunState(this)) cancel(false)
      else if (!isPeriodic()) super.run()
      else if (runAndReset()) {
        setNextRunTime()
        reExecutePeriodic(outerTask)
      }
    }
  }

  /** Returns true if can run a task given current run state and
   *  run-after-shutdown parameters.
   */
  private[concurrent] def canRunInCurrentRunState(
      task: RunnableScheduledFuture[_]
  ): Boolean = {
    if (!isShutdown()) return true
    if (isStopped()) return false
    if (task.isPeriodic()) continueExistingPeriodicTasksAfterShutdown
    else
      executeExistingDelayedTasksAfterShutdown ||
        task.getDelay(NANOSECONDS) <= 0
  }

  /** Main execution method for delayed or periodic tasks. If pool is shut down,
   *  rejects the task. Otherwise adds task to queue and starts a thread, if
   *  necessary, to run it. (We cannot prestart the thread to run the task
   *  because the task (probably) shouldn't be run yet.) If the pool is shut
   *  down while the task is being added, cancel and remove it if required by
   *  state and run-after-shutdown parameters.
   *
   *  @param task
   *    the task
   */
  private def delayedExecute(task: RunnableScheduledFuture[_]): Unit = {
    if (isShutdown()) reject(task)
    else {
      super.getQueue().add(task)
      if (!canRunInCurrentRunState(task) && remove(task)) task.cancel(false)
      else ensurePrestart()
    }
  }

  /** Requeues a periodic task unless current run state precludes it. Same idea
   *  as delayedExecute except drops task rather than rejecting.
   *
   *  @param task
   *    the task
   */
  private[concurrent] def reExecutePeriodic(
      task: RunnableScheduledFuture[_]
  ): Unit = {
    if (canRunInCurrentRunState(task)) {
      super.getQueue().add(task)
      if (canRunInCurrentRunState(task) || !remove(task)) {
        ensurePrestart()
        return
      }
    }
    task.cancel(false)
  }

  /** Cancels and clears the queue of all tasks that should not be run due to
   *  shutdown policy. Invoked within super.shutdown.
   */
  override private[concurrent] def onShutdown(): Unit = {
    val q = super.getQueue()
    val keepDelayed = getExecuteExistingDelayedTasksAfterShutdownPolicy()
    val keepPeriodic = getContinueExistingPeriodicTasksAfterShutdownPolicy()
    // Traverse snapshot to avoid iterator exceptions
    // TODO: implement and use efficient removeIf
    // super.getQueue().removeIf(...);
    for (e <- q.toArray()) {
      e match {
        case t: RunnableScheduledFuture[_] =>
          def check =
            if (t.isPeriodic()) !keepPeriodic
            else !keepDelayed && t.getDelay(NANOSECONDS) > 0
          if (check || t.isCancelled()) { // also remove if already cancelled
            if (q.remove(t)) t.cancel(false)
          }

        case _ => ()
      }
    }
    tryTerminate()
  }

  /** Modifies or replaces the task used to execute a runnable. This method can
   *  be used to override the concrete class used for managing internal tasks.
   *  The default implementation simply returns the given task.
   *
   *  @param runnable
   *    the submitted Runnable
   *  @param task
   *    the task created to execute the runnable
   *  @param <V>
   *    the type of the task's result
   *  @return
   *    a task that can execute the runnable
   *  @since 1.6
   */
  protected def decorateTask[V](
      runnable: Runnable,
      task: RunnableScheduledFuture[V]
  ): RunnableScheduledFuture[V] = task

  /** Modifies or replaces the task used to execute a callable. This method can
   *  be used to override the concrete class used for managing internal tasks.
   *  The default implementation simply returns the given task.
   *
   *  @param callable
   *    the submitted Callable
   *  @param task
   *    the task created to execute the callable
   *  @param <V>
   *    the type of the task's result
   *  @return
   *    a task that can execute the callable
   *  @since 1.6
   */
  protected def decorateTask[V](
      callable: Callable[V],
      task: RunnableScheduledFuture[V]
  ): RunnableScheduledFuture[V] = task

  /** Creates a new {@code ScheduledThreadPoolExecutor} with the given core pool
   *  size.
   *
   *  @param corePoolSize
   *    the number of threads to keep in the pool, even if they are idle, unless
   *    {@code allowCoreThreadTimeOut} is set
   *  @throws IllegalArgumentException
   *    if {@code corePoolSize < 0}
   */
  def this(corePoolSize: Int) = this(
    corePoolSize,
    Executors.defaultThreadFactory(),
    ThreadPoolExecutor.defaultHandler
  )

  /** Creates a new {@code ScheduledThreadPoolExecutor} with the given initial
   *  parameters.
   *
   *  @param corePoolSize
   *    the number of threads to keep in the pool, even if they are idle, unless
   *    {@code allowCoreThreadTimeOut} is set
   *  @param threadFactory
   *    the factory to use when the executor creates a new thread
   *  @throws IllegalArgumentException
   *    if {@code corePoolSize < 0}
   *  @throws NullPointerException
   *    if {@code threadFactory} is null
   */
  def this(corePoolSize: Int, threadFactory: ThreadFactory) =
    this(corePoolSize, threadFactory, ThreadPoolExecutor.defaultHandler)

  /** Creates a new {@code ScheduledThreadPoolExecutor} with the given initial
   *  parameters.
   *
   *  @param corePoolSize
   *    the number of threads to keep in the pool, even if they are idle, unless
   *    {@code allowCoreThreadTimeOut} is set
   *  @param handler
   *    the handler to use when execution is blocked because the thread bounds
   *    and queue capacities are reached
   *  @throws IllegalArgumentException
   *    if {@code corePoolSize < 0}
   *  @throws NullPointerException
   *    if {@code handler} is null
   */
  def this(corePoolSize: Int, handler: RejectedExecutionHandler) =
    this(corePoolSize, Executors.defaultThreadFactory(), handler)

  /** Returns the nanoTime-based trigger time of a delayed action.
   */
  private def triggerTime(delay: Long, unit: TimeUnit): Long = triggerTime(
    unit.toNanos(
      if (delay < 0) 0
      else delay
    )
  )
  private[concurrent] def triggerTime(delay: Long): Long =
    System.nanoTime() + (if (delay < (java.lang.Long.MAX_VALUE >> 1)) delay
                         else overflowFree(delay))

  /** Constrains the values of all delays in the queue to be within
   *  Long.MAX_VALUE of each other, to avoid overflow in compareTo. This may
   *  occur if a task is eligible to be dequeued, but has not yet been, while
   *  some other task is added with a delay of Long.MAX_VALUE.
   */
  private def overflowFree(delay: Long): Long = {
    val head = super.getQueue().peek().asInstanceOf[Delayed]
    if (head != null) {
      val headDelay = head.getDelay(NANOSECONDS)
      if (headDelay < 0 && (delay - headDelay < 0))
        return java.lang.Long.MAX_VALUE + headDelay
    }
    delay
  }

  /** @throws RejectedExecutionException
   *    {@inheritDoc}
   *  @throws NullPointerException
   *    {@inheritDoc}
   */
  override def schedule(
      command: Runnable,
      delay: Long,
      unit: TimeUnit
  ): ScheduledFuture[AnyRef] = {
    if (command == null || unit == null) throw new NullPointerException
    val t = decorateTask(
      command,
      new ScheduledFutureRunableTask(
        command,
        null: AnyRef,
        triggerTime(delay, unit),
        ScheduledThreadPoolExecutor.sequencer.getAndIncrement()
      )
    )
    delayedExecute(t)
    t
  }

  override def schedule[V <: AnyRef](
      callable: Callable[V],
      delay: Long,
      unit: TimeUnit
  ): ScheduledFuture[V] = {
    if (callable == null || unit == null) throw new NullPointerException
    val t = decorateTask(
      callable,
      new ScheduledFutureCallableTask[V](
        callable,
        triggerTime(delay, unit),
        ScheduledThreadPoolExecutor.sequencer.getAndIncrement()
      )
    )
    delayedExecute(t)
    t
  }

  /** Submits a periodic action that becomes enabled first after the given
   *  initial delay, and subsequently with the given period; that is, executions
   *  will commence after {@code initialDelay}, then {@code initialDelay +
   *  period}, then {@code initialDelay + 2 * period}, and so on.
   *
   *  <p>The sequence of task executions continues indefinitely until one of the
   *  following exceptional completions occur: <ul> <li>The task is {@linkplain
   *  Future#cancel explicitly cancelled} via the returned future. <li>Method
   *  {@link #shutdown} is called and the {@linkplain
   *  #getContinueExistingPeriodicTasksAfterShutdownPolicy policy on whether to
   *  continue after shutdown} is not set true, or method {@link #shutdownNow}
   *  is called; also resulting in task cancellation. <li>An execution of the
   *  task throws an exception. In this case calling {@link Future#get() get} on
   *  the returned future will throw {@link ExecutionException}, holding the
   *  exception as its cause. </ul> Subsequent executions are suppressed.
   *  Subsequent calls to {@link Future#isDone isDone()} on the returned future
   *  will return {@code true}.
   *
   *  <p>If any execution of this task takes longer than its period, then
   *  subsequent executions may start late, but will not concurrently execute.
   *
   *  @throws RejectedExecutionException
   *    {@inheritDoc}
   *  @throws NullPointerException
   *    {@inheritDoc}
   *  @throws IllegalArgumentException
   *    {@inheritDoc}
   */
  override def scheduleAtFixedRate(
      command: Runnable,
      initialDelay: Long,
      period: Long,
      unit: TimeUnit
  ): ScheduledFuture[AnyRef] = {
    if (command == null || unit == null) throw new NullPointerException
    if (period <= 0L) throw new IllegalArgumentException
    val sft = new ScheduledFutureRunableTask(
      command,
      null: AnyRef,
      triggerTime(initialDelay, unit),
      unit.toNanos(period),
      ScheduledThreadPoolExecutor.sequencer.getAndIncrement()
    )
    val t = decorateTask(command, sft)
    sft.outerTask = t
    delayedExecute(t)
    t
  }

  /** Submits a periodic action that becomes enabled first after the given
   *  initial delay, and subsequently with the given delay between the
   *  termination of one execution and the commencement of the next.
   *
   *  <p>The sequence of task executions continues indefinitely until one of the
   *  following exceptional completions occur: <ul> <li>The task is {@linkplain
   *  Future#cancel explicitly cancelled} via the returned future. <li>Method
   *  {@link #shutdown} is called and the {@linkplain
   *  #getContinueExistingPeriodicTasksAfterShutdownPolicy policy on whether to
   *  continue after shutdown} is not set true, or method {@link #shutdownNow}
   *  is called; also resulting in task cancellation. <li>An execution of the
   *  task throws an exception. In this case calling {@link Future#get() get} on
   *  the returned future will throw {@link ExecutionException}, holding the
   *  exception as its cause. </ul> Subsequent executions are suppressed.
   *  Subsequent calls to {@link Future#isDone isDone()} on the returned future
   *  will return {@code true}.
   *
   *  @throws RejectedExecutionException
   *    {@inheritDoc}
   *  @throws NullPointerException
   *    {@inheritDoc}
   *  @throws IllegalArgumentException
   *    {@inheritDoc}
   */
  override def scheduleWithFixedDelay(
      command: Runnable,
      initialDelay: Long,
      delay: Long,
      unit: TimeUnit
  ): ScheduledFuture[AnyRef] = {
    if (command == null || unit == null) throw new NullPointerException
    if (delay <= 0L) throw new IllegalArgumentException
    val sft = new ScheduledFutureRunableTask(
      command,
      null: AnyRef,
      triggerTime(initialDelay, unit),
      -unit.toNanos(delay),
      ScheduledThreadPoolExecutor.sequencer.getAndIncrement()
    )
    val t = decorateTask(command, sft)
    sft.outerTask = t
    delayedExecute(t)
    t
  }

  /** Executes {@code command} with zero required delay. This has effect
   *  equivalent to {@link #schedule(Runnable,long,TimeUnit) schedule(command,
   *  0, anyUnit)}. Note that inspections of the queue and of the list returned
   *  by {@code shutdownNow} will access the zero-delayed {@link
   *  ScheduledFuture}, not the {@code command} itself.
   *
   *  <p>A consequence of the use of {@code ScheduledFuture} objects is that
   *  {@link ThreadPoolExecutor#afterExecute afterExecute} is always called with
   *  a null second {@code Throwable} argument, even if the {@code command}
   *  terminated abruptly. Instead, the {@code Throwable} thrown by such a task
   *  can be obtained via {@link Future#get}.
   *
   *  @throws RejectedExecutionException
   *    at discretion of {@code RejectedExecutionHandler}, if the task cannot be
   *    accepted for execution because the executor has been shut down
   *  @throws NullPointerException
   *    {@inheritDoc}
   */
  override def execute(command: Runnable): Unit = {
    schedule(command, 0, NANOSECONDS)
  }

  override def submit(task: Runnable): Future[_] =
    schedule(task, 0, NANOSECONDS)

  override def submit[T <: AnyRef](task: Runnable, result: T): Future[T] =
    schedule(Executors.callable(task, result), 0L, NANOSECONDS)

  override def submit[T <: AnyRef](task: Callable[T]): Future[T] =
    schedule(task, 0, NANOSECONDS)

  /** Sets the policy on whether to continue executing existing periodic tasks
   *  even when this executor has been {@code shutdown}. In this case,
   *  executions will continue until {@code shutdownNow} or the policy is set to
   *  {@code false} when already shutdown. This value is by default {@code
   *  false}.
   *
   *  @param value
   *    if {@code true}, continue after shutdown, else don't
   *  @see
   *    #getContinueExistingPeriodicTasksAfterShutdownPolicy
   */
  def setContinueExistingPeriodicTasksAfterShutdownPolicy(
      value: Boolean
  ): Unit = {
    continueExistingPeriodicTasksAfterShutdown = value
    if (!value && isShutdown()) onShutdown()
  }

  /** Gets the policy on whether to continue executing existing periodic tasks
   *  even when this executor has been {@code shutdown}. In this case,
   *  executions will continue until {@code shutdownNow} or the policy is set to
   *  {@code false} when already shutdown. This value is by default {@code
   *  false}.
   *
   *  @return
   *    {@code true} if will continue after shutdown
   *  @see
   *    #setContinueExistingPeriodicTasksAfterShutdownPolicy
   */
  def getContinueExistingPeriodicTasksAfterShutdownPolicy(): Boolean =
    continueExistingPeriodicTasksAfterShutdown

  /** Sets the policy on whether to execute existing delayed tasks even when
   *  this executor has been {@code shutdown}. In this case, these tasks will
   *  only terminate upon {@code shutdownNow}, or after setting the policy to
   *  {@code false} when already shutdown. This value is by default {@code
   *  true}.
   *
   *  @param value
   *    if {@code true}, execute after shutdown, else don't
   *  @see
   *    #getExecuteExistingDelayedTasksAfterShutdownPolicy
   */
  def setExecuteExistingDelayedTasksAfterShutdownPolicy(
      value: Boolean
  ): Unit = {
    executeExistingDelayedTasksAfterShutdown = value
    if (!value && isShutdown()) onShutdown()
  }

  /** Gets the policy on whether to execute existing delayed tasks even when
   *  this executor has been {@code shutdown}. In this case, these tasks will
   *  only terminate upon {@code shutdownNow}, or after setting the policy to
   *  {@code false} when already shutdown. This value is by default {@code
   *  true}.
   *
   *  @return
   *    {@code true} if will execute after shutdown
   *  @see
   *    #setExecuteExistingDelayedTasksAfterShutdownPolicy
   */
  def getExecuteExistingDelayedTasksAfterShutdownPolicy(): Boolean =
    executeExistingDelayedTasksAfterShutdown

  /** Sets the policy on whether cancelled tasks should be immediately removed
   *  from the work queue at time of cancellation. This value is by default
   *  {@code false}.
   *
   *  @param value
   *    if {@code true}, remove on cancellation, else don't
   *  @see
   *    #getRemoveOnCancelPolicy
   *  @since 1.7
   */
  def setRemoveOnCancelPolicy(value: Boolean): Unit = removeOnCancel = value

  /** Gets the policy on whether cancelled tasks should be immediately removed
   *  from the work queue at time of cancellation. This value is by default
   *  {@code false}.
   *
   *  @return
   *    {@code true} if cancelled tasks are immediately removed from the queue
   *  @see
   *    #setRemoveOnCancelPolicy
   *  @since 1.7
   */
  def getRemoveOnCancelPolicy(): Boolean = removeOnCancel

  /** Initiates an orderly shutdown in which previously submitted tasks are
   *  executed, but no new tasks will be accepted. Invocation has no additional
   *  effect if already shut down.
   *
   *  <p>This method does not wait for previously submitted tasks to complete
   *  execution. Use {@link #awaitTermination awaitTermination} to do that.
   *
   *  <p>If the {@code ExecuteExistingDelayedTasksAfterShutdownPolicy} has been
   *  set {@code false}, existing delayed tasks whose delays have not yet
   *  elapsed are cancelled. And unless the {@code
   *  ContinueExistingPeriodicTasksAfterShutdownPolicy} has been set {@code
   *  true}, future executions of existing periodic tasks will be cancelled.
   *
   *  @throws SecurityException
   *    {@inheritDoc}
   */
  override def shutdown(): Unit = super.shutdown()

  /** Attempts to stop all actively executing tasks, halts the processing of
   *  waiting tasks, and returns a list of the tasks that were awaiting
   *  execution. These tasks are drained (removed) from the task queue upon
   *  return from this method.
   *
   *  <p>This method does not wait for actively executing tasks to terminate.
   *  Use {@link #awaitTermination awaitTermination} to do that.
   *
   *  <p>There are no guarantees beyond best-effort attempts to stop processing
   *  actively executing tasks. This implementation interrupts tasks via {@link
   *  Thread#interrupt}; any task that fails to respond to interrupts may never
   *  terminate.
   *
   *  @return
   *    list of tasks that never commenced execution. Each element of this list
   *    is a {@link ScheduledFuture}. For tasks submitted via one of the {@code
   *    schedule} methods, the element will be identical to the returned {@code
   *    ScheduledFuture}. For tasks submitted using {@link #execute execute},
   *    the element will be a zero-delay {@code ScheduledFuture}.
   *  @throws SecurityException
   *    {@inheritDoc}
   */
  override def shutdownNow(): List[Runnable] = super.shutdownNow()

  /** Returns the task queue used by this executor. Access to the task queue is
   *  intended primarily for debugging and monitoring. This queue may be in
   *  active use. Retrieving the task queue does not prevent queued tasks from
   *  executing.
   *
   *  <p>Each element of this queue is a {@link ScheduledFuture}. For tasks
   *  submitted via one of the {@code schedule} methods, the element will be
   *  identical to the returned {@code ScheduledFuture}. For tasks submitted
   *  using {@link #execute execute}, the element will be a zero-delay {@code
   *  ScheduledFuture}.
   *
   *  <p>Iteration over this queue is <em>not</em> guaranteed to traverse tasks
   *  in the order in which they will execute.
   *
   *  @return
   *    the task queue
   */
  override def getQueue(): BlockingQueue[Runnable] = super.getQueue()
}
