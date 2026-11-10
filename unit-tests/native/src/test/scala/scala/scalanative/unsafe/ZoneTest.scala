package scala.scalanative
package unsafe

import org.junit.Assert._
import org.junit.Test

import org.scalanative.testsuite.utils.AssertThrows.assertThrows

import scalanative.unsigned._

class ZoneTest {
  private def assertAccessible(bptr: Ptr[_], n: Int): Unit = {
    val ptr = bptr.asInstanceOf[Ptr[Int]]
    var i = 0
    while (i < n) {
      ptr(i) = i
      i += 1
    }

    i = 0
    var sum = 0
    while (i < n) {
      sum += ptr(i)
      i += 1
    }

    assertTrue(sum == (0 until n).sum)
  }

  @Test def zoneAllocatorAllocWithApply(): Unit = {
    Zone.acquire { implicit z =>
      val ptr = z.alloc(64.toUSize * sizeof[Int])

      assertAccessible(ptr, 64)

      val ptr2: Ptr[Int] = alloc[Int](128)

      assertAccessible(ptr2, 128)
    }
  }

  @Test def zoneAllocatorAllocWithOpen(): Unit = {
    implicit val zone: Zone = Zone.open()
    assertTrue(zone.isOpen)
    assertFalse(zone.isClosed)

    val ptr = zone.alloc(64.toUSize * sizeof[Int])

    assertAccessible(ptr, 64)

    val ptr2: Ptr[Int] = alloc[Int](128)

    assertAccessible(ptr2, 128)

    zone.close()
    assertFalse(zone.isOpen)
    assertTrue(zone.isClosed)
  }

  @Test def allocThrowsExceptionIfZoneAllocatorIsClosed(): Unit = {
    implicit val zone: Zone = Zone.open()

    zone.alloc(64.toUSize * sizeof[Int])

    zone.close()

    assertThrows(
      classOf[IllegalStateException],
      zone.alloc(64.toUSize * sizeof[Int])
    )
    assertThrows(classOf[IllegalStateException], zone.close())
  }

  // Issue #2491: references stored only in zone memory must keep their
  // referents alive while the zone is open.
  @Test def zoneMemoryIsScannedByGC(): Unit = {
    import ZoneTest._
    Zone.acquire { implicit z =>
      // Small allocations (< half a page) spread over many pool pages.
      val small = Array.fill(Chunks)(alloc[Holder](ChunkSize))
      // A single allocation larger than a page, served by malloc.
      val large = alloc[Holder](Count)
      val bigInts = alloc[BigInt](Count)
      fill(small, large, bigInts)

      var round = 0
      while (round < 3) {
        churn()
        System.gc()
        round += 1
      }

      var i = 0
      while (i < Count) {
        check(small(i / ChunkSize)(i % ChunkSize), i)
        check(large(i), i + Count)
        assertEquals(s"bigInt($i)", BigInt(2).pow(i % 512), bigInts(i))
        i += 1
      }
    }
  }
}

object ZoneTest {
  final val ChunkSize = 128
  final val Chunks = 32
  final val Count = ChunkSize * Chunks

  final class Holder(val id: Int, val payload: Array[Int])

  @noinline def fill(
      small: Array[Ptr[Holder]],
      large: Ptr[Holder],
      bigInts: Ptr[BigInt]
  ): Unit = {
    var i = 0
    while (i < Count) {
      small(i / ChunkSize)(i % ChunkSize) = new Holder(i, Array.fill(8)(i))
      large(i) = new Holder(i + Count, Array.fill(8)(i + Count))
      bigInts(i) = BigInt(2).pow(i % 512)
      i += 1
    }
  }

  @noinline def churn(): Unit = {
    // Allocate garbage so that memory of wrongly collected objects is reused.
    var i = 0
    var acc = 0L
    while (i < 200000) {
      val arr = new Array[Int](8)
      arr(0) = i
      acc += arr(0)
      i += 1
    }
    assert(acc != 0L)
  }

  def check(holder: Holder, id: Int): Unit = {
    assertNotNull(s"holder($id)", holder)
    assertEquals(s"holder($id).id", id, holder.id)
    assertEquals(s"holder($id).payload.length", 8, holder.payload.length)
    var j = 0
    while (j < 8) {
      assertEquals(s"holder($id).payload($j)", id, holder.payload(j))
      j += 1
    }
  }
}
