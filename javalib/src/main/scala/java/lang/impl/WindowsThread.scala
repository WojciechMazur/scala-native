package java.lang.impl

import scala.scalanative.annotation._
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.runtime.{fromRawPtr, toRawPtr, Intrinsics, ByteArray}
import scala.scalanative.windows._
import scala.scalanative.windows.HandleApi._
import scala.scalanative.windows.ErrorHandlingApi._
import scala.scalanative.windows.ProcessThreadsApi._
import scala.scalanative.windows.SynchApi._
import scala.scalanative.windows.WinBaseApi._
import scala.annotation.tailrec

private[java] case class WindowsThread(handle: Handle, id: UInt, thread: Thread)
    extends NativeThread {
  import WindowsThread._

  private[this] val internalBuffer = new Array[scala.Byte](InnerBufferSize)
    .asInstanceOf[ByteArray]

  final val threadParkingSection: CriticalSection =
    internalBuffer
      .at(ThreadParkingSectionOffset)

  final val isUnparked: ConditionVariable =
    internalBuffer
      .at(IsUnparkedConditionOffset)

  {
    assert(internalBuffer != null, "internal buffer not initialized")
    InitializeCriticalSectionAndSpinCount(threadParkingSection, 4.toUInt)
    InitializeConditionVariable(isUnparked)
  }

  override protected def onTermination() {
    super.onTermination()
    DeleteCriticalSection(threadParkingSection)
    CloseHandle(handle)
  }

  @stub def setPriority(priority: CInt): Unit = () // TODO

  @inline
  def resume(): Unit = {
    ResumeThread(handle).toInt match {
      case -1 =>
        throw new RuntimeException(
          s"Failed to resume thread: errCode=${GetLastError()}"
        )
      case prev => ()
    }
  }

  @inline
  def suspend(): Unit = {
    SuspendThread(handle).toInt match {
      case -1 =>
        throw new RuntimeException(
          s"Failed to suspend thread: errCode=${GetLastError()}"
        )
      case prev => ()
    }
  }

  @inline
  def stop(): Unit = ExitThread(0.toUInt)

  @inline
  def tryPark(): Unit = {
    if (!SleepConditionVariableCS(isUnparked, threadParkingSection, Infinite)) {
      GetLastError() match {
        case ErrorCodes.ERROR_TIMEOUT =>
          state = NativeThread.State.Running
        case errCode =>
          throw new RuntimeException(s"Failed to park thread: errCode=$errCode")
      }
    }
  }

  @alwaysinline
  def tryParkUntil(deadline: scala.Long): Unit = tryParkTimed(deadline)

  @alwaysinline
  def tryParkNanos(nanos: scala.Long): Unit = {
    val NanosecondsInMillisecond = 1000000
    val deadline = (System.nanoTime + nanos) / NanosecondsInMillisecond
    tryParkTimed(deadline)
  }

  @alwaysinline
  def tryUnpark(): Unit = {
    WakeConditionVariable(isUnparked)
  }

  @inline @tailrec
  private def tryParkTimed(deadline: scala.Long): Unit = {
    val milliseconds = System.currentTimeMillis() - deadline
    val successfull =
      if (milliseconds > 0L) {
        SleepConditionVariableCS(
          isUnparked,
          threadParkingSection,
          milliseconds.toUInt
        )
      } else {
        state = NativeThread.State.Running
        true
      }

    if (successfull) {
      if (state == NativeThread.State.Parked) {
        // spurious wakeup, retry
        tryParkTimed(deadline)
      }
    } else {
      GetLastError() match {
        case ErrorCodes.ERROR_TIMEOUT =>
          state = NativeThread.State.Running
        case errCode =>
          throw new RuntimeException(s"Failed to park thread: errCode=$errCode")
      }
    }
  }

  @alwaysinline
  def withParkingLock(fn: => Unit): Unit = {
    EnterCriticalSection(threadParkingSection)
    try {
      fn
    } finally {
      LeaveCriticalSection(threadParkingSection)
    }
  }
}

object WindowsThread {
  import NativeThread._
  @extern
  @link("gc") @link("user32")
  object GCExt {
    def GC_CreateThread(
        threadAttributes: Ptr[SecurityAttributes],
        stackSize: UWord,
        startRoutine: ThreadStartRoutine,
        routineArg: PtrAny,
        creationFlags: DWord,
        threadId: Ptr[DWord]
    ): Handle = extern
    def GC_ExitThread(exitCode: DWord): Unit = extern
  }

  private val InnerBufferSize = {
    SizeOfCriticalSection + SizeOfConditionVariable
  }.toInt

  private final val ThreadParkingSectionOffset = 0
  private final val IsUnparkedConditionOffset = SizeOfCriticalSection.toInt

  def apply(thread: Thread): WindowsThread = {
    import GCExt._
    val id = stackalloc[DWord]
    val handle = GC_CreateThread(
      threadAttributes = null,
      stackSize = 0.toUInt, // Default
      startRoutine = NativeThread.threadRoutine,
      routineArg = NativeThread.threadRoutineArgs(thread),
      creationFlags = 0.toUInt, // Default, run immediately,
      threadId = id
    )

    if (handle == null) {
      throw new RuntimeException(
        s"Failed to create new thread, errCode=${GetLastError()}"
      )
    }
    new WindowsThread(handle, !id, thread)
  }
  def sleep(millis: scala.Long, nanos: scala.Int): Unit = {
    // No support for nanos sleep on windows
    Sleep(millis.toUInt)
  }

  def yieldThread(): Unit = SwitchToThread()
}
