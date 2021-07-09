// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 1)
package scala.scalanative.unsafe

import scala.scalanative.runtime.{fromRawPtr, toRawPtr, Intrinsics}
import scala.scalanative.unsigned._
import scala.scalanative.annotation._
import scala.language.implicitConversions

// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 24)
@extern
object atomic {
  type memory_order = Int // enum
  @extern
  object memory_order {
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 30)
    @name("scalanative_atomic_memory_order_relaxed")
    final def memory_order_relaxed: memory_order = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 30)
    @name("scalanative_atomic_memory_order_consume")
    final def memory_order_consume: memory_order = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 30)
    @name("scalanative_atomic_memory_order_acquire")
    final def memory_order_acquire: memory_order = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 30)
    @name("scalanative_atomic_memory_order_release")
    final def memory_order_release: memory_order = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 30)
    @name("scalanative_atomic_memory_order_acq_rel")
    final def memory_order_acq_rel: memory_order = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 30)
    @name("scalanative_atomic_memory_order_seq_cst")
    final def memory_order_seq_cst: memory_order = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 33)
  }

  @name("scalanative_atomic_thread_fence")
  final def atomic_thread_fence(order: memory_order): Unit = extern

  @name("scalanative_atomic_signal_fence")
  final def atomic_signal_fence(order: memory_order): Unit = extern

// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 42)
  type CAtomicByte = Ptr[Byte]

  @name("scalanative_atomic_init_byte")
  def atomic_init_byte(atm: CAtomicByte, initValue: Byte): Unit = extern

  @name("scalanative_atomic_load_byte")
  def atomic_load_byte(ptr: CAtomicByte): Byte = extern

  @name("scalanative_atomic_load_explicit_byte")
  def atomic_load_explicit_byte(
      ptr: CAtomicByte,
      memoryOrder: memory_order
  ): Byte = extern

  @name("scalanative_atomic_store_byte")
  def atomic_store_byte(ptr: CAtomicByte, v: Byte): Unit = extern

  @name("scalanative_atomic_store_explicit_byte")
  def atomic_store_explicit_byte(
      ptr: CAtomicByte,
      v: Byte,
      memoryOrder: memory_order
  ): Unit = extern

  @name("scalanative_atomic_exchange_byte")
  def atomic_exchange_byte(ptr: CAtomicByte, v: Byte): Byte = extern

  @name("scalanative_atomic_exchange_explicit_byte")
  def atomic_exchange_explicit_byte(
      ptr: CAtomicByte,
      v: Byte,
      memoryOrder: memory_order
  ): Byte = extern

  @name("scalanative_atomic_compare_exchange_strong_byte")
  def atomic_compare_exchange_strong_byte(
      value: CAtomicByte,
      expected: CAtomicByte,
      desired: Byte
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_strong_explicit_byte")
  def atomic_compare_exchange_strong_explicit_byte(
      value: CAtomicByte,
      expected: CAtomicByte,
      desired: Byte,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_weak_byte")
  def atomic_compare_exchange_weak_byte(
      value: CAtomicByte,
      expected: CAtomicByte,
      desired: Byte
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_weak_explicit_byte")
  def atomic_compare_exchange_weak_explicit_byte(
      value: CAtomicByte,
      expected: CAtomicByte,
      desired: Byte,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): CBool = extern

// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_add_byte")
  def atomic_fetch_add_byte(ptr: CAtomicByte, value: Byte): Byte = extern

  @name("scalanative_atomic_fetch_add_explicit_byte")
  def atomic_fetch_add_explicit_byte(
      ptr: CAtomicByte,
      value: Byte,
      memoryOrder: memory_order
  ): Byte = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_sub_byte")
  def atomic_fetch_sub_byte(ptr: CAtomicByte, value: Byte): Byte = extern

  @name("scalanative_atomic_fetch_sub_explicit_byte")
  def atomic_fetch_sub_explicit_byte(
      ptr: CAtomicByte,
      value: Byte,
      memoryOrder: memory_order
  ): Byte = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_or_byte")
  def atomic_fetch_or_byte(ptr: CAtomicByte, value: Byte): Byte = extern

  @name("scalanative_atomic_fetch_or_explicit_byte")
  def atomic_fetch_or_explicit_byte(
      ptr: CAtomicByte,
      value: Byte,
      memoryOrder: memory_order
  ): Byte = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_and_byte")
  def atomic_fetch_and_byte(ptr: CAtomicByte, value: Byte): Byte = extern

  @name("scalanative_atomic_fetch_and_explicit_byte")
  def atomic_fetch_and_explicit_byte(
      ptr: CAtomicByte,
      value: Byte,
      memoryOrder: memory_order
  ): Byte = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_xor_byte")
  def atomic_fetch_xor_byte(ptr: CAtomicByte, value: Byte): Byte = extern

  @name("scalanative_atomic_fetch_xor_explicit_byte")
  def atomic_fetch_xor_explicit_byte(
      ptr: CAtomicByte,
      value: Byte,
      memoryOrder: memory_order
  ): Byte = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 42)
  type CAtomicShort = Ptr[CShort]

  @name("scalanative_atomic_init_short")
  def atomic_init_short(atm: CAtomicShort, initValue: CShort): Unit = extern

  @name("scalanative_atomic_load_short")
  def atomic_load_short(ptr: CAtomicShort): CShort = extern

  @name("scalanative_atomic_load_explicit_short")
  def atomic_load_explicit_short(
      ptr: CAtomicShort,
      memoryOrder: memory_order
  ): CShort = extern

  @name("scalanative_atomic_store_short")
  def atomic_store_short(ptr: CAtomicShort, v: CShort): Unit = extern

  @name("scalanative_atomic_store_explicit_short")
  def atomic_store_explicit_short(
      ptr: CAtomicShort,
      v: CShort,
      memoryOrder: memory_order
  ): Unit = extern

  @name("scalanative_atomic_exchange_short")
  def atomic_exchange_short(ptr: CAtomicShort, v: CShort): CShort = extern

  @name("scalanative_atomic_exchange_explicit_short")
  def atomic_exchange_explicit_short(
      ptr: CAtomicShort,
      v: CShort,
      memoryOrder: memory_order
  ): CShort = extern

  @name("scalanative_atomic_compare_exchange_strong_short")
  def atomic_compare_exchange_strong_short(
      value: CAtomicShort,
      expected: CAtomicShort,
      desired: CShort
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_strong_explicit_short")
  def atomic_compare_exchange_strong_explicit_short(
      value: CAtomicShort,
      expected: CAtomicShort,
      desired: CShort,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_weak_short")
  def atomic_compare_exchange_weak_short(
      value: CAtomicShort,
      expected: CAtomicShort,
      desired: CShort
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_weak_explicit_short")
  def atomic_compare_exchange_weak_explicit_short(
      value: CAtomicShort,
      expected: CAtomicShort,
      desired: CShort,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): CBool = extern

// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_add_short")
  def atomic_fetch_add_short(ptr: CAtomicShort, value: CShort): CShort = extern

  @name("scalanative_atomic_fetch_add_explicit_short")
  def atomic_fetch_add_explicit_short(
      ptr: CAtomicShort,
      value: CShort,
      memoryOrder: memory_order
  ): CShort =
    extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_sub_short")
  def atomic_fetch_sub_short(ptr: CAtomicShort, value: CShort): CShort = extern

  @name("scalanative_atomic_fetch_sub_explicit_short")
  def atomic_fetch_sub_explicit_short(
      ptr: CAtomicShort,
      value: CShort,
      memoryOrder: memory_order
  ): CShort =
    extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_or_short")
  def atomic_fetch_or_short(ptr: CAtomicShort, value: CShort): CShort = extern

  @name("scalanative_atomic_fetch_or_explicit_short")
  def atomic_fetch_or_explicit_short(
      ptr: CAtomicShort,
      value: CShort,
      memoryOrder: memory_order
  ): CShort = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_and_short")
  def atomic_fetch_and_short(ptr: CAtomicShort, value: CShort): CShort = extern

  @name("scalanative_atomic_fetch_and_explicit_short")
  def atomic_fetch_and_explicit_short(
      ptr: CAtomicShort,
      value: CShort,
      memoryOrder: memory_order
  ): CShort =
    extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_xor_short")
  def atomic_fetch_xor_short(ptr: CAtomicShort, value: CShort): CShort = extern

  @name("scalanative_atomic_fetch_xor_explicit_short")
  def atomic_fetch_xor_explicit_short(
      ptr: CAtomicShort,
      value: CShort,
      memoryOrder: memory_order
  ): CShort =
    extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 42)
  type CAtomicInt = Ptr[CInt]

  @name("scalanative_atomic_init_int")
  def atomic_init_int(atm: CAtomicInt, initValue: CInt): Unit = extern

  @name("scalanative_atomic_load_int")
  def atomic_load_int(ptr: CAtomicInt): CInt = extern

  @name("scalanative_atomic_load_explicit_int")
  def atomic_load_explicit_int(
      ptr: CAtomicInt,
      memoryOrder: memory_order
  ): CInt = extern

  @name("scalanative_atomic_store_int")
  def atomic_store_int(ptr: CAtomicInt, v: CInt): Unit = extern

  @name("scalanative_atomic_store_explicit_int")
  def atomic_store_explicit_int(
      ptr: CAtomicInt,
      v: CInt,
      memoryOrder: memory_order
  ): Unit = extern

  @name("scalanative_atomic_exchange_int")
  def atomic_exchange_int(ptr: CAtomicInt, v: CInt): CInt = extern

  @name("scalanative_atomic_exchange_explicit_int")
  def atomic_exchange_explicit_int(
      ptr: CAtomicInt,
      v: CInt,
      memoryOrder: memory_order
  ): CInt = extern

  @name("scalanative_atomic_compare_exchange_strong_int")
  def atomic_compare_exchange_strong_int(
      value: CAtomicInt,
      expected: CAtomicInt,
      desired: CInt
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_strong_explicit_int")
  def atomic_compare_exchange_strong_explicit_int(
      value: CAtomicInt,
      expected: CAtomicInt,
      desired: CInt,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_weak_int")
  def atomic_compare_exchange_weak_int(
      value: CAtomicInt,
      expected: CAtomicInt,
      desired: CInt
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_weak_explicit_int")
  def atomic_compare_exchange_weak_explicit_int(
      value: CAtomicInt,
      expected: CAtomicInt,
      desired: CInt,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): CBool = extern

// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_add_int")
  def atomic_fetch_add_int(ptr: CAtomicInt, value: CInt): CInt = extern

  @name("scalanative_atomic_fetch_add_explicit_int")
  def atomic_fetch_add_explicit_int(
      ptr: CAtomicInt,
      value: CInt,
      memoryOrder: memory_order
  ): CInt = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_sub_int")
  def atomic_fetch_sub_int(ptr: CAtomicInt, value: CInt): CInt = extern

  @name("scalanative_atomic_fetch_sub_explicit_int")
  def atomic_fetch_sub_explicit_int(
      ptr: CAtomicInt,
      value: CInt,
      memoryOrder: memory_order
  ): CInt = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_or_int")
  def atomic_fetch_or_int(ptr: CAtomicInt, value: CInt): CInt = extern

  @name("scalanative_atomic_fetch_or_explicit_int")
  def atomic_fetch_or_explicit_int(
      ptr: CAtomicInt,
      value: CInt,
      memoryOrder: memory_order
  ): CInt = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_and_int")
  def atomic_fetch_and_int(ptr: CAtomicInt, value: CInt): CInt = extern

  @name("scalanative_atomic_fetch_and_explicit_int")
  def atomic_fetch_and_explicit_int(
      ptr: CAtomicInt,
      value: CInt,
      memoryOrder: memory_order
  ): CInt = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_xor_int")
  def atomic_fetch_xor_int(ptr: CAtomicInt, value: CInt): CInt = extern

  @name("scalanative_atomic_fetch_xor_explicit_int")
  def atomic_fetch_xor_explicit_int(
      ptr: CAtomicInt,
      value: CInt,
      memoryOrder: memory_order
  ): CInt = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 42)
  type CAtomicLong = Ptr[CLong]

  @name("scalanative_atomic_init_long")
  def atomic_init_long(atm: CAtomicLong, initValue: CLong): Unit = extern

  @name("scalanative_atomic_load_long")
  def atomic_load_long(ptr: CAtomicLong): CLong = extern

  @name("scalanative_atomic_load_explicit_long")
  def atomic_load_explicit_long(
      ptr: CAtomicLong,
      memoryOrder: memory_order
  ): CLong = extern

  @name("scalanative_atomic_store_long")
  def atomic_store_long(ptr: CAtomicLong, v: CLong): Unit = extern

  @name("scalanative_atomic_store_explicit_long")
  def atomic_store_explicit_long(
      ptr: CAtomicLong,
      v: CLong,
      memoryOrder: memory_order
  ): Unit = extern

  @name("scalanative_atomic_exchange_long")
  def atomic_exchange_long(ptr: CAtomicLong, v: CLong): CLong = extern

  @name("scalanative_atomic_exchange_explicit_long")
  def atomic_exchange_explicit_long(
      ptr: CAtomicLong,
      v: CLong,
      memoryOrder: memory_order
  ): CLong = extern

  @name("scalanative_atomic_compare_exchange_strong_long")
  def atomic_compare_exchange_strong_long(
      value: CAtomicLong,
      expected: CAtomicLong,
      desired: CLong
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_strong_explicit_long")
  def atomic_compare_exchange_strong_explicit_long(
      value: CAtomicLong,
      expected: CAtomicLong,
      desired: CLong,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_weak_long")
  def atomic_compare_exchange_weak_long(
      value: CAtomicLong,
      expected: CAtomicLong,
      desired: CLong
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_weak_explicit_long")
  def atomic_compare_exchange_weak_explicit_long(
      value: CAtomicLong,
      expected: CAtomicLong,
      desired: CLong,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): CBool = extern

// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_add_long")
  def atomic_fetch_add_long(ptr: CAtomicLong, value: CLong): CLong = extern

  @name("scalanative_atomic_fetch_add_explicit_long")
  def atomic_fetch_add_explicit_long(
      ptr: CAtomicLong,
      value: CLong,
      memoryOrder: memory_order
  ): CLong = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_sub_long")
  def atomic_fetch_sub_long(ptr: CAtomicLong, value: CLong): CLong = extern

  @name("scalanative_atomic_fetch_sub_explicit_long")
  def atomic_fetch_sub_explicit_long(
      ptr: CAtomicLong,
      value: CLong,
      memoryOrder: memory_order
  ): CLong = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_or_long")
  def atomic_fetch_or_long(ptr: CAtomicLong, value: CLong): CLong = extern

  @name("scalanative_atomic_fetch_or_explicit_long")
  def atomic_fetch_or_explicit_long(
      ptr: CAtomicLong,
      value: CLong,
      memoryOrder: memory_order
  ): CLong = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_and_long")
  def atomic_fetch_and_long(ptr: CAtomicLong, value: CLong): CLong = extern

  @name("scalanative_atomic_fetch_and_explicit_long")
  def atomic_fetch_and_explicit_long(
      ptr: CAtomicLong,
      value: CLong,
      memoryOrder: memory_order
  ): CLong = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_xor_long")
  def atomic_fetch_xor_long(ptr: CAtomicLong, value: CLong): CLong = extern

  @name("scalanative_atomic_fetch_xor_explicit_long")
  def atomic_fetch_xor_explicit_long(
      ptr: CAtomicLong,
      value: CLong,
      memoryOrder: memory_order
  ): CLong = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 42)
  type CAtomicUnsignedByte = Ptr[UByte]

  @name("scalanative_atomic_init_ubyte")
  def atomic_init_ubyte(atm: CAtomicUnsignedByte, initValue: UByte): Unit =
    extern

  @name("scalanative_atomic_load_ubyte")
  def atomic_load_ubyte(ptr: CAtomicUnsignedByte): UByte = extern

  @name("scalanative_atomic_load_explicit_ubyte")
  def atomic_load_explicit_ubyte(
      ptr: CAtomicUnsignedByte,
      memoryOrder: memory_order
  ): UByte = extern

  @name("scalanative_atomic_store_ubyte")
  def atomic_store_ubyte(ptr: CAtomicUnsignedByte, v: UByte): Unit = extern

  @name("scalanative_atomic_store_explicit_ubyte")
  def atomic_store_explicit_ubyte(
      ptr: CAtomicUnsignedByte,
      v: UByte,
      memoryOrder: memory_order
  ): Unit = extern

  @name("scalanative_atomic_exchange_ubyte")
  def atomic_exchange_ubyte(ptr: CAtomicUnsignedByte, v: UByte): UByte = extern

  @name("scalanative_atomic_exchange_explicit_ubyte")
  def atomic_exchange_explicit_ubyte(
      ptr: CAtomicUnsignedByte,
      v: UByte,
      memoryOrder: memory_order
  ): UByte = extern

  @name("scalanative_atomic_compare_exchange_strong_ubyte")
  def atomic_compare_exchange_strong_ubyte(
      value: CAtomicUnsignedByte,
      expected: CAtomicUnsignedByte,
      desired: UByte
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_strong_explicit_ubyte")
  def atomic_compare_exchange_strong_explicit_ubyte(
      value: CAtomicUnsignedByte,
      expected: CAtomicUnsignedByte,
      desired: UByte,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_weak_ubyte")
  def atomic_compare_exchange_weak_ubyte(
      value: CAtomicUnsignedByte,
      expected: CAtomicUnsignedByte,
      desired: UByte
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_weak_explicit_ubyte")
  def atomic_compare_exchange_weak_explicit_ubyte(
      value: CAtomicUnsignedByte,
      expected: CAtomicUnsignedByte,
      desired: UByte,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): CBool = extern

// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_add_ubyte")
  def atomic_fetch_add_ubyte(ptr: CAtomicUnsignedByte, value: UByte): UByte =
    extern

  @name("scalanative_atomic_fetch_add_explicit_ubyte")
  def atomic_fetch_add_explicit_ubyte(
      ptr: CAtomicUnsignedByte,
      value: UByte,
      memoryOrder: memory_order
  ): UByte = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_sub_ubyte")
  def atomic_fetch_sub_ubyte(ptr: CAtomicUnsignedByte, value: UByte): UByte =
    extern

  @name("scalanative_atomic_fetch_sub_explicit_ubyte")
  def atomic_fetch_sub_explicit_ubyte(
      ptr: CAtomicUnsignedByte,
      value: UByte,
      memoryOrder: memory_order
  ): UByte = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_or_ubyte")
  def atomic_fetch_or_ubyte(ptr: CAtomicUnsignedByte, value: UByte): UByte =
    extern

  @name("scalanative_atomic_fetch_or_explicit_ubyte")
  def atomic_fetch_or_explicit_ubyte(
      ptr: CAtomicUnsignedByte,
      value: UByte,
      memoryOrder: memory_order
  ): UByte = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_and_ubyte")
  def atomic_fetch_and_ubyte(ptr: CAtomicUnsignedByte, value: UByte): UByte =
    extern

  @name("scalanative_atomic_fetch_and_explicit_ubyte")
  def atomic_fetch_and_explicit_ubyte(
      ptr: CAtomicUnsignedByte,
      value: UByte,
      memoryOrder: memory_order
  ): UByte = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_xor_ubyte")
  def atomic_fetch_xor_ubyte(ptr: CAtomicUnsignedByte, value: UByte): UByte =
    extern

  @name("scalanative_atomic_fetch_xor_explicit_ubyte")
  def atomic_fetch_xor_explicit_ubyte(
      ptr: CAtomicUnsignedByte,
      value: UByte,
      memoryOrder: memory_order
  ): UByte = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 42)
  type CAtomicUnsignedShort = Ptr[CUnsignedShort]

  @name("scalanative_atomic_init_ushort")
  def atomic_init_ushort(
      atm: CAtomicUnsignedShort,
      initValue: CUnsignedShort
  ): Unit = extern

  @name("scalanative_atomic_load_ushort")
  def atomic_load_ushort(ptr: CAtomicUnsignedShort): CUnsignedShort = extern

  @name("scalanative_atomic_load_explicit_ushort")
  def atomic_load_explicit_ushort(
      ptr: CAtomicUnsignedShort,
      memoryOrder: memory_order
  ): CUnsignedShort =
    extern

  @name("scalanative_atomic_store_ushort")
  def atomic_store_ushort(ptr: CAtomicUnsignedShort, v: CUnsignedShort): Unit =
    extern

  @name("scalanative_atomic_store_explicit_ushort")
  def atomic_store_explicit_ushort(
      ptr: CAtomicUnsignedShort,
      v: CUnsignedShort,
      memoryOrder: memory_order
  ): Unit = extern

  @name("scalanative_atomic_exchange_ushort")
  def atomic_exchange_ushort(
      ptr: CAtomicUnsignedShort,
      v: CUnsignedShort
  ): CUnsignedShort = extern

  @name("scalanative_atomic_exchange_explicit_ushort")
  def atomic_exchange_explicit_ushort(
      ptr: CAtomicUnsignedShort,
      v: CUnsignedShort,
      memoryOrder: memory_order
  ): CUnsignedShort = extern

  @name("scalanative_atomic_compare_exchange_strong_ushort")
  def atomic_compare_exchange_strong_ushort(
      value: CAtomicUnsignedShort,
      expected: CAtomicUnsignedShort,
      desired: CUnsignedShort
  ): CBool =
    extern

  @name("scalanative_atomic_compare_exchange_strong_explicit_ushort")
  def atomic_compare_exchange_strong_explicit_ushort(
      value: CAtomicUnsignedShort,
      expected: CAtomicUnsignedShort,
      desired: CUnsignedShort,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_weak_ushort")
  def atomic_compare_exchange_weak_ushort(
      value: CAtomicUnsignedShort,
      expected: CAtomicUnsignedShort,
      desired: CUnsignedShort
  ): CBool =
    extern

  @name("scalanative_atomic_compare_exchange_weak_explicit_ushort")
  def atomic_compare_exchange_weak_explicit_ushort(
      value: CAtomicUnsignedShort,
      expected: CAtomicUnsignedShort,
      desired: CUnsignedShort,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): CBool = extern

// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_add_ushort")
  def atomic_fetch_add_ushort(
      ptr: CAtomicUnsignedShort,
      value: CUnsignedShort
  ): CUnsignedShort = extern

  @name("scalanative_atomic_fetch_add_explicit_ushort")
  def atomic_fetch_add_explicit_ushort(
      ptr: CAtomicUnsignedShort,
      value: CUnsignedShort,
      memoryOrder: memory_order
  ): CUnsignedShort = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_sub_ushort")
  def atomic_fetch_sub_ushort(
      ptr: CAtomicUnsignedShort,
      value: CUnsignedShort
  ): CUnsignedShort = extern

  @name("scalanative_atomic_fetch_sub_explicit_ushort")
  def atomic_fetch_sub_explicit_ushort(
      ptr: CAtomicUnsignedShort,
      value: CUnsignedShort,
      memoryOrder: memory_order
  ): CUnsignedShort = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_or_ushort")
  def atomic_fetch_or_ushort(
      ptr: CAtomicUnsignedShort,
      value: CUnsignedShort
  ): CUnsignedShort = extern

  @name("scalanative_atomic_fetch_or_explicit_ushort")
  def atomic_fetch_or_explicit_ushort(
      ptr: CAtomicUnsignedShort,
      value: CUnsignedShort,
      memoryOrder: memory_order
  ): CUnsignedShort = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_and_ushort")
  def atomic_fetch_and_ushort(
      ptr: CAtomicUnsignedShort,
      value: CUnsignedShort
  ): CUnsignedShort = extern

  @name("scalanative_atomic_fetch_and_explicit_ushort")
  def atomic_fetch_and_explicit_ushort(
      ptr: CAtomicUnsignedShort,
      value: CUnsignedShort,
      memoryOrder: memory_order
  ): CUnsignedShort = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_xor_ushort")
  def atomic_fetch_xor_ushort(
      ptr: CAtomicUnsignedShort,
      value: CUnsignedShort
  ): CUnsignedShort = extern

  @name("scalanative_atomic_fetch_xor_explicit_ushort")
  def atomic_fetch_xor_explicit_ushort(
      ptr: CAtomicUnsignedShort,
      value: CUnsignedShort,
      memoryOrder: memory_order
  ): CUnsignedShort = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 42)
  type CAtomicUnsignedInt = Ptr[CUnsignedInt]

  @name("scalanative_atomic_init_uint")
  def atomic_init_uint(atm: CAtomicUnsignedInt, initValue: CUnsignedInt): Unit =
    extern

  @name("scalanative_atomic_load_uint")
  def atomic_load_uint(ptr: CAtomicUnsignedInt): CUnsignedInt = extern

  @name("scalanative_atomic_load_explicit_uint")
  def atomic_load_explicit_uint(
      ptr: CAtomicUnsignedInt,
      memoryOrder: memory_order
  ): CUnsignedInt =
    extern

  @name("scalanative_atomic_store_uint")
  def atomic_store_uint(ptr: CAtomicUnsignedInt, v: CUnsignedInt): Unit = extern

  @name("scalanative_atomic_store_explicit_uint")
  def atomic_store_explicit_uint(
      ptr: CAtomicUnsignedInt,
      v: CUnsignedInt,
      memoryOrder: memory_order
  ): Unit = extern

  @name("scalanative_atomic_exchange_uint")
  def atomic_exchange_uint(
      ptr: CAtomicUnsignedInt,
      v: CUnsignedInt
  ): CUnsignedInt = extern

  @name("scalanative_atomic_exchange_explicit_uint")
  def atomic_exchange_explicit_uint(
      ptr: CAtomicUnsignedInt,
      v: CUnsignedInt,
      memoryOrder: memory_order
  ): CUnsignedInt =
    extern

  @name("scalanative_atomic_compare_exchange_strong_uint")
  def atomic_compare_exchange_strong_uint(
      value: CAtomicUnsignedInt,
      expected: CAtomicUnsignedInt,
      desired: CUnsignedInt
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_strong_explicit_uint")
  def atomic_compare_exchange_strong_explicit_uint(
      value: CAtomicUnsignedInt,
      expected: CAtomicUnsignedInt,
      desired: CUnsignedInt,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_weak_uint")
  def atomic_compare_exchange_weak_uint(
      value: CAtomicUnsignedInt,
      expected: CAtomicUnsignedInt,
      desired: CUnsignedInt
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_weak_explicit_uint")
  def atomic_compare_exchange_weak_explicit_uint(
      value: CAtomicUnsignedInt,
      expected: CAtomicUnsignedInt,
      desired: CUnsignedInt,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): CBool = extern

// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_add_uint")
  def atomic_fetch_add_uint(
      ptr: CAtomicUnsignedInt,
      value: CUnsignedInt
  ): CUnsignedInt = extern

  @name("scalanative_atomic_fetch_add_explicit_uint")
  def atomic_fetch_add_explicit_uint(
      ptr: CAtomicUnsignedInt,
      value: CUnsignedInt,
      memoryOrder: memory_order
  ): CUnsignedInt =
    extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_sub_uint")
  def atomic_fetch_sub_uint(
      ptr: CAtomicUnsignedInt,
      value: CUnsignedInt
  ): CUnsignedInt = extern

  @name("scalanative_atomic_fetch_sub_explicit_uint")
  def atomic_fetch_sub_explicit_uint(
      ptr: CAtomicUnsignedInt,
      value: CUnsignedInt,
      memoryOrder: memory_order
  ): CUnsignedInt =
    extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_or_uint")
  def atomic_fetch_or_uint(
      ptr: CAtomicUnsignedInt,
      value: CUnsignedInt
  ): CUnsignedInt = extern

  @name("scalanative_atomic_fetch_or_explicit_uint")
  def atomic_fetch_or_explicit_uint(
      ptr: CAtomicUnsignedInt,
      value: CUnsignedInt,
      memoryOrder: memory_order
  ): CUnsignedInt =
    extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_and_uint")
  def atomic_fetch_and_uint(
      ptr: CAtomicUnsignedInt,
      value: CUnsignedInt
  ): CUnsignedInt = extern

  @name("scalanative_atomic_fetch_and_explicit_uint")
  def atomic_fetch_and_explicit_uint(
      ptr: CAtomicUnsignedInt,
      value: CUnsignedInt,
      memoryOrder: memory_order
  ): CUnsignedInt =
    extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_xor_uint")
  def atomic_fetch_xor_uint(
      ptr: CAtomicUnsignedInt,
      value: CUnsignedInt
  ): CUnsignedInt = extern

  @name("scalanative_atomic_fetch_xor_explicit_uint")
  def atomic_fetch_xor_explicit_uint(
      ptr: CAtomicUnsignedInt,
      value: CUnsignedInt,
      memoryOrder: memory_order
  ): CUnsignedInt =
    extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 42)
  type CAtomicUnsignedLong = Ptr[CUnsignedLong]

  @name("scalanative_atomic_init_ulong")
  def atomic_init_ulong(
      atm: CAtomicUnsignedLong,
      initValue: CUnsignedLong
  ): Unit = extern

  @name("scalanative_atomic_load_ulong")
  def atomic_load_ulong(ptr: CAtomicUnsignedLong): CUnsignedLong = extern

  @name("scalanative_atomic_load_explicit_ulong")
  def atomic_load_explicit_ulong(
      ptr: CAtomicUnsignedLong,
      memoryOrder: memory_order
  ): CUnsignedLong =
    extern

  @name("scalanative_atomic_store_ulong")
  def atomic_store_ulong(ptr: CAtomicUnsignedLong, v: CUnsignedLong): Unit =
    extern

  @name("scalanative_atomic_store_explicit_ulong")
  def atomic_store_explicit_ulong(
      ptr: CAtomicUnsignedLong,
      v: CUnsignedLong,
      memoryOrder: memory_order
  ): Unit = extern

  @name("scalanative_atomic_exchange_ulong")
  def atomic_exchange_ulong(
      ptr: CAtomicUnsignedLong,
      v: CUnsignedLong
  ): CUnsignedLong = extern

  @name("scalanative_atomic_exchange_explicit_ulong")
  def atomic_exchange_explicit_ulong(
      ptr: CAtomicUnsignedLong,
      v: CUnsignedLong,
      memoryOrder: memory_order
  ): CUnsignedLong =
    extern

  @name("scalanative_atomic_compare_exchange_strong_ulong")
  def atomic_compare_exchange_strong_ulong(
      value: CAtomicUnsignedLong,
      expected: CAtomicUnsignedLong,
      desired: CUnsignedLong
  ): CBool =
    extern

  @name("scalanative_atomic_compare_exchange_strong_explicit_ulong")
  def atomic_compare_exchange_strong_explicit_ulong(
      value: CAtomicUnsignedLong,
      expected: CAtomicUnsignedLong,
      desired: CUnsignedLong,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_weak_ulong")
  def atomic_compare_exchange_weak_ulong(
      value: CAtomicUnsignedLong,
      expected: CAtomicUnsignedLong,
      desired: CUnsignedLong
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_weak_explicit_ulong")
  def atomic_compare_exchange_weak_explicit_ulong(
      value: CAtomicUnsignedLong,
      expected: CAtomicUnsignedLong,
      desired: CUnsignedLong,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): CBool = extern

// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_add_ulong")
  def atomic_fetch_add_ulong(
      ptr: CAtomicUnsignedLong,
      value: CUnsignedLong
  ): CUnsignedLong = extern

  @name("scalanative_atomic_fetch_add_explicit_ulong")
  def atomic_fetch_add_explicit_ulong(
      ptr: CAtomicUnsignedLong,
      value: CUnsignedLong,
      memoryOrder: memory_order
  ): CUnsignedLong = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_sub_ulong")
  def atomic_fetch_sub_ulong(
      ptr: CAtomicUnsignedLong,
      value: CUnsignedLong
  ): CUnsignedLong = extern

  @name("scalanative_atomic_fetch_sub_explicit_ulong")
  def atomic_fetch_sub_explicit_ulong(
      ptr: CAtomicUnsignedLong,
      value: CUnsignedLong,
      memoryOrder: memory_order
  ): CUnsignedLong = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_or_ulong")
  def atomic_fetch_or_ulong(
      ptr: CAtomicUnsignedLong,
      value: CUnsignedLong
  ): CUnsignedLong = extern

  @name("scalanative_atomic_fetch_or_explicit_ulong")
  def atomic_fetch_or_explicit_ulong(
      ptr: CAtomicUnsignedLong,
      value: CUnsignedLong,
      memoryOrder: memory_order
  ): CUnsignedLong =
    extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_and_ulong")
  def atomic_fetch_and_ulong(
      ptr: CAtomicUnsignedLong,
      value: CUnsignedLong
  ): CUnsignedLong = extern

  @name("scalanative_atomic_fetch_and_explicit_ulong")
  def atomic_fetch_and_explicit_ulong(
      ptr: CAtomicUnsignedLong,
      value: CUnsignedLong,
      memoryOrder: memory_order
  ): CUnsignedLong = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_xor_ulong")
  def atomic_fetch_xor_ulong(
      ptr: CAtomicUnsignedLong,
      value: CUnsignedLong
  ): CUnsignedLong = extern

  @name("scalanative_atomic_fetch_xor_explicit_ulong")
  def atomic_fetch_xor_explicit_ulong(
      ptr: CAtomicUnsignedLong,
      value: CUnsignedLong,
      memoryOrder: memory_order
  ): CUnsignedLong = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 42)
  type CAtomicChar = Ptr[CChar]

  @name("scalanative_atomic_init_char")
  def atomic_init_char(atm: CAtomicChar, initValue: CChar): Unit = extern

  @name("scalanative_atomic_load_char")
  def atomic_load_char(ptr: CAtomicChar): CChar = extern

  @name("scalanative_atomic_load_explicit_char")
  def atomic_load_explicit_char(
      ptr: CAtomicChar,
      memoryOrder: memory_order
  ): CChar = extern

  @name("scalanative_atomic_store_char")
  def atomic_store_char(ptr: CAtomicChar, v: CChar): Unit = extern

  @name("scalanative_atomic_store_explicit_char")
  def atomic_store_explicit_char(
      ptr: CAtomicChar,
      v: CChar,
      memoryOrder: memory_order
  ): Unit = extern

  @name("scalanative_atomic_exchange_char")
  def atomic_exchange_char(ptr: CAtomicChar, v: CChar): CChar = extern

  @name("scalanative_atomic_exchange_explicit_char")
  def atomic_exchange_explicit_char(
      ptr: CAtomicChar,
      v: CChar,
      memoryOrder: memory_order
  ): CChar = extern

  @name("scalanative_atomic_compare_exchange_strong_char")
  def atomic_compare_exchange_strong_char(
      value: CAtomicChar,
      expected: CAtomicChar,
      desired: CChar
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_strong_explicit_char")
  def atomic_compare_exchange_strong_explicit_char(
      value: CAtomicChar,
      expected: CAtomicChar,
      desired: CChar,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_weak_char")
  def atomic_compare_exchange_weak_char(
      value: CAtomicChar,
      expected: CAtomicChar,
      desired: CChar
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_weak_explicit_char")
  def atomic_compare_exchange_weak_explicit_char(
      value: CAtomicChar,
      expected: CAtomicChar,
      desired: CChar,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): CBool = extern

// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_add_char")
  def atomic_fetch_add_char(ptr: CAtomicChar, value: CChar): CChar = extern

  @name("scalanative_atomic_fetch_add_explicit_char")
  def atomic_fetch_add_explicit_char(
      ptr: CAtomicChar,
      value: CChar,
      memoryOrder: memory_order
  ): CChar = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_sub_char")
  def atomic_fetch_sub_char(ptr: CAtomicChar, value: CChar): CChar = extern

  @name("scalanative_atomic_fetch_sub_explicit_char")
  def atomic_fetch_sub_explicit_char(
      ptr: CAtomicChar,
      value: CChar,
      memoryOrder: memory_order
  ): CChar = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_or_char")
  def atomic_fetch_or_char(ptr: CAtomicChar, value: CChar): CChar = extern

  @name("scalanative_atomic_fetch_or_explicit_char")
  def atomic_fetch_or_explicit_char(
      ptr: CAtomicChar,
      value: CChar,
      memoryOrder: memory_order
  ): CChar = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_and_char")
  def atomic_fetch_and_char(ptr: CAtomicChar, value: CChar): CChar = extern

  @name("scalanative_atomic_fetch_and_explicit_char")
  def atomic_fetch_and_explicit_char(
      ptr: CAtomicChar,
      value: CChar,
      memoryOrder: memory_order
  ): CChar = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_xor_char")
  def atomic_fetch_xor_char(ptr: CAtomicChar, value: CChar): CChar = extern

  @name("scalanative_atomic_fetch_xor_explicit_char")
  def atomic_fetch_xor_explicit_char(
      ptr: CAtomicChar,
      value: CChar,
      memoryOrder: memory_order
  ): CChar = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 42)
  type CAtomicCSize = Ptr[CSize]

  @name("scalanative_atomic_init_csize")
  def atomic_init_csize(atm: CAtomicCSize, initValue: CSize): Unit = extern

  @name("scalanative_atomic_load_csize")
  def atomic_load_csize(ptr: CAtomicCSize): CSize = extern

  @name("scalanative_atomic_load_explicit_csize")
  def atomic_load_explicit_csize(
      ptr: CAtomicCSize,
      memoryOrder: memory_order
  ): CSize = extern

  @name("scalanative_atomic_store_csize")
  def atomic_store_csize(ptr: CAtomicCSize, v: CSize): Unit = extern

  @name("scalanative_atomic_store_explicit_csize")
  def atomic_store_explicit_csize(
      ptr: CAtomicCSize,
      v: CSize,
      memoryOrder: memory_order
  ): Unit = extern

  @name("scalanative_atomic_exchange_csize")
  def atomic_exchange_csize(ptr: CAtomicCSize, v: CSize): CSize = extern

  @name("scalanative_atomic_exchange_explicit_csize")
  def atomic_exchange_explicit_csize(
      ptr: CAtomicCSize,
      v: CSize,
      memoryOrder: memory_order
  ): CSize = extern

  @name("scalanative_atomic_compare_exchange_strong_csize")
  def atomic_compare_exchange_strong_csize(
      value: CAtomicCSize,
      expected: CAtomicCSize,
      desired: CSize
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_strong_explicit_csize")
  def atomic_compare_exchange_strong_explicit_csize(
      value: CAtomicCSize,
      expected: CAtomicCSize,
      desired: CSize,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_weak_csize")
  def atomic_compare_exchange_weak_csize(
      value: CAtomicCSize,
      expected: CAtomicCSize,
      desired: CSize
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_weak_explicit_csize")
  def atomic_compare_exchange_weak_explicit_csize(
      value: CAtomicCSize,
      expected: CAtomicCSize,
      desired: CSize,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): CBool = extern

// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_add_csize")
  def atomic_fetch_add_csize(ptr: CAtomicCSize, value: CSize): CSize = extern

  @name("scalanative_atomic_fetch_add_explicit_csize")
  def atomic_fetch_add_explicit_csize(
      ptr: CAtomicCSize,
      value: CSize,
      memoryOrder: memory_order
  ): CSize = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_sub_csize")
  def atomic_fetch_sub_csize(ptr: CAtomicCSize, value: CSize): CSize = extern

  @name("scalanative_atomic_fetch_sub_explicit_csize")
  def atomic_fetch_sub_explicit_csize(
      ptr: CAtomicCSize,
      value: CSize,
      memoryOrder: memory_order
  ): CSize = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_or_csize")
  def atomic_fetch_or_csize(ptr: CAtomicCSize, value: CSize): CSize = extern

  @name("scalanative_atomic_fetch_or_explicit_csize")
  def atomic_fetch_or_explicit_csize(
      ptr: CAtomicCSize,
      value: CSize,
      memoryOrder: memory_order
  ): CSize = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_and_csize")
  def atomic_fetch_and_csize(ptr: CAtomicCSize, value: CSize): CSize = extern

  @name("scalanative_atomic_fetch_and_explicit_csize")
  def atomic_fetch_and_explicit_csize(
      ptr: CAtomicCSize,
      value: CSize,
      memoryOrder: memory_order
  ): CSize = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_xor_csize")
  def atomic_fetch_xor_csize(ptr: CAtomicCSize, value: CSize): CSize = extern

  @name("scalanative_atomic_fetch_xor_explicit_csize")
  def atomic_fetch_xor_explicit_csize(
      ptr: CAtomicCSize,
      value: CSize,
      memoryOrder: memory_order
  ): CSize = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 42)
  type CAtomicPtr = Ptr[Word]

  @name("scalanative_atomic_init_intptr")
  def atomic_init_intptr(atm: CAtomicPtr, initValue: Word): Unit = extern

  @name("scalanative_atomic_load_intptr")
  def atomic_load_intptr(ptr: CAtomicPtr): Word = extern

  @name("scalanative_atomic_load_explicit_intptr")
  def atomic_load_explicit_intptr(
      ptr: CAtomicPtr,
      memoryOrder: memory_order
  ): Word = extern

  @name("scalanative_atomic_store_intptr")
  def atomic_store_intptr(ptr: CAtomicPtr, v: Word): Unit = extern

  @name("scalanative_atomic_store_explicit_intptr")
  def atomic_store_explicit_intptr(
      ptr: CAtomicPtr,
      v: Word,
      memoryOrder: memory_order
  ): Unit = extern

  @name("scalanative_atomic_exchange_intptr")
  def atomic_exchange_intptr(ptr: CAtomicPtr, v: Word): Word = extern

  @name("scalanative_atomic_exchange_explicit_intptr")
  def atomic_exchange_explicit_intptr(
      ptr: CAtomicPtr,
      v: Word,
      memoryOrder: memory_order
  ): Word = extern

  @name("scalanative_atomic_compare_exchange_strong_intptr")
  def atomic_compare_exchange_strong_intptr(
      value: CAtomicPtr,
      expected: CAtomicPtr,
      desired: Word
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_strong_explicit_intptr")
  def atomic_compare_exchange_strong_explicit_intptr(
      value: CAtomicPtr,
      expected: CAtomicPtr,
      desired: Word,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_weak_intptr")
  def atomic_compare_exchange_weak_intptr(
      value: CAtomicPtr,
      expected: CAtomicPtr,
      desired: Word
  ): CBool = extern

  @name("scalanative_atomic_compare_exchange_weak_explicit_intptr")
  def atomic_compare_exchange_weak_explicit_intptr(
      value: CAtomicPtr,
      expected: CAtomicPtr,
      desired: Word,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): CBool = extern

// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_add_intptr")
  def atomic_fetch_add_intptr(ptr: CAtomicPtr, value: Word): Word = extern

  @name("scalanative_atomic_fetch_add_explicit_intptr")
  def atomic_fetch_add_explicit_intptr(
      ptr: CAtomicPtr,
      value: Word,
      memoryOrder: memory_order
  ): Word = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_sub_intptr")
  def atomic_fetch_sub_intptr(ptr: CAtomicPtr, value: Word): Word = extern

  @name("scalanative_atomic_fetch_sub_explicit_intptr")
  def atomic_fetch_sub_explicit_intptr(
      ptr: CAtomicPtr,
      value: Word,
      memoryOrder: memory_order
  ): Word = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_or_intptr")
  def atomic_fetch_or_intptr(ptr: CAtomicPtr, value: Word): Word = extern

  @name("scalanative_atomic_fetch_or_explicit_intptr")
  def atomic_fetch_or_explicit_intptr(
      ptr: CAtomicPtr,
      value: Word,
      memoryOrder: memory_order
  ): Word = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_and_intptr")
  def atomic_fetch_and_intptr(ptr: CAtomicPtr, value: Word): Word = extern

  @name("scalanative_atomic_fetch_and_explicit_intptr")
  def atomic_fetch_and_explicit_intptr(
      ptr: CAtomicPtr,
      value: Word,
      memoryOrder: memory_order
  ): Word = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 90)
  @name("scalanative_atomic_fetch_xor_intptr")
  def atomic_fetch_xor_intptr(ptr: CAtomicPtr, value: Word): Word = extern

  @name("scalanative_atomic_fetch_xor_explicit_intptr")
  def atomic_fetch_xor_explicit_intptr(
      ptr: CAtomicPtr,
      value: Word,
      memoryOrder: memory_order
  ): Word = extern
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 97)

  type CAtomicFlag = Ptr[Boolean]

  @name("scalanative_atomic_flag_init")
  def atomic_flag_init(atm: CAtomicFlag, initValue: Boolean): Unit = extern

  @name("scalanative_atomic_flag_test_and_set")
  def atomic_flag_test_and_set(obj: CAtomicFlag): Boolean = extern

  @name("scalanative_atomic_flag_test_and_set_explicit")
  def atomic_flag_test_and_set_explicit(
      obj: CAtomicFlag,
      memoryOrder: memory_order
  ): Boolean =
    extern

  @name("scalanative_atomic_flag_clear")
  def atomic_flag_clear(obj: CAtomicFlag): Boolean = extern

  @name("scalanative_atomic_flag_clear_explicit")
  def atomic_flag_clear_explicit(
      obj: CAtomicFlag,
      memoryOrder: memory_order
  ): Boolean = extern
}

import atomic._
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 120)
object CAtomicByte {
  def apply(initialValue: Byte)(implicit zone: Zone): CAtomicByte = {
    val ref = new CAtomicByte(
      zone.alloc(sizeof[Byte]).asInstanceOf[atomic.CAtomicByte]
    )
    ref.init(initialValue)
    ref
  }
}

final class CAtomicByte(private val underlying: atomic.CAtomicByte)
    extends AnyVal {
  def init(value: Byte): Unit = atomic_init_byte(underlying, value)

  def load(): Byte = atomic_load_byte(underlying)
  def load(memoryOrder: memory_order): Byte =
    atomic_load_explicit_byte(underlying, memoryOrder)

  def store(value: Byte): Unit = atomic_store_byte(underlying, value)
  def store(value: Byte, memoryOrder: memory_order): Unit =
    atomic_store_explicit_byte(underlying, value, memoryOrder)

  def exchange(value: Byte): Byte = atomic_exchange_byte(underlying, value)
  def exchange(value: Byte, memoryOrder: memory_order): Byte =
    atomic_exchange_explicit_byte(underlying, value, memoryOrder)

  def compareExchangeStrong(expected: Byte, desired: Byte): (Boolean, Byte) = {
    val expectedPtr: Ptr[Byte] = fromRawPtr(Intrinsics.stackalloc(sizeof[Byte]))
    !expectedPtr = expected
    val res =
      atomic_compare_exchange_strong_byte(underlying, expectedPtr, desired)
    (res, !expectedPtr)
  }

  def compareExchangeStrong(
      expected: Byte,
      desired: Byte,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, Byte) = {
    val expectedPtr: Ptr[Byte] = fromRawPtr(Intrinsics.stackalloc(sizeof[Byte]))
    !expectedPtr = expected
    val res = atomic_compare_exchange_strong_explicit_byte(
      underlying,
      expectedPtr,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, !expectedPtr)
  }

  def compareExchangeStrong(
      expected: Byte,
      desired: Byte,
      memoryOrder: memory_order
  ): (Boolean, Byte) = {
    compareExchangeStrong(
      expected: Byte,
      desired: Byte,
      memoryOrder,
      memoryOrder
    )
  }

  def compareExchangeWeak(expected: Byte, desired: Byte): (Boolean, Byte) = {
    val expectedPtr: Ptr[Byte] = fromRawPtr(Intrinsics.stackalloc(sizeof[Byte]))
    !expectedPtr = expected
    val res =
      atomic_compare_exchange_weak_byte(underlying, expectedPtr, desired)
    (res, !expectedPtr)
  }

  def compareExchangeWeak(
      expected: Byte,
      desired: Byte,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, Byte) = {
    val expectedPtr: Ptr[Byte] = fromRawPtr(Intrinsics.stackalloc(sizeof[Byte]))
    !expectedPtr = expected
    val res = atomic_compare_exchange_weak_explicit_byte(
      underlying,
      expectedPtr,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, !expectedPtr)
  }

  def compareExchangeWeak(
      expected: Byte,
      desired: Byte,
      memoryOrder: memory_order
  ): (Boolean, Byte) = {
    compareExchangeWeak(expected: Byte, desired: Byte, memoryOrder, memoryOrder)
  }

  def fetchAdd(value: Byte): Byte = atomic_fetch_add_byte(underlying, value)
  def fetchAdd(value: Byte, memoryOrder: memory_order): Byte =
    atomic_fetch_add_explicit_byte(underlying, value, memoryOrder)

  def fetchSub(value: Byte): Byte = atomic_fetch_sub_byte(underlying, value)
  def fetchSub(value: Byte, memoryOrder: memory_order): Byte =
    atomic_fetch_sub_explicit_byte(underlying, value, memoryOrder)

  def fetchAnd(value: Byte): Byte = atomic_fetch_and_byte(underlying, value)
  def fetchAnd(value: Byte, memoryOrder: memory_order): Byte =
    atomic_fetch_and_explicit_byte(underlying, value, memoryOrder)

  def fetchOr(value: Byte): Byte = atomic_fetch_or_byte(underlying, value)
  def fetchOr(value: Byte, memoryOrder: memory_order): Byte =
    atomic_fetch_or_explicit_byte(underlying, value, memoryOrder)

  def fetchXor(value: Byte): Byte = atomic_fetch_xor_byte(underlying, value)
  def fetchXor(value: Byte, memoryOrder: memory_order): Byte =
    atomic_fetch_xor_explicit_byte(underlying, value, memoryOrder)
}
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 120)
object CAtomicShort {
  def apply(initialValue: CShort)(implicit zone: Zone): CAtomicShort = {
    val ref = new CAtomicShort(
      zone.alloc(sizeof[CShort]).asInstanceOf[atomic.CAtomicShort]
    )
    ref.init(initialValue)
    ref
  }
}

final class CAtomicShort(private val underlying: atomic.CAtomicShort)
    extends AnyVal {
  def init(value: CShort): Unit = atomic_init_short(underlying, value)

  def load(): CShort = atomic_load_short(underlying)
  def load(memoryOrder: memory_order): CShort =
    atomic_load_explicit_short(underlying, memoryOrder)

  def store(value: CShort): Unit = atomic_store_short(underlying, value)
  def store(value: CShort, memoryOrder: memory_order): Unit =
    atomic_store_explicit_short(underlying, value, memoryOrder)

  def exchange(value: CShort): CShort = atomic_exchange_short(underlying, value)
  def exchange(value: CShort, memoryOrder: memory_order): CShort =
    atomic_exchange_explicit_short(underlying, value, memoryOrder)

  def compareExchangeStrong(
      expected: CShort,
      desired: CShort
  ): (Boolean, CShort) = {
    val expectedPtr: Ptr[CShort] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CShort])
    )
    !expectedPtr = expected
    val res =
      atomic_compare_exchange_strong_short(underlying, expectedPtr, desired)
    (res, !expectedPtr)
  }

  def compareExchangeStrong(
      expected: CShort,
      desired: CShort,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, CShort) = {
    val expectedPtr: Ptr[CShort] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CShort])
    )
    !expectedPtr = expected
    val res = atomic_compare_exchange_strong_explicit_short(
      underlying,
      expectedPtr,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, !expectedPtr)
  }

  def compareExchangeStrong(
      expected: CShort,
      desired: CShort,
      memoryOrder: memory_order
  ): (Boolean, CShort) = {
    compareExchangeStrong(
      expected: CShort,
      desired: CShort,
      memoryOrder,
      memoryOrder
    )
  }

  def compareExchangeWeak(
      expected: CShort,
      desired: CShort
  ): (Boolean, CShort) = {
    val expectedPtr: Ptr[CShort] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CShort])
    )
    !expectedPtr = expected
    val res =
      atomic_compare_exchange_weak_short(underlying, expectedPtr, desired)
    (res, !expectedPtr)
  }

  def compareExchangeWeak(
      expected: CShort,
      desired: CShort,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, CShort) = {
    val expectedPtr: Ptr[CShort] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CShort])
    )
    !expectedPtr = expected
    val res = atomic_compare_exchange_weak_explicit_short(
      underlying,
      expectedPtr,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, !expectedPtr)
  }

  def compareExchangeWeak(
      expected: CShort,
      desired: CShort,
      memoryOrder: memory_order
  ): (Boolean, CShort) = {
    compareExchangeWeak(
      expected: CShort,
      desired: CShort,
      memoryOrder,
      memoryOrder
    )
  }

  def fetchAdd(value: CShort): CShort =
    atomic_fetch_add_short(underlying, value)
  def fetchAdd(value: CShort, memoryOrder: memory_order): CShort =
    atomic_fetch_add_explicit_short(underlying, value, memoryOrder)

  def fetchSub(value: CShort): CShort =
    atomic_fetch_sub_short(underlying, value)
  def fetchSub(value: CShort, memoryOrder: memory_order): CShort =
    atomic_fetch_sub_explicit_short(underlying, value, memoryOrder)

  def fetchAnd(value: CShort): CShort =
    atomic_fetch_and_short(underlying, value)
  def fetchAnd(value: CShort, memoryOrder: memory_order): CShort =
    atomic_fetch_and_explicit_short(underlying, value, memoryOrder)

  def fetchOr(value: CShort): CShort = atomic_fetch_or_short(underlying, value)
  def fetchOr(value: CShort, memoryOrder: memory_order): CShort =
    atomic_fetch_or_explicit_short(underlying, value, memoryOrder)

  def fetchXor(value: CShort): CShort =
    atomic_fetch_xor_short(underlying, value)
  def fetchXor(value: CShort, memoryOrder: memory_order): CShort =
    atomic_fetch_xor_explicit_short(underlying, value, memoryOrder)
}
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 120)
object CAtomicInt {
  def apply(initialValue: CInt)(implicit zone: Zone): CAtomicInt = {
    val ref = new CAtomicInt(
      zone.alloc(sizeof[CInt]).asInstanceOf[atomic.CAtomicInt]
    )
    ref.init(initialValue)
    ref
  }
}

final class CAtomicInt(private val underlying: atomic.CAtomicInt)
    extends AnyVal {
  def init(value: CInt): Unit = atomic_init_int(underlying, value)

  def load(): CInt = atomic_load_int(underlying)
  def load(memoryOrder: memory_order): CInt =
    atomic_load_explicit_int(underlying, memoryOrder)

  def store(value: CInt): Unit = atomic_store_int(underlying, value)
  def store(value: CInt, memoryOrder: memory_order): Unit =
    atomic_store_explicit_int(underlying, value, memoryOrder)

  def exchange(value: CInt): CInt = atomic_exchange_int(underlying, value)
  def exchange(value: CInt, memoryOrder: memory_order): CInt =
    atomic_exchange_explicit_int(underlying, value, memoryOrder)

  def compareExchangeStrong(expected: CInt, desired: CInt): (Boolean, CInt) = {
    val expectedPtr: Ptr[CInt] = fromRawPtr(Intrinsics.stackalloc(sizeof[CInt]))
    !expectedPtr = expected
    val res =
      atomic_compare_exchange_strong_int(underlying, expectedPtr, desired)
    (res, !expectedPtr)
  }

  def compareExchangeStrong(
      expected: CInt,
      desired: CInt,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, CInt) = {
    val expectedPtr: Ptr[CInt] = fromRawPtr(Intrinsics.stackalloc(sizeof[CInt]))
    !expectedPtr = expected
    val res = atomic_compare_exchange_strong_explicit_int(
      underlying,
      expectedPtr,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, !expectedPtr)
  }

  def compareExchangeStrong(
      expected: CInt,
      desired: CInt,
      memoryOrder: memory_order
  ): (Boolean, CInt) = {
    compareExchangeStrong(
      expected: CInt,
      desired: CInt,
      memoryOrder,
      memoryOrder
    )
  }

  def compareExchangeWeak(expected: CInt, desired: CInt): (Boolean, CInt) = {
    val expectedPtr: Ptr[CInt] = fromRawPtr(Intrinsics.stackalloc(sizeof[CInt]))
    !expectedPtr = expected
    val res = atomic_compare_exchange_weak_int(underlying, expectedPtr, desired)
    (res, !expectedPtr)
  }

  def compareExchangeWeak(
      expected: CInt,
      desired: CInt,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, CInt) = {
    val expectedPtr: Ptr[CInt] = fromRawPtr(Intrinsics.stackalloc(sizeof[CInt]))
    !expectedPtr = expected
    val res = atomic_compare_exchange_weak_explicit_int(
      underlying,
      expectedPtr,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, !expectedPtr)
  }

  def compareExchangeWeak(
      expected: CInt,
      desired: CInt,
      memoryOrder: memory_order
  ): (Boolean, CInt) = {
    compareExchangeWeak(expected: CInt, desired: CInt, memoryOrder, memoryOrder)
  }

  def fetchAdd(value: CInt): CInt = atomic_fetch_add_int(underlying, value)
  def fetchAdd(value: CInt, memoryOrder: memory_order): CInt =
    atomic_fetch_add_explicit_int(underlying, value, memoryOrder)

  def fetchSub(value: CInt): CInt = atomic_fetch_sub_int(underlying, value)
  def fetchSub(value: CInt, memoryOrder: memory_order): CInt =
    atomic_fetch_sub_explicit_int(underlying, value, memoryOrder)

  def fetchAnd(value: CInt): CInt = atomic_fetch_and_int(underlying, value)
  def fetchAnd(value: CInt, memoryOrder: memory_order): CInt =
    atomic_fetch_and_explicit_int(underlying, value, memoryOrder)

  def fetchOr(value: CInt): CInt = atomic_fetch_or_int(underlying, value)
  def fetchOr(value: CInt, memoryOrder: memory_order): CInt =
    atomic_fetch_or_explicit_int(underlying, value, memoryOrder)

  def fetchXor(value: CInt): CInt = atomic_fetch_xor_int(underlying, value)
  def fetchXor(value: CInt, memoryOrder: memory_order): CInt =
    atomic_fetch_xor_explicit_int(underlying, value, memoryOrder)
}
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 120)
object CAtomicLong {
  def apply(initialValue: CLong)(implicit zone: Zone): CAtomicLong = {
    val ref = new CAtomicLong(
      zone.alloc(sizeof[CLong]).asInstanceOf[atomic.CAtomicLong]
    )
    ref.init(initialValue)
    ref
  }
}

