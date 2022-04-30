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
    setCurrentBlocker(blocker)
    park()
    setCurrentBlocker(null: Object)
  }

  def parkNanos(nanos: Long): Unit = {
    Thread
      .currentThread()
      .nativeThread
      .parkNanos(nanos)
  }

  def parkNanos(blocker: Object, nanos: Long): Unit = {
    setCurrentBlocker(blocker)
    parkNanos(nanos)
    setCurrentBlocker(null: Object)
  }

  def parkUntil(deadline: Long): Unit = {
    Thread
      .currentThread()
      .nativeThread
      .parkUntil(deadline)
  }

  def parkUntil(blocker: Object, deadline: Long): Unit = {
    setCurrentBlocker(blocker)
    parkUntil(deadline)
    setCurrentBlocker(null: Object)
  }

  def unpark(thread: Thread): Unit = {
    if (thread != null) {
      thread.nativeThread.unpark()
    }
  }

  @alwaysinline
  private def setCurrentBlocker(blocker: Object): Unit = {
    Thread.currentThread().parkBlocker.setOpaque(blocker)
  }
}
