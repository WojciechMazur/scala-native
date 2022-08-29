package java.lang.impl

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport

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
import scala.scalanative.posix.errno.{ETIMEDOUT, EINTR}
import scala.scalanative.posix.sys.eventfd._
import scala.scalanative.posix.poll._
import scala.scalanative.posix.unistd._
import scala.scalanative.libc.errno
import scala.scalanative.libc.signal.{SIGUSR1 => _, _}
import scala.scalanative.posix
import scala.scalanative.runtime.ByteArray
import scala.scalanative.libc.string.strerror
import scala.scalanative.runtime.GC.MutatorThread
import scala.scalanative.libc.stdlib.malloc

private[java] case class PosixThread(handle: pthread_t, thread: Thread)
    extends NativeThread {
  import PosixThread._

  @volatile private[impl] var sleepEvent = UnsetEvent

  private[this] val nativeArray = new Array[scala.Byte](InnerBufferSize)
    .asInstanceOf[ByteArray]

  private[java] val lock: Ptr[pthread_mutex_t] =
    nativeArray
      .at(LockOffset)
      .asInstanceOf[Ptr[pthread_mutex_t]]

  private[java] val conditions =
    nativeArray
      .at(ConditionsOffset)
      .asInstanceOf[Ptr[pthread_cond_t]]

  def condition(idx: Int): Ptr[pthread_cond_t] = idx match {
    case 0 => conditions
    case 1 =>
      fromRawPtr(
        Intrinsics.elemRawPtr(
          toRawPtr(conditions),
          pthread_cond_t_size.toInt
        )
      )
  }

  assert(0 == pthread_mutex_init(lock, mutexAttr))
  assert(
    0 == pthread_cond_init(
      condition(ConditionRelativeIdx),
      clockMonotonicCondAttr
    )
  )
  assert(0 == pthread_cond_init(condition(ConditionAbsoluteIdx), null))

  override def onTermination(): Unit = {
    super.onTermination()
    pthread_cond_destroy(condition(0))
    pthread_cond_destroy(condition(1))
    pthread_mutex_destroy(lock)
  }

  def setPriority(priority: CInt): Unit = {
    val schedParam = stackalloc[sched_param]()
    val policy = stackalloc[CInt]()
    pthread_getschedparam(handle, policy, schedParam)
    schedParam.priority = priority
    pthread_setschedparam(handle, !policy, schedParam)
  }

  @alwaysinline def withGCSafeZone[T](fn: => T) = {
    val prev = MutatorThread.switchState(MutatorThread.State.InSafeZone)
    try fn
    finally MutatorThread.switchState(MutatorThread.State.Running)
  }

  def resume(): Unit = withGCSafeZone {
    pthread_mutex_lock(lock)
    while (state == NativeThread.State.Waiting) {
      state = NativeThread.State.Running
      pthread_cond_signal(condition(ConditionRelativeIdx))
    }
    pthread_mutex_unlock(lock)
  }

  def suspend(): Unit = withGCSafeZone {
    pthread_mutex_lock(lock)
    state = NativeThread.State.Waiting
    while (state == NativeThread.State.Waiting) {
      pthread_cond_wait(condition(ConditionRelativeIdx), lock)
    }
    pthread_mutex_unlock(lock)
  }

  def stop(): Unit = GCExt.GC_pthread_cancel(handle) match {
    case 0   => state = NativeThread.State.Terminated
    case err => throw new RuntimeException("Failed to stop thread")
  }

  def interrupt(): Unit = {
    // for LockSupport.park
    this.unpark()
    // for Thread.sleep
    if (sleepEvent != UnsetEvent) {
      val eventSize = 8.toUInt
      val buf = stackalloc[Byte](eventSize)
      !buf = 1

      val res = write(sleepEvent, buf, eventSize)
      assert(res != -1)
    }
  }

  @volatile private var counter: Int = 0
  @volatile private var conditionIdx = -1 // index of currently used condition

  import scala.scalanative.libc.atomic._
  import scala.scalanative.runtime.Intrinsics
  final private val counterAtromic = new CAtomicInt(
    fromRawPtr(Intrinsics.classFieldRawPtr(this, "counter"))
  )
  override def park(): Unit =
    park(0, isAbsolute = false)
  override def parkNanos(nanos: Long): Unit = if (nanos > 0) {
    park(nanos, isAbsolute = false)
  }
  override def parkUntil(deadline: scala.Long): Unit =
    park(deadline, isAbsolute = true)

  private def park(time: Long, isAbsolute: Boolean): Unit = {
    // fast-path check, return if can skip parking
    if (counterAtromic.exchange(0) > 0) return
    // Avoid parking if there's an interrupt pending
    if (thread.isInterrupted()) return
    // Don't wait at all
    if (time < 0 || (isAbsolute && time == 0)) return
    val absTime = stackalloc[timespec]()
    if (time > 0) toAbsoluteTime(absTime, time, isAbsolute)
    // Interference with ongoing unpark
    if (pthread_mutex_trylock(lock) != 0) return

    try {
      if (counter > 0) { // no wait needed
        counter = 0
        return
      }

      if (time == 0) {
        assert(conditionIdx == -1, "conditiond idx")
        conditionIdx = 0
        state = NativeThread.State.ParkedWaiting
        val cond = condition(conditionIdx)
        val status = withGCSafeZone(pthread_cond_wait(cond, lock))
        assert(
          status == 0 ||
            (scalanative.runtime.Platform.isMac() && status == ETIMEDOUT),
          "park, wait"
        )
      } else {
        assert(conditionIdx == -1, "conditiond idx")
        conditionIdx =
          if (isAbsolute) ConditionAbsoluteIdx else ConditionRelativeIdx
        state = NativeThread.State.ParkedWaitingTimed
        val status = withGCSafeZone {
          pthread_cond_timedwait(condition(conditionIdx), lock, absTime)
        }
        assert(status == 0 || status == ETIMEDOUT, "park, timed-wait")
      }

      conditionIdx = -1
      counter = 0
    } finally {
      state = NativeThread.State.Running
      val status = pthread_mutex_unlock(lock)
      assert(status == 0, "park, unlock")
      atomic_thread_fence(memory_order.memory_order_seq_cst)
    }
  }

  override def unpark(): Unit = {
    assert(withGCSafeZone(pthread_mutex_lock(lock)) == 0, "unpark, lock")
    val s = counter
    counter = 1
    val index = conditionIdx
    assert(pthread_mutex_unlock(lock) == 0, "unpark, unlock")

    if (s < 1 && index != -1) {
      assert(
        0 == pthread_cond_signal(condition(index)),
        "unpark, signal"
      )
    }
  }

  private def toAbsoluteTime(
      abstime: Ptr[timespec],
      _timeout: Long,
      isAbsolute: Boolean
  ) = {
    val timeout = if (_timeout < 0) 0 else _timeout
    val clock = if (isAbsolute) CLOCK_REALTIME else CLOCK_MONOTONIC
    val now = stackalloc[timespec]()
    clock_gettime(clock, now)
    if (isAbsolute) unpackAbsoluteTime(abstime, timeout, now.tv_sec)
    else calculateRelativeTime(abstime, timeout, now)
  }

  private def calculateRelativeTime(
      abstime: Ptr[timespec],
      timeout: Long,
      now: Ptr[timespec]
  ) = {
    val maxSeconds = now.tv_sec + MaxSeconds
    val seconds = timeout / NanonsInSecond
    if (seconds > maxSeconds) {
      abstime.tv_sec = maxSeconds
      abstime.tv_nsec = 0
    } else {
      abstime.tv_sec = now.tv_sec + seconds
      val nanos = now.tv_nsec + (timeout % NanonsInSecond)
      abstime.tv_nsec = if (nanos >= NanonsInSecond) {
        abstime.tv_sec += 1
        nanos - NanonsInSecond
      } else nanos
    }
  }

  private final val MaxSeconds = 100000000
  private final val MillisInSecond = 1000
  private final val NanonsInSecond = 1000000000
  private final val NanosInMillisecond = NanonsInSecond / MillisInSecond

  private def unpackAbsoluteTime(
      abstime: Ptr[timespec],
      deadline: Long,
      nowSeconds: Long
  ) = {
    val maxSeconds = nowSeconds + MaxSeconds
    val seconds = deadline / MillisInSecond
    val millis = deadline % MillisInSecond

    if (seconds >= maxSeconds) {
      abstime.tv_sec = maxSeconds
      abstime.tv_nsec = 0
    } else {
      abstime.tv_sec = seconds
      abstime.tv_nsec = millis * NanosInMillisecond
    }

    assert(abstime.tv_sec <= maxSeconds, "tvSec")
    assert(abstime.tv_nsec <= NanonsInSecond, "tvNSec")
  }

  @inline def tryPark(): Unit = ???
  @inline def tryParkUntil(deadline: scala.Long): Unit = ???
  @inline def tryParkNanos(nanos: scala.Long): Unit = ???
  @inline def tryUnpark(): Unit = ???

  @inline
  def withParkingLock(fn: => Unit): Unit = ???
}

