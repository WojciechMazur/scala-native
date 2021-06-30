/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent
import java.security.AccessControlContext
import java.security.AccessControlException
import java.security.AccessController
import java.security.PrivilegedAction
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction
import java.util.Collection
import java.util.List
import java.util.concurrent.atomic.AtomicInteger
import scala.scalanative.annotation.alwaysinline

/**
 * Factory and utility methods for {@link Executor}, {@link
 * ExecutorService}, {@link ScheduledExecutorService}, {@link
 * ThreadFactory}, and {@link Callable} classes defined in this
 * package. This class supports the following kinds of methods:
 *
 * <ul>
 *   <li>Methods that create and return an {@link ExecutorService}
 *       set up with commonly useful configuration settings.
 *   <li>Methods that create and return a {@link ScheduledExecutorService}
 *       set up with commonly useful configuration settings.
 *   <li>Methods that create and return a "wrapped" ExecutorService, that
 *       disables reconfiguration by making implementation-specific methods
 *       inaccessible.
 *   <li>Methods that create and return a {@link ThreadFactory}
 *       that sets newly created threads to a known state.
 *   <li>Methods that create and return a {@link Callable}
 *       out of other closure-like forms, so they can be used
 *       in execution methods requiring {@code Callable}.
 * </ul>
 *
 * @since 1.5
 * @author Doug Lea
 */
object Executors {

  /**
   * Creates a thread pool that reuses a fixed number of threads
   * operating off a shared unbounded queue.  At any point, at most
   * {@code nThreads} threads will be active processing tasks.
   * If additional tasks are submitted when all threads are active,
   * they will wait in the queue until a thread is available.
   * If any thread terminates due to a failure during execution
   * prior to shutdown, a new one will take its place if needed to
   * execute subsequent tasks.  The threads in the pool will exist
   * until it is explicitly {@link ExecutorService#shutdown shutdown}.
   *
   * @param nThreads the number of threads in the pool
   * @return the newly created thread pool
   * @throws IllegalArgumentException if {@code nThreads <= 0}
   */
  def newFixedThreadPool(nThreads: Int): ExecutorService =
    new ThreadPoolExecutor(nThreads,
                           nThreads,
                           0L,
                           TimeUnit.MILLISECONDS,
                           new LinkedBlockingQueue[Runnable])

  /**
   * Creates a thread pool that maintains enough threads to support
   * the given parallelism level, and may use multiple queues to
   * reduce contention. The parallelism level corresponds to the
   * maximum number of threads actively engaged in, or available to
   * engage in, task processing. The actual number of threads may
   * grow and shrink dynamically. A work-stealing pool makes no
   * guarantees about the order in which submitted tasks are
   * executed.
   *
   * @param parallelism the targeted parallelism level
   * @return the newly created thread pool
   * @throws IllegalArgumentException if {@code parallelism <= 0}
   * @since 1.8
   */
  def newWorkStealingPool(parallelism: Int): ExecutorService =
    new ForkJoinPool(parallelism,
                     ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                     null,
                     true)

  /**
   * Creates a work-stealing thread pool using the number of
   * {@linkplain Runtime#availableProcessors() available processors}
   * as its target parallelism level.
   *
   * @return the newly created thread pool
   * @see #newWorkStealingPool(int)
   * @since 1.8
   */
  def newWorkStealingPool: ExecutorService =
    new ForkJoinPool(Runtime.getRuntime().availableProcessors(),
                     ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                     null,
                     true)

  /**
   * Creates a thread pool that reuses a fixed number of threads
   * operating off a shared unbounded queue, using the provided
   * ThreadFactory to create new threads when needed.  At any point,
   * at most {@code nThreads} threads will be active processing
   * tasks.  If additional tasks are submitted when all threads are
   * active, they will wait in the queue until a thread is
   * available.  If any thread terminates due to a failure during
   * execution prior to shutdown, a new one will take its place if
   * needed to execute subsequent tasks.  The threads in the pool will
   * exist until it is explicitly {@link ExecutorService#shutdown
   * shutdown}.
   *
   * @param nThreads the number of threads in the pool
   * @param threadFactory the factory to use when creating new threads
   * @return the newly created thread pool
   * @throws NullPointerException if threadFactory is null
   * @throws IllegalArgumentException if {@code nThreads <= 0}
   */
  def newFixedThreadPool(nThreads: Int,
                         threadFactory: ThreadFactory): ExecutorService = {
    new ThreadPoolExecutor(nThreads,
                           nThreads,
                           0L,
                           TimeUnit.MILLISECONDS,
                           new LinkedBlockingQueue[Runnable],
                           threadFactory)
  }