final class CAtomicLong(private val underlying: atomic.CAtomicLong)
    extends AnyVal {
  def init(value: CLong): Unit = atomic_init_long(underlying, value)

  def load(): CLong = atomic_load_long(underlying)
  def load(memoryOrder: memory_order): CLong =
    atomic_load_explicit_long(underlying, memoryOrder)

  def store(value: CLong): Unit = atomic_store_long(underlying, value)
  def store(value: CLong, memoryOrder: memory_order): Unit =
    atomic_store_explicit_long(underlying, value, memoryOrder)

  def exchange(value: CLong): CLong = atomic_exchange_long(underlying, value)
  def exchange(value: CLong, memoryOrder: memory_order): CLong =
    atomic_exchange_explicit_long(underlying, value, memoryOrder)

  def compareExchangeStrong(
      expected: CLong,
      desired: CLong
  ): (Boolean, CLong) = {
    val expectedPtr: Ptr[CLong] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CLong])
    )
    !expectedPtr = expected
    val res =
      atomic_compare_exchange_strong_long(underlying, expectedPtr, desired)
    (res, !expectedPtr)
  }

  def compareExchangeStrong(
      expected: CLong,
      desired: CLong,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, CLong) = {
    val expectedPtr: Ptr[CLong] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CLong])
    )
    !expectedPtr = expected
    val res = atomic_compare_exchange_strong_explicit_long(
      underlying,
      expectedPtr,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, !expectedPtr)
  }

  def compareExchangeStrong(
      expected: CLong,
      desired: CLong,
      memoryOrder: memory_order
  ): (Boolean, CLong) = {
    compareExchangeStrong(
      expected: CLong,
      desired: CLong,
      memoryOrder,
      memoryOrder
    )
  }

  def compareExchangeWeak(expected: CLong, desired: CLong): (Boolean, CLong) = {
    val expectedPtr: Ptr[CLong] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CLong])
    )
    !expectedPtr = expected
    val res =
      atomic_compare_exchange_weak_long(underlying, expectedPtr, desired)
    (res, !expectedPtr)
  }

  def compareExchangeWeak(
      expected: CLong,
      desired: CLong,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, CLong) = {
    val expectedPtr: Ptr[CLong] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CLong])
    )
    !expectedPtr = expected
    val res = atomic_compare_exchange_weak_explicit_long(
      underlying,
      expectedPtr,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, !expectedPtr)
  }

  def compareExchangeWeak(
      expected: CLong,
      desired: CLong,
      memoryOrder: memory_order
  ): (Boolean, CLong) = {
    compareExchangeWeak(
      expected: CLong,
      desired: CLong,
      memoryOrder,
      memoryOrder
    )
  }

  def fetchAdd(value: CLong): CLong = atomic_fetch_add_long(underlying, value)
  def fetchAdd(value: CLong, memoryOrder: memory_order): CLong =
    atomic_fetch_add_explicit_long(underlying, value, memoryOrder)

  def fetchSub(value: CLong): CLong = atomic_fetch_sub_long(underlying, value)
  def fetchSub(value: CLong, memoryOrder: memory_order): CLong =
    atomic_fetch_sub_explicit_long(underlying, value, memoryOrder)

  def fetchAnd(value: CLong): CLong = atomic_fetch_and_long(underlying, value)
  def fetchAnd(value: CLong, memoryOrder: memory_order): CLong =
    atomic_fetch_and_explicit_long(underlying, value, memoryOrder)

  def fetchOr(value: CLong): CLong = atomic_fetch_or_long(underlying, value)
  def fetchOr(value: CLong, memoryOrder: memory_order): CLong =
    atomic_fetch_or_explicit_long(underlying, value, memoryOrder)

  def fetchXor(value: CLong): CLong = atomic_fetch_xor_long(underlying, value)
  def fetchXor(value: CLong, memoryOrder: memory_order): CLong =
    atomic_fetch_xor_explicit_long(underlying, value, memoryOrder)
}
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 120)
object CAtomicUnsignedByte {
  def apply(initialValue: UByte)(implicit zone: Zone): CAtomicUnsignedByte = {
    val ref = new CAtomicUnsignedByte(
      zone.alloc(sizeof[UByte]).asInstanceOf[atomic.CAtomicUnsignedByte]
    )
    ref.init(initialValue)
    ref
  }
}

