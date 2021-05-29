// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 1)
package scala.scalanative.runtime

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.runtime.Atomic._
import scala.language.implicitConversions

sealed trait CAtomic[T]{
  def load(): T
  def store(value: T): Unit
  def free(): Unit
  def compareAndSwapStrong(expected: T, desired: T): (Boolean, T)
  def compareAndSwapWeak(expected: T, desired: T): (Boolean, T)
  def addFetch(value: T): T
  def fetchAdd(value: T): T
  def subFetch(value: T): T
  def fetchSub(value: T): T
  def andFetch(value: T): T
  def fetchAnd(value: T): T
  def orFetch(value: T): T
  def fetchOr(value: T): T
  def xorFetch(value: T): T
  def fetchXor(value: T): T
}

// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 44)

final case class CAtomicByte(private val initValue: Byte = 0.toByte) extends CAtomic[Byte] {
  private[this] val underlying: Ptr[Byte] = fromRawPtr(libc.malloc(sizeof[Byte]))
  atomic_init_byte(underlying, initValue)

  def free(): Unit = libc.free(toRawPtr(underlying))

  def load(): Byte = atomic_load_byte(underlying)

  def store(value: Byte): Unit = atomic_store_byte(underlying, value)

  def exchange(value: Byte): Byte = atomic_exchange_byte(underlying, value)

  def compareAndSwapStrong(expected: Byte, desired: Byte): (Boolean, Byte) = {
    val expectedPtr: Ptr[Byte] = fromRawPtr(Intrinsics.stackalloc(sizeof[Byte]))
    !expectedPtr = expected    
    if (compare_and_swap_strong_byte(underlying, expectedPtr, desired)) {
      (true, desired)
    } else {
      (false, !expectedPtr)
    }
  }

  def compareAndSwapWeak(expected: Byte, desired: Byte): (Boolean, Byte) = {
    val expectedPtr: Ptr[Byte] = fromRawPtr(Intrinsics.stackalloc(sizeof[Byte]))
    !expectedPtr = expected    
    if (compare_and_swap_weak_byte(underlying, expectedPtr, desired)) {
      (true, desired)
    } else {
      (false, !expectedPtr)
    }
  }

  def addFetch(value: Byte): Byte = {
    fetchAdd(value)
    load()
  }

  def fetchAdd(value: Byte): Byte = atomic_add_byte(underlying, value)

  def subFetch(value: Byte): Byte = {
    fetchSub(value)
    load()
  }

  def fetchSub(value: Byte): Byte = atomic_sub_byte(underlying, value)

  def andFetch(value: Byte): Byte = {
    fetchAnd(value)
    load()
  }

  def fetchAnd(value: Byte): Byte = atomic_and_byte(underlying, value)

  def orFetch(value: Byte): Byte = {
    fetchOr(value)
    load()
  }

  def fetchOr(value: Byte): Byte = atomic_or_byte(underlying, value)

  def xorFetch(value: Byte): Byte = {
    fetchXor(value)
    load()
  }

  def fetchXor(value: Byte): Byte = atomic_xor_byte(underlying, value)

  override def toString(): String = load().toString

  override def equals(that: Any): Boolean = that match {
    case o: CAtomicByte => o.load() == load()
    case o: Byte => load() == o
    case _ => false
  }

}
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 44)

