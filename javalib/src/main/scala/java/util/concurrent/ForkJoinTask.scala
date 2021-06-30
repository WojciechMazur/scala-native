/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.List;
import java.util.RandomAccess;
import java.util.concurrent.locks.LockSupport;
import scala.scalanative.annotation.stub
import scala.annotation.tailrec
import java.util.concurrent.atomic.{AtomicReference, AtomicInteger}

/**
 * Abstract base class for tasks that run within a {@link ForkJoinPool}.
 * A {@code ForkJoinTask} is a thread-like entity that is much
 * lighter weight than a normal thread.  Huge numbers of tasks and
 * subtasks may be hosted by a small number of actual threads in a
 * ForkJoinPool, at the price of some usage limitations.
 *
 * <p>A "main" {@code ForkJoinTask} begins execution when it is
 * explicitly submitted to a {@link ForkJoinPool}, or, if not already
 * engaged in a ForkJoin computation, commenced in the {@link
 * ForkJoinPool#commonPool()} via {@link #fork}, {@link #invoke}, or
 * related methods.  Once started, it will usually in turn start other
 * subtasks.  As indicated by the name of this class, many programs
 * using {@code ForkJoinTask} employ only methods {@link #fork} and
 * {@link #join}, or derivatives such as {@link
 * #invokeAll(ForkJoinTask...) invokeAll}.  However, this class also
 * provides a number of other methods that can come into play in
 * advanced usages, as well as extension mechanics that allow support
 * of new forms of fork/join processing.
 *
 * <p>A {@code ForkJoinTask} is a lightweight form of {@link Future}.
 * The efficiency of {@code ForkJoinTask}s stems from a set of
 * restrictions (that are only partially statically enforceable)
 * reflecting their main use as computational tasks calculating pure
 * functions or operating on purely isolated objects.  The primary
 * coordination mechanisms are {@link #fork}, that arranges
 * asynchronous execution, and {@link #join}, that doesn't proceed
 * until the task's result has been computed.  Computations should
 * ideally avoid {@code synchronized} methods or blocks, and should
 * minimize other blocking synchronization apart from joining other
 * tasks or using synchronizers such as Phasers that are advertised to
 * cooperate with fork/join scheduling. Subdividable tasks should also
 * not perform blocking I/O, and should ideally access variables that
 * are completely independent of those accessed by other running
 * tasks. These guidelines are loosely enforced by not permitting
 * checked exceptions such as {@code IOExceptions} to be
 * thrown. However, computations may still encounter unchecked
 * exceptions, that are rethrown to callers attempting to join
 * them. These exceptions may additionally include {@link
 * RejectedExecutionException} stemming from internal resource
 * exhaustion, such as failure to allocate internal task
 * queues. Rethrown exceptions behave in the same way as regular
 * exceptions, but, when possible, contain stack traces (as displayed
 * for example using {@code ex.printStackTrace()}) of both the thread
 * that initiated the computation as well as the thread actually
 * encountering the exception; minimally only the latter.
 *
 * <p>It is possible to define and use ForkJoinTasks that may block,
 * but doing so requires three further considerations: (1) Completion
 * of few if any <em>other</em> tasks should be dependent on a task
 * that blocks on external synchronization or I/O. Event-style async
 * tasks that are never joined (for example, those subclassing {@link
 * CountedCompleter}) often fall into this category.  (2) To minimize
 * resource impact, tasks should be small; ideally performing only the
 * (possibly) blocking action. (3) Unless the {@link
 * ForkJoinPool.ManagedBlocker} API is used, or the number of possibly
 * blocked tasks is known to be less than the pool's {@link
 * ForkJoinPool#getParallelism} level, the pool cannot guarantee that
 * enough threads will be available to ensure progress or good
 * performance.
 *
 * <p>The primary method for awaiting completion and extracting
 * results of a task is {@link #join}, but there are several variants:
 * The {@link Future#get} methods support interruptible and/or timed
 * waits for completion and report results using {@code Future}
 * conventions. Method {@link #invoke} is semantically
 * equivalent to {@code fork(); join()} but always attempts to begin
 * execution in the current thread. The "<em>quiet</em>" forms of
 * these methods do not extract results or report exceptions. These
 * may be useful when a set of tasks are being executed, and you need
 * to delay processing of results or exceptions until all complete.
 * Method {@code invokeAll} (available in multiple versions)
 * performs the most common form of parallel invocation: forking a set
 * of tasks and joining them all.
 *
 * <p>In the most typical usages, a fork-join pair act like a call
 * (fork) and return (join) from a parallel recursive function. As is
 * the case with other forms of recursive calls, returns (joins)
 * should be performed innermost-first. For example, {@code a.fork();
 * b.fork(); b.join(); a.join();} is likely to be substantially more
 * efficient than joining {@code a} before {@code b}.
 *
 * <p>The execution status of tasks may be queried at several levels
 * of detail: {@link #isDone} is true if a task completed in any way
 * (including the case where a task was cancelled without executing);
 * {@link #isCompletedNormally} is true if a task completed without
 * cancellation or encountering an exception; {@link #isCancelled} is
 * true if the task was cancelled (in which case {@link #getException}
 * returns a {@link CancellationException}); and
 * {@link #isCompletedAbnormally} is true if a task was either
 * cancelled or encountered an exception, in which case {@link
 * #getException} will return either the encountered exception or
 * {@link CancellationException}.
 *
 * <p>The ForkJoinTask class is not usually directly subclassed.
 * Instead, you subclass one of the abstract classes that support a
 * particular style of fork/join processing, typically {@link
 * RecursiveAction} for most computations that do not return results,
 * {@link RecursiveTask} for those that do, and {@link
 * CountedCompleter} for those in which completed actions trigger
 * other actions.  Normally, a concrete ForkJoinTask subclass declares
 * fields comprising its parameters, established in a constructor, and
 * then defines a {@code compute} method that somehow uses the control
 * methods supplied by this base class.
 *
 * <p>Method {@link #join} and its variants are appropriate for use
 * only when completion dependencies are acyclic; that is, the
 * parallel computation can be described as a directed acyclic graph
 * (DAG). Otherwise, executions may encounter a form of deadlock as
 * tasks cyclically wait for each other.  However, this framework
 * supports other methods and techniques (for example the use of
 * {@link Phaser}, {@link #helpQuiesce}, and {@link #complete}) that
 * may be of use in constructing custom subclasses for problems that
 * are not statically structured as DAGs. To support such usages, a
 * ForkJoinTask may be atomically <em>tagged</em> with a {@code short}
 * value using {@link #setForkJoinTaskTag} or {@link
 * #compareAndSetForkJoinTaskTag} and checked using {@link
 * #getForkJoinTaskTag}. The ForkJoinTask implementation does not use
 * these {@code protected} methods or tags for any purpose, but they
 * may be of use in the construction of specialized subclasses.  For
 * example, parallel graph traversals can use the supplied methods to
 * avoid revisiting nodes/tasks that have already been processed.
 * (Method names for tagging are bulky in part to encourage definition
 * of methods that reflect their usage patterns.)
 *
 * <p>Most base support methods are {@code final}, to prevent
 * overriding of implementations that are intrinsically tied to the
 * underlying lightweight task scheduling framework.  Developers
 * creating new basic styles of fork/join processing should minimally
 * implement {@code protected} methods {@link #exec}, {@link
 * #setRawResult}, and {@link #getRawResult}, while also introducing
 * an abstract computational method that can be implemented in its
 * subclasses, possibly relying on other {@code protected} methods
 * provided by this class.
 *
 * <p>ForkJoinTasks should perform relatively small amounts of
 * computation. Large tasks should be split into smaller subtasks,
 * usually via recursive decomposition. As a very rough rule of thumb,
 * a task should perform more than 100 and less than 10000 basic
 * computational steps, and should avoid indefinite looping. If tasks
 * are too big, then parallelism cannot improve throughput. If too
 * small, then memory and internal task maintenance overhead may
 * overwhelm processing.
 *
 * <p>This class provides {@code adapt} methods for {@link Runnable}
 * and {@link Callable}, that may be of use when mixing execution of
 * {@code ForkJoinTasks} with other kinds of tasks. When all tasks are
 * of this form, consider using a pool constructed in <em>asyncMode</em>.
 *
 * <p>ForkJoinTasks are {@code Serializable}, which enables them to be
 * used in extensions such as remote execution frameworks. It is
 * sensible to serialize tasks only before or after, but not during,
 * execution. Serialization is not relied on during execution itself.
 *
 * @since 1.7
 * @author Doug Lea
 */