final class CAtomicUnsignedByte(
    private val underlying: atomic.CAtomicUnsignedByte
) extends AnyVal {
  def init(value: UByte): Unit = atomic_init_ubyte(underlying, value)

  def load(): UByte = atomic_load_ubyte(underlying)
  def load(memoryOrder: memory_order): UByte =
    atomic_load_explicit_ubyte(underlying, memoryOrder)

  def store(value: UByte): Unit = atomic_store_ubyte(underlying, value)
  def store(value: UByte, memoryOrder: memory_order): Unit =
    atomic_store_explicit_ubyte(underlying, value, memoryOrder)

  def exchange(value: UByte): UByte = atomic_exchange_ubyte(underlying, value)
  def exchange(value: UByte, memoryOrder: memory_order): UByte =
    atomic_exchange_explicit_ubyte(underlying, value, memoryOrder)

  def compareExchangeStrong(
      expected: UByte,
      desired: UByte
  ): (Boolean, UByte) = {
    val expectedPtr: Ptr[UByte] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[UByte])
    )
    !expectedPtr = expected
    val res =
      atomic_compare_exchange_strong_ubyte(underlying, expectedPtr, desired)
    (res, !expectedPtr)
  }

  def compareExchangeStrong(
      expected: UByte,
      desired: UByte,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, UByte) = {
    val expectedPtr: Ptr[UByte] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[UByte])
    )
    !expectedPtr = expected
    val res = atomic_compare_exchange_strong_explicit_ubyte(
      underlying,
      expectedPtr,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, !expectedPtr)
  }

  def compareExchangeStrong(
      expected: UByte,
      desired: UByte,
      memoryOrder: memory_order
  ): (Boolean, UByte) = {
    compareExchangeStrong(
      expected: UByte,
      desired: UByte,
      memoryOrder,
      memoryOrder
    )
  }

  def compareExchangeWeak(expected: UByte, desired: UByte): (Boolean, UByte) = {
    val expectedPtr: Ptr[UByte] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[UByte])
    )
    !expectedPtr = expected
    val res =
      atomic_compare_exchange_weak_ubyte(underlying, expectedPtr, desired)
    (res, !expectedPtr)
  }

  def compareExchangeWeak(
      expected: UByte,
      desired: UByte,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, UByte) = {
    val expectedPtr: Ptr[UByte] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[UByte])
    )
    !expectedPtr = expected
    val res = atomic_compare_exchange_weak_explicit_ubyte(
      underlying,
      expectedPtr,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, !expectedPtr)
  }

  def compareExchangeWeak(
      expected: UByte,
      desired: UByte,
      memoryOrder: memory_order
  ): (Boolean, UByte) = {
    compareExchangeWeak(
      expected: UByte,
      desired: UByte,
      memoryOrder,
      memoryOrder
    )
  }

  def fetchAdd(value: UByte): UByte = atomic_fetch_add_ubyte(underlying, value)
  def fetchAdd(value: UByte, memoryOrder: memory_order): UByte =
    atomic_fetch_add_explicit_ubyte(underlying, value, memoryOrder)

  def fetchSub(value: UByte): UByte = atomic_fetch_sub_ubyte(underlying, value)
  def fetchSub(value: UByte, memoryOrder: memory_order): UByte =
    atomic_fetch_sub_explicit_ubyte(underlying, value, memoryOrder)

  def fetchAnd(value: UByte): UByte = atomic_fetch_and_ubyte(underlying, value)
  def fetchAnd(value: UByte, memoryOrder: memory_order): UByte =
    atomic_fetch_and_explicit_ubyte(underlying, value, memoryOrder)

  def fetchOr(value: UByte): UByte = atomic_fetch_or_ubyte(underlying, value)
  def fetchOr(value: UByte, memoryOrder: memory_order): UByte =
    atomic_fetch_or_explicit_ubyte(underlying, value, memoryOrder)

  def fetchXor(value: UByte): UByte = atomic_fetch_xor_ubyte(underlying, value)
  def fetchXor(value: UByte, memoryOrder: memory_order): UByte =
    atomic_fetch_xor_explicit_ubyte(underlying, value, memoryOrder)
}
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 120)
object CAtomicUnsignedShort {
  def apply(
      initialValue: CUnsignedShort
  )(implicit zone: Zone): CAtomicUnsignedShort = {
    val ref = new CAtomicUnsignedShort(
      zone
        .alloc(sizeof[CUnsignedShort])
        .asInstanceOf[atomic.CAtomicUnsignedShort]
    )
    ref.init(initialValue)
    ref
  }
}