final case class CAtomicShort(private val initValue: CShort = 0.toShort) extends CAtomic[CShort] {
  private[this] val underlying: Ptr[CShort] = fromRawPtr(libc.malloc(sizeof[CShort]))
  atomic_init_short(underlying, initValue)

  def free(): Unit = libc.free(toRawPtr(underlying))

  def load(): CShort = atomic_load_short(underlying)

  def store(value: CShort): Unit = atomic_store_short(underlying, value)

  def exchange(value: CShort): CShort = atomic_exchange_short(underlying, value)

  def compareAndSwapStrong(expected: CShort, desired: CShort): (Boolean, CShort) = {
    val expectedPtr: Ptr[CShort] = fromRawPtr(Intrinsics.stackalloc(sizeof[CShort]))
    !expectedPtr = expected    
    if (compare_and_swap_strong_short(underlying, expectedPtr, desired)) {
      (true, desired)
    } else {
      (false, !expectedPtr)
    }
  }

  def compareAndSwapWeak(expected: CShort, desired: CShort): (Boolean, CShort) = {
    val expectedPtr: Ptr[CShort] = fromRawPtr(Intrinsics.stackalloc(sizeof[CShort]))
    !expectedPtr = expected    
    if (compare_and_swap_weak_short(underlying, expectedPtr, desired)) {
      (true, desired)
    } else {
      (false, !expectedPtr)
    }
  }

  def addFetch(value: CShort): CShort = {
    fetchAdd(value)
    load()
  }

  def fetchAdd(value: CShort): CShort = atomic_add_short(underlying, value)

  def subFetch(value: CShort): CShort = {
    fetchSub(value)
    load()
  }

  def fetchSub(value: CShort): CShort = atomic_sub_short(underlying, value)

  def andFetch(value: CShort): CShort = {
    fetchAnd(value)
    load()
  }

  def fetchAnd(value: CShort): CShort = atomic_and_short(underlying, value)

  def orFetch(value: CShort): CShort = {
    fetchOr(value)
    load()
  }

  def fetchOr(value: CShort): CShort = atomic_or_short(underlying, value)

  def xorFetch(value: CShort): CShort = {
    fetchXor(value)
    load()
  }

  def fetchXor(value: CShort): CShort = atomic_xor_short(underlying, value)

  override def toString(): String = load().toString

  override def equals(that: Any): Boolean = that match {
    case o: CAtomicShort => o.load() == load()
    case o: CShort => load() == o
    case _ => false
  }

}
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 44)

final case class CAtomicInt(private val initValue: CInt = 0) extends CAtomic[CInt] {
  private[this] val underlying: Ptr[CInt] = fromRawPtr(libc.malloc(sizeof[CInt]))
  atomic_init_int(underlying, initValue)

  def free(): Unit = libc.free(toRawPtr(underlying))

  def load(): CInt = atomic_load_int(underlying)

  def store(value: CInt): Unit = atomic_store_int(underlying, value)

  def exchange(value: CInt): CInt = atomic_exchange_int(underlying, value)

  def compareAndSwapStrong(expected: CInt, desired: CInt): (Boolean, CInt) = {
    val expectedPtr: Ptr[CInt] = fromRawPtr(Intrinsics.stackalloc(sizeof[CInt]))
    !expectedPtr = expected    
    if (compare_and_swap_strong_int(underlying, expectedPtr, desired)) {
      (true, desired)
    } else {
      (false, !expectedPtr)
    }
  }

  def compareAndSwapWeak(expected: CInt, desired: CInt): (Boolean, CInt) = {
    val expectedPtr: Ptr[CInt] = fromRawPtr(Intrinsics.stackalloc(sizeof[CInt]))
    !expectedPtr = expected    
    if (compare_and_swap_weak_int(underlying, expectedPtr, desired)) {
      (true, desired)
    } else {
      (false, !expectedPtr)
    }
  }

  def addFetch(value: CInt): CInt = {
    fetchAdd(value)
    load()
  }

  def fetchAdd(value: CInt): CInt = atomic_add_int(underlying, value)

  def subFetch(value: CInt): CInt = {
    fetchSub(value)
    load()
  }

  def fetchSub(value: CInt): CInt = atomic_sub_int(underlying, value)

  def andFetch(value: CInt): CInt = {
    fetchAnd(value)
    load()
  }

  def fetchAnd(value: CInt): CInt = atomic_and_int(underlying, value)

  def orFetch(value: CInt): CInt = {
    fetchOr(value)
    load()
  }

  def fetchOr(value: CInt): CInt = atomic_or_int(underlying, value)

  def xorFetch(value: CInt): CInt = {
    fetchXor(value)
    load()
  }

  def fetchXor(value: CInt): CInt = atomic_xor_int(underlying, value)

  override def toString(): String = load().toString

  override def equals(that: Any): Boolean = that match {
    case o: CAtomicInt => o.load() == load()
    case o: CInt => load() == o
    case _ => false
  }

}
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 44)

