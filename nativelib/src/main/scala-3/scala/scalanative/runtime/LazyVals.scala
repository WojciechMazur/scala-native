package scala.scalanative.runtime

import scala.scalanative.unsafe._
import scala.scalanative.annotation._
import scala.runtime.LazyVals.{BITS_PER_LAZY_VAL, STATE}

/** Helper methods used in thread-safe lazy vals adapted for Scala Native usage
 *  Make sure to sync them with the logic defined in Scala 3
 *  scala.runtime.LazyVals
 */
private object LazyVals {
  
  private final val LAZY_VAL_MASK = 3L

  /* ------------- Start of public API ------------- */

  @`inline`
  def CAS(bitmap: Ptr[Long], e: Long, v: Int, ord: Int): Boolean = {
    val mask = ~(LAZY_VAL_MASK << ord * BITS_PER_LAZY_VAL)
    val n = (e & mask) | (v.toLong << (ord * BITS_PER_LAZY_VAL))
    // Todo: with multithreading use atomic cas
    if (get(bitmap) != e) false
    else {
      Intrinsics.storeLong(bitmap.rawptr, n)
      true
    }
  }

  @`inline`
  def setFlag(bitmap: Ptr[Long], v: Int, ord: Int): Unit = {
    val cur = get(bitmap)
    // TODO: with multithreading add waiting for notifications
    CAS(bitmap, cur, v, ord)
  }

  def wait4Notification(bitmap: Ptr[Long], cur: Long, ord: Int): Unit = {
    throw new IllegalStateException(
      "wait4Notification not supported in single-threaded Scala Native runtime"
    )
  }

  @alwaysinline
  def get(bitmap: Ptr[Long]): Long = {
    // Todo: make it volatile read with multithreading
    Intrinsics.loadLong(bitmap.rawptr)
  }

}