final class CAtomicUnsignedShort(
    private val underlying: atomic.CAtomicUnsignedShort
) extends AnyVal {
  def init(value: CUnsignedShort): Unit = atomic_init_ushort(underlying, value)

  def load(): CUnsignedShort = atomic_load_ushort(underlying)
  def load(memoryOrder: memory_order): CUnsignedShort =
    atomic_load_explicit_ushort(underlying, memoryOrder)

  def store(value: CUnsignedShort): Unit =
    atomic_store_ushort(underlying, value)
  def store(value: CUnsignedShort, memoryOrder: memory_order): Unit =
    atomic_store_explicit_ushort(underlying, value, memoryOrder)

  def exchange(value: CUnsignedShort): CUnsignedShort =
    atomic_exchange_ushort(underlying, value)
  def exchange(
      value: CUnsignedShort,
      memoryOrder: memory_order
  ): CUnsignedShort =
    atomic_exchange_explicit_ushort(underlying, value, memoryOrder)

  def compareExchangeStrong(
      expected: CUnsignedShort,
      desired: CUnsignedShort
  ): (Boolean, CUnsignedShort) = {
    val expectedPtr: Ptr[CUnsignedShort] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CUnsignedShort])
    )
    !expectedPtr = expected
    val res =
      atomic_compare_exchange_strong_ushort(underlying, expectedPtr, desired)
    (res, !expectedPtr)
  }

  def compareExchangeStrong(
      expected: CUnsignedShort,
      desired: CUnsignedShort,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, CUnsignedShort) = {
    val expectedPtr: Ptr[CUnsignedShort] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CUnsignedShort])
    )
    !expectedPtr = expected
    val res = atomic_compare_exchange_strong_explicit_ushort(
      underlying,
      expectedPtr,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, !expectedPtr)
  }

  def compareExchangeStrong(
      expected: CUnsignedShort,
      desired: CUnsignedShort,
      memoryOrder: memory_order
  ): (Boolean, CUnsignedShort) = {
    compareExchangeStrong(
      expected: CUnsignedShort,
      desired: CUnsignedShort,
      memoryOrder,
      memoryOrder
    )
  }

  def compareExchangeWeak(
      expected: CUnsignedShort,
      desired: CUnsignedShort
  ): (Boolean, CUnsignedShort) = {
    val expectedPtr: Ptr[CUnsignedShort] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CUnsignedShort])
    )
    !expectedPtr = expected
    val res =
      atomic_compare_exchange_weak_ushort(underlying, expectedPtr, desired)
    (res, !expectedPtr)
  }

  def compareExchangeWeak(
      expected: CUnsignedShort,
      desired: CUnsignedShort,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, CUnsignedShort) = {
    val expectedPtr: Ptr[CUnsignedShort] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CUnsignedShort])
    )
    !expectedPtr = expected
    val res = atomic_compare_exchange_weak_explicit_ushort(
      underlying,
      expectedPtr,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, !expectedPtr)
  }

  def compareExchangeWeak(
      expected: CUnsignedShort,
      desired: CUnsignedShort,
      memoryOrder: memory_order
  ): (Boolean, CUnsignedShort) = {
    compareExchangeWeak(
      expected: CUnsignedShort,
      desired: CUnsignedShort,
      memoryOrder,
      memoryOrder
    )
  }

  def fetchAdd(value: CUnsignedShort): CUnsignedShort =
    atomic_fetch_add_ushort(underlying, value)
  def fetchAdd(
      value: CUnsignedShort,
      memoryOrder: memory_order
  ): CUnsignedShort =
    atomic_fetch_add_explicit_ushort(underlying, value, memoryOrder)

  def fetchSub(value: CUnsignedShort): CUnsignedShort =
    atomic_fetch_sub_ushort(underlying, value)
  def fetchSub(
      value: CUnsignedShort,
      memoryOrder: memory_order
  ): CUnsignedShort =
    atomic_fetch_sub_explicit_ushort(underlying, value, memoryOrder)

  def fetchAnd(value: CUnsignedShort): CUnsignedShort =
    atomic_fetch_and_ushort(underlying, value)
  def fetchAnd(
      value: CUnsignedShort,
      memoryOrder: memory_order
  ): CUnsignedShort =
    atomic_fetch_and_explicit_ushort(underlying, value, memoryOrder)

  def fetchOr(value: CUnsignedShort): CUnsignedShort =
    atomic_fetch_or_ushort(underlying, value)
  def fetchOr(
      value: CUnsignedShort,
      memoryOrder: memory_order
  ): CUnsignedShort =
    atomic_fetch_or_explicit_ushort(underlying, value, memoryOrder)

  def fetchXor(value: CUnsignedShort): CUnsignedShort =
    atomic_fetch_xor_ushort(underlying, value)
  def fetchXor(
      value: CUnsignedShort,
      memoryOrder: memory_order
  ): CUnsignedShort =
    atomic_fetch_xor_explicit_ushort(underlying, value, memoryOrder)
}
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 120)
object CAtomicUnsignedInt {
  def apply(
      initialValue: CUnsignedInt
  )(implicit zone: Zone): CAtomicUnsignedInt = {
    val ref = new CAtomicUnsignedInt(
      zone.alloc(sizeof[CUnsignedInt]).asInstanceOf[atomic.CAtomicUnsignedInt]
    )
    ref.init(initialValue)
    ref
  }
}