final case class CAtomicLong(private val initValue: CLong = 0L) extends CAtomic[CLong] {
  private[this] val underlying: Ptr[CLong] = fromRawPtr(libc.malloc(sizeof[CLong]))
  atomic_init_long(underlying, initValue)

  def free(): Unit = libc.free(toRawPtr(underlying))

  def load(): CLong = atomic_load_long(underlying)

  def store(value: CLong): Unit = atomic_store_long(underlying, value)

  def exchange(value: CLong): CLong = atomic_exchange_long(underlying, value)

  def compareAndSwapStrong(expected: CLong, desired: CLong): (Boolean, CLong) = {
    val expectedPtr: Ptr[CLong] = fromRawPtr(Intrinsics.stackalloc(sizeof[CLong]))
    !expectedPtr = expected    
    if (compare_and_swap_strong_long(underlying, expectedPtr, desired)) {
      (true, desired)
    } else {
      (false, !expectedPtr)
    }
  }

  def compareAndSwapWeak(expected: CLong, desired: CLong): (Boolean, CLong) = {
    val expectedPtr: Ptr[CLong] = fromRawPtr(Intrinsics.stackalloc(sizeof[CLong]))
    !expectedPtr = expected    
    if (compare_and_swap_weak_long(underlying, expectedPtr, desired)) {
      (true, desired)
    } else {
      (false, !expectedPtr)
    }
  }

  def addFetch(value: CLong): CLong = {
    fetchAdd(value)
    load()
  }

  def fetchAdd(value: CLong): CLong = atomic_add_long(underlying, value)

  def subFetch(value: CLong): CLong = {
    fetchSub(value)
    load()
  }

  def fetchSub(value: CLong): CLong = atomic_sub_long(underlying, value)

  def andFetch(value: CLong): CLong = {
    fetchAnd(value)
    load()
  }

  def fetchAnd(value: CLong): CLong = atomic_and_long(underlying, value)

  def orFetch(value: CLong): CLong = {
    fetchOr(value)
    load()
  }

  def fetchOr(value: CLong): CLong = atomic_or_long(underlying, value)

  def xorFetch(value: CLong): CLong = {
    fetchXor(value)
    load()
  }

  def fetchXor(value: CLong): CLong = atomic_xor_long(underlying, value)

  override def toString(): String = load().toString

  override def equals(that: Any): Boolean = that match {
    case o: CAtomicLong => o.load() == load()
    case o: CLong => load() == o
    case _ => false
  }

}
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 44)

final case class CAtomicUByte(private val initValue: UByte = 0.toUByte) extends CAtomic[UByte] {
  private[this] val underlying: Ptr[UByte] = fromRawPtr(libc.malloc(sizeof[UByte]))
  atomic_init_ubyte(underlying, initValue)

  def free(): Unit = libc.free(toRawPtr(underlying))

  def load(): UByte = atomic_load_ubyte(underlying)

  def store(value: UByte): Unit = atomic_store_ubyte(underlying, value)

  def exchange(value: UByte): UByte = atomic_exchange_ubyte(underlying, value)

  def compareAndSwapStrong(expected: UByte, desired: UByte): (Boolean, UByte) = {
    val expectedPtr: Ptr[UByte] = fromRawPtr(Intrinsics.stackalloc(sizeof[UByte]))
    !expectedPtr = expected    
    if (compare_and_swap_strong_ubyte(underlying, expectedPtr, desired)) {
      (true, desired)
    } else {
      (false, !expectedPtr)
    }
  }

  def compareAndSwapWeak(expected: UByte, desired: UByte): (Boolean, UByte) = {
    val expectedPtr: Ptr[UByte] = fromRawPtr(Intrinsics.stackalloc(sizeof[UByte]))
    !expectedPtr = expected    
    if (compare_and_swap_weak_ubyte(underlying, expectedPtr, desired)) {
      (true, desired)
    } else {
      (false, !expectedPtr)
    }
  }

  def addFetch(value: UByte): UByte = {
    fetchAdd(value)
    load()
  }

  def fetchAdd(value: UByte): UByte = atomic_add_ubyte(underlying, value)

  def subFetch(value: UByte): UByte = {
    fetchSub(value)
    load()
  }

  def fetchSub(value: UByte): UByte = atomic_sub_ubyte(underlying, value)

  def andFetch(value: UByte): UByte = {
    fetchAnd(value)
    load()
  }

  def fetchAnd(value: UByte): UByte = atomic_and_ubyte(underlying, value)

  def orFetch(value: UByte): UByte = {
    fetchOr(value)
    load()
  }

  def fetchOr(value: UByte): UByte = atomic_or_ubyte(underlying, value)

  def xorFetch(value: UByte): UByte = {
    fetchXor(value)
    load()
  }

  def fetchXor(value: UByte): UByte = atomic_xor_ubyte(underlying, value)

  override def toString(): String = load().toString

  override def equals(that: Any): Boolean = that match {
    case o: CAtomicUByte => o.load() == load()
    case o: UByte => load() == o
    case _ => false
  }

}
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 44)