@SerialVersionUID(-7721805057305804111L)
abstract class ForkJoinTask[V] private[concurrent] ()
    extends Future[V]
    with Serializable {
  import ForkJoinTask._

  // Fields
  // @volatile
  private[concurrent] val status = new AtomicInteger(0)                // accessed directly by pool and workers
  private val aux                = new AtomicReference[Aux](null: Aux) // either waiters or thrown Exception

  // Support for atomic operations
  private def getAndBitwiseOrStatus(v: Int): Int = {
    status.valueRef.fetchXor(v)
  }

  /** Removes and unparks waiters */
  @tailrec
  private def signalWaiters(): Unit = {
    @tailrec
    def unparkThreads(a: Aux): Unit = {
      if (a != null) {
        if (a.thread != Thread.currentThread() && a.thread != null) {
          LockSupport.unpark(a.thread)
        }
        unparkThreads(a.next.get())
      }
    }

    aux.get() match {
      case a @ Aux(_, None) =>
        if (aux.compareAndSet(a, null)) unparkThreads(a)
        else signalWaiters()
      case _ => ()
    }
  }

  /**
   * Sets DONE status and wakes up threads waiting to join this task.
   * @return status on exit
   */
  private def setDone(): Int = {
    val result = getAndBitwiseOrStatus(DONE) | DONE;
    signalWaiters();
    result
  }

  /**
   * Sets ABNORMAL DONE status unless already done, and wakes up threads
   * waiting to join this task.
   * @return status on exit
   */
  private def trySetCancelled(): Int = {
    var s = status.get()
    while ({
      val lastStatus = status.get()
      s = lastStatus | DONE | ABNORMAL
      lastStatus >= 0 && !status.compareAndSet(lastStatus, s)
    }) ()
    signalWaiters()
    s
  }

  /**
   * Records exception and sets ABNORMAL THROWN DONE status unless
   * already done, and wakes up threads waiting to join this task.
   * If losing a race with setDone or trySetCancelled, the exception
   * may be recorded but not reported.
   *
   * @return status on exit
   */
  private[concurrent] final def trySetThrown(ex: Throwable): Int = {
    val h = Aux(Thread.currentThread(), Option(ex))
    @tailrec
    def setupLoop(installed: Boolean,
                  optAux: Option[Aux]): (Int, Option[Aux]) = {
      val s = status.get()
      if (s < 0) s -> optAux
      else {
        val a = aux.get()
        val isNowInstalled = {
          !installed &&
          (a == null || a.ex.isEmpty) &&
          aux.compareAndSet(a, h)
        }

        if (!isNowInstalled) setupLoop(false, optAux)
        else {
          val newStatus   = s | DONE | ABNORMAL | THROWN
          val replacedAux = Some(a) // list of waiters replaced by h
          if (status.compareAndSet(s, newStatus)) newStatus -> replacedAux
          else setupLoop(isNowInstalled, replacedAux)
        }
      }
    }

    var (s, currentAux) = setupLoop(installed = false, None)
    while (currentAux.isDefined) {
      val a = currentAux.get
      LockSupport.unpark(a.thread)
      currentAux = Option(a.next.get())
    }
    s
  }

  /**
   * Records exception unless already done. Overridable in subclasses.
   *
   * @return status on exit
   */
  private[concurrent] def trySetException(ex: Throwable): Int = {
    trySetThrown(ex);
  }

  /**
   * Unless done, calls exec and records status if completed, but
   * doesn't wait for completion otherwise.
   *
   * @return status on exit from this method
   */
  private[concurrent] final def doExec(): Int = {
    status.get() match {
      case s if s >= 0 =>
        try {
          val completed = exec()
          if (completed) setDone()
          else s
        } catch {
          case ex: Throwable =>
            trySetException(ex)
        }
      case s => s
    }
  }

  /**
   * Helps and/or waits for completion from join, get, or invoke;
   * called from either internal or external threads.
   *
   * @param pool if nonnull, known submitted pool, else assumes current pool
   * @param ran true if task known to have been exec'd
   * @param interruptible true if park interruptibly when external
   * @param timed true if use timed wait
   * @param nanos if timed, timeout value
   * @return ABNORMAL if interrupted, else status on exit
   */
  private def awaitDone(_pool: ForkJoinPool,
                        ran: Boolean,
                        interruptible: Boolean,
                        timed: Boolean,
                        nanos: Long): Int = {
    var s = 0
    val (currentPool, isInternal, queue) = Thread.currentThread() match {
      case worker: ForkJoinWorkerThread =>
        val p          = worker.getPool()
        val isInternal = _pool == null || _pool == p
        val queue =
          if (isInternal) Option(worker.workQueue)
          else None
        (p, isInternal, queue)

      case _ =>
        val p          = ForkJoinPool.commonPool()
        val isInternal = false
        val queue =
          if ((_pool == null || _pool == p) && p != null)
            Option(p.externalQueue())
          else None
        (p, isInternal, queue)
    }
    val pool = Option(_pool).getOrElse(currentPool)

    if (interruptible && Thread.interrupted()) return ABNORMAL
    s = status.get()
    if (s < 0) return s

    val deadline = {
      if (!timed) None
      else if (nanos <= 0L) return 0
      else Some(nanos + System.nanoTime())
    }

    val uncompensate = {
      queue match {
        case Some(q) if currentPool != null =>
          val canHelp = !timed || (currentPool.mode.get() & SMASK) == 0
          if (canHelp) {
            if (this.isInstanceOf[CountedCompleter[_]]) {
              s = currentPool.helpComplete(this, q, isInternal)
              if (s < 0) return s
            }
            def tryUnpushOrRemove() = {
              !isInternal && q.externalTryUnpush(this) ||
              q.tryRemove(this, isInternal)
            }
            def tryExec() = {
              s = doExec()
              s < 0
            }
            if (!ran && tryUnpushOrRemove() && tryExec()) return s
          }

          if (!isInternal) false
          else {
            s = currentPool.helpJoin(this, q, canHelp)
            if (s < 0) return s
            s == UNCOMPENSATE
          }
        case _ => false
      }
    }

    var interrupted          = false
    var queued               = false
    var auxNode: Option[Aux] = None
    // block until done or cancelled wait
    def block(): Unit = {
      var parked = false
      var fail   = false

      while ({
        s = status.get()
        s >= 0
      }) {
        def checkShouldFail() = {
          fail = pool != null && pool.mode.get() < 0
          fail
        }

        if (fail || checkShouldFail())
          status.compareAndSet(s, s | DONE | ABNORMAL) // try to cancel
        else if (parked && Thread.interrupted()) {
          if (interruptible) return s = ABNORMAL
          else interrupted = true
        } else if (queued) {
          deadline match {
            case Some(deadline) =>
              if (deadline > System.nanoTime())
                LockSupport.parkUntil(this, deadline)
              else return
            case None => LockSupport.park(this)
          }
          parked = true
        } else {
          auxNode match {
            case Some(node) =>
              Option(aux.get()) match {
                case Some(a) if a.ex.isDefined =>
                  Thread.onSpinWait() // exception in progress
                case nextNode =>
                  node.next.set(nextNode.orNull)
                  queued = aux.compareAndSet(nextNode.orNull, node)
              }
            case None =>
              try {
                auxNode = Some(new Aux(Thread.currentThread(), None))
              } catch {
                case ex: Throwable => // cannot create
                  fail = true;
              }
          }
        }
      }
    }

    @tailrec
    def cancel(): Unit = {
      @tailrec
      def tryCancel(a: Aux, trail: Aux): Boolean = {
        val next = a.next.get()
        if (auxNode.contains(a)) {
          if (trail != null)
            trail.next.compareAndSet(trail, next)
          else if (aux.compareAndSet(a, next)) false // cannot be re-encountered
          else true                                  // restart
        } else {
          next match {
            case null => false
            case next => tryCancel(a = next, trail = a)
          }
        }
      }

      Option(aux.get()) match {
        case Some(a @ Aux(_, None)) =>
          val shouldRestart = tryCancel(a, null)
          if (shouldRestart)
            cancel()
        case _ => ()
      }
    }

    block()
    if (pool != null && uncompensate) {
      pool.uncompensate()
    }

    if (queued) cancel()
    else {
      signalWaiters() // help clean or signal
      if (interrupted) Thread.currentThread().interrupt();
    }
    s
  }

  /**
   * Returns a rethrowable exception for this task, if available.
   * To provide accurate stack traces, if the exception was not
   * thrown by the current thread, we try to create a new exception
   * of the same type as the one thrown, but with the recorded
   * exception as its cause. If there is no such constructor, we
   * instead try to use a no-arg constructor, followed by initCause,
   * to the same effect. If none of these apply, or any fail due to
   * other exceptions, we return the recorded exception, which is
   * still correct, although it may contain a misleading stack
   * trace.
   *
   * @return the exception, or null if none
   */
  private def getThrowableException(): Throwable = {
    aux.get() match {
      case null => null
      case a =>
        a.ex
          .filter(_ => a.thread == Thread.currentThread()) // JSR-166 used reflection here to spawn new instance
          .orNull
    }
  }

  /**
   * Returns exception associated with the given status, or null if none.
   */
  private def getException(s: Int): Throwable = {
    def cancellation() = new CancellationException()
    if ((s & ABNORMAL) != 0) cancellation()
    else {
      val ex = getThrowableException()
      if ((s & THROWN) == 0 || (ex == null)) cancellation()
      else null
    }
  }

  /**
   * Throws exception associated with the given status, or
   * CancellationException if none recorded.
   */
  private def reportException(s: Int): Unit = {
    val exception = (s & THROWN) match {
      case 0 => null
      case _ => getThrowableException()
    }
    uncheckedThrow(exception)
  }

  /**
   * Throws exception for (timed or untimed) get, wrapping if
   * necessary in an ExecutionException.
   */
  private def reportExecutionException(s: Int) = {
    val exception: Throwable =
      if (s == ABNORMAL) new InterruptedException()
      else if (s >= 0) new TimeoutException()
      else if ((s & THROWN) != 0) {
        getThrowableException() match {
          case null => null
          case ex   => new ExecutionException(ex)
        }
      } else null
    uncheckedThrow(exception)
  }

  // public methods

  /**
   * Arranges to asynchronously execute this task in the pool the
   * current task is running in, if applicable, or using the {@link
   * ForkJoinPool#commonPool()} if not {@link #inForkJoinPool}.  While
   * it is not necessarily enforced, it is a usage error to fork a
   * task more than once unless it has completed and been
   * reinitialized.  Subsequent modifications to the state of this
   * task or any data it operates on are not necessarily
   * consistently observable by any thread other than the one
   * executing it unless preceded by a call to {@link #join} or
   * related methods, or a call to {@link #isDone} returning {@code
   * true}.
   *
   * @return {@code this}, to simplify usage
   */
  final def fork(): ForkJoinTask[V] = {
    Thread.currentThread() match {
      case worker: ForkJoinWorkerThread =>
        worker.workQueue.push(this, worker.pool)
      case _ =>
        ForkJoinPool.commonPool().externalPush(this)
    }
    this
  }

  /**
   * Returns the result of the computation when it
   * {@linkplain #isDone is done}.
   * This method differs from {@link #get()} in that abnormal
   * completion results in {@code RuntimeException} or {@code Error},
   * not {@code ExecutionException}, and that interrupts of the
   * calling thread do <em>not</em> cause the method to abruptly
   * return by throwing {@code InterruptedException}.
   *
   * @return the computed result
   */
  final def join(): V = {
    var s = status.get()
    if (s >= 0) {
      s = awaitDone(null, false, false, false, 0L)
    }
    if ((s & ABNORMAL) != 0) {
      reportException(s)
    }
    getRawResult()
  }

  /**
   * Commences performing this task, awaits its completion if
   * necessary, and returns its result, or throws an (unchecked)
   * {@code RuntimeException} or {@code Error} if the underlying
   * computation did so.
   *
   * @return the computed result
   */
  final def invoke(): V = {
    var s = doExec()
    if (s >= 0) {
      s = awaitDone(null, true, false, false, 0L)
    }
    if ((s & ABNORMAL) != 0) {
      reportException(s)
    }
    getRawResult()
  }

  /**
   * Attempts to cancel execution of this task. This attempt will
   * fail if the task has already completed or could not be
   * cancelled for some other reason. If successful, and this task
   * has not started when {@code cancel} is called, execution of
   * this task is suppressed. After this method returns
   * successfully, unless there is an intervening call to {@link
   * #reinitialize}, subsequent calls to {@link #isCancelled},
   * {@link #isDone()}, and {@code cancel} will return {@code true}
   * and calls to {@link #join} and related methods will result in
   * {@code CancellationException}.
   *
   * <p>This method may be overridden in subclasses, but if so, must
   * still ensure that these properties hold. In particular, the
   * {@code cancel} method itself must not throw exceptions.
   *
   * <p>This method is designed to be invoked by <em>other</em>
   * tasks. To terminate the current task, you can just return or
   * throw an unchecked exception from its computation method, or
   * invoke {@link #completeExceptionally(Throwable)}.
   *
   * @param mayInterruptIfRunning this value has no effect in the
   * default implementation because interrupts are not used to
   * control cancellation.
   *
   * @return {@code true} if this task is now cancelled
   */
  def cancel(mayInterruptIfRunning: Boolean): Boolean = {
    (trySetCancelled() & (ABNORMAL | THROWN)) == ABNORMAL;
  }

  final def isDone(): Boolean = {
    status.get() < 0;
  }

  final def isCancelled(): Boolean = {
    (status.get() & (ABNORMAL | THROWN)) == ABNORMAL;
  }

  /**
   * Returns {@code true} if this task threw an exception or was cancelled.
   *
   * @return {@code true} if this task threw an exception or was cancelled
   */
  final def isCompletedAbnormally(): Boolean = {
    (status.get() & ABNORMAL) != 0;
  }

  /**
   * Returns {@code true} if this task completed without throwing an
   * exception and was not cancelled.
   *
   * @return {@code true} if this task completed without throwing an
   * exception and was not cancelled
   */
  final def isCompletedNormally(): Boolean = {
    (status.get() & (DONE | ABNORMAL)) == DONE;
  }

  /**
   * Returns the exception thrown by the base computation, or a
   * {@code CancellationException} if cancelled, or {@code null} if
   * none or if the method has not yet completed.
   *
   * @return the exception, or {@code null} if none
   */
  final def getException(): Throwable = {
    getException(status.get());
  }

  /**
   * Completes this task abnormally, and if not already aborted or
   * cancelled, causes it to throw the given exception upon
   * {@code join} and related operations. This method may be used
   * to induce exceptions in asynchronous tasks, or to force
   * completion of tasks that would not otherwise complete.  Its use
   * in other situations is discouraged.  This method is
   * overridable, but overridden versions must invoke {@code super}
   * implementation to maintain guarantees.
   *
   * @param ex the exception to throw. If this exception is not a
   * {@code RuntimeException} or {@code Error}, the actual exception
   * thrown will be a {@code RuntimeException} with cause {@code ex}.
   */
  @stub()
  def completeExceptionally(ex: Throwable): Unit = {
    ???
    // trySetException((ex instanceof RuntimeException) ||
    //                 (ex instanceof Error) ? ex :
    //                 new RuntimeException(ex));
  }

  /**
   * Completes this task, and if not already aborted or cancelled,
   * returning the given value as the result of subsequent
   * invocations of {@code join} and related operations. This method
   * may be used to provide results for asynchronous tasks, or to
   * provide alternative handling for tasks that would not otherwise
   * complete normally. Its use in other situations is
   * discouraged. This method is overridable, but overridden
   * versions must invoke {@code super} implementation to maintain
   * guarantees.
   *
   * @param value the result value for this task
   */
  @stub()
  def complete(value: V): Unit = {
    ???

    // try {
    //     setRawResult(value);
    // } catch (Throwable rex) {
    //     trySetException(rex);
    //     return;
    // }
    // setDone();
  }

  /**
   * Completes this task normally without setting a value. The most
   * recent value established by {@link #setRawResult} (or {@code
   * null} by default) will be returned as the result of subsequent
   * invocations of {@code join} and related operations.
   *
   * @since 1.8
   */
  final def quietlyComplete(): Unit = {
    setDone();
  }

  /**
   * Waits if necessary for the computation to complete, and then
   * retrieves its result.
   *
   * @return the computed result
   * @throws CancellationException if the computation was cancelled
   * @throws ExecutionException if the computation threw an
   * exception
   * @throws InterruptedException if the current thread is not a
   * member of a ForkJoinPool and was interrupted while waiting
   */
  @throws[InterruptedException]
  @throws[ExecutionException]
  @stub()
  final def get(): V = {
    ???
    // int s = awaitDone(null, false, true, false, 0L);
    // if ((s & ABNORMAL) != 0)
    //     reportExecutionException(s);
    // return getRawResult();
  }

  /**
   * Waits if necessary for at most the given time for the computation
   * to complete, and then retrieves its result, if available.
   *
   * @param timeout the maximum time to wait
   * @param unit the time unit of the timeout argument
   * @return the computed result
   * @throws CancellationException if the computation was cancelled
   * @throws ExecutionException if the computation threw an
   * exception
   * @throws InterruptedException if the current thread is not a
   * member of a ForkJoinPool and was interrupted while waiting
   * @throws TimeoutException if the wait timed out
   */
  @throws[InterruptedException]
  @throws[ExecutionException]
  @throws[TimeoutException]
  @stub()
  final def get(timeout: Long, unit: TimeUnit): V = {
    ???
    // long nanos = unit.toNanos(timeout);
    // int s = awaitDone(null, false, true, true, nanos);
    // if (s >= 0 || (s & ABNORMAL) != 0)
    //     reportExecutionException(s);
    // return getRawResult();
  }

  /**
   * Joins this task, without returning its result or throwing its
   * exception. This method may be useful when processing
   * collections of tasks when some have been cancelled or otherwise
   * known to have aborted.
   */
  final def quietlyJoin(): Unit = {
    if (status.get() >= 0)
      awaitDone(null, false, false, false, 0L);
  }

  /**
   * Commences performing this task and awaits its completion if
   * necessary, without returning its result or throwing its
   * exception.
   */
  final def quietlyInvoke(): Unit = {
    if (doExec() >= 0)
      awaitDone(null, true, false, false, 0L);
  }

  // Versions of join/get for pool.invoke* methods that use external,
  // possibly-non-commonPool submits

  final def awaitPoolInvoke(pool: ForkJoinPool): Unit = {
    awaitDone(pool, false, false, false, 0L);
  }

  final def awaitPoolInvoke(pool: ForkJoinPool, nanos: Long) = {
    awaitDone(pool, false, true, true, nanos);
  }

  final def joinForPoolInvoke(pool: ForkJoinPool): V = {
    val s = awaitDone(pool, false, false, false, 0L)
    if ((s & ABNORMAL) != 0)
      reportException(s)
    getRawResult()
  }

  @throws[InterruptedException]
  @throws[ExecutionException]
  @stub()
  final def getForPoolInvoke(pool: ForkJoinPool): V = {
    ???
    // int s = awaitDone(pool, false, true, false, 0L);
    // if ((s & ABNORMAL) != 0)
    //     reportExecutionException(s);
    // return getRawResult();
  }

  @throws[InterruptedException]
  @throws[ExecutionException]
  @throws[TimeoutException]
  @stub()
  final def getForPoolInvoke(pool: ForkJoinPool, nanos: Long): V = {
    ???
    // int s = awaitDone(pool, false, true, true, nanos);
    // if (s >= 0 || (s & ABNORMAL) != 0)
    //     reportExecutionException(s);
    // return getRawResult();
  }

  /**
   * Resets the internal bookkeeping state of this task, allowing a
   * subsequent {@code fork}. This method allows repeated reuse of
   * this task, but only if reuse occurs when this task has either
   * never been forked, or has been forked, then completed and all
   * outstanding joins of this task have also completed. Effects
   * under any other usage conditions are not guaranteed.
   * This method may be useful when executing
   * pre-constructed trees of subtasks in loops.
   *
   * <p>Upon completion of this method, {@code isDone()} reports
   * {@code false}, and {@code getException()} reports {@code
   * null}. However, the value returned by {@code getRawResult} is
   * unaffected. To clear this value, you can invoke {@code
   * setRawResult(null)}.
   */
  def reinitialize(): Unit = {
    aux.set(null);
    status.set(0);
  }

  /**
   * Tries to unschedule this task for execution. This method will
   * typically (but is not guaranteed to) succeed if this task is
   * the most recently forked task by the current thread, and has
   * not commenced executing in another thread.  This method may be
   * useful when arranging alternative local processing of tasks
   * that could have been, but were not, stolen.
   *
   * @return {@code true} if unforked
   */
  def tryUnfork(): Boolean = {
    Thread.currentThread() match {
      case worker: ForkJoinWorkerThread =>
        val q = worker.workQueue
        q != null && q.tryUnpush(this)
      case _ =>
        val q = ForkJoinPool.commonQueue()
        q != null && q.externalTryUnpush(this)
    }
  }

  // Extension methods

  /**
   * Returns the result that would be returned by {@link #join}, even
   * if this task completed abnormally, or {@code null} if this task
   * is not known to have been completed.  This method is designed
   * to aid debugging, as well as to support extensions. Its use in
   * any other context is discouraged.
   *
   * @return the result, or {@code null} if not completed
   */
  def getRawResult(): V

  /**
   * Forces the given value to be returned as a result.  This method
   * is designed to support extensions, and should not in general be
   * called otherwise.
   *
   * @param value the value
   */
  protected def setRawResult(value: V): Unit

  /**
   * Immediately performs the base action of this task and returns
   * true if, upon return from this method, this task is guaranteed
   * to have completed. This method may return false otherwise, to
   * indicate that this task is not necessarily complete (or is not
   * known to be complete), for example in asynchronous actions that
   * require explicit invocations of completion methods. This method
   * may also throw an (unchecked) exception to indicate abnormal
   * exit. This method is designed to support extensions, and should
   * not in general be called otherwise.
   *
   * @return {@code true} if this task is known to have completed normally
   */
  protected def exec(): Boolean;

  // tag operations

  /**
   * Returns the tag for this task.
   *
   * @return the tag for this task
   * @since 1.8
   */
  final def getForkJoinTaskTag(): Short = {
    status.get().toShort;
  }

  /**
   * Atomically sets the tag value for this task and returns the old value.
   *
   * @param newValue the new tag value
   * @return the previous value of the tag
   * @since 1.8
   */
  @stub()
  final def setForkJoinTaskTag(newValue: Short): Short = {
    ???
    // for (int s;;) {
    //     if (status.compareAndSet(s = status, (s & ~SMASK) | (newValue & SMASK)))
    //         return (short)s;
    // }
  }

  /**
   * Atomically conditionally sets the tag value for this task.
   * Among other applications, tags can be used as visit markers
   * in tasks operating on graphs, as in methods that check: {@code
   * if (task.compareAndSetForkJoinTaskTag((short)0, (short)1))}
   * before processing, otherwise exiting because the node has
   * already been visited.
   *
   * @param expect the expected tag value
   * @param update the new tag value
   * @return {@code true} if successful; i.e., the current value was
   * equal to {@code expect} and was changed to {@code update}.
   * @since 1.8
   */
  @stub()
  final def compareAndSetForkJoinTaskTag(expect: Short,
                                         update: Short): Boolean = {
    ???
    // for (int s;;) {
    //     if ((short)(s = status) != expect)
    //         return false;
    //     if (status.compareAndSet(s, (s & ~SMASK) | (update & SMASK)))
    //         return true;
    // }
  }

  /**
   * Saves this task to a stream (that is, serializes it).
   *
   * @param s the stream
   * @throws java.io.IOException if an I/O error occurs
   * @serialData the current run status and the exception thrown
   * during execution, or {@code null} if none
   */
  @throws[java.io.IOException]
  @stub()
  private def writeObject(s: java.io.ObjectOutputStream): Unit = {
    ???
    // Aux a;
    // s.defaultWriteObject();
    // s.writeObject((a = aux) == null ? null : a.ex);
  }

  /**
   * Reconstitutes this task from a stream (that is, deserializes it).
   * @param s the stream
   * @throws ClassNotFoundException if the class of a serialized object
   *         could not be found
   * @throws java.io.IOException if an I/O error occurs
   */
  @throws[java.io.IOException]
  @throws[ClassNotFoundException]
  @stub()
  private def readObject(s: java.io.ObjectInputStream): Unit = {
    ???
    // s.defaultReadObject();
    // Object ex = s.readObject();
    // if (ex != null)
    //     trySetThrown((Throwable)ex);
  }

}

