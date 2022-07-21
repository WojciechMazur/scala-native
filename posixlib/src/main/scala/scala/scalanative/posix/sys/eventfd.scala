package scala.scalanative.posix.sys

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

@extern object eventfd {
  type eventfd_t = ULong
  /* Return file descriptor for generic event channel.  Set initial
  value to COUNT.  */
  def eventfd(initVal: UInt, flags: Int): Int = extern
  /* Read event counter and possibly wait for events.  */
  def eventfd_read(fd: Int, value: Ptr[eventfd_t]): Int = extern
  /* Increment event counter.  */
  def eventfd_write(fd: Int, value: eventfd_t): Int = extern

  @name("scalanative_efd_cloexec")
  def EFD_CLOEXEC: Int = extern
  @name("scalanative_efd_nonblock")
  def EFD_NONBLOCK: Int = extern
  @name("scalanative_efd_semaphore")
  def EFD_SEMAPHORE: Int = extern
}
