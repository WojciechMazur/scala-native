package scala.scalanative.unsafe

import scala.scalanative.meta.LinktimeInfo.asanEnabled
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

/** Pool of fixed-size memory pages backing [[MemoryPoolZone]].
 *
 *  Page bookkeeping lives in native code (`zone/UnsafeZonePool.c`) so that the
 *  garbage collector can find the memory of all open zones: the used prefix of
 *  every page claimed by an open zone, as well as its large allocations, is
 *  scanned conservatively for references to GC-managed objects. Free pages and
 *  the unused remainder of pages are never scanned.
 *
 *  There is a single, process-wide pool. Memory is reclaimed back to the
 *  underlying allocator only at exit when running with AddressSanitizer.
 */
private[unsafe] final class MemoryPool private {

  /** Released all claimed memory chunks */
  private[scalanative] def freeChunks(): Unit =
    MemoryPool.native.scalanative_unsafe_zone_free_pool()
}
private[unsafe] object MemoryPool {
  final val PAGE_SIZE = 4096.toUSize

  lazy val defaultMemoryPool: MemoryPool = {
    // Release allocated chunks satisfy AdressSanitizer
    if (asanEnabled)
      try
        Runtime.getRuntime().addShutdownHook {
          val t = new Thread(() => defaultMemoryPool.freeChunks())
          t.setPriority(Thread.MIN_PRIORITY)
          t.setName("shutdown-hook:memory-pool-cleanup")
          t
        }
      catch { case ex: IllegalStateException => () } // shutdown already started
    new MemoryPool()
  }

  /** Mirrors `UnsafeZonePage` from `zone/UnsafeZonePool.h`: start, offset,
   *  next, head, large, prevZone, nextZone. The first page of a zone is also
   *  its zone record (`head` = page currently allocated from).
   */
  type Page = CStruct7[
    Ptr[Byte],
    CSize,
    Ptr[Byte],
    Ptr[Byte],
    Ptr[Byte],
    Ptr[Byte],
    Ptr[Byte]
  ]

  @extern
  private[unsafe] object native {
    def scalanative_unsafe_zone_open(): Ptr[Page] = extern
    def scalanative_unsafe_zone_add_page(zone: Ptr[Page]): Ptr[Page] = extern
    def scalanative_unsafe_zone_alloc_large(
        zone: Ptr[Page],
        size: CSize
    ): Ptr[Byte] = extern
    def scalanative_unsafe_zone_close(zone: Ptr[Page]): Unit = extern
    def scalanative_unsafe_zone_free_pool(): Unit = extern
  }
}

/** An optimized implementation of a zone that performs all allocations
 *  sequentially in pages that are claimed from memory pool. Larger allocations
 *  are allocated using the system allocator and persisted in an array buffer.
 */
final class MemoryPoolZone(private val pool: MemoryPool) extends Zone {
  import MemoryPool.native._

  // Zone record, registered as a GC root until the zone is closed.
  private var zone: Ptr[MemoryPool.Page] = scalanative_unsafe_zone_open()
  // Page currently used for small allocations.
  private var headPage: Ptr[MemoryPool.Page] = zone

  private def checkOpen(): Unit =
    if (!isOpen)
      throw new IllegalStateException("Zone {this} is already closed.")

  private def pad(addr: CSize, alignment: CSize): CSize = {
    val alignmentMask: CSize = alignment - 1.toUSize
    val padding: CSize =
      if ((addr & alignmentMask) == 0) 0.toUSize
      else alignment - (addr & alignmentMask)
    addr + padding
  }

  override def isOpen = zone != null

  override def isClosed = !isOpen

  override def close(): Unit = {
    checkOpen()
    // Return borrowed pages to the pool and free large allocations.
    scalanative_unsafe_zone_close(zone)
    zone = null
    headPage = null
  }

  override def alloc(usize: CSize): Ptr[Byte] = {
    val size = usize.toInt
    val alignment =
      if (size >= 16) 16
      else if (size >= 8) 8
      else if (size >= 4) 4
      else if (size >= 2) 2
      else 1

    alloc(usize, alignment.toUSize)
  }

  def alloc(size: CSize, alignment: CSize): Ptr[Byte] = {
    checkOpen()

    if (size <= MemoryPool.PAGE_SIZE / 2.toULong) {
      allocSmall(size, alignment)
    } else {
      allocLarge(size)
    }
  }

  private def allocSmall(size: CSize, alignment: CSize): Ptr[Byte] = {
    var page = headPage
    val paddedOffset = pad(page._2, alignment)
    val resOffset: CSize =
      if (paddedOffset + size <= MemoryPool.PAGE_SIZE) {
        page._2 = paddedOffset + size
        paddedOffset
      } else {
        page = scalanative_unsafe_zone_add_page(zone)
        page._2 = size
        headPage = page
        0L.toUSize
      }

    page._1 + resOffset
  }

  private def allocLarge(size: CSize): Ptr[Byte] =
    scalanative_unsafe_zone_alloc_large(zone, size)
}

private[unsafe] object MemoryPoolZone {
  def open(pool: MemoryPool): MemoryPoolZone =
    new MemoryPoolZone(pool)
}
