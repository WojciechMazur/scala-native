package scala.scalanative.memory

import scala.scalanative.annotation.alwaysinline
import scala.scalanative.unsafe._
import scala.scalanative.runtime.Intrinsics
import scala.scalanative.runtime.toRawPtr
import scala.scalanative.runtime.libc
import scala.scalanative.runtime.MemoryLayout
import scala.scalanative.runtime.Array
import java.lang._Class
import scala.scalanative.runtime.Intrinsics.castRawPtrToObject

object Allocator {
  trait AllocatorException { self: Exception => }
  final class ClosedAllocatorException(msg: String)
      extends IllegalAccessException(msg)
      with AllocatorException
  final class OutOfMemoryException(requestedBytes: Int)
      extends RuntimeException(
        s"No resources available to allocate $requestedBytes bytes"
      )
      with AllocatorException
}

@capabiltyCompat
trait Allocator extends java.lang.AutoCloseable {

  /** Is zone allowing for new allocations */
  def isOpen: Boolean

  /** Is zone closed and does not allow for new allocation */
  @alwaysinline final def isClosed: Boolean = !isOpen

  /** Allocates a segment of memory using this zone
   *
   *  @param size
   *    number of bytes to allocate
   *  @throws Allocator.ClosedAllocatorException
   *    if zone is closed
   *  @throws Allocator.OutOfMemoryException
   *    if allocation is not requested size is not possible
   */
  @inline final def alloc(size: Int): Ptr[Byte] = {
    if (isClosed)
      throw new Allocator.ClosedAllocatorException(
        "Cannot allocate, allocator was closed already"
      )
    allocUnsafe(size) match {
      case null           => throw new Allocator.OutOfMemoryException(size)
      case ptr: Ptr[Byte] => ptr
    }
  }

  /** Allocates a segment of memory using this zone able to contains
   *  elementSize*elements bytes
   *
   *  @param elementSize
   *    size of single element in bytes
   *  @param elemnents
   *    number of elements to allocate
   */
  @inline final def alloc(elementSize: Int, elemnents: Int): Ptr[Byte] = {
    if (isClosed)
      throw new Allocator.ClosedAllocatorException(
        "Cannot allocate, allocator was closed already"
      )
    allocUnsafe(elementSize, elemnents) match {
      case null =>
        throw new Allocator.OutOfMemoryException(elementSize * elemnents)
      case ptr: Ptr[Byte] => ptr
    }
  }

  /** Allocates a segment of memory using this zone This variant does not check
   *  if allocation is possible
   *
   *  @param size
   *    number of bytes to allocate
   *  @return
   *    pointer to the start of allocated memory segment or null if allocation
   *    is not possible
   */
  @inline def allocUnsafe(size: Int): Nullable[Ptr[Byte]]

  /** Allocates a segment of memory using this zone able to contains
   *  elementSize*elements bytes This variant does not check if allocation is
   *  possible
   *
   *  @param elementSize
   *    size of single element in bytes
   *  @param elemnts
   *    number of elements to allocate
   *  @return
   *    pointer to the start of allocated memory segment or null if allocation
   *    is not possible
   */
  @inline def allocUnsafe(elementSize: Int, elements: Int): Nullable[Ptr[Byte]]
}

@capabiltyCompat
trait ZeroInitializedAllocator { self: Allocator =>
  @inline
  protected def zeroInitialize[T](ptr: Ptr[T], size: Int): Unit = {
    libc.memset(toRawPtr(ptr), 0, Intrinsics.castIntToRawSize(size))
  }

  /** Allocates a segment of memory using this zone. The allocated memory is
   *  guaranteed to be zero-initialized
   *
   *  @param size
   *    number of bytes to allocate
   *  @throws Allocator.ClosedAllocatorException
   *    if zone is closed
   *  @throws Allocator.OutOfMemoryException
   *    if allocation is not requested size is not possible
   */
  protected def zeroAlloc(size: Int): Ptr[Byte] = {
    val alloc = self.alloc(size)
    zeroInitialize(alloc, size)
    alloc
  }

  /** Allocates a segment of memory using this zone able to contains
   *  elementSize*elements bytes.The allocated memory is guaranteed to be
   *  zero-initialized
   *
   *  @param elementSize
   *    size of single element in bytes
   *  @param elemnents
   *    number of elements to allocate
   */
  def zeroAlloc(elementSize: Int, elements: Int): Ptr[Byte] = {
    val alloc = self.alloc(elementSize, elements)
    zeroInitialize(alloc, elementSize * elements)
    alloc
  }

  /** Allocates a segment of memory using this zone This variant does not check
   *  if allocation is possible. The allocated memory is guaranteed to be
   *  zero-initialized.
   *
   *  @param size
   *    number of bytes to allocate
   *  @return
   *    pointer to the start of allocated memory segment or null if allocation
   *    is not possible
   */
  def zeroAllocUnsafe(size: Int): Nullable[Ptr[Byte]] =
    self.allocUnsafe(size) match {
      case null           => null
      case ptr: Ptr[Byte] => zeroInitialize(ptr, size); ptr
    }

    /** Allocates a segment of memory using this zone able to contains
     *  elementSize*elements bytes This variant does not check if allocation is
     *  possible.The allocated memory is guaranteed to be zero-initialized
     *
     *  @param elementSize
     *    size of single element in bytes
     *  @param elemnts
     *    number of elements to allocate
     *  @return
     *    pointer to the start of allocated memory segment or null if allocation
     *    is not possible
     */
  def zeroAllocUnsafe(
      elementSize: Int,
      elements: Int
  ): Nullable[Ptr[Byte]] =
    self.allocUnsafe(elementSize, elements) match {
      case null           => null
      case ptr: Ptr[Byte] => zeroInitialize(ptr, elementSize * elements); ptr
    }
}