object ForkJoinTask {

  /*
   * See the internal documentation of class ForkJoinPool for a
   * general implementation overview.  ForkJoinTasks are mainly
   * responsible for maintaining their "status" field amidst relays
   * to methods in ForkJoinWorkerThread and ForkJoinPool.
   *
   * The methods of this class are more-or-less layered into
   * (1) basic status maintenance
   * (2) execution and awaiting completion
   * (3) user-level methods that additionally report results.
   * This is sometimes hard to see because this file orders exported
   * methods in a way that flows well in javadocs.
   *
   * Revision notes: The use of "Aux" field replaces previous
   * reliance on a table to hold exceptions and synchronized blocks
   * and monitors to wait for completion.
   */

  /**
   * Nodes for threads waiting for completion, or holding a thrown
   * exception (never both). Waiting threads prepend nodes
   * Treiber-stack-style.  Signallers detach and unpark
   * waiters. Cancelled waiters try to unsplice.
   */
  private[concurrent] final case class Aux(thread: Thread,
                                           ex: Option[Throwable]) {
    val next = new AtomicReference[Aux]()
  }

  /*
   * The status field holds bits packed into a single int to ensure
   * atomicity.  Status is initially zero, and takes on nonnegative
   * values until completed, upon which it holds (sign bit) DONE,
   * possibly with ABNORMAL (cancelled or exceptional) and THROWN
   * (in which case an exception has been stored). A value of
   * ABNORMAL without DONE signifies an interrupted wait.  These
   * control bits occupy only (some of) the upper half (16 bits) of
   * status field. The lower bits are used for user-defined tags.
   */
  private final val DONE         = 1 << 31; // must be negative
  private final val ABNORMAL     = 1 << 16;
  private final val THROWN       = 1 << 17;
  private final val SMASK        = 0xffff; // short bits for tags
  private final val UNCOMPENSATE = 1 << 16; // helpJoin return sentinel