final class CAtomicUnsignedInt(
    private val underlying: atomic.CAtomicUnsignedInt
) extends AnyVal {
  def init(value: CUnsignedInt): Unit = atomic_init_uint(underlying, value)

  def load(): CUnsignedInt = atomic_load_uint(underlying)
  def load(memoryOrder: memory_order): CUnsignedInt =
    atomic_load_explicit_uint(underlying, memoryOrder)

  def store(value: CUnsignedInt): Unit = atomic_store_uint(underlying, value)
  def store(value: CUnsignedInt, memoryOrder: memory_order): Unit =
    atomic_store_explicit_uint(underlying, value, memoryOrder)

  def exchange(value: CUnsignedInt): CUnsignedInt =
    atomic_exchange_uint(underlying, value)
  def exchange(value: CUnsignedInt, memoryOrder: memory_order): CUnsignedInt =
    atomic_exchange_explicit_uint(underlying, value, memoryOrder)

  def compareExchangeStrong(
      expected: CUnsignedInt,
      desired: CUnsignedInt
  ): (Boolean, CUnsignedInt) = {
    val expectedPtr: Ptr[CUnsignedInt] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CUnsignedInt])
    )
    !expectedPtr = expected
    val res =
      atomic_compare_exchange_strong_uint(underlying, expectedPtr, desired)
    (res, !expectedPtr)
  }

  def compareExchangeStrong(
      expected: CUnsignedInt,
      desired: CUnsignedInt,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, CUnsignedInt) = {
    val expectedPtr: Ptr[CUnsignedInt] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CUnsignedInt])
    )
    !expectedPtr = expected
    val res = atomic_compare_exchange_strong_explicit_uint(
      underlying,
      expectedPtr,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, !expectedPtr)
  }

  def compareExchangeStrong(
      expected: CUnsignedInt,
      desired: CUnsignedInt,
      memoryOrder: memory_order
  ): (Boolean, CUnsignedInt) = {
    compareExchangeStrong(
      expected: CUnsignedInt,
      desired: CUnsignedInt,
      memoryOrder,
      memoryOrder
    )
  }

  def compareExchangeWeak(
      expected: CUnsignedInt,
      desired: CUnsignedInt
  ): (Boolean, CUnsignedInt) = {
    val expectedPtr: Ptr[CUnsignedInt] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CUnsignedInt])
    )
    !expectedPtr = expected
    val res =
      atomic_compare_exchange_weak_uint(underlying, expectedPtr, desired)
    (res, !expectedPtr)
  }

  def compareExchangeWeak(
      expected: CUnsignedInt,
      desired: CUnsignedInt,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, CUnsignedInt) = {
    val expectedPtr: Ptr[CUnsignedInt] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CUnsignedInt])
    )
    !expectedPtr = expected
    val res = atomic_compare_exchange_weak_explicit_uint(
      underlying,
      expectedPtr,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, !expectedPtr)
  }

  def compareExchangeWeak(
      expected: CUnsignedInt,
      desired: CUnsignedInt,
      memoryOrder: memory_order
  ): (Boolean, CUnsignedInt) = {
    compareExchangeWeak(
      expected: CUnsignedInt,
      desired: CUnsignedInt,
      memoryOrder,
      memoryOrder
    )
  }

  def fetchAdd(value: CUnsignedInt): CUnsignedInt =
    atomic_fetch_add_uint(underlying, value)
  def fetchAdd(value: CUnsignedInt, memoryOrder: memory_order): CUnsignedInt =
    atomic_fetch_add_explicit_uint(underlying, value, memoryOrder)

  def fetchSub(value: CUnsignedInt): CUnsignedInt =
    atomic_fetch_sub_uint(underlying, value)
  def fetchSub(value: CUnsignedInt, memoryOrder: memory_order): CUnsignedInt =
    atomic_fetch_sub_explicit_uint(underlying, value, memoryOrder)

  def fetchAnd(value: CUnsignedInt): CUnsignedInt =
    atomic_fetch_and_uint(underlying, value)
  def fetchAnd(value: CUnsignedInt, memoryOrder: memory_order): CUnsignedInt =
    atomic_fetch_and_explicit_uint(underlying, value, memoryOrder)

  def fetchOr(value: CUnsignedInt): CUnsignedInt =
    atomic_fetch_or_uint(underlying, value)
  def fetchOr(value: CUnsignedInt, memoryOrder: memory_order): CUnsignedInt =
    atomic_fetch_or_explicit_uint(underlying, value, memoryOrder)

  def fetchXor(value: CUnsignedInt): CUnsignedInt =
    atomic_fetch_xor_uint(underlying, value)
  def fetchXor(value: CUnsignedInt, memoryOrder: memory_order): CUnsignedInt =
    atomic_fetch_xor_explicit_uint(underlying, value, memoryOrder)
}
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 120)
object CAtomicUnsignedLong {
  def apply(
      initialValue: CUnsignedLong
  )(implicit zone: Zone): CAtomicUnsignedLong = {
    val ref = new CAtomicUnsignedLong(
      zone
        .alloc(sizeof[CUnsignedLong])
        .asInstanceOf[atomic.CAtomicUnsignedLong]
    )
    ref.init(initialValue)
    ref
  }
}

