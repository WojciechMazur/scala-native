package scala.scalanative
package runtime

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

@extern
object NativeThread {
  type pthread_t = ULong

  @name("get_max_priority")
  def THREAD_MAX_PRIORITY: Int = extern

  @name("get_min_priority")
  def THREAD_MIN_PRIORITY: Int = extern

  @name("get_norm_priority")
  def THREAD_NORM_PRIORITY: Int = extern

  @name("get_stack_size")
  def THREAD_DEFAULT_STACK_SIZE: CSize = extern

  @name("set_priority")
  def setPriority(thread: pthread_t, priority: CInt): Unit = extern

  @name("thd_continue")
  def resume(thread: pthread_t): CInt = extern

  @name("thd_suspend")
  def suspend(thread: pthread_t): CInt = extern

}
