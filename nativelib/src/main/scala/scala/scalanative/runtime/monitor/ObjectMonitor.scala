package scala.scalanative.runtime.monitor

import scala.scalanative.annotation.alwaysinline
import java.util.concurrent.locks.ReentrantLock

/** Heavy weight monitor created only upon detection of entering monitor by
 *  multiple threads, upon detection of access from multiple threads is inflated
 *  in ObjectMonitor
 *
 *  Even though BasicMonitor and ObjectMonitor share the same interface, we
 *  don't define it explicityly. This way we don't need to instantiate
 *  BasicMonitor
 *
 *  @param lockWordRef
 *    Pointer to LockWord, internal field of every object header
 */
private[runtime] class ObjectMonitor {
  private val lock = new ReentrantLock(true)
  private val condition = lock.newCondition()

  def enter(): Unit = {
    // TODO: should set Thread.State.BLOCKED
    lock.lock()
  }
  def exit(): Unit = lock.unlock()

  def _notify(): Unit = {
    checkOwnership()
    condition.signal()
  }

  def _notifyAll(): Unit = {
    checkOwnership()
    condition.signalAll()
  }

  @alwaysinline def _wait(): Unit = {
    checkOwnership()
    condition.await()
    if (Thread.interrupted()) throw new InterruptedException()
  }

  @alwaysinline def _wait(timeoutMillis: Long): Unit = _wait(timeoutMillis, 0)
  def _wait(timeoutMillis: Long, nanos: Int): Unit = {
    checkOwnership()
    if (nanos < 0 || nanos > 999999) {
      throw new IllegalArgumentException(
        "nanosecond timeout value out of range"
      )
    }

    if (timeoutMillis < 0) {
      throw new IllegalArgumentException("timeoutMillis value is negative")
    }

    val waitNanos = timeoutMillis * 1000000 + nanos
    if (waitNanos == 0L) condition.await()
    else condition.awaitNanos(waitNanos)

    if (Thread.interrupted()) {
      throw new InterruptedException()
    }
  }

  private def checkOwnership(): Unit = {
    if (!lock.isHeldByCurrentThread()) {
      throw new IllegalMonitorStateException(
        "thread is not an owner this object"
      )
    }
  }
}