  // Support for atomic operations
  private final var STATUS: VarHandle = _
  private final var AUX: VarHandle    = _

  private[concurrent] def isExceptionalStatus(s: Int): Boolean = { // needed by subclasses
    (s & THROWN) != 0;
  }

  /**
   * Cancels, ignoring any exceptions thrown by cancel.  Cancel is
   * spec'ed not to throw any exceptions, but if it does anyway, we
   * have no recourse, so guard against this case.
   */
  private[concurrent] final def cancelIgnoringExceptions(t: Future[_]): Unit = {
    if (t != null) {
      try {
        t.cancel(true);
      } catch {
        case _: Throwable => ()
      }
    }
  }

  /**
   * A version of "sneaky throw" to relay exceptions in other
   * contexts.
   */
  private[concurrent] def rethrow(ex: Throwable): Unit = {
    uncheckedThrow(ex.asInstanceOf[RuntimeException])
  }

  /**
   * The sneaky part of sneaky throw, relying on generics
   * limitations to evade compiler complaints about rethrowing
   * unchecked exceptions. If argument null, throws
   * CancellationException.
   */
  // @SuppressWarnings("unchecked")
  private[concurrent] def uncheckedThrow[T <: Throwable](t: Throwable): Unit = {
    t match {
      case null => throw new CancellationException()
      case _    => throw t.asInstanceOf[T]
    }
  }

