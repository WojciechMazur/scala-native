package java.lang.impl

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport

import scala.annotation._
import scala.scalanative.annotation._
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.unsafe.CFuncPtr1.fromScalaFunction
import scala.scalanative.runtime._
import scala.scalanative.runtime.Intrinsics.{elemRawPtr, classFieldRawPtr}
import scala.scalanative.runtime.GC
import scala.scalanative.meta.LinktimeInfo._

import scala.scalanative.posix
import scala.scalanative.posix.sys.types._
import scala.scalanative.posix.time._
import scala.scalanative.posix.timeOps._
import scala.scalanative.posix.sched._
import scala.scalanative.posix.schedOps._
import scala.scalanative.posix.pthread._
import scala.scalanative.posix.signal._
import scala.scalanative.posix.signalOps._
import scala.scalanative.posix.errno._
import scala.scalanative.posix.poll._
import scala.scalanative.posix.unistd._

import scala.scalanative.libc.string.strerror
import scala.scalanative.libc.stdlib.malloc
import scala.scalanative.libc.atomic._

private[java] class PosixThread(val thread: Thread, stackSize: Long)
    extends NativeThread {
  import NativeThread._
  import PosixThread._

  private[this] val _state = new scala.Array[scala.Byte](StateSize)
  @volatile private[impl] var sleepInterruptEvent: CInt = UnsetEvent
  @volatile private var counter: Int = 0
  // index of currently used condition
  @volatile private var conditionIdx = ConditionUnset

  private val handle: pthread_t =
    if (isMainThread) 0.toULong // main thread
    else if (!isMultithreadingEnabled)
      throw new LinkageError(
        "Multithreading support disabled - cannot create new threads"
      )
    else {
      val id = stackalloc[pthread_t]()
      val attrs = stackalloc[Byte](pthread_attr_t_size)
        .asInstanceOf[Ptr[pthread_attr_t]]

      assert(
        0 == pthread_mutex_init(lock, mutexAttr),
        "PosixThread: mutext init failed"
      )
      assert(
        0 == pthread_cond_init(
          condition(ConditionRelativeIdx),
          conditionRelativeCondAttr
        ),
        "PosixThread: condition relative init failed"
      )
      assert(
        0 == pthread_cond_init(condition(ConditionAbsoluteIdx), null),
        "PosixThread: condition abs init failed"
      )

      assert(0 == pthread_attr_init(attrs), "PosixThread: attr init failed")
      assert(
        0 == pthread_attr_setdetachstate(attrs, PTHREAD_CREATE_DETACHED),
        "PosixThread: detach failed"
      )
      if (stackSize > 0L) {
        assert(
          0 == pthread_attr_setstacksize(attrs, stackSize.toUInt),
          "PosixThread: set stack size failed"
        )
      }
      try
        GC.pthread_create(
          thread = id,
          attr = attrs,
          startroutine = NativeThread.threadRoutine,
          args = NativeThread.threadRoutineArgs(this)
        ) match {
          case 0 => !id
          case status =>
            throw new RuntimeException(
              s"Failed to create new thread, pthread error $status"
            )
        }
      finally if (attrs != null) pthread_attr_destroy(attrs)
    }
  state = State.Running

  override def onTermination(): Unit = {
    super.onTermination()
    pthread_cond_destroy(condition(0))
    pthread_cond_destroy(condition(1))
    pthread_mutex_destroy(lock)
  }

  override def setPriority(priority: CInt): Unit = if (isMainThread) {
    val schedParam = stackalloc[sched_param]()
    val policy = stackalloc[CInt]()
    if (0 == pthread_getschedparam(handle, policy, schedParam)) {
      schedParam.priority = priorityMapping(priority, !policy)
      pthread_setschedparam(handle, !policy, schedParam)
    }
  }

  override def interrupt(): Unit = {
    // for LockSupport.park
    this.unpark()
    // for Thread.sleep
    if (sleepInterruptEvent != UnsetEvent) {
      val eventSize = 8.toUInt
      val buf = stackalloc[Byte](eventSize)
      !buf = 1
      val res = write(sleepInterruptEvent, buf, eventSize)
      assert(res != -1, "PosixThread, sleep interrupt")
    }
  }

  override protected def park(time: Long, isAbsolute: Boolean): Unit = {
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

      assert(conditionIdx == ConditionUnset, "conditiond idx")
      if (time == 0) {
        conditionIdx = ConditionRelativeIdx
        state = NativeThread.State.ParkedWaiting
        val cond = condition(conditionIdx)
        val status = pthread_cond_wait(cond, lock)
        assert(
          status == 0 ||
            (scalanative.runtime.Platform.isMac() && status == ETIMEDOUT),
          "park, wait"
        )
      } else {
        conditionIdx =
          if (isAbsolute) ConditionAbsoluteIdx else ConditionRelativeIdx
        state = NativeThread.State.ParkedWaitingTimed
        val status =
          pthread_cond_timedwait(condition(conditionIdx), lock, absTime)
        assert(status == 0 || status == ETIMEDOUT, "park, timed-wait")
      }

      conditionIdx = ConditionUnset
      counter = 0
    } finally {
      state = NativeThread.State.Running
      val status = pthread_mutex_unlock(lock)
      assert(status == 0, "park, unlock")
      atomic_thread_fence(memory_order.memory_order_seq_cst)
    }
  }

  override def unpark(): Unit = {
    assert(pthread_mutex_lock(lock) == 0, "unpark, lock")
    val s = counter
    counter = 1
    val index = conditionIdx
    assert(pthread_mutex_unlock(lock) == 0, "unpark, unlock")

    if (s < 1 && index != ConditionUnset) {
      assert(
        0 == pthread_cond_signal(condition(index)),
        "unpark, signal"
      )
    }
  }

  override def sleep(_millis: Long): Unit = {
    var millis = _millis
    if (millis <= 0) return
    val deadline = System.currentTimeMillis() + millis

    import scala.scalanative.posix.pollOps._
    import scala.scalanative.posix.pollEvents._

    type PipeFDs = CArray[CInt, Nat._2]
    val pipefd = stackalloc[PipeFDs](1.toUInt)
    if (pipe(pipefd.at(0)) == -1) {
      throw new RuntimeException("Failed to setup sleep interupt event")
    }
    this.sleepInterruptEvent = !pipefd.at(1)
    try {
      val fds = stackalloc[struct_pollfd]()
      fds.fd = !pipefd.at(0)
      fds.events = POLLIN

      if (Thread.interrupted()) throw new InterruptedException()

      try
        while (millis > 0) {
          state = State.ParkedWaitingTimed
          val status = poll(fds, 1.toUInt, (millis min Int.MaxValue).toInt)
          state = State.Running
          assert(
            status >= 0 || errno == EINTR,
            s"sleep, errno=$errno"
          )
          if (Thread.interrupted()) throw new InterruptedException()

          millis = deadline - System.currentTimeMillis()
        }
      finally this.sleepInterruptEvent = UnsetEvent
    } finally {
      close(!pipefd.at(0))
      close(!pipefd.at(1))
    }
  }

  override def sleepNanos(nanos: Int): Unit = {
    val spec = stackalloc[timespec]()
    spec.tv_nsec = nanos
    state = State.ParkedWaitingTimed
    nanosleep(spec, null)
    state = State.Running
  }

  @alwaysinline private def lock: Ptr[pthread_mutex_t] = _state
    .at(LockOffset)
    .asInstanceOf[Ptr[pthread_mutex_t]]

  @alwaysinline private def conditions =
    _state
      .at(ConditionsOffset)
      .asInstanceOf[Ptr[pthread_cond_t]]

  @alwaysinline private def condition(idx: Int): Ptr[pthread_cond_t] =
    (idx: @switch) match {
      case 0 => conditions
      case 1 =>
        val base = toRawPtr(conditions)
        val offset = toRawSize(pthread_cond_t_size)
        fromRawPtr(elemRawPtr(base, offset))
    }

  @alwaysinline private def counterAtromic = new CAtomicInt(
    fromRawPtr(classFieldRawPtr(this, "counter"))
  )

  @inline def priorityMapping(
      threadPriority: Int,
      schedulerPolicy: CInt
  ): Int = {
    val minPriority = sched_get_priority_min(schedulerPolicy)
    val maxPriority = sched_get_priority_max(schedulerPolicy)
    assert(
      minPriority >= 0 && maxPriority >= 0,
      "Failed to resolve priority range"
    )
    val priorityRange = maxPriority - minPriority
    val javaPriorityRange = Thread.MAX_PRIORITY - Thread.MIN_PRIORITY
    val priority =
      (((threadPriority - Thread.MIN_PRIORITY) * priorityRange) / javaPriorityRange) + minPriority
    assert(
      priority >= minPriority && priority <= maxPriority,
      "priority out of range"
    )
    priority
  }

  private def toAbsoluteTime(
      abstime: Ptr[timespec],
      _timeout: Long,
      isAbsolute: Boolean
  ) = {
    val timeout = if (_timeout < 0) 0 else _timeout
    val clock =
      if (isAbsolute || !PosixThread.usesClockMonotonicCondAttr) CLOCK_REALTIME
      else CLOCK_MONOTONIC
    val now = stackalloc[timespec]()
    clock_gettime(clock, now)
    if (isAbsolute) unpackAbsoluteTime(abstime, timeout, now.tv_sec.toLong)
    else calculateRelativeTime(abstime, timeout, now)
  }

  private def calculateRelativeTime(
      abstime: Ptr[timespec],
      timeout: Long,
      now: Ptr[timespec]
  ) = {
    val maxSeconds = now.tv_sec.toLong + MaxSeconds
    val seconds = timeout / NanonsInSecond
    if (seconds > maxSeconds) {
      abstime.tv_sec = maxSeconds.toSize
      abstime.tv_nsec = 0
    } else {
      abstime.tv_sec = now.tv_sec + seconds.toSize
      val nanos = now.tv_nsec + (timeout % NanonsInSecond)
      abstime.tv_nsec =
        if (nanos < NanonsInSecond) nanos.toSize
        else {
          abstime.tv_sec += 1
          (nanos - NanonsInSecond).toSize
        }
    }
  }

  @alwaysinline private def MillisInSecond = 1000
  @alwaysinline private def NanosInMillisecond = 1000000
  @alwaysinline private def NanonsInSecond = 1000000000
  @alwaysinline private def MaxSeconds = 100000000

  private def unpackAbsoluteTime(
      abstime: Ptr[timespec],
      deadline: Long,
      nowSeconds: Long
  ) = {
    val maxSeconds = nowSeconds + MaxSeconds
    val seconds = deadline / MillisInSecond
    val millis = deadline % MillisInSecond

    if (seconds >= maxSeconds) {
      abstime.tv_sec = maxSeconds.toSize
      abstime.tv_nsec = 0
    } else {
      abstime.tv_sec = seconds.toSize
      abstime.tv_nsec = (millis * NanosInMillisecond).toSize
    }

    assert(abstime.tv_sec <= maxSeconds, "tvSec")
    assert(abstime.tv_nsec <= NanonsInSecond, "tvNSec")
  }
}