  /**
   * Creates an Executor that uses a single worker thread operating
   * off an unbounded queue. (Note however that if this single
   * thread terminates due to a failure during execution prior to
   * shutdown, a new one will take its place if needed to execute
   * subsequent tasks.)  Tasks are guaranteed to execute
   * sequentially, and no more than one task will be active at any
   * given time. Unlike the otherwise equivalent
   * {@code newFixedThreadPool(1)} the returned executor is
   * guaranteed not to be reconfigurable to use additional threads.
   *
   * @return the newly created single-threaded Executor
   */
  def newSingleThreadExecutor: ExecutorService = {
    new Executors.FinalizableDelegatedExecutorService(
      new ThreadPoolExecutor(1,
                             1,
                             0L,
                             TimeUnit.MILLISECONDS,
                             new LinkedBlockingQueue[Runnable]))
  }

  /**
   * Creates an Executor that uses a single worker thread operating
   * off an unbounded queue, and uses the provided ThreadFactory to
   * create a new thread when needed. Unlike the otherwise
   * equivalent {@code newFixedThreadPool(1, threadFactory)} the
   * returned executor is guaranteed not to be reconfigurable to use
   * additional threads.
   *
   * @param threadFactory the factory to use when creating new threads
   * @return the newly created single-threaded Executor
   * @throws NullPointerException if threadFactory is null
   */
  def newSingleThreadExecutor(threadFactory: ThreadFactory): ExecutorService = {
    new Executors.FinalizableDelegatedExecutorService(
      new ThreadPoolExecutor(1,
                             1,
                             0L,
                             TimeUnit.MILLISECONDS,
                             new LinkedBlockingQueue[Runnable],
                             threadFactory))
  }

  /**
   * Creates a thread pool that creates new threads as needed, but
   * will reuse previously constructed threads when they are
   * available.  These pools will typically improve the performance
   * of programs that execute many short-lived asynchronous tasks.
   * Calls to {@code execute} will reuse previously constructed
   * threads if available. If no existing thread is available, a new
   * thread will be created and added to the pool. Threads that have
   * not been used for sixty seconds are terminated and removed from
   * the cache. Thus, a pool that remains idle for long enough will
   * not consume any resources. Note that pools with similar
   * properties but different details (for example, timeout parameters)
   * may be created using {@link ThreadPoolExecutor} constructors.
   *
   * @return the newly created thread pool
   */
  def newCachedThreadPool: ExecutorService = {
    new ThreadPoolExecutor(0,
                           Integer.MAX_VALUE,
                           60L,
                           TimeUnit.SECONDS,
                           new SynchronousQueue[Runnable])
  }

  /**
   * Creates a thread pool that creates new threads as needed, but
   * will reuse previously constructed threads when they are
   * available, and uses the provided
   * ThreadFactory to create new threads when needed.
   *
   * @param threadFactory the factory to use when creating new threads
   * @return the newly created thread pool
   * @throws NullPointerException if threadFactory is null
   */
  def newCachedThreadPool(threadFactory: ThreadFactory): ExecutorService = {
    new ThreadPoolExecutor(0,
                           Integer.MAX_VALUE,
                           60L,
                           TimeUnit.SECONDS,
                           new SynchronousQueue[Runnable],
                           threadFactory)
  }

  /**
   * Creates a single-threaded executor that can schedule commands
   * to run after a given delay, or to execute periodically.
   * (Note however that if this single
   * thread terminates due to a failure during execution prior to
   * shutdown, a new one will take its place if needed to execute
   * subsequent tasks.)  Tasks are guaranteed to execute
   * sequentially, and no more than one task will be active at any
   * given time. Unlike the otherwise equivalent
   * {@code newScheduledThreadPool(1)} the returned executor is
   * guaranteed not to be reconfigurable to use additional threads.
   *
   * @return the newly created scheduled executor
   */
  def newSingleThreadScheduledExecutor: ScheduledExecutorService = {
    new Executors.DelegatedScheduledExecutorService(
      new ScheduledThreadPoolExecutor(1))
  }