@capabiltyCompat
trait ClassAllocator {
  self: ZeroInitializedAllocator =>

  /** Allocates a segment of memory capable of containing instance of class T.
   *  It does zero-initialize allocated memory and assignes object header for
   *  specified type.
   *
   *  @param runtimeClass
   *    Runtime class instance of type to allocate
   */
  @inline final def alloc[T <: AnyRef](runtimeClass: Class[T]): T =
    withAssignedObjectHeader(
      runtimeClass,
      zeroAlloc(runtimeClass.asInstanceOf[_Class[_]].size)
    )

  /** Allocates a segment of memory capable of containing N instances of class
   *  T. It does zero-initialize allocated memory and assignes object header for
   *  each allocated instance of class T
   *
   *  @param runtimeClass
   *    Runtime class instance of type to allocate
   *  @param elements
   *    number of instances to allocate
   */
  @inline final def alloc[T <: AnyRef](
      runtimeClass: Class[T],
      elements: Int
  ): T = {
    val elementSize = runtimeClass.asInstanceOf[_Class[_]].size
    withAssignedObjectHeaders(
      runtimeClass,
      zeroAlloc(elementSize, elements),
      elementSize,
      elements
    )
  }

  /** Allocates a segment of memory capable of containing instance of class T.
   *  It does zero-initialize allocated memory and assignes object header for
   *  specified type. This variant does not perform runtime checks to
   *  determinate if allocation is possible.
   *
   *  @param runtimeClass
   *    Runtime class instance of type to allocate
   */
  @inline final def allocUnsafe[T <: AnyRef](runtimeClass: Class[T]): T =
    withAssignedObjectHeader(
      runtimeClass,
      zeroAllocUnsafe(runtimeClass.asInstanceOf[_Class[_]].size)
    )

  /** Allocates a segment of memory capable of containing N instances of class
   *  T. It does zero-initialize allocated memory and assignes object header for
   *  each allocated instance of class T. This variant does not perform runtime
   *  checks to determinate if allocation is possible.
   *
   *  @param runtimeClass
   *    Runtime class instance of type to allocate
   *  @param elements
   *    number of instances to allocate
   */
  @inline final def allocUnsafe[T <: AnyRef](
      runtimeClass: Class[T],
      elements: Int
  ): T = {
    val elementSize = runtimeClass.asInstanceOf[_Class[_]].size
    withAssignedObjectHeaders(
      runtimeClass,
      zeroAllocUnsafe(elementSize, elements),
      elementSize,
      elements
    )
  }

  @inline private def withAssignedObjectHeader[T <: AnyRef](
      runtimeClass: Class[T],
      ptr: Ptr[_]
  ): T = {
    val rawPtr = toRawPtr(ptr)
    Intrinsics.storeObject(rawPtr, runtimeClass)
    castRawPtrToObject(rawPtr).asInstanceOf[T]
  }

  @inline private def withAssignedObjectHeaders[T <: AnyRef](
      runtimeClass: Class[T],
      ptr: Ptr[_],
      elementSize: Int,
      elements: Int
  ): T = {
    var idx = 0
    val rawPtr = toRawPtr(ptr)
    while (idx < elements) {
      Intrinsics.storeObject(
        Intrinsics.elemRawPtr(rawPtr, idx * elementSize),
        runtimeClass
      )
      idx += 1
    }
    castRawPtrToObject(rawPtr).asInstanceOf[T]
  }

}

@capabiltyCompat
trait ArrayAllocator { self: ZeroInitializedAllocator =>

  /** Allocate a chunk memory able to contain scala.Array[T] including both
   *  array header and all of its values
   *
   *  @param runtimeClass
   *    runtime class instance of array to allocate
   *  @param elementSize
   *    size of single element
   *  @param elements
   *    number of elements array should contains
   *  @return
   */
  @inline final def alloc[T <: Array[_]](
      runtimeClass: Class[T],
      elementSize: Int,
      elements: Int
  ): T = {
    val arraySize = MemoryLayout.Array.ValuesOffset + elementSize * elements
    val ptr = zeroAlloc(arraySize)
    withArrayHeader(runtimeClass, ptr, elementSize, elements)
  }

  /** Allocate a chunk memory able to contain scala.Array[T] including both
   *  array header and all of its values This variant does not perform runtime
   *  checks to determinate if allocation is possible.
   *
   *  @param runtimeClass
   *    runtime class instance of array to allocate
   *  @param elementSize
   *    size of single element
   *  @param elements
   *    number of elements array should contains
   *  @return
   */
  final def allocUnsafe[T <: Array[_]](
      runtimeClass: Class[T],
      elementSize: Int,
      elements: Int
  ): T = {
    val arraySize = MemoryLayout.Array.ValuesOffset + elementSize * elements
    val ptr = zeroAllocUnsafe(arraySize)
    withArrayHeader(runtimeClass, ptr, elementSize, elements)
  }

  @alwaysinline private def withArrayHeader[T <: Array[_]](
      runtimeClass: Class[T],
      ptr: Ptr[_],
      elementSize: Int,
      elements: Int
  ): T = {
    val rawPtr = toRawPtr(ptr)
    Intrinsics.storeObject(rawPtr, runtimeClass)
    Intrinsics.storeInt(
      Intrinsics.elemRawPtr(rawPtr, MemoryLayout.Array.LengthOffset),
      elements
    )
    Intrinsics.storeInt(
      Intrinsics.elemRawPtr(rawPtr, MemoryLayout.Array.StrideOffset),
      elementSize
    )
    Intrinsics.castRawPtrToObject(rawPtr).asInstanceOf[T]
  }
}