private[lang] object PosixThread {
  import NativeThread._
  @extern
  @link("pthread")
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
  private final val ConditionsOffset = LockOffset + pthread_mutex_t_size.toInt

  private final val ConditionRelativeIdx = 0
  private final val ConditionAbsoluteIdx = 1

  private final val InnerBufferSize =
    (pthread_mutex_t_size + pthread_cond_t_size * 2.toUInt).toInt

  private lazy val clockMonotonicCondAttr = {
    val condAttr = malloc(sizeof[pthread_condattr_t])
      .asInstanceOf[Ptr[pthread_condattr_t]]
    assert(condAttr != null)
    assert(0 == pthread_condattr_init(condAttr))
    assert(0 == pthread_condattr_setclock(condAttr, CLOCK_MONOTONIC))
    condAttr
  }

  private lazy val mutexAttr = {
    val attr = malloc(sizeof[pthread_mutexattr_t])
      .asInstanceOf[Ptr[pthread_mutexattr_t]]
    assert(0 == pthread_mutexattr_init(attr))
    assert(0 == pthread_mutexattr_settype(attr, PTHREAD_MUTEX_NORMAL))
    attr
  }
  def apply(thread: Thread): PosixThread = {
    import GCExt._
    val id = stackalloc[pthread_t]()

    GC_pthread_create(
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

  private val UnsetEvent = -1

  def sleep(_millis: scala.Long, nanos: scala.Int): Unit = {
    val nativeThread =
      Thread.currentThread().nativeThread.asInstanceOf[PosixThread]

    var millis = if (nanos > 0) _millis + 1 else _millis
    if (millis <= 0) return
    val deadline = System.currentTimeMillis() + millis

    import scala.scalanative.posix.pollOps._
    import scala.scalanative.posix.pollEvents._

    val sleepEvent = eventfd(0.toUInt, 0)
    assert(sleepEvent != -1, "sleep event")
    nativeThread.sleepEvent = sleepEvent

    val fds = stackalloc[struct_pollfd]()
    fds.fd = sleepEvent
    fds.events = POLLIN

    try
      while (true) {
        if (Thread.interrupted())
          throw new InterruptedException("Sleep was interrupted")
        if (millis <= 0) return ()

        val status = nativeThread.withGCSafeZone {
          poll(fds, 1.toUInt, (millis min Int.MaxValue).toInt)
        }
        assert(
          status == 0 || errno.errno == EINTR,
          s"sleep, ${fromCString(strerror(errno.errno))}"
        )

        millis = deadline - System.currentTimeMillis()
      }
    finally nativeThread.sleepEvent = UnsetEvent
  }

  def yieldThread(): Unit = sched_yield()
}
