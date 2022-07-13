package scala.scalanative.runtime.monitor

import LockWord._
import scala.scalanative.annotation.alwaysinline
import scala.scalanative.unsafe._
import scala.annotation.tailrec
import scala.scalanative.runtime.{libc, fromRawPtr, Monitor, Intrinsics}
import scala.annotation.switch

/** Lightweight monitor used for single-threaded execution, upon detection of
 *  access from multiple threads is inflated in ObjectMonitor
 *
 *  Even though BasicMonitor and ObjectMonitor share the same interface, we
 *  don't define it explicityly. This way we don't need to instantiate
 *  BasicMonitor
 *
 *  @param lockWordRef
 *    Pointer to LockWord, internal field of every object header
 */
final case class BasicMonitor(lockWordRef: Ptr[Word]) extends AnyVal {
  import BasicMonitor._

  @inline def _notify(): Unit = withInflatedLockIfPresent(_._notify())
  @inline def _notifyAll(): Unit = withInflatedLockIfPresent(_._notifyAll())
  @inline def _wait(): Unit = withInflatedLock(_._wait())
  @inline def _wait(timeout: Long): Unit = withInflatedLock(_._wait(timeout))
  @inline def _wait(timeout: Long, nanos: Int): Unit = withInflatedLock(
    _._wait(timeout, nanos)
  )

  def enter(): Unit = {
    val threadId = Thread.currentThread().getId()
    tryLockPreviouslyUnlocked(threadId) match {
      case (true, _) => ()
      case (false, witnessed) =>
        (witnessed.lockStatus: @switch) match {
          case LockStatus.Locked =>
            val isSelfLocked = threadId == witnessed.threadId
            if (isSelfLocked) {
              if (witnessed.recursionCount < ThinMonitorMaxRecursion) {
                !lockWordRef = witnessed.withIncreasedRecursion()
              } else inflate()
              // No need for atomic operation since we already obtain the lock
            } else lockAndInflate(threadId)

          case LockStatus.Inflated => witnessed.getObjectMonitor.enter()

          case _ => ??? // should not happen
        }
    }
  }

  def exit(): Unit = {
    val threadId = Thread.currentThread().getId()
    val current = !lockWordRef
    val lockedOnce = LockStatus.Locked.withThreadId(threadId)

    (current.lockStatus: @switch) match {
      case LockStatus.Locked =>
        !lockWordRef =
          if (current == lockedOnce) LockStatus.Unlocked
          else current.withDecresedRecursion()

      case LockStatus.Inflated =>
        current.getObjectMonitor.exit()

      case LockStatus.Unlocked => ()
      case _                   => ??? // should not happen
    }
  }

  @alwaysinline
  private def withInflatedLockIfPresent(fn: ObjectMonitor => Unit): Unit = {
    val current = !lockWordRef
    if (current.isInflated) fn(current.getObjectMonitor)
    else ()
  }

  @alwaysinline
  private def withInflatedLock(fn: ObjectMonitor => Unit): Unit = {
    def current = !lockWordRef
    val monitor =
      if (current.isInflated) current.getObjectMonitor
      else inflate()
    fn(monitor)
  }

  @alwaysinline
  private def tryLockPreviouslyUnlocked(threadId: Long) = {
    val expected = fromRawPtr[CLong](Intrinsics.stackalloc(sizeof[CLong]))
    !expected = LockStatus.Unlocked // ThreadId set to 0, recursion set to 0
    val result = libc.atomic_compare_exchange_strong(
      lockWordRef,
      expected,
      LockStatus.Locked.withThreadId(threadId)
    )
    (result, !expected)
  }

  // Monitor is currently locked by other thread. Wait until getting over owership
  // of this object and transform LockWord to use HeavyWeight monitor
  private def lockAndInflate(threadId: Long): Unit = {
    @tailrec
    def waitForOwnership(): (Boolean, Word) = {
      tryLockPreviouslyUnlocked(threadId) match {
        case result @ (true, _)                                  => result
        case result @ (false, witnessed) if witnessed.isInflated => result
        case _                                                   =>
          // Todo: Add back-off
          waitForOwnership()
      }
    }

    val (needsInflating, current) = waitForOwnership()

    // Check if other thread has not inflated lock already
    if (!needsInflating) current.getObjectMonitor.enter()
    else inflate()
  }

  private def inflate(): ObjectMonitor = {
    val objectMonitor = new ObjectMonitor()
    objectMonitor.enter()
    val prev = !lockWordRef
    !lockWordRef = LockStatus.Inflated.withMonitorAssigned(objectMonitor)
    objectMonitor
  }
}
