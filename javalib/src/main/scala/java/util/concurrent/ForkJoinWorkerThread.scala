/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;

import java.security.AccessController;
import java.security.PrivilegedAction;

/*
 * ForkJoinWorkerThreads are managed by ForkJoinPools and perform
 * ForkJoinTasks. For explanation, see the internal documentation
 * of class ForkJoinPool.
 *
 * This class just maintains links to its pool and WorkQueue.
 */

/**
 * A thread managed by a {@link ForkJoinPool}, which executes
 * {@link ForkJoinTask}s.
 * This class is subclassable solely for the sake of adding
 * functionality -- there are no overridable methods dealing with
 * scheduling or execution.  However, you can override initialization
 * and termination methods surrounding the main task processing loop.
 * If you do create such a subclass, you will also need to supply a
 * custom {@link ForkJoinPool.ForkJoinWorkerThreadFactory} to
 * {@linkplain ForkJoinPool#ForkJoinPool(int, ForkJoinWorkerThreadFactory,
 * UncaughtExceptionHandler, boolean, int, int, int, Predicate, long, TimeUnit)
 * use it} in a {@code ForkJoinPool}.
 *
 * @since 1.7
 * @author Doug Lea
 */
class ForkJoinWorkerThread private[concurrent] (
    group: ThreadGroup,
    private[concurrent] val pool: ForkJoinPool,
    useSystemClassLoader: Boolean,
    isInnocuous: Boolean)
    extends Thread(group, null, pool.nextWorkerThreadName(), 0L) {
  super.setDaemon(true)
  if (pool.ueh != null) {
    super.setUncaughtExceptionHandler(pool.ueh)
  }
  private[concurrent] val workQueue =
    new ForkJoinPool.WorkQueue(this, isInnocuous);

  // if (useSystemClassLoader)
  //     super.setContextClassLoader(ClassLoader.getSystemClassLoader());

  /**
   * Creates a ForkJoinWorkerThread operating in the given thread group and
   * pool.
   *
   * @param group if non-null, the thread group for this thread
   * @param pool the pool this thread works in
   * @throws NullPointerException if pool is null
   */
  /**/
  private[concurrent] def this(group: ThreadGroup, pool: ForkJoinPool) = {
    this(group, pool, false, false);
  }

  /**
   * Creates a ForkJoinWorkerThread operating in the given pool.
   *
   * @param pool the pool this thread works in
   * @throws NullPointerException if pool is null
   */
  protected def this(pool: ForkJoinPool) = {
    this(null, pool, false, false);
  }

  /**
   * Returns the pool hosting this thread.
   *
   * @return the pool
   */
  def getPool(): ForkJoinPool = {
    pool;
  }

  /**
   * Returns the unique index number of this thread in its pool.
   * The returned value ranges from zero to the maximum number of
   * threads (minus one) that may exist in the pool, and does not
   * change during the lifetime of the thread.  This method may be
   * useful for applications that track status or collect results
   * per-worker-thread rather than per-task.
   *
   * @return the index number
   */
  def getPoolIndex(): Int = {
    workQueue.getPoolIndex();
  }

  /**
   * Initializes internal state after construction but before
   * processing any tasks. If you override this method, you must
   * invoke {@code super.onStart()} at the beginning of the method.
   * Initialization requires care: Most fields must have legal
   * default values, to ensure that attempted accesses from other
   * threads work correctly even before this thread starts
   * processing tasks.
   */
  protected def onStart(): Unit = ()

  /**
   * Performs cleanup associated with termination of this worker
   * thread.  If you override this method, you must invoke
   * {@code super.onTermination} at the end of the overridden method.
   *
   * @param exception the exception causing this thread to abort due
   * to an unrecoverable error, or {@code null} if completed normally
   */
  protected def onTermination(exception: Throwable): Unit = {
    if (exception != null) {
      exception.printStackTrace()
    }
  }

  /**
   * This method is required to be public, but should never be
   * called explicitly. It performs the main run loop to execute
   * {@link ForkJoinTask}s.
   */
  override def run(): Unit = {
    var exception: Throwable = null;
    val p                    = pool;
    val w                    = workQueue;
    if (p != null && w != null) { // skip on failed initialization
      try {
        p.registerWorker(w);
        onStart();
        p.runWorker(w);
      } catch {
        case ex: Throwable => exception = ex
      } finally {
        try {
          onTermination(exception);
        } catch {
          case ex: Throwable =>
            if (exception == null)
              exception = ex;
        } finally {
          p.deregisterWorker(this, exception);
        }
      }
    }
  }

}
