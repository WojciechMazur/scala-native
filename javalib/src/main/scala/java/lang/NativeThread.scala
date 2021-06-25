package java.lang

import scala.collection.mutable
import scala.annotation._
import scala.scalanative.annotation._
import scala.scalanative.unsafe._
import scala.scalanative.unsafe.CFuncPtr1.fromScalaFunction
import scala.scalanative.unsigned._
import scala.scalanative.runtime.{
  Intrinsics,
  fromRawPtr,
  toRawPtr,
  NativeThread => NThread
}

import scala.scalanative.posix.sys.types._
import scala.scalanative.posix.sched._
import scala.scalanative.posix.schedOps._
import scala.scalanative.posix.pthread._
import scala.scalanative.posix.signal.{pthread_kill => _, _}
import scala.scalanative.posix.signalOps._
import scala.scalanative.libc.signal.{SIGUSR1 => _, _}
import java.util.concurrent.atomic.AtomicInteger
import scala.scalanative.posix
import scala.scalanative.runtime.ByteArray

@extern
object GCExt {

  @name("scalanative_gc_pthread_create")
  def pthread_create(thread: Ptr[pthread_t],
                     attr: Ptr[pthread_attr_t],
                     startroutine: CFuncPtr1[Ptr[scala.Byte], Ptr[scala.Byte]],
                     args: Ptr[scala.Byte]): CInt = extern
  @name("scalanative_gc_pthread_join")
  def pthread_join(thread: pthread_t, value_ptr: Ptr[Ptr[Byte]]): CInt = extern

  @name("scalanative_gc_pthread_detach")
  def pthread_detach(thread: pthread_t): CInt = extern

  @name("scalanative_gc_pthread_cancel")
  def pthread_cancel(thread: pthread_t): CInt = extern

  @name("scalanative_gc_pthread_exit")
  def pthread_exit(retVal: Ptr[Byte]): Unit = extern
}

sealed trait NativeThreadFactory {
  def startThread(thread: Thread): NativeThread

  def MaxPriority: Int
  def MinPriority: Int
  def DefaultPriority: Int
  def DefaultStackSize: CSize

  protected def threadEntryPoint(thread: Thread): Unit = {
    NativeThread.TLS.currentThread = thread
    thread.started = true
    thread.alive = true
    try {
      thread.run()
    } catch {
      case ex: Throwable => ex.printStackTrace()
    } finally {
      thread.underlying.state = NativeThread.State.Terminated
      thread.alive = false
      thread.getThreadGroup().remove(thread)
    }
  }

}

trait NativeThread {
  type ThreadRef

  def thread: Thread
  def setPriority(priority: CInt): Unit
  def resume(): Unit
  def suspend(): Unit
  def stop(): Unit

  @volatile var state: NativeThread.State = NativeThread.State.New
  var unparkEvents                        = 0
}

object NativeThread {
  def factory: NativeThreadFactory = PosixThread

  @alwaysinline
  def `yield`(): Unit = sched_yield()

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

  // Thread Local Storage
  @extern
  object TLS {
    @name("scalanative_set_currentThread")
    def currentThread_=(thread: Thread): Unit = extern

    @name("scalanative_currentThread")
    def currentThread: Thread = extern
  }

  @extern
  object Intrinsics {
    @name("scalanative_yieldProcessor")
    def yieldProcessor(): Unit = extern
  }

  sealed trait State
  object State {
    case object New                   extends State
    case object Running               extends State
    case object Waiting               extends State
    case object WaitingWithTimeout    extends State
    case object WaitingOnMonitorEnter extends State
    case object Parked                extends State
    case object Terminated            extends State
  }

}

