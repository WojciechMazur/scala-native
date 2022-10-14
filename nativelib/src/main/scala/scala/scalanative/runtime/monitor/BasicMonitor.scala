package scala.scalanative.runtime
package monitor

import LockWord._
import scala.annotation.{tailrec, switch}
import scala.scalanative.annotation.alwaysinline
import scala.scalanative.unsafe.{stackalloc => _, _}
import scala.scalanative.runtime.Intrinsics._
import scala.scalanative.runtime.libc._
import scala.scalanative.runtime.libc.memory_order._
import scala.scalanative.meta.LinktimeInfo.{is32BitPlatform => is32bit}

/** Lightweight monitor used for single-threaded execution, upon detection of
 *  access from multiple threads is inflated in ObjectMonitor
 *
 *  @param lockWordRef
 *    Pointer to LockWord, internal field of every object header
 */
@inline final class BasicMonitor(val lockWordRef: RawPtr) extends AnyVal {
  import BasicMonitor._
  type ThreadId = RawPtr

  @alwaysinline def _notify(): Unit = {
    val current = lockWord
    if (current.isInflated) current.getObjectMonitor._notify()
  }

  @alwaysinline def _notifyAll(): Unit = {
    val current = lockWord
    if (current.isInflated) current.getObjectMonitor._notifyAll()
  }

  @alwaysinline def _wait(): Unit =
    getObjectMonitor()._wait()

  @alwaysinline def _wait(timeout: Long): Unit =
    getObjectMonitor()._wait(timeout)

  @alwaysinline def _wait(timeout: Long, nanos: Int): Unit =
    getObjectMonitor()._wait(timeout, nanos)

  @inline def enter(obj: Object): Unit = {
    val thread = Thread.currentThread()
    val threadId = getThreadId(thread)

    if (!tryLock(threadId)) {
      val current = lockWord
      if (current.isInflated) lockWord.getObjectMonitor.enter(thread)
      else {
        if (threadId == current.threadId) {
          if (current.recursionCount < ThinMonitorMaxRecursion) {
            // No need for atomic operation since we already obtain the lock
            storeRawPtr(lockWordRef, current.withIncreasedRecursion)
          } else inflate(thread)
        } else lockAndInflate(thread, threadId)
      }
    }
  }

  @inline def exit(obj: Object): Unit = {
    val thread = Thread.currentThread()
    val threadId = getThreadId(thread)
    val current = lockWord
    val lockedOnce = lockedWithThreadId(threadId)

    if (current.isInflated)
      current.getObjectMonitor.exit(thread)
    else if (current == lockedOnce)
      atomic_store_explicit(
        lockWordRef,
        castIntToRawPtr(0),
        memory_order_release
      )
    else storeRawPtr(lockWordRef, current.withDecresedRecursion)
  }

  @alwaysinline private def lockWord: LockWord = loadRawPtr(lockWordRef)

  @inline private def getObjectMonitor() = {
    val current = lockWord
    if (current.isInflated) current.getObjectMonitor
    else inflate(Thread.currentThread())
  }

  @alwaysinline private def lockedWithThreadId(threadId: ThreadId): RawPtr =
    // lockType=0, recursion=0
    if (is32bit) castIntToRawPtr(castRawPtrToInt(threadId) << ThreadIdOffset)
    else castLongToRawPtr(castRawPtrToLong(threadId) << ThreadIdOffset)

  @alwaysinline private def getThreadId(thread: Thread): ThreadId = {
    val addr = castObjectToRawPtr(thread)
    if (is32bit) castIntToRawPtr(castRawPtrToInt(addr) & LockWord32.ThreadIdMax)
    else castLongToRawPtr(castRawPtrToLong(addr) & LockWord.ThreadIdMax)
  }

  @inline
  private def tryLock(threadId: ThreadId) = {
    // val _ = lockWordRef.longValue.toBinaryString
    val expected = stackalloc(SizeOfPtr)
    // ThreadId set to 0, recursion set to 0
    storeRawSize(expected, castIntToRawSize(0))
    atomic_compare_exchange_strong(
      lockWordRef,
      expected,
      lockedWithThreadId(threadId)
    )
  }

  // Monitor is currently locked by other thread. Wait until getting over owership
  // of this object and transform LockWord to use HeavyWeight monitor
  @inline private def lockAndInflate(
      thread: Thread,
      threadId: ThreadId
  ): Unit = {
    @tailrec @alwaysinline def waitForOwnership(yields: Int): Unit =
      if (!tryLock(threadId) && !lockWord.isInflated) {
        if (yields > 16) {
          usleep(32)
          waitForOwnership(yields)
        } else {
          onSpinWait()
          waitForOwnership(yields + 1)
        }
      }
    waitForOwnership(0)

    // // Check if other thread has not inflated lock already
    val current = lockWord
    if (current.isInflated) current.getObjectMonitor.enter(thread)
    else inflate(thread)
  }

  @inline private def inflate(thread: Thread): ObjectMonitor = {
    val objectMonitor = new ObjectMonitor()
    objectMonitor.enter(thread)
    // Increment recursion by basic lock recursion count if present
    objectMonitor.recursion += lockWord.recursionCount

    // Since pointers are always alligned we can safely override N=sizeof(Word) right most bits
    val monitorAddress = castObjectToRawPtr(objectMonitor)
    val inflated =
      if (is32bit) {
        val lockMark = (LockType.Inflated: Int) << LockWord32.LockTypeOffset
        val addr = castRawPtrToInt(monitorAddress)
        castIntToRawSize(lockMark | addr)
      } else {
        val lockMark = (LockType.Inflated: Long) << LockWord.LockTypeOffset
        val addr = castRawPtrToLong(monitorAddress)
        castLongToRawSize(lockMark | addr)
      }
    storeRawSize(lockWordRef, inflated)
    atomic_thread_fence(memory_order_seq_cst)

    objectMonitor
  }
}
