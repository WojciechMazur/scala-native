/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent

/**
 * A service that decouples the production of new asynchronous tasks
 * from the consumption of the results of completed tasks.  Producers
 * {@code submit} tasks for execution. Consumers {@code take}
 * completed tasks and process their results in the order they
 * complete.  A {@code CompletionService} can for example be used to
 * manage asynchronous I/O, in which tasks that perform reads are
 * submitted in one part of a program or system, and then acted upon
 * in a different part of the program when the reads complete,
 * possibly in a different order than they were requested.
 *
 * <p>Typically, a {@code CompletionService} relies on a separate
 * {@link Executor} to actually execute the tasks, in which case the
 * {@code CompletionService} only manages an internal completion
 * queue. The {@link ExecutorCompletionService} class provides an
 * implementation of this approach.
 *
 * <p>Memory consistency effects: Actions in a thread prior to
 * submitting a task to a {@code CompletionService}
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * actions taken by that task, which in turn <i>happen-before</i>
 * actions following a successful return from the corresponding {@code take()}.
 *
 * @since 1.5
 */
trait CompletionService[V] {

  def submit(task: Callable[V]): Future[V]

  def submit(task: Runnable, result: V): Future[V]

  def take(): Future[V]

  def poll(): Future[V]

  def poll(timeout: Long, unit: TimeUnit): Future[V]

}
