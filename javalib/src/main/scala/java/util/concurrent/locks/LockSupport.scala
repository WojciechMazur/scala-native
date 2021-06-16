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

object LockSupport {
  private final val bufferSlotSize        = (pthread_mutex_t_size + pthread_cond_t_size).toInt
  final val initialBufferCapacity = 32

  private var freeList      = List.empty[Int]
  private val parkedThreads = mutable.Map.empty[Thread, Int]
  private var nativeBuffer: ByteArray = {
    val initialBufferSize = initialBufferCapacity * bufferSlotSize
    val array             = new Array[Byte](initialBufferSize).asInstanceOf[ByteArray]

    freeList = 0.until(initialBufferCapacity).toList

    array
  }

  def getBlocker(t: Thread): Object = t.parkBlocker.get()

  def park(): Unit = usingNextFreeSlot { (lock, condition, slotIdx) =>
    val currentThread = Thread.currentThread()

    if (currentThread.isAlive() && currentThread.underlying.skipParking) {
      currentThread.underlying.skipParking = false
    } else {
      parkedThreads(currentThread) = slotIdx

      withLock(lock) {
        while (parkedThreads.contains(currentThread)) {
          pthread_cond_wait(condition, lock) match {
            case 0 => ()
            case errno =>
              throw new RuntimeException(
                s"Waiting on thread unparking failed - errCode $errno")
          }
        }
      }
    }

  }

  def park(blocker: Object): Unit = {
    setCurrentBlocker(blocker)
    park()
    setCurrentBlocker(null: Object)
  }

  def parkNanos(nanos: Long): Unit = {
    val currentThread = Thread.currentThread()
    if (currentThread.isAlive() && currentThread.underlying.skipParking) {
      currentThread.underlying.skipParking = false
    } else {
      val deadlineSpec = stackalloc[timespec]

      val deadline            = System.nanoTime() + nanos
      val NanosecondsInSecond = 1000000000
      deadlineSpec.tv_sec = deadline / NanosecondsInSecond
      deadlineSpec.tv_nsec = deadline % NanosecondsInSecond

      usingNextFreeSlot { (lock, condition, slotIdx) =>
        parkedThreads(currentThread) = slotIdx
        withLock(lock) {
          waitForThreadUnparking(currentThread, condition, lock, deadlineSpec)
        }
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

    if (currentThread.isAlive() && currentThread.underlying.skipParking) {
      currentThread.underlying.skipParking = false
    } else {
      val deadlineSpec         = stackalloc[timespec]
      val MillisecondsInSecond = 1000
      deadlineSpec.tv_sec = TimeUnit.MILLISECONDS.toSeconds(deadline)
      deadlineSpec.tv_nsec =
        TimeUnit.MILLISECONDS.toNanos(deadline % MillisecondsInSecond)

      usingNextFreeSlot { (lock, condition, slotIdx) =>
        parkedThreads(currentThread) = slotIdx
        withLock(lock) {
          waitForThreadUnparking(currentThread, condition, lock, deadlineSpec)
        }
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
      parkedThreads
        .remove(thread)
        .map(getSlotAtIdx(_))
        .fold {
          thread.underlying.skipParking = true
        } {
          case (mutex, condition) =>
            withLock(mutex) {
              pthread_cond_signal(condition) match {
                case 0 => ()
                case errno =>
                  throw new RuntimeException(
                    s"Failed to signal thread unparking - errno $errno")
              }
            }
        }
    }
  }

  def setCurrentBlocker(blocker: Object): Unit = {
    setBlocker(Thread.currentThread(), blocker)
  }

  @alwaysinline
  private def setBlocker(thread: Thread, blocker: Object): Unit = {
    thread.parkBlocker.setOpaque(blocker)
  }

  private def getNextFreeSlot(): Int = {
    def resize() = {
      val newBuffer       = new Array[Byte](nativeBuffer.length * 2)
      val newNativeBuffer = newBuffer.asInstanceOf[ByteArray]
      Array.copy(nativeBuffer, 0, newBuffer, 0, nativeBuffer.length)

      freeList = (nativeBuffer.length / bufferSlotSize)
        .until(newBuffer.length / bufferSlotSize)
        .toList
      nativeBuffer = newNativeBuffer
    }

    freeList.synchronized {
      freeList match {
        case Nil =>
          resize()
          getNextFreeSlot
        case head :: tail =>
          freeList = tail
          head
      }
    }
  }

  @alwaysinline
  private def getSlotAtIdx(idx: Int, nativeArray: ByteArray = nativeBuffer) = {
    val slotOffset = idx * bufferSlotSize
    val mutexPtr = nativeArray
      .at(slotOffset)
      .asInstanceOf[Ptr[pthread_mutex_t]]
    val conditionPtr = nativeArray
      .at(slotOffset + pthread_mutex_t_size.toInt)
      .asInstanceOf[Ptr[pthread_cond_t]]

    (mutexPtr, conditionPtr)
  }

  private def usingNextFreeSlot(
      fn: (Ptr[pthread_mutex_t], Ptr[pthread_cond_t], Int) => Unit): Unit = {
    val slot               = getNextFreeSlot()
    val (mutex, condition) = getSlotAtIdx(slot)

    assert(0 == pthread_mutex_init(mutex, null))
    assert(0 == pthread_cond_init(condition, null))

    try {
      fn(mutex, condition, slot)
    } finally {
      assert(0 == pthread_cond_destroy(condition))
      assert(0 == pthread_mutex_destroy(mutex))
    }

    freeList ::= slot
  }

  final val TimeoutCode = ETIMEDOUT

  @inline
  private def waitForThreadUnparking(thread: Thread,
                                     condition: Ptr[pthread_cond_t],
                                     lock: Ptr[pthread_mutex_t],
                                     deadline: Ptr[timespec]): Unit = {
    while (parkedThreads.contains(thread)) {
      pthread_cond_timedwait(condition, lock, deadline) match {
        case 0 => ()
        case TimeoutCode =>
          unpark(thread)
        case errno =>
          throw new RuntimeException(
            s"Failed to wait on thread unparking - $errno")
      }
    }
  }

  @inline
  private def withLock(lock: Ptr[pthread_mutex_t])(fn: => Unit): Unit = {
    @alwaysinline
    def checkResult(res: Int): Unit = {
      res match {
        case 0       => ()
        case errCode => throw new RuntimeException(s"Failed to lock or unlock @ ${Thread.currentThread()}")
      }
    }

    checkResult(pthread_mutex_lock(lock))
    try {
      fn
    } finally {
      checkResult(pthread_mutex_unlock(lock))
    }
  }
}