final case class CAtomicUShort(private val initValue: UShort = 0.toUShort) extends CAtomic[UShort] {
  private[this] val underlying: Ptr[UShort] = fromRawPtr(libc.malloc(sizeof[UShort]))
  atomic_init_ushort(underlying, initValue)

  def free(): Unit = libc.free(toRawPtr(underlying))

  def load(): UShort = atomic_load_ushort(underlying)

  def store(value: UShort): Unit = atomic_store_ushort(underlying, value)

  def exchange(value: UShort): UShort = atomic_exchange_ushort(underlying, value)

  def compareAndSwapStrong(expected: UShort, desired: UShort): (Boolean, UShort) = {
    val expectedPtr: Ptr[UShort] = fromRawPtr(Intrinsics.stackalloc(sizeof[UShort]))
    !expectedPtr = expected    
    if (compare_and_swap_strong_ushort(underlying, expectedPtr, desired)) {
      (true, desired)
    } else {
      (false, !expectedPtr)
    }
  }

  def compareAndSwapWeak(expected: UShort, desired: UShort): (Boolean, UShort) = {
    val expectedPtr: Ptr[UShort] = fromRawPtr(Intrinsics.stackalloc(sizeof[UShort]))
    !expectedPtr = expected    
    if (compare_and_swap_weak_ushort(underlying, expectedPtr, desired)) {
      (true, desired)
    } else {
      (false, !expectedPtr)
    }
  }

  def addFetch(value: UShort): UShort = {
    fetchAdd(value)
    load()
  }

  def fetchAdd(value: UShort): UShort = atomic_add_ushort(underlying, value)

  def subFetch(value: UShort): UShort = {
    fetchSub(value)
    load()
  }

  def fetchSub(value: UShort): UShort = atomic_sub_ushort(underlying, value)

  def andFetch(value: UShort): UShort = {
    fetchAnd(value)
    load()
  }

  def fetchAnd(value: UShort): UShort = atomic_and_ushort(underlying, value)

  def orFetch(value: UShort): UShort = {
    fetchOr(value)
    load()
  }

  def fetchOr(value: UShort): UShort = atomic_or_ushort(underlying, value)

  def xorFetch(value: UShort): UShort = {
    fetchXor(value)
    load()
  }

  def fetchXor(value: UShort): UShort = atomic_xor_ushort(underlying, value)

  override def toString(): String = load().toString

  override def equals(that: Any): Boolean = that match {
    case o: CAtomicUShort => o.load() == load()
    case o: UShort => load() == o
    case _ => false
  }

}
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 44)

