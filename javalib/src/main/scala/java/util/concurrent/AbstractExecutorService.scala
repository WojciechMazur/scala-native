/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent
import java.util
import java.lang

/**
 * Provides default implementations of {@link ExecutorService}
 * execution methods. This class implements the {@code submit},
 * {@code invokeAny} and {@code invokeAll} methods using a
 * {@link RunnableFuture} returned by {@code newTaskFor}, which defaults
 * to the {@link FutureTask} class provided in this package.  For example,
 * the implementation of {@code submit(Runnable)} creates an
 * associated {@code RunnableFuture} that is executed and
 * returned. Subclasses may override the {@code newTaskFor} methods
 * to return {@code RunnableFuture} implementations other than
 * {@code FutureTask}.
 *
 * <p><b>Extension example.</b> Here is a sketch of a class
 * that customizes {@link ThreadPoolExecutor} to use
 * a {@code CustomTask} class instead of the default {@code FutureTask}:
 * <pre> {@code
 * public class CustomThreadPoolExecutor extends ThreadPoolExecutor {
 *
 *   static class CustomTask<V> implements RunnableFuture<V> { ... }
 *
 *   protected <V> RunnableFuture<V> newTaskFor(Callable<V> c) {
 *       return new CustomTask<V>(c);
 *   }
 *   protected <V> RunnableFuture<V> newTaskFor(Runnable r, V v) {
 *       return new CustomTask<V>(r, v);
 *   }
 *   // ... add constructors, etc.
 * }}</pre>
 *
 * @since 1.5
 * @author Doug Lea
 */
abstract class AbstractExecutorService() extends ExecutorService {

  /**
   * Returns a {@code RunnableFuture} for the given runnable and default
   * value.
   *
   * @param runnable the runnable task being wrapped
   * @param value the default value for the returned future
   * @param <T> the type of the given value
   * @return a {@code RunnableFuture} which, when run, will run the
   * underlying runnable and which, as a {@code Future}, will yield
   * the given value as its result and provide for cancellation of
   * the underlying task
   * @since 1.6
   */
  protected[concurrent] def newTaskFor[T](runnable: Runnable,
                                          value: T): RunnableFuture[T] =
    new FutureTask[T](runnable, value)

  /**
   * Returns a {@code RunnableFuture} for the given callable task.
   *
   * @param callable the callable task being wrapped
   * @param <T> the type of the callable's result
   * @return a {@code RunnableFuture} which, when run, will call the
   * underlying callable and which, as a {@code Future}, will yield
   * the callable's result as its result and provide for
   * cancellation of the underlying task
   * @since 1.6
   */
  protected[concurrent] def newTaskFor[T](
      callable: Callable[T]): RunnableFuture[T] =
    new FutureTask[T](callable)

  @throws[NullPointerException]
  @throws[java.lang.RejectedExecutionException]
  override def submit(task: Runnable): Future[_] = {
    if (task == null) throw new NullPointerException()
    val ftask: RunnableFuture[Object] = newTaskFor(task, null)
    execute(ftask)
    ftask
  }

  @throws[NullPointerException]
  @throws[java.lang.RejectedExecutionException]
  override def submit[T](task: Runnable, result: T): Future[T] = {
    if (task == null) throw new NullPointerException()
    val ftask: RunnableFuture[T] = newTaskFor(task, result)
    execute(ftask)
    ftask
  }

  @throws[NullPointerException]
  @throws[java.lang.RejectedExecutionException]
  override def submit[T](task: Callable[T]): Future[T] = {
    if (task == null) throw new NullPointerException()
    val ftask: RunnableFuture[T] = newTaskFor(task)
    execute(ftask)
    ftask
  }