final class CAtomicUnsignedLong(
    private val underlying: atomic.CAtomicUnsignedLong
) extends AnyVal {
  def init(value: CUnsignedLong): Unit = atomic_init_ulong(underlying, value)

  def load(): CUnsignedLong = atomic_load_ulong(underlying)
  def load(memoryOrder: memory_order): CUnsignedLong =
    atomic_load_explicit_ulong(underlying, memoryOrder)

  def store(value: CUnsignedLong): Unit = atomic_store_ulong(underlying, value)
  def store(value: CUnsignedLong, memoryOrder: memory_order): Unit =
    atomic_store_explicit_ulong(underlying, value, memoryOrder)

  def exchange(value: CUnsignedLong): CUnsignedLong =
    atomic_exchange_ulong(underlying, value)
  def exchange(value: CUnsignedLong, memoryOrder: memory_order): CUnsignedLong =
    atomic_exchange_explicit_ulong(underlying, value, memoryOrder)

  def compareExchangeStrong(
      expected: CUnsignedLong,
      desired: CUnsignedLong
  ): (Boolean, CUnsignedLong) = {
    val expectedPtr: Ptr[CUnsignedLong] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CUnsignedLong])
    )
    !expectedPtr = expected
    val res =
      atomic_compare_exchange_strong_ulong(underlying, expectedPtr, desired)
    (res, !expectedPtr)
  }

  def compareExchangeStrong(
      expected: CUnsignedLong,
      desired: CUnsignedLong,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, CUnsignedLong) = {
    val expectedPtr: Ptr[CUnsignedLong] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CUnsignedLong])
    )
    !expectedPtr = expected
    val res = atomic_compare_exchange_strong_explicit_ulong(
      underlying,
      expectedPtr,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, !expectedPtr)
  }

  def compareExchangeStrong(
      expected: CUnsignedLong,
      desired: CUnsignedLong,
      memoryOrder: memory_order
  ): (Boolean, CUnsignedLong) = {
    compareExchangeStrong(
      expected: CUnsignedLong,
      desired: CUnsignedLong,
      memoryOrder,
      memoryOrder
    )
  }

  def compareExchangeWeak(
      expected: CUnsignedLong,
      desired: CUnsignedLong
  ): (Boolean, CUnsignedLong) = {
    val expectedPtr: Ptr[CUnsignedLong] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CUnsignedLong])
    )
    !expectedPtr = expected
    val res =
      atomic_compare_exchange_weak_ulong(underlying, expectedPtr, desired)
    (res, !expectedPtr)
  }

  def compareExchangeWeak(
      expected: CUnsignedLong,
      desired: CUnsignedLong,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, CUnsignedLong) = {
    val expectedPtr: Ptr[CUnsignedLong] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CUnsignedLong])
    )
    !expectedPtr = expected
    val res = atomic_compare_exchange_weak_explicit_ulong(
      underlying,
      expectedPtr,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, !expectedPtr)
  }

  def compareExchangeWeak(
      expected: CUnsignedLong,
      desired: CUnsignedLong,
      memoryOrder: memory_order
  ): (Boolean, CUnsignedLong) = {
    compareExchangeWeak(
      expected: CUnsignedLong,
      desired: CUnsignedLong,
      memoryOrder,
      memoryOrder
    )
  }

  def fetchAdd(value: CUnsignedLong): CUnsignedLong =
    atomic_fetch_add_ulong(underlying, value)
  def fetchAdd(value: CUnsignedLong, memoryOrder: memory_order): CUnsignedLong =
    atomic_fetch_add_explicit_ulong(underlying, value, memoryOrder)

  def fetchSub(value: CUnsignedLong): CUnsignedLong =
    atomic_fetch_sub_ulong(underlying, value)
  def fetchSub(value: CUnsignedLong, memoryOrder: memory_order): CUnsignedLong =
    atomic_fetch_sub_explicit_ulong(underlying, value, memoryOrder)

  def fetchAnd(value: CUnsignedLong): CUnsignedLong =
    atomic_fetch_and_ulong(underlying, value)
  def fetchAnd(value: CUnsignedLong, memoryOrder: memory_order): CUnsignedLong =
    atomic_fetch_and_explicit_ulong(underlying, value, memoryOrder)

  def fetchOr(value: CUnsignedLong): CUnsignedLong =
    atomic_fetch_or_ulong(underlying, value)
  def fetchOr(value: CUnsignedLong, memoryOrder: memory_order): CUnsignedLong =
    atomic_fetch_or_explicit_ulong(underlying, value, memoryOrder)

  def fetchXor(value: CUnsignedLong): CUnsignedLong =
    atomic_fetch_xor_ulong(underlying, value)
  def fetchXor(value: CUnsignedLong, memoryOrder: memory_order): CUnsignedLong =
    atomic_fetch_xor_explicit_ulong(underlying, value, memoryOrder)
}
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 120)
object CAtomicChar {
  def apply(initialValue: CChar)(implicit zone: Zone): CAtomicChar = {
    val ref = new CAtomicChar(
      zone.alloc(sizeof[CChar]).asInstanceOf[atomic.CAtomicChar]
    )
    ref.init(initialValue)
    ref
  }
}

final class CAtomicChar(private val underlying: atomic.CAtomicChar)
    extends AnyVal {
  def init(value: CChar): Unit = atomic_init_char(underlying, value)

  def load(): CChar = atomic_load_char(underlying)
  def load(memoryOrder: memory_order): CChar =
    atomic_load_explicit_char(underlying, memoryOrder)

  def store(value: CChar): Unit = atomic_store_char(underlying, value)
  def store(value: CChar, memoryOrder: memory_order): Unit =
    atomic_store_explicit_char(underlying, value, memoryOrder)

  def exchange(value: CChar): CChar = atomic_exchange_char(underlying, value)
  def exchange(value: CChar, memoryOrder: memory_order): CChar =
    atomic_exchange_explicit_char(underlying, value, memoryOrder)

  def compareExchangeStrong(
      expected: CChar,
      desired: CChar
  ): (Boolean, CChar) = {
    val expectedPtr: Ptr[CChar] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CChar])
    )
    !expectedPtr = expected
    val res =
      atomic_compare_exchange_strong_char(underlying, expectedPtr, desired)
    (res, !expectedPtr)
  }

  def compareExchangeStrong(
      expected: CChar,
      desired: CChar,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, CChar) = {
    val expectedPtr: Ptr[CChar] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CChar])
    )
    !expectedPtr = expected
    val res = atomic_compare_exchange_strong_explicit_char(
      underlying,
      expectedPtr,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, !expectedPtr)
  }

  def compareExchangeStrong(
      expected: CChar,
      desired: CChar,
      memoryOrder: memory_order
  ): (Boolean, CChar) = {
    compareExchangeStrong(
      expected: CChar,
      desired: CChar,
      memoryOrder,
      memoryOrder
    )
  }

  def compareExchangeWeak(expected: CChar, desired: CChar): (Boolean, CChar) = {
    val expectedPtr: Ptr[CChar] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CChar])
    )
    !expectedPtr = expected
    val res =
      atomic_compare_exchange_weak_char(underlying, expectedPtr, desired)
    (res, !expectedPtr)
  }

  def compareExchangeWeak(
      expected: CChar,
      desired: CChar,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, CChar) = {
    val expectedPtr: Ptr[CChar] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CChar])
    )
    !expectedPtr = expected
    val res = atomic_compare_exchange_weak_explicit_char(
      underlying,
      expectedPtr,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, !expectedPtr)
  }

  def compareExchangeWeak(
      expected: CChar,
      desired: CChar,
      memoryOrder: memory_order
  ): (Boolean, CChar) = {
    compareExchangeWeak(
      expected: CChar,
      desired: CChar,
      memoryOrder,
      memoryOrder
    )
  }

  def fetchAdd(value: CChar): CChar = atomic_fetch_add_char(underlying, value)
  def fetchAdd(value: CChar, memoryOrder: memory_order): CChar =
    atomic_fetch_add_explicit_char(underlying, value, memoryOrder)

  def fetchSub(value: CChar): CChar = atomic_fetch_sub_char(underlying, value)
  def fetchSub(value: CChar, memoryOrder: memory_order): CChar =
    atomic_fetch_sub_explicit_char(underlying, value, memoryOrder)

  def fetchAnd(value: CChar): CChar = atomic_fetch_and_char(underlying, value)
  def fetchAnd(value: CChar, memoryOrder: memory_order): CChar =
    atomic_fetch_and_explicit_char(underlying, value, memoryOrder)

  def fetchOr(value: CChar): CChar = atomic_fetch_or_char(underlying, value)
  def fetchOr(value: CChar, memoryOrder: memory_order): CChar =
    atomic_fetch_or_explicit_char(underlying, value, memoryOrder)

  def fetchXor(value: CChar): CChar = atomic_fetch_xor_char(underlying, value)
  def fetchXor(value: CChar, memoryOrder: memory_order): CChar =
    atomic_fetch_xor_explicit_char(underlying, value, memoryOrder)
}
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 120)
object CAtomicCSize {
  def apply(initialValue: CSize)(implicit zone: Zone): CAtomicCSize = {
    val ref = new CAtomicCSize(
      zone.alloc(sizeof[CSize]).asInstanceOf[atomic.CAtomicCSize]
    )
    ref.init(initialValue)
    ref
  }
}

