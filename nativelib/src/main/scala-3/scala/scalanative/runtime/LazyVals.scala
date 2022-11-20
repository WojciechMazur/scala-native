package scala.scalanative.runtime

import scala.scalanative.annotation._
import scala.runtime.LazyVals.{BITS_PER_LAZY_VAL, STATE}
import scala.scalanative.runtime.libc._
import scala.scalanative.meta.LinktimeInfo.isMultithreadingEnabled
import scala.scalanative.runtime.Intrinsics._
import scala.scalanative.runtime.libc.memory_order._
import scala.scalanative.unsafe.sizeof

/** Helper methods used in thread-safe lazy vals adapted for Scala Native usage
 *  Make sure to sync them with the logic defined in Scala 3
 *  scala.runtime.LazyVals
 */
private object LazyVals {

  @alwaysinline def LAZY_VAL_MASK = 3L

  final val base: Int = {
    val processors = java.lang.Runtime.getRuntime().nn.availableProcessors()
    8 * processors * processors
  }
  final val monitors = scala.Array.tabulate(base)(_ => new Object)

  private def getMonitor(bitMap: RawPtr, fieldId: Int = 0) = {
    var id = (castRawPtrToInt(bitMap) + fieldId) % base
    if (id < 0) id += base
    monitors(id)
  }

  /* ------------- Start of public API ------------- */

  @`inline`
  def CAS(bitmap: RawPtr, e: Long, v: Int, ord: Int): Boolean = {
    val mask = ~(LAZY_VAL_MASK << ord * BITS_PER_LAZY_VAL)
    val n = (e & mask) | (v.toLong << (ord * BITS_PER_LAZY_VAL))
    if (isMultithreadingEnabled) {
      // multi-threaded
      val expected = stackalloc(sizeof[Long])
      storeLong(expected, e)
      atomic_compare_exchange_strong(bitmap, expected, n)
    } else {
      // single-threaded
      if (get(bitmap) != e) false
      else {
        storeLong(bitmap, n)
        true
      }
    }
  }

  @`inline`
  def setFlag(bitmap: RawPtr, v: Int, ord: Int): Unit =
    if (!isMultithreadingEnabled) {
      // single-threaded
      val cur = get(bitmap)
      CAS(bitmap, cur, v, ord)
    } else {
      // multi-threaded
      var retry = true
      while (retry) {
        val cur = get(bitmap)
        if (STATE(cur, ord) == 1) retry = !CAS(bitmap, cur, v, ord)
        else {
          // cur == 2, somebody is waiting on monitor
          if (CAS(bitmap, cur, v, ord)) {
            val monitor = getMonitor(bitmap, ord)
            monitor.synchronized {
              monitor.notifyAll()
            }
            retry = false
          }
        }
      }
    }

  def wait4Notification(bitmap: RawPtr, cur: Long, ord: Int): Unit = {
    if (!isMultithreadingEnabled)
      throw new IllegalStateException(
        "wait4Notification not supported in single-threaded Scala Native runtime"
      )

    var retry = true
    while (retry) {
      val cur = get(bitmap)
      val state = STATE(cur, ord)
      if (state == 1) CAS(bitmap, cur, 2, ord)
      else if (state == 2) {
        val monitor = getMonitor(bitmap, ord)
        monitor.synchronized {
          // make sure notification did not happen yet.
          if (STATE(get(bitmap), ord) == 2)
            monitor.wait()
        }
      } else retry = false
    }
  }

  @alwaysinline
  def get(bitmap: RawPtr): Long = {
    if (!isMultithreadingEnabled) Intrinsics.loadLong(bitmap)
    else atomic_load_explicit(bitmap, memory_order_relaxed)
  }

}
