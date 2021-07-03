package java.lang

import scala.collection.mutable
import scala.annotation._
import scala.scalanative.annotation._
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.unsafe.CFuncPtr1.fromScalaFunction
import scala.scalanative.runtime.{fromRawPtr, toRawPtr}
import scala.scalanative.runtime.Intrinsics.{
  castRawPtrToObject,
  castObjectToRawPtr
}

import scala.scalanative.posix.sys.types._
import scala.scalanative.posix.sched._
import scala.scalanative.posix.schedOps._
import scala.scalanative.posix.pthread._
import scala.scalanative.posix.signal.{pthread_kill => _, _}
import scala.scalanative.posix.signalOps._
import scala.scalanative.libc.signal.{SIGUSR1 => _, _}
import scala.scalanative.posix
import scala.scalanative.runtime.ByteArray
import scala.scalanative.windows._
import scala.scalanative.windows.HandleApi._

import scala.scalanative.meta.LinktimeInfo.isWindows

trait NativeThread {
  @volatile var state: NativeThread.State = NativeThread.State.New
  @volatile var skipNextUnparkEvent       = true

  def thread: Thread
  def setPriority(priority: CInt): Unit
  def resume(): Unit
  def suspend(): Unit
  def stop(): Unit

  protected def tryPark(): Unit
  protected def tryParkUntil(deadline: scala.Long): Unit
  protected def tryParkNanos(nanos: scala.Long): Unit
  protected def tryUnpark(): Unit
  protected def withParkingLock(fn: => Unit): Unit

  def park(): Unit = {
    withParkingLock {
      if (skipNextUnparkEvent) {
        skipNextUnparkEvent = false
      } else {
        state = NativeThread.State.Parked
        while (state != NativeThread.State.Running) {
          tryPark()
        }
        state = NativeThread.State.Running
      }
    }
  }

  def parkNanos(nanos: Long): Unit = {
    withParkingLock {
      if (skipNextUnparkEvent) {
        skipNextUnparkEvent = false
      } else {
        state = NativeThread.State.Parked
        tryParkNanos(nanos)
        state = NativeThread.State.Running
      }
    }
  }

  def parkUntil(deadline: Long): Unit = {
    withParkingLock {
      if (skipNextUnparkEvent) {
        skipNextUnparkEvent = false
      } else {
        state = NativeThread.State.Parked
        tryParkUntil(deadline)
        state = NativeThread.State.Running
      }
    }
  }

  def unpark(): Unit = {
    withParkingLock {
      state match {
        case NativeThread.State.Parked =>
          state = NativeThread.State.Running
          tryUnpark()

        case _ =>
          // Race between park/unpark won, ignore next park event
          skipNextUnparkEvent = true
      }
    }
  }

  protected def onTermination(): Unit = {
    state = NativeThread.State.Terminated
  }

}

object NativeThread {
  type PtrAny             = Ptr[scala.Byte]
  type ThreadRoutineArg   = PtrAny
  type ThreadStartRoutine = CFuncPtr1[PtrAny, PtrAny]

  import impl.{PosixThread, WindowsThread}
  final val DefaultStackSize: CSize = Config.defaultStackSize()
  final val DefaultPriority: Int    = Config.defaultPriority()
  final val MaxPriority: Int        = Config.maxPriority()
  final val MinPriority: Int        = Config.minPriority()

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

  private def threadEntryPoint(thread: Thread): Unit = {
    Thread.TLS.currentThread = thread
    thread.started = true
    thread.alive = true
    try {
      // Wait until Java thread assigns this NativeThread
      while (thread.nativeThread == null) Thread.onSpinWait()
      thread.run()
    } catch {
      case ex: Throwable => ex.printStackTrace()
    } finally {
      thread.nativeThread.state = NativeThread.State.Terminated
      thread.alive = false
      thread.getThreadGroup().remove(thread)
      try {
        thread.nativeThread.onTermination()
      } catch {
        case ex: Throwable => ()
      }
    }
  }

  def threadRoutineArgs(thread: Thread): ThreadRoutineArg = {
    fromRawPtr[scala.Byte](castObjectToRawPtr(thread))
  }

  def threadRoutine: ThreadStartRoutine = {
    CFuncPtr1.fromScalaFunction { arg: PtrAny =>
      val thread = castRawPtrToObject(toRawPtr(arg))
        .asInstanceOf[Thread]

      NativeThread.threadEntryPoint(thread)

      null: PtrAny
    }
  }

  @alwaysinline
  def apply(thread: Thread): NativeThread =
    if (isWindows) WindowsThread(thread)
    else PosixThread(thread)

  @alwaysinline
  def `yield`(): Unit =
    if (isWindows) WindowsThread.yieldThread()
    else PosixThread.yieldThread()

  @alwaysinline
  def sleep(millis: scala.Long, nanos: scala.Int): Unit =
    if (isWindows) WindowsThread.sleep(millis, nanos)
    else PosixThread.sleep(millis, nanos)

  @extern
  object Intrinsics {
    @name("scalanative_yieldProcessor")
    def yieldProcessor(): Unit = extern
  }

  @extern
  object Config {
    @name("scalanative_get_max_priority")
    def maxPriority(): Int = extern

    @name("scalanative_get_min_priority")
    def minPriority(): Int = extern

    @name("scalanative_get_norm_priority")
    def defaultPriority(): Int = extern

    @name("scalanative_get_stack_size")
    def defaultStackSize(): CSize = extern
  }

}