  /**
   * Forks the given tasks, returning when {@code isDone} holds for
   * each task or an (unchecked) exception is encountered, in which
   * case the exception is rethrown. If more than one task
   * encounters an exception, then this method throws any one of
   * these exceptions. If any task encounters an exception, the
   * other may be cancelled. However, the execution status of
   * individual tasks is not guaranteed upon exceptional return. The
   * status of each task may be obtained using {@link
   * #getException()} and related methods to check if they have been
   * cancelled, completed normally or exceptionally, or left
   * unprocessed.
   *
   * @param t1 the first task
   * @param t2 the second task
   * @throws NullPointerException if any task is null
   */
  @stub()
  def invokeAll(t1: ForkJoinTask[_], t2: ForkJoinTask[_]): Unit = {
    ???
    // int s1, s2;
    // if (t1 == null || t2 == null)
    //     throw new NullPointerException();
    // t2.fork();
    // if ((s1 = t1.doExec()) >= 0)
    //     s1 = t1.awaitDone(null, true, false, false, 0L);
    // if ((s1 & ABNORMAL) != 0) {
    //     cancelIgnoringExceptions(t2);
    //     t1.reportException(s1);
    // }
    // else if (((s2 = t2.awaitDone(null, false, false, false, 0L)) & ABNORMAL) != 0)
    //     t2.reportException(s2);
  }

