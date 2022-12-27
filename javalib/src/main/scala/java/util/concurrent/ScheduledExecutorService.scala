/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent

trait ScheduledExecutorService extends ExecutorService {

  /** Submits a one-shot task that becomes enabled after the given delay.
   *
   *  @param command
   *    the task to execute
   *  @param delay
   *    the time from now to delay execution
   *  @param unit
   *    the time unit of the delay parameter
   *  @return
   *    a ScheduledFuture representing pending completion of the task and whose
   *    {@code get()} method will return {@code null} upon completion
   *  @throws RejectedExecutionException
   *    if the task cannot be scheduled for execution
   *  @throws NullPointerException
   *    if command or unit is null
   */
  def schedule(
      command: Runnable,
      delay: Long,
      unit: TimeUnit
  ): ScheduledFuture[AnyRef]

  /** Submits a value-returning one-shot task that becomes enabled after the
   *  given delay.
   *
   *  @param callable
   *    the function to execute
   *  @param delay
   *    the time from now to delay execution
   *  @param unit
   *    the time unit of the delay parameter
   *  @param <V>
   *    the type of the callable's result
   *  @return
   *    a ScheduledFuture that can be used to extract result or cancel
   *  @throws RejectedExecutionException
   *    if the task cannot be scheduled for execution
   *  @throws NullPointerException
   *    if callable or unit is null
   */
  def schedule[V <: AnyRef](
      command: Callable[V],
      delay: Long,
      unit: TimeUnit
  ): ScheduledFuture[V]

  /** Submits a periodic action that becomes enabled first after the given
   *  initial delay, and subsequently with the given period; that is, executions
   *  will commence after {@code initialDelay}, then {@code initialDelay +
   *  period}, then {@code initialDelay + 2 * period}, and so on.
   *
   *  <p>The sequence of task executions continues indefinitely until one of the
   *  following exceptional completions occur: <ul> <li>The task is {@linkplain
   *  Future#cancel explicitly cancelled} via the returned future. <li>The
   *  executor terminates, also resulting in task cancellation. <li>An execution
   *  of the task throws an exception. In this case calling {@link Future#get()
   *  get} on the returned future will throw {@link ExecutionException}, holding
   *  the exception as its cause. </ul> Subsequent executions are suppressed.
   *  Subsequent calls to {@link Future#isDone isDone()} on the returned future
   *  will return {@code true}.
   *
   *  <p>If any execution of this task takes longer than its period, then
   *  subsequent executions may start late, but will not concurrently execute.
   *
   *  @param command
   *    the task to execute
   *  @param initialDelay
   *    the time to delay first execution
   *  @param period
   *    the period between successive executions
   *  @param unit
   *    the time unit of the initialDelay and period parameters
   *  @return
   *    a ScheduledFuture representing pending completion of the series of
   *    repeated tasks. The future's {@link Future#get() get()} method will
   *    never return normally, and will throw an exception upon task
   *    cancellation or abnormal termination of a task execution.
   *  @throws RejectedExecutionException
   *    if the task cannot be scheduled for execution
   *  @throws NullPointerException
   *    if command or unit is null
   *  @throws IllegalArgumentException
   *    if period less than or equal to zero
   */
  def scheduleAtFixedRate(
      command: Runnable,
      initialDelay: Long,
      period: Long,
      unit: TimeUnit
  ): ScheduledFuture[AnyRef]

  /** Submits a periodic action that becomes enabled first after the given
   *  initial delay, and subsequently with the given delay between the
   *  termination of one execution and the commencement of the next.
   *
   *  <p>The sequence of task executions continues indefinitely until one of the
   *  following exceptional completions occur: <ul> <li>The task is {@linkplain
   *  Future#cancel explicitly cancelled} via the returned future. <li>The
   *  executor terminates, also resulting in task cancellation. <li>An execution
   *  of the task throws an exception. In this case calling {@link Future#get()
   *  get} on the returned future will throw {@link ExecutionException}, holding
   *  the exception as its cause. </ul> Subsequent executions are suppressed.
   *  Subsequent calls to {@link Future#isDone isDone()} on the returned future
   *  will return {@code true}.
   *
   *  @param command
   *    the task to execute
   *  @param initialDelay
   *    the time to delay first execution
   *  @param delay
   *    the delay between the termination of one execution and the commencement
   *    of the next
   *  @param unit
   *    the time unit of the initialDelay and delay parameters
   *  @return
   *    a ScheduledFuture representing pending completion of the series of
   *    repeated tasks. The future's {@link Future#get() get()} method will
   *    never return normally, and will throw an exception upon task
   *    cancellation or abnormal termination of a task execution.
   *  @throws RejectedExecutionException
   *    if the task cannot be scheduled for execution
   *  @throws NullPointerException
   *    if command or unit is null
   *  @throws IllegalArgumentException
   *    if delay less than or equal to zero
   */
  def scheduleWithFixedDelay(
      command: Runnable,
      initialDelay: Long,
      period: Long,
      unit: TimeUnit
  ): ScheduledFuture[AnyRef]

}