final class CAtomicCSize(private val underlying: atomic.CAtomicCSize)
    extends AnyVal {
  def init(value: CSize): Unit = atomic_init_csize(underlying, value)

  def load(): CSize = atomic_load_csize(underlying)
  def load(memoryOrder: memory_order): CSize =
    atomic_load_explicit_csize(underlying, memoryOrder)

  def store(value: CSize): Unit = atomic_store_csize(underlying, value)
  def store(value: CSize, memoryOrder: memory_order): Unit =
    atomic_store_explicit_csize(underlying, value, memoryOrder)

  def exchange(value: CSize): CSize = atomic_exchange_csize(underlying, value)
  def exchange(value: CSize, memoryOrder: memory_order): CSize =
    atomic_exchange_explicit_csize(underlying, value, memoryOrder)

  def compareExchangeStrong(
      expected: CSize,
      desired: CSize
  ): (Boolean, CSize) = {
    val expectedPtr: Ptr[CSize] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CSize])
    )
    !expectedPtr = expected
    val res =
      atomic_compare_exchange_strong_csize(underlying, expectedPtr, desired)
    (res, !expectedPtr)
  }

  def compareExchangeStrong(
      expected: CSize,
      desired: CSize,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, CSize) = {
    val expectedPtr: Ptr[CSize] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CSize])
    )
    !expectedPtr = expected
    val res = atomic_compare_exchange_strong_explicit_csize(
      underlying,
      expectedPtr,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, !expectedPtr)
  }

  def compareExchangeStrong(
      expected: CSize,
      desired: CSize,
      memoryOrder: memory_order
  ): (Boolean, CSize) = {
    compareExchangeStrong(
      expected: CSize,
      desired: CSize,
      memoryOrder,
      memoryOrder
    )
  }

  def compareExchangeWeak(expected: CSize, desired: CSize): (Boolean, CSize) = {
    val expectedPtr: Ptr[CSize] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CSize])
    )
    !expectedPtr = expected
    val res =
      atomic_compare_exchange_weak_csize(underlying, expectedPtr, desired)
    (res, !expectedPtr)
  }

  def compareExchangeWeak(
      expected: CSize,
      desired: CSize,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, CSize) = {
    val expectedPtr: Ptr[CSize] = fromRawPtr(
      Intrinsics.stackalloc(sizeof[CSize])
    )
    !expectedPtr = expected
    val res = atomic_compare_exchange_weak_explicit_csize(
      underlying,
      expectedPtr,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, !expectedPtr)
  }

  def compareExchangeWeak(
      expected: CSize,
      desired: CSize,
      memoryOrder: memory_order
  ): (Boolean, CSize) = {
    compareExchangeWeak(
      expected: CSize,
      desired: CSize,
      memoryOrder,
      memoryOrder
    )
  }

  def fetchAdd(value: CSize): CSize = atomic_fetch_add_csize(underlying, value)
  def fetchAdd(value: CSize, memoryOrder: memory_order): CSize =
    atomic_fetch_add_explicit_csize(underlying, value, memoryOrder)

  def fetchSub(value: CSize): CSize = atomic_fetch_sub_csize(underlying, value)
  def fetchSub(value: CSize, memoryOrder: memory_order): CSize =
    atomic_fetch_sub_explicit_csize(underlying, value, memoryOrder)

  def fetchAnd(value: CSize): CSize = atomic_fetch_and_csize(underlying, value)
  def fetchAnd(value: CSize, memoryOrder: memory_order): CSize =
    atomic_fetch_and_explicit_csize(underlying, value, memoryOrder)

  def fetchOr(value: CSize): CSize = atomic_fetch_or_csize(underlying, value)
  def fetchOr(value: CSize, memoryOrder: memory_order): CSize =
    atomic_fetch_or_explicit_csize(underlying, value, memoryOrder)

  def fetchXor(value: CSize): CSize = atomic_fetch_xor_csize(underlying, value)
  def fetchXor(value: CSize, memoryOrder: memory_order): CSize =
    atomic_fetch_xor_explicit_csize(underlying, value, memoryOrder)
}
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 120)
object CAtomicPtr {
  def apply(initialValue: Word)(implicit zone: Zone): CAtomicPtr = {
    val ref = new CAtomicPtr(
      zone.alloc(sizeof[Word]).asInstanceOf[atomic.CAtomicPtr]
    )
    ref.init(initialValue)
    ref
  }
}

final class CAtomicPtr(private val underlying: atomic.CAtomicPtr)
    extends AnyVal {
  def init(value: Word): Unit = atomic_init_intptr(underlying, value)

  def load(): Word = atomic_load_intptr(underlying)
  def load(memoryOrder: memory_order): Word =
    atomic_load_explicit_intptr(underlying, memoryOrder)

  def store(value: Word): Unit = atomic_store_intptr(underlying, value)
  def store(value: Word, memoryOrder: memory_order): Unit =
    atomic_store_explicit_intptr(underlying, value, memoryOrder)

  def exchange(value: Word): Word = atomic_exchange_intptr(underlying, value)
  def exchange(value: Word, memoryOrder: memory_order): Word =
    atomic_exchange_explicit_intptr(underlying, value, memoryOrder)

  def compareExchangeStrong(expected: Word, desired: Word): (Boolean, Word) = {
    val expectedPtr: Ptr[Word] = fromRawPtr(Intrinsics.stackalloc(sizeof[Word]))
    !expectedPtr = expected
    val res =
      atomic_compare_exchange_strong_intptr(underlying, expectedPtr, desired)
    (res, !expectedPtr)
  }

  def compareExchangeStrong(
      expected: Word,
      desired: Word,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, Word) = {
    val expectedPtr: Ptr[Word] = fromRawPtr(Intrinsics.stackalloc(sizeof[Word]))
    !expectedPtr = expected
    val res = atomic_compare_exchange_strong_explicit_intptr(
      underlying,
      expectedPtr,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, !expectedPtr)
  }

  def compareExchangeStrong(
      expected: Word,
      desired: Word,
      memoryOrder: memory_order
  ): (Boolean, Word) = {
    compareExchangeStrong(
      expected: Word,
      desired: Word,
      memoryOrder,
      memoryOrder
    )
  }

  def compareExchangeWeak(expected: Word, desired: Word): (Boolean, Word) = {
    val expectedPtr: Ptr[Word] = fromRawPtr(Intrinsics.stackalloc(sizeof[Word]))
    !expectedPtr = expected
    val res =
      atomic_compare_exchange_weak_intptr(underlying, expectedPtr, desired)
    (res, !expectedPtr)
  }

  def compareExchangeWeak(
      expected: Word,
      desired: Word,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, Word) = {
    val expectedPtr: Ptr[Word] = fromRawPtr(Intrinsics.stackalloc(sizeof[Word]))
    !expectedPtr = expected
    val res = atomic_compare_exchange_weak_explicit_intptr(
      underlying,
      expectedPtr,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, !expectedPtr)
  }

  def compareExchangeWeak(
      expected: Word,
      desired: Word,
      memoryOrder: memory_order
  ): (Boolean, Word) = {
    compareExchangeWeak(expected: Word, desired: Word, memoryOrder, memoryOrder)
  }

  def fetchAdd(value: Word): Word = atomic_fetch_add_intptr(underlying, value)
  def fetchAdd(value: Word, memoryOrder: memory_order): Word =
    atomic_fetch_add_explicit_intptr(underlying, value, memoryOrder)

  def fetchSub(value: Word): Word = atomic_fetch_sub_intptr(underlying, value)
  def fetchSub(value: Word, memoryOrder: memory_order): Word =
    atomic_fetch_sub_explicit_intptr(underlying, value, memoryOrder)

  def fetchAnd(value: Word): Word = atomic_fetch_and_intptr(underlying, value)
  def fetchAnd(value: Word, memoryOrder: memory_order): Word =
    atomic_fetch_and_explicit_intptr(underlying, value, memoryOrder)

  def fetchOr(value: Word): Word = atomic_fetch_or_intptr(underlying, value)
  def fetchOr(value: Word, memoryOrder: memory_order): Word =
    atomic_fetch_or_explicit_intptr(underlying, value, memoryOrder)

  def fetchXor(value: Word): Word = atomic_fetch_xor_intptr(underlying, value)
  def fetchXor(value: Word, memoryOrder: memory_order): Word =
    atomic_fetch_xor_explicit_intptr(underlying, value, memoryOrder)
}
// ###sourceLocation(file: "nativelib/src/main/scala/scala/scalanative/unsafe/CAtomic.scala.gyb", line: 192)

object CAtomicRef {
  def apply[T <: AnyRef: Tag](
      initialValue: T
  )(implicit zone: Zone): CAtomicRef[T] = {
    val ref = new CAtomicRef[T](zone.alloc(sizeof[T]).asInstanceOf[Ptr[T]])
    ref.init(initialValue)
    ref
  }
}

final class CAtomicRef[T <: AnyRef](private val underlying: Ptr[T])
    extends AnyVal {
  type Repr = Word

  @alwaysinline
  private def asCAtomicPtr: CAtomicPtr =
    new CAtomicPtr(fromRawPtr[Word](toRawPtr(underlying)))

  def init(value: T): Unit = asCAtomicPtr.init(value)

  def load(): T = asCAtomicPtr.load()
  def load(memoryOrder: memory_order): T = asCAtomicPtr.load(memoryOrder)

  def store(value: T): Unit = asCAtomicPtr.store(value)
  def store(value: T, memoryOrder: memory_order): Unit =
    asCAtomicPtr.store(value, memoryOrder)

  def exchange(value: T): T = asCAtomicPtr.exchange(value)
  def exchange(value: T, memoryOrder: memory_order): T =
    asCAtomicPtr.exchange(value, memoryOrder)

  def compareExchangeStrong(expected: T, desired: T): (Boolean, T) = {
    val (res, addr) = asCAtomicPtr.compareExchangeStrong(expected, desired)
    (res, addr)
  }

  def compareExchangeStrong(
      expected: T,
      desired: T,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, T) = {
    val (res, addr) = asCAtomicPtr.compareExchangeStrong(
      expected,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, addr)
  }

  def compareExchangeStrong(
      expected: T,
      desired: T,
      memoryOrder: memory_order
  ): (Boolean, T) = {
    compareExchangeStrong(expected: T, desired: T, memoryOrder, memoryOrder)
  }

  def compareExchangeWeak(expected: T, desired: T): (Boolean, T) = {
    val (res, addr) = asCAtomicPtr.compareExchangeWeak(expected, desired)
    (res, addr)
  }

  def compareExchangeWeak(
      expected: T,
      desired: T,
      memoryOrderOnSuccess: memory_order,
      memoryOrderOnFailure: memory_order
  ): (Boolean, T) = {
    val (res, addr) = asCAtomicPtr.compareExchangeWeak(
      expected,
      desired,
      memoryOrderOnSuccess,
      memoryOrderOnFailure
    )
    (res, addr)
  }

  def compareExchangeWeak(
      expected: T,
      desired: T,
      memoryOrder: memory_order
  ): (Boolean, T) = {
    compareExchangeWeak(expected: T, desired: T, memoryOrder, memoryOrder)
  }

  private implicit def toCAtomicPtr(ref: T): Repr =
    Intrinsics.castRawPtrToLong {
      Intrinsics.castObjectToRawPtr(ref)
    }

  private implicit def fromCAtomicPtr(repr: Repr): T = {
    if (repr == 0L) null.asInstanceOf[T]
    else {
      Intrinsics
        .castRawPtrToObject {
          Intrinsics.castLongToRawPtr(repr)
        }
        .asInstanceOf[T]
    }
  }
}

object CAtomicFlag {
  def apply(initialValue: Boolean)(implicit zone: Zone): CAtomicFlag = {
    val ref = new CAtomicFlag(
      zone.alloc(sizeof[Boolean]).asInstanceOf[atomic.CAtomicFlag]
    )
    ref.init(initialValue)
    ref
  }
}

final class CAtomicFlag(private val underlying: atomic.CAtomicFlag)
    extends AnyVal {
  def init(value: Boolean) = atomic_flag_init(underlying, value)

  def testAndSet(): Boolean = atomic_flag_test_and_set(underlying)
  def testAndSet(order: memory_order): Boolean =
    atomic_flag_test_and_set_explicit(underlying, order)

  def clear(): Unit = atomic_flag_clear(underlying)
  def clear(order: memory_order): Unit =
    atomic_flag_clear_explicit(underlying, order)
}