  /**
   * Creates a single-threaded executor that can schedule commands
   * to run after a given delay, or to execute periodically.  (Note
   * however that if this single thread terminates due to a failure
   * during execution prior to shutdown, a new one will take its
   * place if needed to execute subsequent tasks.)  Tasks are
   * guaranteed to execute sequentially, and no more than one task
   * will be active at any given time. Unlike the otherwise
   * equivalent {@code newScheduledThreadPool(1, threadFactory)}
   * the returned executor is guaranteed not to be reconfigurable to
   * use additional threads.
   *
   * @param threadFactory the factory to use when creating new threads
   * @return the newly created scheduled executor
   * @throws NullPointerException if threadFactory is null
   */
  def newSingleThreadScheduledExecutor(
      threadFactory: ThreadFactory): ScheduledExecutorService = {
    new Executors.DelegatedScheduledExecutorService(
      new ScheduledThreadPoolExecutor(1, threadFactory))
  }

  /**
   * Creates a thread pool that can schedule commands to run after a
   * given delay, or to execute periodically.
   * @param corePoolSize the number of threads to keep in the pool,
   * even if they are idle
   * @return the newly created scheduled thread pool
   * @throws IllegalArgumentException if {@code corePoolSize < 0}
   */
  def newScheduledThreadPool(corePoolSize: Int): ScheduledExecutorService = {
    new ScheduledThreadPoolExecutor(corePoolSize)
  }

  /**
   * Creates a thread pool that can schedule commands to run after a
   * given delay, or to execute periodically.
   * @param corePoolSize the number of threads to keep in the pool,
   * even if they are idle
   * @param threadFactory the factory to use when the executor
   * creates a new thread
   * @return the newly created scheduled thread pool
   * @throws IllegalArgumentException if {@code corePoolSize < 0}
   * @throws NullPointerException if threadFactory is null
   */
  def newScheduledThreadPool(
      corePoolSize: Int,
      threadFactory: ThreadFactory): ScheduledExecutorService = {
    new ScheduledThreadPoolExecutor(corePoolSize, threadFactory)
  }

  /**
   * Returns an object that delegates all defined {@link
   * ExecutorService} methods to the given executor, but not any
   * other methods that might otherwise be accessible using
   * casts. This provides a way to safely "freeze" configuration and
   * disallow tuning of a given concrete implementation.
   * @param executor the underlying implementation
   * @return an {@code ExecutorService} instance
   * @throws NullPointerException if executor null
   */
  def unconfigurableExecutorService(
      executor: ExecutorService): ExecutorService = {
    if (executor == null) throw new NullPointerException
    new Executors.DelegatedExecutorService(executor)
  }

  /**
   * Returns an object that delegates all defined {@link
   * ScheduledExecutorService} methods to the given executor, but
   * not any other methods that might otherwise be accessible using
   * casts. This provides a way to safely "freeze" configuration and
   * disallow tuning of a given concrete implementation.
   * @param executor the underlying implementation
   * @return a {@code ScheduledExecutorService} instance
   * @throws NullPointerException if executor null
   */
  def unconfigurableScheduledExecutorService(
      executor: ScheduledExecutorService): ScheduledExecutorService = {
    if (executor == null) throw new NullPointerException
    new Executors.DelegatedScheduledExecutorService(executor)
  }

  /**
   * Returns a default thread factory used to create new threads.
   * This factory creates all new threads used by an Executor in the
   * same {@link ThreadGroup}. If there is a {@link
   * java.lang.SecurityManager}, it uses the group of {@link
   * System#getSecurityManager}, else the group of the thread
   * invoking this {@code defaultThreadFactory} method. Each new
   * thread is created as a non-daemon thread with priority set to
   * the smaller of {@code Thread.NORM_PRIORITY} and the maximum
   * priority permitted in the thread group.  New threads have names
   * accessible via {@link Thread#getName} of
   * <em>pool-N-thread-M</em>, where <em>N</em> is the sequence
   * number of this factory, and <em>M</em> is the sequence number
   * of the thread created by this factory.
   * @return a thread factory
   */
  def defaultThreadFactory: ThreadFactory = {
    return new Executors.DefaultThreadFactory
  }

