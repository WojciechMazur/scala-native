package java.util.concurrent.locks

import scala.scalanative.annotation.alwaysinline
import scala.annotation._

import scala.scalanative.unsafe._
import scala.scalanative.posix.pthread._
import scala.scalanative.posix.sys.types._
import scala.scalanative.posix.time._
import scala.scalanative.posix.timeOps._
import scala.scalanative.runtime.ByteArray
import scala.collection.mutable
import scala.collection.immutable
import java.util.concurrent.TimeUnit
import scala.scalanative.posix
import scala.scalanative.posix.errno.ETIMEDOUT
import scala.scalanative.libc.string
import java.lang.impl._

object LockSupport {
  def getBlocker(t: Thread): Object = t.parkBlocker.get()

  def park(): Unit = {
    Thread
      .currentThread()
      .nativeThread
      .park()
  }

  def park(blocker: Object): Unit = {
    val thread = Thread.currentThread()
    setBlocker(thread, blocker)
    park()
    setBlocker(thread, null: Object)
  }

  def parkNanos(nanos: Long): Unit = {
    Thread
      .currentThread()
      .nativeThread
      .parkNanos(nanos)
  }

  def parkNanos(blocker: Object, nanos: Long): Unit = if (nanos > 0) {
    val thread = Thread.currentThread()
    setBlocker(thread, blocker)
    parkNanos(nanos)
    setBlocker(thread, null: Object)
  }

  def parkUntil(deadline: Long): Unit = {
    Thread
      .currentThread()
      .nativeThread
      .parkUntil(deadline)
  }

  def parkUntil(blocker: Object, deadline: Long): Unit = {
    val thread = Thread.currentThread()
    setBlocker(thread, blocker)
    parkUntil(deadline)
    setBlocker(thread, null: Object)
  }

  def unpark(thread: Thread): Unit = if (thread != null) {
    thread.nativeThread.unpark()
  }

  @alwaysinline private def setBlocker(
      thread: Thread,
      blocker: Object
  ): Unit = {
    thread.parkBlocker.setOpaque(blocker)
  }

  /** Sets the object to be returned by invocations of {@link #getBlocker
   *  getBlocker} for the current thread. This method may be used before
   *  invoking the no-argument version of {@link LockSupport#park() park()} from
   *  non-public objects, allowing more helpful diagnostics, or retaining
   *  compatibility with previous implementations of blocking methods. Previous
   *  values of the blocker are not automatically restored after blocking. To
   *  obtain the effects of {@code park(b}}, use {@code setCurrentBlocker(b);
   *  park(); setCurrentBlocker(null);}
   *
   *  @param blocker
   *    the blocker object
   *  @since 14
   */
  @alwaysinline def setCurrentBlocker(blocker: Object): Unit =
    // TODO: parkBlocker can be accessed using CAtomics
    Thread.currentThread().parkBlocker.setOpaque(blocker)

  private[locks] def getThreadId(thread: Thread) = thread.threadId
}
