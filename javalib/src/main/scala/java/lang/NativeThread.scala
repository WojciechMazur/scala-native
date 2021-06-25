package java.lang

import scala.collection.mutable
import scala.scalanative.annotation.alwaysinline
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

@extern
object GCExt {

  @name("scalanative_gc_pthread_create")
  def pthread_create(thread: Ptr[pthread_t],
                     attr: Ptr[pthread_attr_t],
                     startroutine: CFuncPtr1[Ptr[scala.Byte], Ptr[scala.Byte]],
                     args: Ptr[scala.Byte]): CInt = extern
  @name("scalanative_gc_pthread_join")
  def pthread_join(thread: pthread_t, value_ptr: Ptr[Ptr[Byte]]): CInt = extern
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
      thread.alive = false
    }

  }

}

sealed trait NativeThread {
  type ThreadRef

  def thread: Thread
  def setPriority(priority: CInt): Unit
  def resume(): Unit
  def suspend(): Unit
  def suspend(deadline: Long): Unit
  def stop(): Unit
  def toLong: Long
}

object NativeThread {
  def factory: NativeThreadFactory = PosixThread

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
}

private[lang] final case class PosixThread(underlying: pthread_t,
                                           thread: Thread)
    extends NativeThread {
  import PosixThread._
  type ThreadRef = pthread_t

  def setPriority(priority: CInt): Unit = {
    val schedParam = stackalloc[sched_param]
    val policy     = stackalloc[CInt]
    pthread_getschedparam(underlying, policy, schedParam)
    schedParam.priority = priority
    pthread_setschedparam(underlying, !policy, schedParam)
  }

  def resume(): Unit = PosixThread.resume(this)

  def suspend(): Unit = PosixThread.suspend(this)

  def suspend(forNanos: Long): Unit = {
    suspend()
    ???
  }

  def stop(): Unit = pthread_cancel(underlying) match {
    case 0   => ()
    case err => throw new RuntimeException("Faield to stop thread")
  }

  def toLong: Long = underlying.toLong

}

private[lang] object PosixThread extends NativeThreadFactory {
  private[this] val sentinel: AtomicInteger = new AtomicInteger(0)
  private[this] val suspendedThreads        = mutable.Set.empty[NativeThread]

  private final val SuspendSignal = SIGUSR1
  private final val ResumeSignal  = SIGUSR2

  locally {
    val suspendSigAction = stackalloc[sigaction]
    val resumeSigAction  = stackalloc[sigaction]

    suspendSigAction.sa_flags = 0
    suspendSigAction.sa_handler = onSuspend _
    sigemptyset(suspendSigAction.at2) // sa_mask

    if (sigaction(SuspendSignal, suspendSigAction, null) != 0) {
      throw new RuntimeException("Failed to setup suspend handler")
    }

    resumeSigAction.sa_flags = 0
    resumeSigAction.sa_handler = fromScalaFunction { _ => () }
    resumeSigAction.sa_mask = suspendSigAction.sa_mask

    if (sigaction(ResumeSignal, resumeSigAction, null) != 0) {
      throw new RuntimeException("Failed to setup resume handler")
    }
  }

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

  private def suspend(thread: PosixThread): Unit = synchronized {
    if (suspendedThreads.add(thread)) {
      suspendedThreads += thread
      sentinel.set(0)
      pthread_kill(thread.underlying, SuspendSignal) match {
        case 0 => ()
        case status =>
          throw new RuntimeException(
            s"Failed to suspend thread, status $status")
      }

      while (sentinel.get() == 0) sched_yield()
    }
  }

  private def resume(thread: PosixThread): Unit = synchronized {
    if (suspendedThreads.remove(thread)) {
      pthread_kill(thread.underlying, ResumeSignal) match {
        case 0 => ()
        case status =>
          throw new RuntimeException(s"Failed to resume thread, status $status")
      }
    }
  }

  private def onSuspend(sig: Int): Unit = {
    /*
     * Block all signals except ResumeSignal while suspended.
     */
    val signalSet = stackalloc[Byte](sizeof_sigset_t())
      .asInstanceOf[Ptr[sigset_t]]

    sigfillset(signalSet)
    sigdelset(signalSet, ResumeSignal)

    sentinel.set(1)
    sigsuspend(signalSet)
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