  /**
   * Returns a thread factory used to create new threads that
   * have the same permissions as the current thread.
   * This factory creates threads with the same settings as {@link
   * Executors#defaultThreadFactory}, additionally setting the
   * AccessControlContext and contextClassLoader of new threads to
   * be the same as the thread invoking this
   * {@code privilegedThreadFactory} method.  A new
   * {@code privilegedThreadFactory} can be created within an
   * {@link AccessController#doPrivileged AccessController.doPrivileged}
   * action setting the current thread's access control context to
   * create threads with the selected permission settings holding
   * within that action.
   *
   * <p>Note that while tasks running within such threads will have
   * the same access control and class loader settings as the
   * current thread, they need not have the same {@link
   * java.lang.ThreadLocal} or {@link
   * java.lang.InheritableThreadLocal} values. If necessary,
   * particular values of thread locals can be set or reset before
   * any task runs in {@link ThreadPoolExecutor} subclasses using
   * {@link ThreadPoolExecutor#beforeExecute(Thread, Runnable)}.
   * Also, if it is necessary to initialize worker threads to have
   * the same InheritableThreadLocal settings as some other
   * designated thread, you can create a custom ThreadFactory in
   * which that thread waits for and services requests to create
   * others that will inherit its values.
   *
   * @return a thread factory
   * @throws AccessControlException if the current access control
   * context does not have permission to both get and set context
   * class loader
   */
  def privilegedThreadFactory: ThreadFactory = {
    new Executors.PrivilegedThreadFactory
  }

  /**
   * Returns a {@link Callable} object that, when
   * called, runs the given task and returns the given result.  This
   * can be useful when applying methods requiring a
   * {@code Callable} to an otherwise resultless action.
   * @param task the task to run
   * @param result the result to return
   * @param <T> the type of the result
   * @return a callable object
   * @throws NullPointerException if task null
   */
  def callable[T](task: Runnable, result: T): Callable[T] = {
    if (task == null) throw new NullPointerException
    new Executors.RunnableAdapter[T](task, result)
  }

  /**
   * Returns a {@link Callable} object that, when
   * called, runs the given task and returns {@code null}.
   * @param task the task to run
   * @return a callable object
   * @throws NullPointerException if task null
   */
  def callable(task: Runnable): Callable[AnyRef] = {
    if (task == null) throw new NullPointerException
    new Executors.RunnableAdapter[AnyRef](task, null)
  }

  /**
   * Returns a {@link Callable} object that, when
   * called, runs the given privileged action and returns its result.
   * @param action the privileged action to run
   * @return a callable object
   * @throws NullPointerException if action null
   */
  def callable(action: PrivilegedAction[_]): Callable[Any] = {
    if (action == null) throw new NullPointerException
    new Callable[Any]() {
      override def call(): Any = { action.run() }
    }
  }

  /**
   * Returns a {@link Callable} object that, when
   * called, runs the given privileged exception action and returns
   * its result.
   * @param action the privileged exception action to run
   * @return a callable object
   * @throws NullPointerException if action null
   */
  def callable(action: PrivilegedExceptionAction[_]): Callable[Any] = {
    if (action == null) { throw new NullPointerException }
    new Callable[Any]() {
      @throws[Exception]
      override def call(): Any = { action.run() }
    }
  }

  /**
   * Returns a {@link Callable} object that will, when called,
   * execute the given {@code callable} under the current access
   * control context. This method should normally be invoked within
   * an {@link AccessController#doPrivileged AccessController.doPrivileged}
   * action to create callables that will, if possible, execute
   * under the selected permission settings holding within that
   * action; or if not possible, throw an associated {@link
   * AccessControlException}.
   * @param callable the underlying task
   * @param <T> the type of the callable's result
   * @return a callable object
   * @throws NullPointerException if callable null
   */
  def privilegedCallable[T](callable: Callable[T]): Callable[T] = {
    if (callable == null) { throw new NullPointerException }
    new Executors.PrivilegedCallable[T](callable)
  }