final case class CAtomicUInt(private val initValue: UInt = 0.toUInt) extends CAtomic[UInt] {
  private[this] val underlying: Ptr[UInt] = fromRawPtr(libc.malloc(sizeof[UInt]))
  atomic_init_uint(underlying, initValue)

  def free(): Unit = libc.free(toRawPtr(underlying))

  def load(): UInt = atomic_load_uint(underlying)

  def store(value: UInt): Unit = atomic_store_uint(underlying, value)

  def exchange(value: UInt): UInt = atomic_exchange_uint(underlying, value)

  def compareAndSwapStrong(expected: UInt, desired: UInt): (Boolean, UInt) = {
    val expectedPtr: Ptr[UInt] = fromRawPtr(Intrinsics.stackalloc(sizeof[UInt]))
    !expectedPtr = expected    
    if (compare_and_swap_strong_uint(underlying, expectedPtr, desired)) {
      (true, desired)
    } else {
      (false, !expectedPtr)
    }
  }

  def compareAndSwapWeak(expected: UInt, desired: UInt): (Boolean, UInt) = {
    val expectedPtr: Ptr[UInt] = fromRawPtr(Intrinsics.stackalloc(sizeof[UInt]))
    !expectedPtr = expected    
    if (compare_and_swap_weak_uint(underlying, expectedPtr, desired)) {
      (true, desired)
    } else {
      (false, !expectedPtr)
    }
  }

  def addFetch(value: UInt): UInt = {
    fetchAdd(value)
    load()
  }

  def fetchAdd(value: UInt): UInt = atomic_add_uint(underlying, value)

  def subFetch(value: UInt): UInt = {
    fetchSub(value)
    load()
  }

  def fetchSub(value: UInt): UInt = atomic_sub_uint(underlying, value)

  def andFetch(value: UInt): UInt = {
    fetchAnd(value)
    load()
  }

  def fetchAnd(value: UInt): UInt = atomic_and_uint(underlying, value)

  def orFetch(value: UInt): UInt = {
    fetchOr(value)
    load()
  }

  def fetchOr(value: UInt): UInt = atomic_or_uint(underlying, value)

  def xorFetch(value: UInt): UInt = {
    fetchXor(value)
    load()
  }

  def fetchXor(value: UInt): UInt = atomic_xor_uint(underlying, value)

  override def toString(): String = load().toString

  override def equals(that: Any): Boolean = that match {
    case o: CAtomicUInt => o.load() == load()
    case o: UInt => load() == o
    case _ => false
  }

}
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 44)

final case class CAtomicULong(private val initValue: ULong = 0.toULong) extends CAtomic[ULong] {
  private[this] val underlying: Ptr[ULong] = fromRawPtr(libc.malloc(sizeof[ULong]))
  atomic_init_ulong(underlying, initValue)

  def free(): Unit = libc.free(toRawPtr(underlying))

  def load(): ULong = atomic_load_ulong(underlying)

  def store(value: ULong): Unit = atomic_store_ulong(underlying, value)

  def exchange(value: ULong): ULong = atomic_exchange_ulong(underlying, value)

  def compareAndSwapStrong(expected: ULong, desired: ULong): (Boolean, ULong) = {
    val expectedPtr: Ptr[ULong] = fromRawPtr(Intrinsics.stackalloc(sizeof[ULong]))
    !expectedPtr = expected    
    if (compare_and_swap_strong_ulong(underlying, expectedPtr, desired)) {
      (true, desired)
    } else {
      (false, !expectedPtr)
    }
  }

  def compareAndSwapWeak(expected: ULong, desired: ULong): (Boolean, ULong) = {
    val expectedPtr: Ptr[ULong] = fromRawPtr(Intrinsics.stackalloc(sizeof[ULong]))
    !expectedPtr = expected    
    if (compare_and_swap_weak_ulong(underlying, expectedPtr, desired)) {
      (true, desired)
    } else {
      (false, !expectedPtr)
    }
  }

  def addFetch(value: ULong): ULong = {
    fetchAdd(value)
    load()
  }

  def fetchAdd(value: ULong): ULong = atomic_add_ulong(underlying, value)

  def subFetch(value: ULong): ULong = {
    fetchSub(value)
    load()
  }

  def fetchSub(value: ULong): ULong = atomic_sub_ulong(underlying, value)

  def andFetch(value: ULong): ULong = {
    fetchAnd(value)
    load()
  }

  def fetchAnd(value: ULong): ULong = atomic_and_ulong(underlying, value)

  def orFetch(value: ULong): ULong = {
    fetchOr(value)
    load()
  }

  def fetchOr(value: ULong): ULong = atomic_or_ulong(underlying, value)

  def xorFetch(value: ULong): ULong = {
    fetchXor(value)
    load()
  }

  def fetchXor(value: ULong): ULong = atomic_xor_ulong(underlying, value)

  override def toString(): String = load().toString

  override def equals(that: Any): Boolean = that match {
    case o: CAtomicULong => o.load() == load()
    case o: ULong => load() == o
    case _ => false
  }

}
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 44)

