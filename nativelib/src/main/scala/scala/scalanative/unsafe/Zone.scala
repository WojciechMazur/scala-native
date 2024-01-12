package scala.scalanative
package unsafe

import scala.annotation.implicitNotFound
import scala.scalanative.runtime.Intrinsics.{
  unsignedOf,
  castIntToRawSizeUnsigned
}
import scala.scalanative.runtime.{MemoryPool, MemoryPoolZone}
import scala.scalanative.unsigned._

/** Zone allocator which manages memory allocations. */
@implicitNotFound("Given method requires an implicit zone.")
trait Zone extends memory.Allocator {

  /** Allocates memory of given size. */
  def alloc(size: CSize): Ptr[Byte] = alloc(size.toInt)

    /** Allocates memory of given size. */
  def alloc(size: UInt): Ptr[Byte] =
    alloc(size.toUSize)

  /** Allocates memory of given size. */
  def alloc(size: ULong): Ptr[Byte] =
    alloc(size.toUSize)

  /** Frees allocations. This zone allocator is not reusable once closed. */
  override def close(): Unit

}

object Zone {

  /** Run given function with a fresh zone and destroy it afterwards. */
  final def apply[T](f: Zone => T): T = {
    val zone = open()
    try f(zone)
    finally zone.close()
  }

  /** Create a new zone allocator. Use Zone#close to free allocations. */
  final def open(): Zone = MemoryPoolZone.open(MemoryPool.defaultMemoryPool)
}