  /**
   * Returns a {@link Callable} object that will, when called,
   * execute the given {@code callable} under the current access
   * control context, with the current context class loader as the
   * context class loader. This method should normally be invoked
   * within an
   * {@link AccessController#doPrivileged AccessController.doPrivileged}
   * action to create callables that will, if possible, execute
   * under the selected permission settings holding within that
   * action; or if not possible, throw an associated {@link
   * AccessControlException}.
   *
   * @param callable the underlying task
   * @param <T> the type of the callable's result
   * @return a callable object
   * @throws NullPointerException if callable null
   * @throws AccessControlException if the current access control
   * context does not have permission to both set and get context
   * class loader
   */
  def privilegedCallableUsingCurrentClassLoader[T](
      callable: Callable[T]): Callable[T] = {
    if (callable == null) { throw new NullPointerException }
    new Executors.PrivilegedCallableUsingCurrentClassLoader[T](callable)
  }

  /**
   * A callable that runs given task and returns given result.
   */
  final private class RunnableAdapter[T] private[concurrent] (
      val task: Runnable,
      val result: T)
      extends Callable[T] {
    override def call(): T = {
      task.run()
      result
    }
    override def toString(): String = {
      super.toString + "[Wrapped task = " + task + "]"
    }
  }

  /**
   * A callable that runs under established access control settings.
   */
  final private class PrivilegedCallable[T] private[concurrent] (
      val task: Callable[T])
      extends Callable[T] {
    final private[concurrent] val acc: AccessControlContext =
      AccessController.getContext()
    @throws[Exception]
    override def call(): T = {
      try {
        val action = new PrivilegedExceptionAction[T]() {
          @throws[Exception]
          override def run(): T = task.call()
        }
        AccessController.doPrivileged(action, acc)
      } catch {
        case e: PrivilegedActionException =>
          throw e.getException
      }
    }
    override def toString(): String = {
      super.toString + "[Wrapped task = " + task + "]"
    }
  }

  /**
   * A callable that runs under established access control settings and
   * current ClassLoader.
   */
  final private class PrivilegedCallableUsingCurrentClassLoader[T] private[concurrent] (
      val task: Callable[T])
      extends Callable[T] {
    final private[concurrent] var acc: AccessControlContext =
      AccessController.getContext()
    final private[concurrent] var ccl: ClassLoader =
      Thread.currentThread().getContextClassLoader()

    @throws[Exception]
    override def call(): T = {
      try return AccessController.doPrivileged(
        new PrivilegedExceptionAction[T]() {
          @throws[Exception]
          override def run(): T = {
            val t: Thread       = Thread.currentThread()
            val cl: ClassLoader = t.getContextClassLoader()
            if (ccl eq cl) { return task.call() }
            else {
              t.setContextClassLoader(ccl)
              try return task.call()
              finally {
                t.setContextClassLoader(cl)
              }
            }
          }
        },
        acc
      )
      catch {
        case e: PrivilegedActionException =>
          throw e.getException
      }
    }
    override def toString(): String = {
      return super.toString + "[Wrapped task = " + task + "]"
    }
  }

