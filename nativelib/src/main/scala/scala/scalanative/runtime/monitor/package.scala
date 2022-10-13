package scala.scalanative.runtime

import scala.scalanative.unsafe._
import scala.scalanative.runtime.Intrinsics._
import scala.scalanative.annotation.alwaysinline

package object monitor {
  type MonitorRef = Ptr[Word]

  private[runtime] object LockWord {
    final val RecursionOffset = 0
    final val RecursionBits = 7
    final val ThinMonitorMaxRecursion = (1 << RecursionBits) - 1
    final val RecursionMask = ThinMonitorMaxRecursion // << RecursionOffset

    final val ThreadIdOffset = 7
    final val ThreadIdBits = 56
    final val ThreadIdMax = (1L << ThreadIdBits) - 1
    final val ThreadIdMask = ThreadIdMax << ThreadIdOffset

    final val LockTypeOffset = 63
    final val LockTypeBits = 1
    final val LockTypeMask = ((1L << LockTypeBits) - 1) << LockTypeOffset

    type LockType = Long
    object LockType {
      final val Deflated = 0L
      final val Inflated = 1L
    }

    // Potentially can decreased 60bits if we would need to encode additioanl flags
    final val ObjectMonitorBits = 63
    final val ObjectMonitorMask = (1L << ObjectMonitorBits) - 1
  }

  /*
   * Lock word can contain one of two memory layouts: Thin or Inflated Monitor
   *
   * Thin monitor is lightweight structure used in single-threaded workflows.
   * It does not support wait/notify routines. In case of detected contention (
   * when two threads are trying to lock the same object) ThinMonitor is being
   * inflated.
   *
   * 64bit lock word as ThinMonitor =
   * |--1bit--|------56bit-------|---7bit----|
   * |  0     | threadID (owner) | recursion |
   *
   * InflatedMonitor contains reference to heavy-weitgh ObjectMonitor
   * 64bit lock word as InflatedMonitor
   * |---1bit--|------63bit------------------|
   * |    1    |     ObjectMonitor ref       |
   *
   * 32bit layout is currently not defined
   */

  @inline private[runtime] implicit class LockWord(val value: RawPtr)
      extends AnyVal {
    @alwaysinline def longValue = castRawPtrToLong(value)
    import LockWord._

    // @alwaysinline def lockType: LockType = longValue >> LockTypeMask
    // (longValue & LockTypeMask).toInt

    @alwaysinline def withLockType(v: LockType): LockWord =
      castLongToRawPtr((longValue & ~LockTypeMask) | v)

    @alwaysinline def isDefalted = longValue >= 0
    @alwaysinline def isInflated = longValue < 0
    @alwaysinline def isUnlocked = longValue == 0

    // Thin monitor ops
    @alwaysinline def threadId = longValue >> ThreadIdOffset

    @alwaysinline def recursionCount = (longValue & RecursionMask).toInt
    @alwaysinline def withIncreasedRecursion: Word = longValue + 1
    @alwaysinline def withDecresedRecursion: Word = longValue - 1

    // Inflated monitor ops
    @alwaysinline def getObjectMonitor: ObjectMonitor = {
      // assert(isInflated, "LockWord was not inflated")
      val addr = longValue & ObjectMonitorMask
      castRawPtrToObject(castLongToRawPtr(addr))
        .asInstanceOf[ObjectMonitor]
    }
  }

}
