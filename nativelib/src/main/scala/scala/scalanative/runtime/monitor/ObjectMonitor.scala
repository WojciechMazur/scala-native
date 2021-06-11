package scala.scalanative.runtime.monitor

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.annotation.nowarn

/**
 * Heavy weight monitor created only upon detecion of entering monitor by multiple threads, upon detection
 * of access from multiple threads is inflated in ObjectMonitor
 *
 * Even though BasicMonitor and ObjectMonitor share the same interface, we don't define it explicityly.
 * This way we don't need to instantiate BasicMonitor
 *
 * @param lockWordRef Pointer to LockWord, internal field of every object header
 */
private[runtime] class ObjectMonitor {
  private var recursionCount: Int = 0
  private var owner: Thread       = null
  private val waitList            = new AtomicReference[Set[Thread]](Set.empty)
  private var isLocked            = new AtomicBoolean(false)

// Todo move to javalib to allow usage of Posix/Windows

  def _notify(): Unit                        = ()
  def _notifyAll(): Unit                     = ()
  def _wait(): Unit                          = ()
  def _wait(timeout: Long): Unit             = ()
  def _wait(timeout: Long, nanos: Int): Unit = ()

  def enter(): Unit = {
    val currentThread = Thread.currentThread()
    tryEnter(currentThread)
    waitList.updateAndGet(_ - currentThread)
  }

  @nowarn("msg=deprecated")
  def exit(): Unit = {
    if (recursionCount == 0) {
      owner = null
      isLocked.setOpaque(false)
      waitList.get().foreach(_.resume())
    } else {
      recursionCount -= 1
    }
  }

  @tailrec
  @nowarn("msg=deprecated")
  private def tryEnter(thread: Thread): Unit = {
    if (isLocked.compareAndSet(false, true)) {
      owner = thread
    } else if (owner eq thread) {
      recursionCount += 1
    } else {
      waitList.updateAndGet(_ + thread)
      thread.suspend()
      tryEnter(thread)
    }
  }
}