final case class CAtomicChar(private val initValue: CChar = 0.toByte) extends CAtomic[CChar] {
  private[this] val underlying: Ptr[CChar] = fromRawPtr(libc.malloc(sizeof[CChar]))
  atomic_init_char(underlying, initValue)

  def free(): Unit = libc.free(toRawPtr(underlying))

  def load(): CChar = atomic_load_char(underlying)

  def store(value: CChar): Unit = atomic_store_char(underlying, value)

  def exchange(value: CChar): CChar = atomic_exchange_char(underlying, value)

  def compareAndSwapStrong(expected: CChar, desired: CChar): (Boolean, CChar) = {
    val expectedPtr: Ptr[CChar] = fromRawPtr(Intrinsics.stackalloc(sizeof[CChar]))
    !expectedPtr = expected    
    if (compare_and_swap_strong_char(underlying, expectedPtr, desired)) {
      (true, desired)
    } else {
      (false, !expectedPtr)
    }
  }

  def compareAndSwapWeak(expected: CChar, desired: CChar): (Boolean, CChar) = {
    val expectedPtr: Ptr[CChar] = fromRawPtr(Intrinsics.stackalloc(sizeof[CChar]))
    !expectedPtr = expected    
    if (compare_and_swap_weak_char(underlying, expectedPtr, desired)) {
      (true, desired)
    } else {
      (false, !expectedPtr)
    }
  }

  def addFetch(value: CChar): CChar = {
    fetchAdd(value)
    load()
  }

  def fetchAdd(value: CChar): CChar = atomic_add_char(underlying, value)

  def subFetch(value: CChar): CChar = {
    fetchSub(value)
    load()
  }

  def fetchSub(value: CChar): CChar = atomic_sub_char(underlying, value)

  def andFetch(value: CChar): CChar = {
    fetchAnd(value)
    load()
  }

  def fetchAnd(value: CChar): CChar = atomic_and_char(underlying, value)

  def orFetch(value: CChar): CChar = {
    fetchOr(value)
    load()
  }

  def fetchOr(value: CChar): CChar = atomic_or_char(underlying, value)

  def xorFetch(value: CChar): CChar = {
    fetchXor(value)
    load()
  }

  def fetchXor(value: CChar): CChar = atomic_xor_char(underlying, value)

  override def toString(): String = load().toString

  override def equals(that: Any): Boolean = that match {
    case o: CAtomicChar => o.load() == load()
    case o: CChar => load() == o
    case _ => false
  }

}
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 44)

final case class CAtomicCSize(private val initValue: CSize = 0.toUInt) extends CAtomic[CSize] {
  private[this] val underlying: Ptr[CSize] = fromRawPtr(libc.malloc(sizeof[CSize]))
  atomic_init_csize(underlying, initValue)

  def free(): Unit = libc.free(toRawPtr(underlying))

  def load(): CSize = atomic_load_csize(underlying)

  def store(value: CSize): Unit = atomic_store_csize(underlying, value)

  def exchange(value: CSize): CSize = atomic_exchange_csize(underlying, value)

  def compareAndSwapStrong(expected: CSize, desired: CSize): (Boolean, CSize) = {
    val expectedPtr: Ptr[CSize] = fromRawPtr(Intrinsics.stackalloc(sizeof[CSize]))
    !expectedPtr = expected    
    if (compare_and_swap_strong_csize(underlying, expectedPtr, desired)) {
      (true, desired)
    } else {
      (false, !expectedPtr)
    }
  }

  def compareAndSwapWeak(expected: CSize, desired: CSize): (Boolean, CSize) = {
    val expectedPtr: Ptr[CSize] = fromRawPtr(Intrinsics.stackalloc(sizeof[CSize]))
    !expectedPtr = expected    
    if (compare_and_swap_weak_csize(underlying, expectedPtr, desired)) {
      (true, desired)
    } else {
      (false, !expectedPtr)
    }
  }

  def addFetch(value: CSize): CSize = {
    fetchAdd(value)
    load()
  }

  def fetchAdd(value: CSize): CSize = atomic_add_csize(underlying, value)

  def subFetch(value: CSize): CSize = {
    fetchSub(value)
    load()
  }

  def fetchSub(value: CSize): CSize = atomic_sub_csize(underlying, value)

  def andFetch(value: CSize): CSize = {
    fetchAnd(value)
    load()
  }

  def fetchAnd(value: CSize): CSize = atomic_and_csize(underlying, value)

  def orFetch(value: CSize): CSize = {
    fetchOr(value)
    load()
  }

  def fetchOr(value: CSize): CSize = atomic_or_csize(underlying, value)

  def xorFetch(value: CSize): CSize = {
    fetchXor(value)
    load()
  }

  def fetchXor(value: CSize): CSize = atomic_xor_csize(underlying, value)

  override def toString(): String = load().toString

  override def equals(that: Any): Boolean = that match {
    case o: CAtomicCSize => o.load() == load()
    case o: CSize => load() == o
    case _ => false
  }

}
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 122)

