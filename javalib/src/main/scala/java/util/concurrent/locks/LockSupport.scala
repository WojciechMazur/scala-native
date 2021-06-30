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
import java.lang.Thread.State._

object LockSupport {
  def getBlocker(t: Thread): Object = t.parkBlocker.get()

  def park(): Unit = {
    val currentThread = Thread.currentThread()
    val nativeThread  = currentThread.nativeThread
    withLocked(nativeThread) {
      if (nativeThread.skipNextUnparkEvent) {
        nativeThread.skipNextUnparkEvent = false
      } else {
        nativeThread.state = NativeThread.State.Parked
        while (!wasUnparked(nativeThread)) {
          nativeThread match {
            case thread: PosixThread =>
              pthread_cond_wait(thread.condition, thread.lock) match {
                case 0 => ()
                case errno =>
                  throw new RuntimeException(
                    s"Waiting on thread unparking failed - ${fromCString(
                      string.strerror(errno))}")
              }
            case _ => ???
          }
        }
        nativeThread.state = NativeThread.State.Running
      }
    }
  }

  @alwaysinline
  private[this] def wasUnparked(nativeThread: NativeThread): Boolean = {
    nativeThread.state == NativeThread.State.Running
  }

  def park(blocker: Object): Unit = {
    setCurrentBlocker(blocker)
    park()
    setCurrentBlocker(null: Object)
  }

  def parkNanos(nanos: Long): Unit = {
    val currentThread = Thread.currentThread()
    val nativeThread  = currentThread.nativeThread

    withLocked(nativeThread) {
      if (nativeThread.skipNextUnparkEvent) {
        nativeThread.skipNextUnparkEvent = false
      } else {
        nativeThread.state = NativeThread.State.Parked

        nativeThread match {
          case _: PosixThread =>
            val deadlineSpec = stackalloc[timespec]

            val deadline            = System.nanoTime() + nanos
            val NanosecondsInSecond = 1000000000
            deadlineSpec.tv_sec = deadline / NanosecondsInSecond
            deadlineSpec.tv_nsec = deadline % NanosecondsInSecond
            waitForThreadUnparking(nativeThread, deadlineSpec)

          case _ => ???
        }
        nativeThread.state = NativeThread.State.Running
      }
    }
  }

  def parkNanos(blocker: Object, nanos: Long): Unit = {
    setCurrentBlocker(blocker)
    parkNanos(nanos)
    setCurrentBlocker(null: Object)
  }

  def parkUntil(deadline: Long): Unit = {
    val currentThread = Thread.currentThread()
    val nativeThread  = currentThread.nativeThread

    withLocked(nativeThread) {
      if (nativeThread.skipNextUnparkEvent) {
        nativeThread.skipNextUnparkEvent = false
      } else {
        nativeThread.state = NativeThread.State.Parked
        nativeThread match {
          case _: PosixThread =>
            val deadlineSpec         = stackalloc[timespec]
            val MillisecondsInSecond = 1000
            deadlineSpec.tv_sec = TimeUnit.MILLISECONDS.toSeconds(deadline)
            deadlineSpec.tv_nsec =
              TimeUnit.MILLISECONDS.toNanos(deadline % MillisecondsInSecond)
            waitForThreadUnparking(nativeThread, deadlineSpec)

          case _ => ???
        }
        nativeThread.state = NativeThread.State.Running
      }
    }
  }

  def parkUntil(blocker: Object, deadline: Long): Unit = {
    setCurrentBlocker(blocker)
    parkUntil(deadline)
    setCurrentBlocker(null: Object)
  }

  def unpark(thread: Thread): Unit = {
    if (thread != null) {
      val nativeThread = thread.nativeThread
      withLocked(nativeThread) {
        nativeThread.state match {
          case NativeThread.State.Parked =>
            nativeThread.state = NativeThread.State.Running
            nativeThread match {
              case thread: PosixThread =>
                pthread_cond_signal(thread.condition) match {
                  case 0 => ()
                  case errno =>
                    val errorMsg = fromCString(string.strerror(errno))
                    throw new RuntimeException(
                      s"Failed to signal thread unparking - $errorMsg")
                }
              case _ => ???
            }

          case _ =>
            // Race between park/unpark won, ignore next park event
            nativeThread.skipNextUnparkEvent = true
        }
      }
    }
  }

  @alwaysinline
  private def setCurrentBlocker(blocker: Object): Unit = {
    Thread.currentThread.parkBlocker.setOpaque(blocker)
  }

  private final val TimeoutCode = ETIMEDOUT

  @inline
  private def waitForThreadUnparking(thread: NativeThread,
                                     deadline: Ptr[timespec]): Unit = {
    while (!wasUnparked(thread)) {
      thread match {
        case thread: PosixThread =>
          pthread_cond_timedwait(thread.condition, thread.lock, deadline) match {
            case 0           => ()
            case TimeoutCode => thread.state = NativeThread.State.Running
            case errno =>
              val errorMsg = fromCString(string.strerror(errno))
              throw new RuntimeException(
                s"Failed to wait on thread unparking - $errorMsg")
          }
        case _ => ???
      }
    }
  }

  @inline
  private def withLocked(thread: NativeThread)(fn: => Unit): Unit = {
    @alwaysinline
    def checkResult(res: Int, op: => String): Unit = {
      res match {
        case 0 => ()
        case errCode =>
          val errorMsg = fromCString(string.strerror(errCode))
          throw new RuntimeException(
            s"Failed to $op @ ${Thread.currentThread()} - $errorMsg")
      }
    }
    val lock = thread match {
      case nativeThread: PosixThread => nativeThread.lock
      case other =>
        sys.error(s"withLocked support for $other not implemented, thread=${Thread
          .currentThread()}, nativeThread=${Thread.currentThread().nativeThread}")
    }

    checkResult(pthread_mutex_lock(lock), "lock")
    try {
      fn
    } finally {
      checkResult(pthread_mutex_unlock(lock), "unlock")
    }
  }
}