  /**
   * The default thread factory.
   */
  private object DefaultThreadFactory {
    private val poolNumber: AtomicInteger = new AtomicInteger(1)
  }
  private class DefaultThreadFactory private[concurrent] ()
      extends ThreadFactory {
    //Originally SecurityManager threadGroup was tried first
    final private val group: ThreadGroup =
      Thread.currentThread().getThreadGroup()

    final private val threadNumber: AtomicInteger = new AtomicInteger(1)
    final private var namePrefix: String =
      "pool-" + DefaultThreadFactory.poolNumber.getAndIncrement() + "-thread-"

    override def newThread(r: Runnable): Thread = {
      val t: Thread =
        new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0)
      if (t.isDaemon()) { t.setDaemon(false) }
      if (t.getPriority() != Thread.NORM_PRIORITY) {
        t.setPriority(Thread.NORM_PRIORITY)
      }
      return t
    }
  }

  /**
   * Thread factory capturing access control context and class loader.
   */
  private class PrivilegedThreadFactory private[concurrent] ()
      extends Executors.DefaultThreadFactory {
    final private[concurrent] val acc: AccessControlContext =
      AccessController.getContext()
    final private[concurrent] val ccl: ClassLoader =
      Thread.currentThread().getContextClassLoader()
    override def newThread(r: Runnable): Thread = {
      return super.newThread(new Runnable() {
        override def run(): Unit = {
          AccessController.doPrivileged(new PrivilegedAction[AnyRef]() {
            override def run(): Void = {
              Thread.currentThread().setContextClassLoader(ccl)
              r.run()
              return null
            }
          }, acc)
        }
      })
    }
  }

  /**
   * A wrapper class that exposes only the ExecutorService methods
   * of an ExecutorService implementation.
   */
  private class DelegatedExecutorService private[concurrent] (
      val executor: ExecutorService)
      extends ExecutorService {

    // Stub used in place of JVM intrinsic
    @alwaysinline
    private def reachabilityFence(target: Any): Unit = ()

    override def execute(command: Runnable): Unit = {
      try executor.execute(command)
      finally {
        reachabilityFence(this)
      }
    }
    override def shutdown(): Unit = { executor.shutdown() }
    override def shutdownNow(): List[Runnable] = {
      try return executor.shutdownNow()
      finally {
        reachabilityFence(this)
      }
    }
    override def isShutdown(): Boolean = {
      try return executor.isShutdown()
      finally {
        reachabilityFence(this)
      }
    }
    override def isTerminated(): Boolean = {
      try return executor.isTerminated()
      finally {
        reachabilityFence(this)
      }
    }
    @throws[InterruptedException]
    override def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = {
      try return executor.awaitTermination(timeout, unit)
      finally {
        reachabilityFence(this)
      }
    }
    override def submit(task: Runnable): Future[_] = {
      try return executor.submit(task)
      finally {
        reachabilityFence(this)
      }
    }
    override def submit[T](task: Callable[T]): Future[T] = {
      try return executor.submit(task)
      finally {
        reachabilityFence(this)
      }
    }
    override def submit[T](task: Runnable, result: T): Future[T] = {
      try return executor.submit(task, result)
      finally {
        reachabilityFence(this)
      }
    }
    @throws[InterruptedException]
    override def invokeAll[T](
        tasks: Collection[_ <: Callable[T]]): List[Future[T]] = {
      try return executor.invokeAll(tasks)
      finally {
        reachabilityFence(this)
      }
    }
    @throws[InterruptedException]
    override def invokeAll[T](tasks: Collection[_ <: Callable[T]],
                              timeout: Long,
                              unit: TimeUnit): List[Future[T]] = {
      try return executor.invokeAll(tasks, timeout, unit)
      finally {
        reachabilityFence(this)
      }
    }
    @throws[InterruptedException]
    @throws[ExecutionException]
    override def invokeAny[T](tasks: Collection[_ <: Callable[T]]): T = {
      try return executor.invokeAny(tasks)
      finally {
        reachabilityFence(this)
      }
    }
    @throws[InterruptedException]
    @throws[ExecutionException]
    @throws[TimeoutException]
    override def invokeAny[T](tasks: Collection[_ <: Callable[T]],
                              timeout: Long,
                              unit: TimeUnit): T = {
      try return executor.invokeAny(tasks, timeout, unit)
      finally {
        reachabilityFence(this)
      }
    }
  }
  private class FinalizableDelegatedExecutorService private[concurrent] (
      executor: ExecutorService)
      extends Executors.DelegatedExecutorService(executor) {
    override protected def finalize(): Unit = { super.shutdown() }
  }

  /**
   * A wrapper class that exposes only the ScheduledExecutorService
   * methods of a ScheduledExecutorService implementation.
   */
  private class DelegatedScheduledExecutorService private[concurrent] (
      e: ScheduledExecutorService)
      extends Executors.DelegatedExecutorService(e)
      with ScheduledExecutorService {
    override def schedule(command: Runnable,
                          delay: Long,
                          unit: TimeUnit): ScheduledFuture[_] = {
      return e.schedule(command, delay, unit)
    }
    override def schedule[V](callable: Callable[V],
                             delay: Long,
                             unit: TimeUnit): ScheduledFuture[V] = {
      return e.schedule(callable, delay, unit)
    }
    override def scheduleAtFixedRate(command: Runnable,
                                     initialDelay: Long,
                                     period: Long,
                                     unit: TimeUnit): ScheduledFuture[_] = {
      return e.scheduleAtFixedRate(command, initialDelay, period, unit)
    }
    override def scheduleWithFixedDelay(command: Runnable,
                                        initialDelay: Long,
                                        delay: Long,
                                        unit: TimeUnit): ScheduledFuture[_] = {
      return e.scheduleWithFixedDelay(command, initialDelay, delay, unit)
    }
  }
}
class Executors private () /** Cannot instantiate. */ {}
