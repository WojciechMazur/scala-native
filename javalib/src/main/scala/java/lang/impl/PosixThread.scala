package java.lang.impl

import java.util.concurrent.TimeUnit

import scala.annotation._
import scala.scalanative.annotation._
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.unsafe.CFuncPtr1.fromScalaFunction
import scala.scalanative.runtime.{Intrinsics, fromRawPtr, toRawPtr}

import scala.scalanative.posix.sys.types._
import scala.scalanative.posix.time._
import scala.scalanative.posix.timeOps._
import scala.scalanative.posix.sched._
import scala.scalanative.posix.schedOps._
import scala.scalanative.posix.pthread._
import scala.scalanative.posix.signal.{pthread_kill => _, _}
import scala.scalanative.posix.signalOps._
import scala.scalanative.posix.errno.ETIMEDOUT
import scala.scalanative.libc.signal.{SIGUSR1 => _, _}
import scala.scalanative.posix
import scala.scalanative.runtime.ByteArray
import scala.scalanative.libc.string.strerror

private[java] case class PosixThread(handle: pthread_t, thread: Thread)
    extends NativeThread {
  import PosixThread._

  private[this] val nativeArray = new Array[scala.Byte](InnerBufferSize)
    .asInstanceOf[ByteArray]

  private[java] val lock: Ptr[pthread_mutex_t] = {
    nativeArray
      .at(LockOffset)
      .asInstanceOf[Ptr[pthread_mutex_t]]
  }

  private[java] val condition: Ptr[pthread_cond_t] = {
    nativeArray
      .at(ConditionOffset)
      .asInstanceOf[Ptr[pthread_cond_t]]
  }

  {
    val mutexAttr = stackalloc[pthread_mutexattr_t]
    assert(0 == pthread_mutexattr_init(mutexAttr))
    assert(0 == pthread_mutexattr_settype(mutexAttr, PTHREAD_MUTEX_RECURSIVE))

    assert(0 == pthread_mutex_init(lock, mutexAttr))
    assert(0 == pthread_cond_init(condition, null))
  }

  def setPriority(priority: CInt): Unit = {
    val schedParam = stackalloc[sched_param]
    val policy = stackalloc[CInt]
    pthread_getschedparam(handle, policy, schedParam)
    schedParam.priority = priority
    pthread_setschedparam(handle, !policy, schedParam)
  }

  def resume(): Unit = {
    pthread_mutex_lock(lock)
    while (state == NativeThread.State.Waiting) {
      state = NativeThread.State.Running
      pthread_cond_signal(condition)
    }
    pthread_mutex_unlock(lock)
  }

  def suspend(): Unit = {
    pthread_mutex_lock(lock)
    state = NativeThread.State.Waiting
    while (state == NativeThread.State.Waiting) {
      pthread_cond_wait(condition, lock)
    }
    pthread_mutex_unlock(lock)
  }

  def stop(): Unit = GCExt.GC_pthread_cancel(handle) match {
    case 0   => state = NativeThread.State.Terminated
    case err => throw new RuntimeException("Faield to stop thread")
  }

  @inline def tryPark(): Unit = {
    pthread_cond_wait(condition, lock) match {
      case 0 => ()
      case errno =>
        throw new RuntimeException(
          s"Failed to park thread - ${fromCString(strerror(errno))}"
        )
    }
  }

  @inline def tryParkUntil(deadline: scala.Long): Unit = {
    val deadlineSpec = stackalloc[timespec]
    val MillisecondsInSecond = 1000
    deadlineSpec.tv_sec = TimeUnit.MILLISECONDS.toSeconds(deadline)
    deadlineSpec.tv_nsec =
      TimeUnit.MILLISECONDS.toNanos(deadline % MillisecondsInSecond)
    waitForThreadUnparking(deadlineSpec)

  }

  @inline def tryParkNanos(nanos: scala.Long): Unit = {
    val deadlineSpec = stackalloc[timespec]

    val deadline = System.nanoTime() + nanos
    val NanosecondsInSecond = 1000000000
    deadlineSpec.tv_sec = deadline / NanosecondsInSecond
    deadlineSpec.tv_nsec = deadline % NanosecondsInSecond
    waitForThreadUnparking(deadlineSpec)
  }

  @inline def tryUnpark(): Unit = {
    pthread_cond_signal(condition) match {
      case 0 => ()
      case errno =>
        val errorMsg = fromCString(strerror(errno))
        throw new RuntimeException(
          s"Failed to signal thread unparking - $errorMsg"
        )
    }
  }

  private final val TimeoutCode = ETIMEDOUT

  @inline private def waitForThreadUnparking(deadline: Ptr[timespec]): Unit = {
    while (state == NativeThread.State.Parked) {
      pthread_cond_timedwait(condition, lock, deadline) match {
        case 0           => ()
        case TimeoutCode => state = NativeThread.State.Running
        case errno =>
          val errorMsg = fromCString(strerror(errno))
          throw new RuntimeException(
            s"Failed to wait on thread unparking - $errorMsg"
          )
      }
    }
  }

  @inline
  def withParkingLock(fn: => Unit): Unit = {
    @alwaysinline
    def checkResult(res: Int, op: => String): Unit = {
      res match {
        case 0 => ()
        case errCode =>
          val errorMsg = fromCString(strerror(errCode))
          throw new RuntimeException(
            s"Failed to $op @ ${Thread.currentThread()} - $errorMsg"
          )
      }
    }

    checkResult(pthread_mutex_lock(lock), "lock")
    try {
      fn
    } finally {
      checkResult(pthread_mutex_unlock(lock), "unlock")
    }
  }
}

