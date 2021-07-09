/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent

trait Executor {

  /** Executes the given command at some time in the future. The command may
   *  execute in a new thread, in a pooled thread, or in the calling thread, at
   *  the discretion of the {@code Executor} implementation.
   *
   *  @param command
   *    the runnable task
   *  @throws RejectedExecutionException
   *    if this task cannot be accepted for execution
   *  @throws NullPointerException
   *    if command is null
   */
  def execute(command: Runnable): Unit
}