final case class CAtomicRef[T <: AnyRef](default: T = null.asInstanceOf[T]) extends CAtomic[T] {
  type Repr = CLong
  private[this] val underlying: Ptr[Repr] = fromRawPtr(libc.malloc(sizeof[Repr]))
  atomic_init_long(underlying, default)

  def free(): Unit = libc.free(toRawPtr(underlying))

  def load(): T = atomic_load_long(underlying)

  def store(value: T): Unit = atomic_store_long(underlying, value)

  def exchange(value: T): T = atomic_exchange_long(underlying, value)

  def compareAndSwapStrong(expected: T, desired: T): (Boolean, T) = {
    val expectedPtr: Ptr[Repr] = fromRawPtr(Intrinsics.stackalloc(sizeof[Repr]))
    !expectedPtr = expected    
    if (compare_and_swap_strong_long(underlying, expectedPtr, desired)) {
      (true, desired)
    } else {
      (false, !expectedPtr)
    }
  }

  def compareAndSwapWeak(expected: T, desired: T): (Boolean, T) = {
    val expectedPtr: Ptr[Repr] = fromRawPtr(Intrinsics.stackalloc(sizeof[Repr]))
    !expectedPtr = expected        
    if (compare_and_swap_weak_long(underlying, expectedPtr, desired)) {
      (true, desired)
    } else {
      (false, !expectedPtr)
    }
  }

  def addFetch(value: T): T = throw new UnsupportedOperationException()
  def fetchAdd(value: T): T = throw new UnsupportedOperationException()
  def subFetch(value: T): T = throw new UnsupportedOperationException()
  def fetchSub(value: T): T = throw new UnsupportedOperationException()
  def andFetch(value: T): T = throw new UnsupportedOperationException()
  def fetchAnd(value: T): T = throw new UnsupportedOperationException()
  def orFetch(value: T): T  = throw new UnsupportedOperationException()
  def fetchOr(value: T): T  = throw new UnsupportedOperationException()
  def xorFetch(value: T): T = throw new UnsupportedOperationException()
  def fetchXor(value: T): T = throw new UnsupportedOperationException()

  override def toString(): String = load().toString

  override def equals(that: Any): Boolean = that match {
    case o: CAtomicRef[_] @unchecked => o.load() == load()
    case o: T @unchecked             => load() == o
    case _                					 => false
  }

  private implicit def toRepr(ref: T): Repr = Intrinsics.castRawPtrToLong {
    Intrinsics.castObjectToRawPtr(ref)
  }
  private implicit def toRef(repr: Repr): T = {
    Intrinsics
      .castRawPtrToObject {
        Intrinsics.castLongToRawPtr(repr)
      }
      .asInstanceOf[T]
  }
}

// Helper object, can be imported for ease of use
object CAtomicsImplicits {
  implicit def cas[T](v: (Boolean, T)): Boolean = v._1
	implicit def underlying[T <: AnyRef](v: CAtomicRef[T]): T = v.load()
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 192)
  implicit def underlying(a: CAtomicByte): Byte = a.load()
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 192)
  implicit def underlying(a: CAtomicShort): CShort = a.load()
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 192)
  implicit def underlying(a: CAtomicInt): CInt = a.load()
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 192)
  implicit def underlying(a: CAtomicLong): CLong = a.load()
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 192)
  implicit def underlying(a: CAtomicUByte): UByte = a.load()
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 192)
  implicit def underlying(a: CAtomicUShort): UShort = a.load()
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 192)
  implicit def underlying(a: CAtomicUInt): UInt = a.load()
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 192)
  implicit def underlying(a: CAtomicULong): ULong = a.load()
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 192)
  implicit def underlying(a: CAtomicChar): CChar = a.load()
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 192)
  implicit def underlying(a: CAtomicCSize): CSize = a.load()
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/CAtomics.scala.gyb", line: 194)
}