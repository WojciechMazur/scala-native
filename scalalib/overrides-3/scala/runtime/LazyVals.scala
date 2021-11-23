package scala.runtime

import scala.scalanative.runtime.{Array => *, *}
import scala.scalanative.unsafe._
import scala.scalanative.annotation.stub

/**
 * Helper methods used in thread-safe lazy vals.
 */
object LazyVals {
  private final val LAZY_VAL_MASK = 3L

  /* ------------- Start of public API ------------- */

  final val BITS_PER_LAZY_VAL = 2L

  def STATE(cur: Long, ord: Int): Long = {
    (cur >> (ord * BITS_PER_LAZY_VAL)) & LAZY_VAL_MASK
  }

  private def objectFieldAtOffset(t: Object, offset: Long): RawPtr = {
   val HeaderSize = 64 // sizeOf[{Ptr}] * 8
   val rawPtr =  Intrinsics.castObjectToRawPtr(t)
   Intrinsics.elemRawPtr(rawPtr, offset * 8 + HeaderSize)
  }

  def CAS(t: Object, offset: Long, e: Long, v: Int, ord: Int): Boolean = {
    val mask = ~(LAZY_VAL_MASK << ord * BITS_PER_LAZY_VAL)
    val n = (e & mask) | (v.toLong << (ord * BITS_PER_LAZY_VAL))
    
    val ptr = fromRawPtr[Long](objectFieldAtOffset(t, offset))
    println(s"LazyVals CAS $t @ $offset\t?=$e cur=${!ptr} new=$n")
    if(!ptr == e){
      !ptr = n
      true
    } else false
  }

  def setFlag(t: Object, offset: Long, v: Int, ord: Int): Unit = {
    val cur = get(t, offset)
    if (STATE(cur, ord) == 1) 
      CAS(t, offset, cur, v, ord)
  }

  def wait4Notification(t: Object, offset: Long, cur: Long, ord: Int): Unit = ()

  def get(t: Object, off: Long): Long = {
    println(s"LazyVals get $t @ $off")
    val ptr = fromRawPtr[Long](objectFieldAtOffset(t, off))
    !ptr
  }

  def getOffset(clz: Class[_], name: String): Long = ???

  object Names {
    final val state = "STATE"
    final val cas = "CAS"
    final val setFlag = "setFlag"
    final val wait4Notification = "wait4Notification"
    final val get = "get"
    final val getOffset = "getOffset"
  }
}