  /**
   * Forks the given tasks, returning when {@code isDone} holds for
   * each task or an (unchecked) exception is encountered, in which
   * case the exception is rethrown. If more than one task
   * encounters an exception, then this method throws any one of
   * these exceptions. If any task encounters an exception, others
   * may be cancelled. However, the execution status of individual
   * tasks is not guaranteed upon exceptional return. The status of
   * each task may be obtained using {@link #getException()} and
   * related methods to check if they have been cancelled, completed
   * normally or exceptionally, or left unprocessed.
   *
   * @param tasks the tasks
   * @throws NullPointerException if any task is null
   */
  @stub()
  def invokeAll(tasks: ForkJoinTask[_]*): Unit = {
    ???
    // Throwable ex = null;
    // int last = tasks.length - 1;
    // for (int i = last; i >= 0; --i) {
    //     ForkJoinTask[_] t;
    //     if ((t = tasks[i]) == null) {
    //         ex = new NullPointerException();
    //         break;
    //     }
    //     if (i == 0) {
    //         int s;
    //         if ((s = t.doExec()) >= 0)
    //             s = t.awaitDone(null, true, false, false, 0L);
    //         if ((s & ABNORMAL) != 0)
    //             ex = t.getException(s);
    //         break;
    //     }
    //     t.fork();
    // }
    // if (ex == null) {
    //     for (int i = 1; i <= last; ++i) {
    //         ForkJoinTask[_] t;
    //         if ((t = tasks[i]) != null) {
    //             int s;
    //             if ((s = t.status) >= 0)
    //                 s = t.awaitDone(null, false, false, false, 0L);
    //             if ((s & ABNORMAL) != 0 && (ex = t.getException(s)) != null)
    //                 break;
    //         }
    //     }
    // }
    // if (ex != null) {
    //     for (int i = 1; i <= last; ++i)
    //         cancelIgnoringExceptions(tasks[i]);
    //     rethrow(ex);
    // }
  }