private[lang] object PosixThread {
  import NativeThread._
  @extern
  @link("gc") @link("pthread")
  object GCExt {
    def GC_pthread_create(
        thread: Ptr[pthread_t],
        attr: Ptr[pthread_attr_t],
        startroutine: ThreadStartRoutine,
        args: PtrAny
    ): CInt = extern
    def GC_pthread_join(thread: pthread_t, value_ptr: Ptr[Ptr[Byte]]): CInt =
      extern
    def GC_pthread_detach(thread: pthread_t): CInt = extern
    def GC_pthread_cancel(thread: pthread_t): CInt = extern
    def GC_pthread_exit(retVal: Ptr[Byte]): Unit = extern
  }

  private final val LockOffset = 0
  private final val ConditionOffset = LockOffset + pthread_mutex_t_size.toInt

  private final val InnerBufferSize =
    (pthread_mutex_t_size + pthread_cond_t_size).toInt

  def apply(thread: Thread): PosixThread = {
    val id = stackalloc[pthread_t]

    GCExt.GC_pthread_create(
      thread = id,
      attr = null: Ptr[pthread_attr_t],
      startroutine = NativeThread.threadRoutine,
      args = NativeThread.threadRoutineArgs(thread)
    ) match {
      case 0 => ()
      case status =>
        throw new RuntimeException(
          "Failed to create new thread, pthread error " + status
        )
    }
    new PosixThread(!id, thread)
  }

  def sleep(millis: scala.Long, nanos: scala.Int): Unit = {
    import scala.scalanative.posix.errno.EINTR
    import scala.scalanative.posix.time._
    import scala.scalanative.posix.timeOps._
    import scala.scalanative.unsafe._
    import scala.scalanative.unsigned._
    import scala.scalanative.posix.unistd
    import scala.scalanative.libc.errno

    @tailrec
    def doSleep(requestedTime: Ptr[timespec]): Unit = {
      val remaining = stackalloc[timespec]

      unistd.nanosleep(requestedTime, remaining) match {
        case _ if Thread.interrupted() =>
          throw new InterruptedException("Sleep was interrupted")

        case -1 if errno.errno == EINTR =>
          doSleep(remaining)

        case _ => ()
      }
    }

    val requestedTime = stackalloc[timespec]
    requestedTime.tv_sec = millis / 1000
    requestedTime.tv_nsec = (millis % 1000) * 1e6.toInt + nanos

    doSleep(requestedTime)
  }

  def yieldThread(): Unit = sched_yield()
}
