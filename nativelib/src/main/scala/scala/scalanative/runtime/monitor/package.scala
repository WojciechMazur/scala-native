package scala.scalanative.runtime

import scala.scalanative.unsafe._
import scala.scalanative.runtime.Intrinsics._
import scala.scalanative.annotation.alwaysinline
import scala.scalanative.meta.LinktimeInfo.{is32BitPlatform => is32bit}

package object monitor {

  /*
   * Lock word can contain one of two memory layouts: Thin or Inflated Monitor
   *
   * Thin monitor is lightweight structure used in single-threaded workflows.
   * It does not support wait/notify routines. In case of detected contention (
   * when two threads are trying to lock the same object) ThinMonitor is being
   * inflated.
   *
   * 64bit platforms
   * 64bit lock word as ThinMonitor =
   * |--1bit--|------56bit-------|---7bit----|
   * |  0     | threadID (owner) | recursion |
   *
   * InflatedMonitor contains reference to heavy-weitgh ObjectMonitor
   * 64bit lock word as InflatedMonitor
   * |---1bit--|------63bit------------------|
   * |    1    |     ObjectMonitor ref       |
   *
   * 32bit platforms
   * Thin monitor
   * |--1bit--|------24bit-------|---7bit----|
   * |  0     | threadID (owner) | recursion |
   *
   * Fat monitor
   * |---1bit--|------31bit------------------|
   * |    1    |     ObjectMonitor ref       |
   *
   */
  private[runtime] object LockWord {
    // For information why we use `def` instead of `val` see comments in the runtime/MemoryLayout
    @alwaysinline def RecursionOffset = 0
    @alwaysinline def RecursionBits = 7
    @alwaysinline def ThinMonitorMaxRecursion = (1 << RecursionBits) - 1
    @alwaysinline def RecursionMask =
      ThinMonitorMaxRecursion // << RecursionOffset

    @alwaysinline def ThreadIdOffset = 7
    @alwaysinline def ThreadIdBits = 56
    @alwaysinline def ThreadIdMax = (1L << ThreadIdBits) - 1
    @alwaysinline def ThreadIdMask = ThreadIdMax << ThreadIdOffset

    @alwaysinline def LockTypeOffset = 63
    @alwaysinline def LockTypeBits = 1
    @alwaysinline def LockTypeMask =
      ((1L << LockTypeBits) - 1) << LockTypeOffset

    object LockType {
      @alwaysinline def Deflated = 0
      @alwaysinline def Inflated = 1
    }

    // Potentially can decreased 60bits if we would need to encode additioanl flags
    @alwaysinline def ObjectMonitorBits = 63
    @alwaysinline def ObjectMonitorMask = (1L << ObjectMonitorBits) - 1
  }

  private[runtime] object LockWord32 {
    import LockWord._
    @alwaysinline def ThreadIdBits = 24
    @alwaysinline def ThreadIdMax = (1 << ThreadIdBits) - 1
    @alwaysinline def ThreadIdMask = ThreadIdMax << ThreadIdOffset

    @alwaysinline def LockTypeOffset = 31
    @alwaysinline def LockTypeBits = 1
    @alwaysinline def LockTypeMask = ((1 << LockTypeBits) - 1) << LockTypeOffset

    @alwaysinline def ObjectMonitorBits = 31
    @alwaysinline def ObjectMonitorMask = (1 << ObjectMonitorBits) - 1
  }

  @inline private[runtime] implicit class LockWord(val value: RawPtr)
      extends AnyVal {
    @alwaysinline def longValue = castRawPtrToLong(value)
    @alwaysinline def intValue = castRawPtrToInt(value)
    @alwaysinline def ==(other: RawPtr) =
      if (is32bit) intValue == other.intValue
      else longValue == other.longValue

    import LockWord._

    @alwaysinline def isDefalted =
      if (is32bit) intValue > 0
      else longValue >= 0L
    @alwaysinline def isInflated =
      if (is32bit) intValue < 0
      else longValue < 0L
    @alwaysinline def isUnlocked =
      if (is32bit) intValue == 0
      else longValue == 0L

    // Thin monitor ops
    @alwaysinline def threadId: RawPtr =
      if (is32bit) castIntToRawPtr(intValue >> ThreadIdOffset)
      else castLongToRawPtr(longValue >> ThreadIdOffset)

    @alwaysinline def recursionCount =
      if (is32bit) (intValue & RecursionMask).toInt
      else (longValue & RecursionMask).toInt

    @alwaysinline def withIncreasedRecursion: RawPtr =
      if (is32bit) castIntToRawPtr(intValue + 1)
      else castLongToRawPtr(longValue + 1L)

    @alwaysinline def withDecresedRecursion: RawPtr =
      if (is32bit) castIntToRawPtr(intValue - 1)
      else castLongToRawPtr(longValue - 1L)

    // Inflated monitor ops
    @alwaysinline def getObjectMonitor: ObjectMonitor = {
      // assert(isInflated, "LockWord was not inflated")
      val addr =
        if (is32bit) castIntToRawPtr(intValue & LockWord32.ObjectMonitorMask)
        else castLongToRawPtr(longValue & LockWord.ObjectMonitorMask)

      castRawPtrToObject(addr).asInstanceOf[ObjectMonitor]
    }
  }

  // glue layer defined in javalib
  @extern
  object Intrinsics {
    @name("scalanative_on_spin_wait")
    def onSpinWait(): Unit = extern
  }

}