  /**
   * Forks all tasks in the specified collection, returning when
   * {@code isDone} holds for each task or an (unchecked) exception
   * is encountered, in which case the exception is rethrown. If
   * more than one task encounters an exception, then this method
   * throws any one of these exceptions. If any task encounters an
   * exception, others may be cancelled. However, the execution
   * status of individual tasks is not guaranteed upon exceptional
   * return. The status of each task may be obtained using {@link
   * #getException()} and related methods to check if they have been
   * cancelled, completed normally or exceptionally, or left
   * unprocessed.
   *
   * @param tasks the collection of tasks
   * @param <T> the type of the values returned from the tasks
   * @return the tasks argument, to simplify usage
   * @throws NullPointerException if tasks or any element are null
   */
  @stub()
  def invokeAll[T <: ForkJoinTask[_]](tasks: Collection[T]): Collection[T] = {
    ???
    // if (!(tasks instanceof RandomAccess) || !(tasks instanceof List<?>)) {
    //     invokeAll(tasks.toArray(new ForkJoinTask[_][0]));
    //     return tasks;
    // }
    // @SuppressWarnings("unchecked")
    // List<? extends ForkJoinTask[_]> ts =
    //     (List<? extends ForkJoinTask[_]>) tasks;
    // Throwable ex = null;
    // int last = ts.size() - 1;  // nearly same as array version
    // for (int i = last; i >= 0; --i) {
    //     ForkJoinTask[_] t;
    //     if ((t = ts.get(i)) == null) {
    //         ex = new NullPointerException();
    //         break;
    //     }
    //     if (i == 0) {
    //         int s;
    //         if ((s = t.doExec()) >= 0)
    //             s = t.awaitDone(null, true, false, false, 0L);
    //         if ((s & ABNORMAL) != 0)
    //             ex = t.getException(s);
    //         break;
    //     }
    //     t.fork();
    // }
    // if (ex == null) {
    //     for (int i = 1; i <= last; ++i) {
    //         ForkJoinTask[_] t;
    //         if ((t = ts.get(i)) != null) {
    //             int s;
    //             if ((s = t.status) >= 0)
    //                 s = t.awaitDone(null, false, false, false, 0L);
    //             if ((s & ABNORMAL) != 0 && (ex = t.getException(s)) != null)
    //                 break;
    //         }
    //     }
    // }
    // if (ex != null) {
    //     for (int i = 1; i <= last; ++i)
    //         cancelIgnoringExceptions(ts.get(i));
    //     rethrow(ex);
    // }
    // return tasks;
  }

  /**
   * Possibly executes tasks until the pool hosting the current task
   * {@linkplain ForkJoinPool#isQuiescent is quiescent}.  This
   * method may be of use in designs in which many tasks are forked,
   * but none are explicitly joined, instead executing them until
   * all are processed.
   */
  @stub()
  def helpQuiesce(): Unit = {
    ???
    // Thread t; ForkJoinWorkerThread w; ForkJoinPool p;
    // if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread &&
    //     (p = (w = (ForkJoinWorkerThread)t).pool) != null)
    //     p.helpQuiescePool(w.workQueue, Long.MAX_VALUE, false);
    // else
    //     ForkJoinPool.common.externalHelpQuiescePool(Long.MAX_VALUE, false);
  }

  /**
   * Returns the pool hosting the current thread, or {@code null}
   * if the current thread is executing outside of any ForkJoinPool.
   *
   * <p>This method returns {@code null} if and only if {@link
   * #inForkJoinPool} returns {@code false}.
   *
   * @return the pool, or {@code null} if none
   */
  @stub()
  def getPool(): ForkJoinPool = {
    ???
    // Thread t;
    // return (((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
    //         ((ForkJoinWorkerThread) t).pool : null);
  }

  /**
   * Returns {@code true} if the current thread is a {@link
   * ForkJoinWorkerThread} executing as a ForkJoinPool computation.
   *
   * @return {@code true} if the current thread is a {@link
   * ForkJoinWorkerThread} executing as a ForkJoinPool computation,
   * or {@code false} otherwise
   */
  @stub()
  def inForkJoinPool(): Boolean = {
    ???
    // return Thread.currentThread() instanceof ForkJoinWorkerThread;
  }

  /**
   * Returns an estimate of the number of tasks that have been
   * forked by the current worker thread but not yet executed. This
   * value may be useful for heuristic decisions about whether to
   * fork other tasks.
   *
   * @return the number of tasks
   */
  @stub()
  def getQueuedTaskCount(): Int = {
    ???
    // Thread t; ForkJoinPool.WorkQueue q;
    // if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread)
    //     q = ((ForkJoinWorkerThread)t).workQueue;
    // else
    //     q = ForkJoinPool.commonQueue();
    // return (q == null) ? 0 : q.queueSize();
  }

  /**
   * Returns an estimate of how many more locally queued tasks are
   * held by the current worker thread than there are other worker
   * threads that might steal them, or zero if this thread is not
   * operating in a ForkJoinPool. This value may be useful for
   * heuristic decisions about whether to fork other tasks. In many
   * usages of ForkJoinTasks, at steady state, each worker should
   * aim to maintain a small constant surplus (for example, 3) of
   * tasks, and to process computations locally if this threshold is
   * exceeded.
   *
   * @return the surplus number of tasks, which may be negative
   */
  @stub()
  def getSurplusQueuedTaskCount(): Int = {
    return ForkJoinPool.getSurplusQueuedTaskCount();
  }

  /**
   * Returns, but does not unschedule or execute, a task queued by
   * the current thread but not yet executed, if one is immediately
   * available. There is no guarantee that this task will actually
   * be polled or executed next. Conversely, this method may return
   * null even if a task exists but cannot be accessed without
   * contention with other threads.  This method is designed
   * primarily to support extensions, and is unlikely to be useful
   * otherwise.
   *
   * @return the next task, or {@code null} if none are available
   */
  @stub()
  protected def peekNextLocalTask(): ForkJoinTask[_] = {
    ???
    // Thread t; ForkJoinPool.WorkQueue q;
    // if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread)
    //     q = ((ForkJoinWorkerThread)t).workQueue;
    // else
    //     q = ForkJoinPool.commonQueue();
    // return (q == null) ? null : q.peek();
  }

  /**
   * Unschedules and returns, without executing, the next task
   * queued by the current thread but not yet executed, if the
   * current thread is operating in a ForkJoinPool.  This method is
   * designed primarily to support extensions, and is unlikely to be
   * useful otherwise.
   *
   * @return the next task, or {@code null} if none are available
   */
  @stub()
  protected def pollNextLocalTask(): ForkJoinTask[_] = {
    ???
    // Thread t;
    // return (((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
    //         ((ForkJoinWorkerThread)t).workQueue.nextLocalTask() : null);
  }

  /**
   * If the current thread is operating in a ForkJoinPool,
   * unschedules and returns, without executing, the next task
   * queued by the current thread but not yet executed, if one is
   * available, or if not available, a task that was forked by some
   * other thread, if available. Availability may be transient, so a
   * {@code null} result does not necessarily imply quiescence of
   * the pool this task is operating in.  This method is designed
   * primarily to support extensions, and is unlikely to be useful
   * otherwise.
   *
   * @return a task, or {@code null} if none are available
   */
  @stub()
  protected def pollTask(): ForkJoinTask[_] = {
    ???
    // Thread t; ForkJoinWorkerThread w;
    // return (((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
    //         (w = (ForkJoinWorkerThread)t).pool.nextTaskFor(w.workQueue) :
    //         null);
  }

