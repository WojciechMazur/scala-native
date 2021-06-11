package scala.scalanative.runtime

import scala.scalanative.unsafe._
import scala.scalanative.runtime.Intrinsics._
import scala.scalanative.annotation.alwaysinline

package object monitor {
  type MonitorRef = Ptr[Word]

  private[runtime] object LockWord {
    final val LockStatusBits = 2
    final val LockStatusMask = (1 << LockStatusBits) - 1

    type LockStatus = Int
    object LockStatus {
      final val Unlocked = 0
      final val Locked   = 1
      final val Inflated = 2
    }

    final val ThreadIdOffset = 10
    final val ThreadIdBits   = 54
    final val ThreadIdMask   = ((1 << ThreadIdBits) - 1) << ThreadIdOffset

    final val RecursionOffset         = 2
    final val RecursionBits           = 8
    final val ThinMonitorMaxRecursion = (1 << RecursionBits) - 1
    final val RecursionMask           = ThinMonitorMaxRecursion << RecursionOffset
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
   * |------54bit-------|---8bit---|---2bit--|
   * | threadID (owner) | recursion|lock type|
   *
   * InflatedMonitor contains reference to heavy-weitgh ObjectMonitor
   * 64bit lock word as InflatedMonitor
   * |------62bit------------------|---2bit--|
   * |     ObjectMonitor ref       |lock type|
   *
   * 32bit layout is currently not defined
   */

  private[runtime] implicit class LockWord(val value: Word) extends AnyVal {
    import LockWord._

    @alwaysinline
    def lockStatus: LockStatus = (value & LockStatusMask).toInt

    @alwaysinline
    def withLockStatus(v: LockStatus): LockWord = (value & ~LockStatusMask) | v

    def isLocked: Boolean   = lockStatus == LockStatus.Locked
    def lsUnlocked: Boolean = lockStatus == LockStatus.Unlocked
    def isInflated: Boolean = lockStatus == LockStatus.Inflated

    // Thin monitor ops

    def threadId: Long = (value & ThreadIdMask) >> ThreadIdOffset
    def withThreadId(v: Long): Word = {
      val threadIdBitset = (v << ThreadIdOffset) & ThreadIdMask
      (value & ~ThreadIdMask) | threadIdBitset
    }

    def recursionCount: Int            = ((value & RecursionMask) >> RecursionOffset).toInt
    def withIncreasedRecursion(): Word = value + (1 << RecursionOffset)
    def withDecresedRecursion(): Word  = value - (1 << RecursionOffset)

    // Inflated monitor ops
    def getObjectMonitor: ObjectMonitor = {
      assert(isInflated, "LockWord was not inflated")
      val addr = value ^ LockStatus.Inflated
      castRawPtrToObject(castLongToRawPtr(addr))
        .asInstanceOf[ObjectMonitor]
    }

    def withMonitorAssigned(v: ObjectMonitor): Word = {
      // Since pointers are always alligned we can safely override N=sizeof(Word) right most bits
      val monitorAddress = castRawPtrToLong(castObjectToRawPtr(v))
      value | monitorAddress
    }
  }

}
