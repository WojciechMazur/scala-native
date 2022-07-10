/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */ /*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent

/** A recursive result-bearing {@link ForkJoinTask}.
 *
 *  <p>For a classic example, here is a task computing Fibonacci numbers:
 *
 *  <pre> {@code class Fibonacci extends RecursiveTask<Integer> { final int n;
 *  Fibonacci(int n) { this.n = n; } protected Integer compute() { if (n <= 1)
 *  return n; Fibonacci f1 = new Fibonacci(n - 1); f1.fork(); Fibonacci f2 = new
 *  Fibonacci(n - 2); return f2.compute() + f1.join(); } }}</pre>
 *
 *  However, besides being a dumb way to compute Fibonacci functions (there is a
 *  simple fast linear algorithm that you'd use in practice), this is likely to
 *  perform poorly because the smallest subtasks are too small to be worthwhile
 *  splitting up. Instead, as is the case for nearly all fork/join applications,
 *  you'd pick some minimum granularity size (for example 10 here) for which you
 *  always sequentially solve rather than subdividing.
 *
 *  @since 1.7
 *  @author
 *    Doug Lea
 */
@SerialVersionUID(5232453952276485270L)
abstract class RecursiveTask[V]() extends ForkJoinTask[V] {
  private[concurrent] var result: V = _

  protected def compute(): V
  override final def getRawResult(): V = result
  override final protected def setRawResult(value: V): Unit = result = value

  /** Implements execution conventions for RecursiveTask.
   */
  override final protected def exec(): Boolean = {
    result = compute()
    true
  }
}