  /**
   * If the current thread is operating in a ForkJoinPool,
   * unschedules and returns, without executing, a task externally
   * submitted to the pool, if one is available. Availability may be
   * transient, so a {@code null} result does not necessarily imply
   * quiescence of the pool.  This method is designed primarily to
   * support extensions, and is unlikely to be useful otherwise.
   *
   * @return a task, or {@code null} if none are available
   * @since 9
   */
  protected def pollSubmission(): ForkJoinTask[_] = {
    Thread.currentThread() match {
      case t: ForkJoinWorkerThread => t.pool.pollSubmission()
      case _                       => null
    }
  }

  /**
   * Adapter for Runnables. This implements RunnableFuture
   * to be compliant with AbstractExecutorService constraints
   * when used in ForkJoinPool.
   */
  @SerialVersionUID(5232453952276885070L)
  private[concurrent] final class AdaptedRunnable[T](runnable: Runnable,
                                                     private var result: T)
      extends ForkJoinTask[T]
      with RunnableFuture[T] {
    if (runnable == null) {
      throw new NullPointerException()
    }

    def run(): Unit = invoke()

    protected def exec(): Boolean = {
      runnable.run()
      true
    }

    protected def setRawResult(value: T): Unit = result = value
    def getRawResult(): T                      = result

    override def toString(): String = {
      super.toString() + "[Wrapped task = " + runnable + "]";
    }
  }

  /**
   * Adapter for Runnables without results.
   */
  @SerialVersionUID(5232453952276885070L)
  private[concurrent] final class AdaptedRunnableAction(runnable: Runnable)
      extends ForkJoinTask[Unit]
      with RunnableFuture[Unit] {
    if (runnable == null) throw new NullPointerException()

    protected def exec(): Boolean = {
      runnable.run()
      true
    }

    def run(): Unit = invoke()

    def getRawResult(): Unit                      = ()
    protected def setRawResult(value: Unit): Unit = ()

    override def toString(): String =
      super.toString() + "[Wrapped task = " + runnable + "]"
  }

  /**
   * Adapter for Runnables in which failure forces worker exception.
   */
  @SerialVersionUID(5232453952276885070L)
  final class RunnableExecuteAction(runnable: Runnable)
      extends ForkJoinTask[Unit] {
    if (runnable == null)
      throw new NullPointerException()

    def getRawResult(): Unit        = ()
    def setRawResult(v: Unit): Unit = ()
    def exec(): Boolean = {
      runnable.run()
      true
    }

    override def trySetException(ex: Throwable): Int = {
      // if a handler, invoke it
      val s: Int                             = trySetThrown(ex)
      val t: Thread                          = Thread.currentThread()
      val h: Thread.UncaughtExceptionHandler = t.getUncaughtExceptionHandler()

      if (isExceptionalStatus(s) && h != null) {
        try {
          h.uncaughtException(t, ex)
        } catch {
          case _: Throwable => ()
        }
      }
      s
    }
  }

  /**
   * Adapter for Callables.
   */
  @SerialVersionUID(2838392045355241008L)
  final class AdaptedCallable[T](callable: Callable[T])
      extends ForkJoinTask[T]
      with RunnableFuture[T] {
    if (callable == null) throw new NullPointerException();

    private var result: T = _

    def getRawResult(): T                      = result
    protected def setRawResult(value: T): Unit = result = result
    protected def exec(): Boolean = {
      try {
        result = callable.call()
        true
      } catch {
        case ex: RuntimeException => throw ex
        case ex: Exception        => throw new RuntimeException(ex)
      }
    }

    def run(): Unit = invoke()
    override def toString(): String =
      super.toString() + "[Wrapped task = " + callable + "]";
  }

  @SerialVersionUID(2838392045355241008L)
  final class AdaptedInterruptibleCallable[T](callable: Callable[T])
      extends ForkJoinTask[T]
      with RunnableFuture[T] {
    if (callable == null) throw new NullPointerException()

    // private val Atomic
    private[concurrent] val runner = new AtomicReference[Thread]()
    private var result: T          = _

    def getRawResult(): T                      = result
    protected def setRawResult(value: T): Unit = result = value
    def run(): Unit                            = invoke()

    protected def exec(): Boolean = {
      Thread.interrupted()
      runner.set(Thread.currentThread())
      try {
        if (!isDone())
          result = callable.call()
        true
      } catch {
        case ex: RuntimeException => throw ex
        case ex: Exception        => throw new RuntimeException(ex)
      } finally {
        runner.set(null: Thread)
        Thread.interrupted()
      }
    }

    override def cancel(mayInterruptIfRunning: Boolean): Boolean = {
      val status = super.cancel(false)
      if (mayInterruptIfRunning) {
        runner.get() match {
          case null => ()
          case runner =>
            try {
              runner.interrupt()
            } catch {
              case _: Throwable => ()
            }
        }
      }
      status
    }

    override def toString(): String =
      super.toString() + "[Wrapped task = " + callable + "]";
  }

  /**
   * Returns a new {@code ForkJoinTask} that performs the {@code run}
   * method of the given {@code Runnable} as its action, and returns
   * a null result upon {@link #join}.
   *
   * @param runnable the runnable action
   * @return the task
   */
  @stub()
  def adapt(runnable: Runnable): ForkJoinTask[_] = {
    ???
    // return new AdaptedRunnableAction(runnable);
  }

  /**
   * Returns a new {@code ForkJoinTask} that performs the {@code run}
   * method of the given {@code Runnable} as its action, and returns
   * the given result upon {@link #join}.
   *
   * @param runnable the runnable action
   * @param result the result upon completion
   * @param <T> the type of the result
   * @return the task
   */
  @stub()
  def adapt[T](runnable: Runnable, result: T): ForkJoinTask[T] = {
    ???
    // return new AdaptedRunnable<T>(runnable, result);
  }

  /**
   * Returns a new {@code ForkJoinTask} that performs the {@code call}
   * method of the given {@code Callable} as its action, and returns
   * its result upon {@link #join}, translating any checked exceptions
   * encountered into {@code RuntimeException}.
   *
   * @param callable the callable action
   * @param <T> the type of the callable's result
   * @return the task
   */
  @stub()
  def adapt[T](callable: Callable[_ <: T]): ForkJoinTask[T] = {
    ???
    // return new AdaptedCallable<T>(callable);
  }

  /**
   * Returns a new {@code ForkJoinTask} that performs the {@code call}
   * method of the given {@code Callable} as its action, and returns
   * its result upon {@link #join}, translating any checked exceptions
   * encountered into {@code RuntimeException}.  Additionally,
   * invocations of {@code cancel} with {@code mayInterruptIfRunning
   * true} will attempt to interrupt the thread performing the task.
   *
   * @param callable the callable action
   * @param <T> the type of the callable's result
   * @return the task
   *
   * @since 17
   */
  // adaptInterruptible deferred to its own independent change
  // https://bugs.openjdk.java.net/browse/JDK-8246587
  @stub()
  private def adaptInterruptible[T](callable: Callable[_ <: T]) = {
    ???
    // return new AdaptedInterruptibleCallable<T>(callable);
  }
}
