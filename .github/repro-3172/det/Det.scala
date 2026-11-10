//> using platform native
//> using resourceDir res

import scala.scalanative.libc.stdlib
import scala.scalanative.runtime.{Intrinsics, fromRawPtr, toRawPtr}
import scala.scalanative.unsafe._

@extern object H {
  def sn3172_supported(): CInt = extern
  def sn3172_hold_in_callee_saved(slot: Ptr[Ptr[Byte]]): Unit = extern
}

// Deterministic reproducer of the root cause of scala-native#3172:
// a reference held only in a callee-saved register while the current thread
// runs a GC must keep the object alive.
object Main {
  final val Magic = 0x5a5a5a5a5a5a5a5aL

  @noinline def fresh(slot: Ptr[Ptr[Byte]]): Unit = {
    val a = new Array[Long](32)
    java.util.Arrays.fill(a, Magic)
    !slot = fromRawPtr[Byte](Intrinsics.castObjectToRawPtr(a))
  }

  @noinline def churn(): Long = {
    var sum = 0L
    var i = 0
    while (i < 200000) {
      val b = new Array[Long](32)
      java.util.Arrays.fill(b, 0x1111L)
      sum += b(i & 31)
      i += 1
    }
    sum
  }

  def main(args: Array[String]): Unit = {
    assert(H.sn3172_supported() == 1, "unsupported platform")
    val slot = stdlib.malloc(sizeof[Ptr[Byte]]).asInstanceOf[Ptr[Ptr[Byte]]]
    var failures = 0
    val trials = 10
    for (t <- 0 until trials) {
      fresh(slot)
      H.sn3172_hold_in_callee_saved(slot)
      // Root the object on the Scala stack again: the malloc'ed slot is not
      // scanned by the GC. If it was freed while held only in the register,
      // churn() reuses its memory.
      val a = Intrinsics
        .castRawPtrToObject(toRawPtr(!slot))
        .asInstanceOf[Array[Long]]
      !slot = null
      churn()
      val ok = a.length == 32 && a.forall(_ == Magic)
      if (!ok) failures += 1
      println(s"trial $t: ${if (ok) "alive" else "COLLECTED/CORRUPTED"}")
    }
    if (failures > 0) {
      println(
        s"FAIL: $failures/$trials objects held in a callee-saved register were freed by GC"
      )
      sys.exit(1)
    } else println("PASS")
  }
}
