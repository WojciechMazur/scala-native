package scala.scalanative.memory

import language.experimental.captureChecking
import scala.annotation.implicitNotFound
import scala.scalanative.unsafe.*
import scala.scalanative.runtime.{RawPtr, Intrinsics, intrinsic}
import scala.scalanative.memory.{Allocator => MemoryAllocator}

@implicitNotFound("Given method requires an implicit zone.")
abstract class SafeZone extends Allocator,ZeroInitializedAllocator,ArrayAllocator,ClassAllocator {

  /** Allocates an object in this zone. The expression of obj must be an instance creation expression. */
  infix inline def alloc[T <: AnyRef](inline obj: T): T^{this} = SafeZone.Allocator.allocate(this, obj)

  /** Return the handle of this zone. */
  private[scalanative] def handle: RawPtr
}

object SafeZone {
  /** Run given function with a fresh zone and destroy it afterwards. */
  final def apply[sealed T](f: (SafeZone^) ?=> T): T = {
    val sz: SafeZone^ = new Impl(Allocator.ffi.open())
    try f(using sz)
    finally sz.close()
  }

  /* Allocates an object in the implicit zone. The expression of obj must be an instance creation expression. */
  inline def alloc[T <: AnyRef](inline obj: T)(using inline sz: SafeZone^): T^{sz} = Allocator.allocate(sz, obj)

  /** Summon the implicit zone. */
  transparent inline def zone(using sz: SafeZone^): SafeZone^{sz} = sz

  private class Impl(private[scalanative] val handle: RawPtr) extends SafeZone {
    private var flagIsOpen = true
    override def isOpen: Boolean = flagIsOpen
    override def close(): Unit = {
      if(isClosed) throw new MemoryAllocator.ClosedAllocatorException("Trying to close already closed zone")
      flagIsOpen = false
      Allocator.ffi.close(handle)
    }
     def allocUnsafe(size: Int): Nullable[Ptr[Byte]] = Allocator.ffi.alloc(handle, size)
     def allocUnsafe(elementSize: Int, elements: Int): Nullable[Ptr[Byte]] = Allocator.ffi.alloc(handle, elementSize * elements)
  }

  object Allocator {
    def allocate[T](sz: SafeZone^, obj: T): T^{sz} = intrinsic

    @extern object ffi {
      @name("scalanative_zone_open")
      def open(): RawPtr = extern

      @name("scalanative_zone_alloc")
      def alloc(rawzone: RawPtr, size: Int): Ptr[Byte] = extern

      @name("scalanative_zone_close")
      def close(rawzone: RawPtr): Unit = extern
    }
  }

}
