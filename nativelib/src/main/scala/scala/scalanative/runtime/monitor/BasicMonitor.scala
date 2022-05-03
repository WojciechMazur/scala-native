package scala.scalanative.runtime.monitor

import LockWord._
import scala.scalanative.annotation.alwaysinline
import scala.scalanative.unsafe._
import scala.annotation.tailrec
import scala.scalanative.runtime.Intrinsics._
import scala.scalanative.runtime.Monitor
import scala.annotation.switch
import scala.scalanative.runtime.Intrinsics

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

  @alwaysinline
  private def atomic = new CAtomicLong(lockWordRef)

  @inline def _notify(): Unit = withInflatedLockIfPresent(_._notify())
  @inline def _notifyAll(): Unit = withInflatedLockIfPresent(_._notifyAll())
  @inline def _wait(): Unit = withInflatedLockIfPresent(_._wait())
  @inline def _wait(timeout: Long): Unit =
    withInflatedLockIfPresent(_._wait(timeout))
  @inline def _wait(timeout: Long, nanos: Int): Unit =
    withInflatedLockIfPresent(_._wait(timeout, nanos))

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
              } else {
                inflate()
                lockInflated(!lockWordRef)
              }
              // No need for atomic operation since we already obtain the lock
            } else lockAndInflate(threadId)

          case LockStatus.Inflated => lockInflated(witnessed)

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
  private def tryLockPreviouslyUnlocked(threadId: Long) =
    atomic
      .compareExchangeStrong(
        LockStatus.Unlocked, // ThreadId set to 0, recursion set to 0
        LockStatus.Locked.withThreadId(threadId)
      )

  private def lockInflated(lockWord: LockWord): Unit =
    lockWord.getObjectMonitor.enter()

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
    if (!needsInflating) lockInflated(current)
    else {
      inflate()
      lockInflated(!lockWordRef)
    }
  }

  private def inflate(): Unit = {
    val objectMonitor = new ObjectMonitor()
    !lockWordRef = LockStatus.Inflated.withMonitorAssigned(objectMonitor)
  }
}