private[lang] object PosixThread extends NativeThread.Companion {
  import NativeThread._
  type Impl = PosixThread

  private[this] val _state = new scala.Array[scala.Byte](CompanionStateSize)
  assert(
    0 == pthread_condattr_init(conditionRelativeCondAttr),
    "PosixThread$, cond attrs init"
  )

  // MacOS does not define `pthread_condattr_setclock`, use realtime (default) clocks instead
  val usesClockMonotonicCondAttr =
    if (isMac || isFreeBSD) false
    else {
      assert(
        0 == pthread_condattr_setclock(
          conditionRelativeCondAttr,
          CLOCK_MONOTONIC
        ),
        "PosixThread$, condattr_setclock monotonic"
      )
      true
    }

  assert(
    0 == pthread_mutexattr_init(mutexAttr) &&
      0 == pthread_mutexattr_settype(mutexAttr, PTHREAD_MUTEX_NORMAL),
    "PosixThread$, mutex attrs init"
  )

  @alwaysinline def conditionRelativeCondAttr = _state
    .at(ConditionRelativeAttrOffset)
    .asInstanceOf[Ptr[pthread_condattr_t]]

  @alwaysinline def mutexAttr =
    _state
      .at(MutexAttrOffset)
      .asInstanceOf[Ptr[pthread_mutexattr_t]]

  @alwaysinline private def UnsetEvent = -1

  @alwaysinline def create(thread: Thread, stackSize: Long): PosixThread =
    new PosixThread(thread, stackSize)

  @alwaysinline def yieldThread(): Unit = sched_yield()

  // PosixThread class state
  @alwaysinline private def LockOffset = 0
  @alwaysinline private def ConditionsOffset = pthread_mutex_t_size.toInt
  @alwaysinline private def ConditionUnset = -1
  @alwaysinline private def ConditionRelativeIdx = 0
  @alwaysinline private def ConditionAbsoluteIdx = 1
  private def StateSize =
    (pthread_mutex_t_size + pthread_cond_t_size * 2.toUInt).toInt

  // PosixThread companion class state
  @alwaysinline private def ConditionRelativeAttrOffset = 0
  @alwaysinline private def MutexAttrOffset = pthread_condattr_t_size.toInt
  def CompanionStateSize =
    (pthread_condattr_t_size + pthread_mutexattr_t_size).toInt

}
