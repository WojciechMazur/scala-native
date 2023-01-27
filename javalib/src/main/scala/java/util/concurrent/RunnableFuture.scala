/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent

/** A {@link Future} that is {@link Runnable}. Successful execution of the
 *  {@code run} method causes completion of the {@code Future} and allows access
 *  to its results.
 *  @see
 *    FutureTask
 *  @see
 *    Executor
 *  @since 1.6
 *  @author
 *    Doug Lea
 *  @param <V>
 *    The result type returned by this Future's {@code get} method
 */
trait RunnableFuture[V] extends Runnable with Future[V] {

  /** Sets this Future to the result of its computation unless it has been
   *  cancelled.
   */
  def run(): Unit

}