  @throws[InterruptedException]
  @throws[TimeoutException]
  @throws[ExecutionException]
  private def doInvokeAny[T](tasks: util.Collection[_ <: Callable[T]],
                             timed: Boolean,
                             n: Long): T = {
    var nanos: Long = n
    if (tasks == null)
      throw new NullPointerException()

    var ntasks: Int = tasks.size()
    if (ntasks == 0)
      throw new IllegalArgumentException()

    val futures: util.List[Future[T]] = new util.ArrayList[Future[T]](ntasks)
    val ecs: ExecutorCompletionService[T] =
      new ExecutorCompletionService[T](this)

    // For efficiency, especially in executors with limited
    // parallelism, check to see if previously submitted tasks are
    // done before submitting more of them. This interleaving
    // plus the exception mechanics account for messiness of main
    // loop.

    try {
      // Record exceptions so that if we fail to obtain any
      // result, we can throw the last exception we got.
      var ee: ExecutionException              = null
      var lastTime: Long                      = if (timed) System.nanoTime() else 0
      val it: util.Iterator[_ <: Callable[T]] = tasks.iterator()

      // Start one task for sure; the rest incrementally
      futures.add(ecs.submit(it.next()))
      ntasks -= 1
      var active: Int = 1

      var break: Boolean = false
      while (!break) {
        var f: Future[T] = ecs.poll()
        if (f == null) {
          if (ntasks > 0) {
            ntasks -= 1
            futures.add(ecs.submit(it.next()))
            active += 1
          } else if (active == 0)
            break = true
          else if (!break && timed) {
            f = ecs.poll(nanos, TimeUnit.NANOSECONDS)
            if (f == null) throw new TimeoutException()
            val now: Long = System.nanoTime()
            nanos -= now - lastTime
            lastTime = now
          } else if (!break) f = ecs.take()
        }
        if (!break && f != null) {
          active -= 1
          try {
            return f.get
          } catch {
            case ie: InterruptedException => throw ie
            case eex: ExecutionException  => ee = eex
            case rex: RuntimeException    => ee = new ExecutionException(rex)
          }
        }
      }
      if (ee == null) ee = new ExecutionException()
      throw ee
    } finally {
      val it = futures.iterator()
      while (it.hasNext()) it.next().cancel(true)
    }

  }

  @throws[InterruptedException]
  @throws[ExecutionException]
  override def invokeAny[T](tasks: util.Collection[_ <: Callable[T]]): T = {
    try {
      doInvokeAny(tasks, false, 0)
    } catch {
      case cannotHappen: TimeoutException =>
        // Not possible
        null.asInstanceOf[T]
    }
  }

  @throws[InterruptedException]
  @throws[ExecutionException]
  @throws[TimeoutException]
  override def invokeAny[T](tasks: java.util.Collection[_ <: Callable[T]],
                            timeout: Long,
                            unit: TimeUnit): T = {
    doInvokeAny(tasks, true, unit.toNanos(timeout))
  }

  @throws[InterruptedException]
  override def invokeAll[T](tasks: java.util.Collection[_ <: Callable[T]])
      : java.util.List[Future[T]] = {
    if (tasks == null) throw new NullPointerException()
    val futures: util.List[Future[T]] =
      new util.ArrayList[Future[T]](tasks.size())
    var done: Boolean = false
    try {
      val it = tasks.iterator()
      while (it.hasNext) {
        val f: RunnableFuture[T] = newTaskFor(it.next())
        futures.add(f)
        execute(f)
      }

      val it1 = futures.iterator()
      while (it1.hasNext) {
        val f = it1.next()
        if (!f.isDone) {
          try {
            f.get
          } catch {
            case ignore: CancellationException =>
            case ignore: ExecutionException    =>
          }
        }
      }
      done = true
      futures
    } finally {
      if (!done) {
        val it = futures.iterator()
        while (it.hasNext) it.next().cancel(true)
      }
    }
  }

  @throws[InterruptedException]
  override def invokeAll[T](tasks: util.Collection[_ <: Callable[T]],
                            timeout: Long,
                            unit: TimeUnit): util.List[Future[T]] = {
    if (tasks == null || unit == null) throw new NullPointerException()
    var nanos: Long = unit.toNanos(timeout)
    val futures: util.List[Future[T]] =
      new util.ArrayList[Future[T]](tasks.size())
    var done: Boolean = false
    try {
      val it = tasks.iterator()
      while (it.hasNext) futures.add(newTaskFor(it.next()))

      var lastTime: Long = System.nanoTime()

      // Interleave time checks and calls to execute in case
      // executor doesn't have any/much parallelism.
      var it1 = futures.iterator()
      while (it1.hasNext) {
        execute(it.next().asInstanceOf[Runnable])
        val now: Long = System.nanoTime()
        nanos -= now - lastTime
        lastTime = now
        if (nanos <= 0)
          return futures
      }

      it1 = futures.iterator()
      while (it1.hasNext) {
        val f: Future[T] = it1.next()
        if (!f.isDone) {
          if (nanos <= 0)
            return futures
          try {
            f.get(nanos, TimeUnit.NANOSECONDS)
          } catch {
            case ignore: CancellationException => ()
            case ignore: ExecutionException    => ()
            case toe: TimeoutException         => return futures
          }
          val now: Long = System.nanoTime()
          nanos -= now - lastTime
          lastTime = now
        }
      }
      done = true
      futures

    } finally {
      if (!done) {
        val it = futures.iterator()
        while (it.hasNext) it.next().cancel(true)
      }
    }
  }

}