private[java] case class PosixThread(underlying: pthread_t, thread: Thread)
    extends NativeThread {
  import PosixThread._
  type ThreadRef = pthread_t

  private[this] val nativeArray =
    new Array[scala.Byte](InnerBufferSize).asInstanceOf[ByteArray]

  {
    val mutexAttr = stackalloc[pthread_mutexattr_t]
    assert(0 == pthread_mutexattr_init(mutexAttr))
    assert(0 == pthread_mutexattr_settype(mutexAttr, PTHREAD_MUTEX_RECURSIVE))

    assert(0 == pthread_mutex_init(lock, mutexAttr))
    assert(0 == pthread_cond_init(condition, null))

    assert(0 == pthread_mutex_init(suspendLock, mutexAttr))
    assert(0 == pthread_cond_init(suspendCondition, null))
  }

  @alwaysinline
  private[java] def lock: Ptr[pthread_mutex_t] =
    nativeArray.at(LockOffset).asInstanceOf[Ptr[pthread_mutex_t]]
  @alwaysinline
  private[java] def condition: Ptr[pthread_cond_t] =
    nativeArray.at(ConditionOffset).asInstanceOf[Ptr[pthread_cond_t]]

  @alwaysinline
  private[this] def suspendLock: Ptr[pthread_mutex_t] =
    nativeArray.at(SuspendLockOffset).asInstanceOf[Ptr[pthread_mutex_t]]
  @alwaysinline
  private[this] def suspendCondition: Ptr[pthread_cond_t] =
    nativeArray.at(SuspendConditionOffset).asInstanceOf[Ptr[pthread_cond_t]]

  def setPriority(priority: CInt): Unit = {
    val schedParam = stackalloc[sched_param]
    val policy     = stackalloc[CInt]
    pthread_getschedparam(underlying, policy, schedParam)
    schedParam.priority = priority
    pthread_setschedparam(underlying, !policy, schedParam)
  }

  def resume(): Unit = {
    val lock      = suspendLock
    val condition = suspendCondition
    pthread_mutex_lock(lock)
    while (state == NativeThread.State.Waiting) {
      state = NativeThread.State.Running
      pthread_cond_signal(condition)
    }
    pthread_mutex_unlock(lock)
  }

  def suspend(): Unit = {
    val lock      = suspendLock
    val condition = suspendCondition
    pthread_mutex_lock(lock)
    state = NativeThread.State.Waiting
    while (state == NativeThread.State.Waiting) {
      pthread_cond_wait(condition, lock)
    }
    pthread_mutex_unlock(lock)
  }

  def stop(): Unit = GCExt.pthread_cancel(underlying) match {
    case 0   => state = NativeThread.State.Terminated
    case err => throw new RuntimeException("Faield to stop thread")
  }

}

private[lang] object PosixThread extends NativeThreadFactory {
  private final val LockOffset      = 0
  private final val ConditionOffset = LockOffset + pthread_mutex_t_size.toInt
  private final val SuspendLockOffset =
    ConditionOffset + pthread_cond_t_size.toInt
  private final val SuspendConditionOffset =
    SuspendLockOffset + pthread_mutex_t_size.toInt

  private final val InnerBufferSize =
    (pthread_mutex_t_size + pthread_cond_t_size).toInt * 2

  final val DefaultStackSize: CSize = NThread.THREAD_DEFAULT_STACK_SIZE
  final val DefaultPriority: Int    = NThread.THREAD_NORM_PRIORITY
  final val MaxPriority: Int        = NThread.THREAD_MAX_PRIORITY
  final val MinPriority: Int        = NThread.THREAD_MIN_PRIORITY

  def startThread(thread: Thread): PosixThread = {
    type Routine = CFuncPtr1[Ptr[scala.Byte], Ptr[scala.Byte]]
    val id  = stackalloc[pthread_t]
    val arg = fromRawPtr[scala.Byte](Intrinsics.castObjectToRawPtr(thread))

    val routine: Routine = { arg: Ptr[scala.Byte] =>
      val thread =
        Intrinsics
          .castRawPtrToObject(toRawPtr(arg))
          .asInstanceOf[Thread]

      threadEntryPoint(thread)

      null: Ptr[scala.Byte]
    }

    val status =
      GCExt.pthread_create(thread = id,
                           attr = null: Ptr[pthread_attr_t],
                           startroutine = routine,
                           args = arg)
    if (status != 0)
      throw new Exception(
        "Failed to create new thread, pthread error " + status)

    new PosixThread(!id, thread)
  }

  @extern
  private[lang] object Ext {
    @name("posixthread_get_max_priority")
    def maxPriority(): Int = extern

    @name("posixthread_get_min_priority")
    def minPriority(): Int = extern

    @name("posixthread_get_norm_priority")
    def defaultPriority(): Int = extern

    @name("posixthread_get_stack_size")
    def defaultStackSize(): CSize = extern
  }
}
