// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 1)
package scala.scalanative.runtime

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 19)

// see http://en.cppreference.com/w/cpp/atomic

@extern
object Atomic {

  // Init
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 27)
  @name("scalanative_atomic_init_byte")
  def atomic_init_byte(atm: CAtomicByte, initValue: Byte): Unit = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 27)
  @name("scalanative_atomic_init_short")
  def atomic_init_short(atm: CAtomicShort, initValue: CShort): Unit = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 27)
  @name("scalanative_atomic_init_int")
  def atomic_init_int(atm: CAtomicInt, initValue: CInt): Unit = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 27)
  @name("scalanative_atomic_init_long")
  def atomic_init_long(atm: CAtomicLong, initValue: CLong): Unit = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 27)
  @name("scalanative_atomic_init_ubyte")
  def atomic_init_ubyte(atm: CAtomicUnsignedByte, initValue: UByte): Unit = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 27)
  @name("scalanative_atomic_init_ushort")
  def atomic_init_ushort(atm: CAtomicUnsignedShort, initValue: CUnsignedShort): Unit = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 27)
  @name("scalanative_atomic_init_uint")
  def atomic_init_uint(atm: CAtomicUnsignedInt, initValue: CUnsignedInt): Unit = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 27)
  @name("scalanative_atomic_init_ulong")
  def atomic_init_ulong(atm: CAtomicUnsignedLong, initValue: CUnsignedLong): Unit = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 27)
  @name("scalanative_atomic_init_char")
  def atomic_init_char(atm: CAtomicChar, initValue: CChar): Unit = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 27)
  @name("scalanative_atomic_init_csize")
  def atomic_init_csize(atm: CAtomicCSize, initValue: CSize): Unit = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 30)

  // Load
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 33)
  @name("scalanative_atomic_load_byte")
  def atomic_load_byte(ptr: CAtomicByte): Byte = extern

  @name("scalanative_atomic_store_byte")
  def atomic_store_byte(ptr: CAtomicByte, v: Byte): Unit = extern

  @name("scalanative_atomic_exchange_byte")
  def atomic_exchange_byte(ptr: CAtomicByte, v: Byte): Byte = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 33)
  @name("scalanative_atomic_load_short")
  def atomic_load_short(ptr: CAtomicShort): CShort = extern

  @name("scalanative_atomic_store_short")
  def atomic_store_short(ptr: CAtomicShort, v: CShort): Unit = extern

  @name("scalanative_atomic_exchange_short")
  def atomic_exchange_short(ptr: CAtomicShort, v: CShort): CShort = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 33)
  @name("scalanative_atomic_load_int")
  def atomic_load_int(ptr: CAtomicInt): CInt = extern

  @name("scalanative_atomic_store_int")
  def atomic_store_int(ptr: CAtomicInt, v: CInt): Unit = extern

  @name("scalanative_atomic_exchange_int")
  def atomic_exchange_int(ptr: CAtomicInt, v: CInt): CInt = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 33)
  @name("scalanative_atomic_load_long")
  def atomic_load_long(ptr: CAtomicLong): CLong = extern

  @name("scalanative_atomic_store_long")
  def atomic_store_long(ptr: CAtomicLong, v: CLong): Unit = extern

  @name("scalanative_atomic_exchange_long")
  def atomic_exchange_long(ptr: CAtomicLong, v: CLong): CLong = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 33)
  @name("scalanative_atomic_load_ubyte")
  def atomic_load_ubyte(ptr: CAtomicUnsignedByte): UByte = extern

  @name("scalanative_atomic_store_ubyte")
  def atomic_store_ubyte(ptr: CAtomicUnsignedByte, v: UByte): Unit = extern

  @name("scalanative_atomic_exchange_ubyte")
  def atomic_exchange_ubyte(ptr: CAtomicUnsignedByte, v: UByte): UByte = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 33)
  @name("scalanative_atomic_load_ushort")
  def atomic_load_ushort(ptr: CAtomicUnsignedShort): CUnsignedShort = extern

  @name("scalanative_atomic_store_ushort")
  def atomic_store_ushort(ptr: CAtomicUnsignedShort, v: CUnsignedShort): Unit = extern

  @name("scalanative_atomic_exchange_ushort")
  def atomic_exchange_ushort(ptr: CAtomicUnsignedShort, v: CUnsignedShort): CUnsignedShort = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 33)
  @name("scalanative_atomic_load_uint")
  def atomic_load_uint(ptr: CAtomicUnsignedInt): CUnsignedInt = extern

  @name("scalanative_atomic_store_uint")
  def atomic_store_uint(ptr: CAtomicUnsignedInt, v: CUnsignedInt): Unit = extern

  @name("scalanative_atomic_exchange_uint")
  def atomic_exchange_uint(ptr: CAtomicUnsignedInt, v: CUnsignedInt): CUnsignedInt = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 33)
  @name("scalanative_atomic_load_ulong")
  def atomic_load_ulong(ptr: CAtomicUnsignedLong): CUnsignedLong = extern

  @name("scalanative_atomic_store_ulong")
  def atomic_store_ulong(ptr: CAtomicUnsignedLong, v: CUnsignedLong): Unit = extern

  @name("scalanative_atomic_exchange_ulong")
  def atomic_exchange_ulong(ptr: CAtomicUnsignedLong, v: CUnsignedLong): CUnsignedLong = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 33)
  @name("scalanative_atomic_load_char")
  def atomic_load_char(ptr: CAtomicChar): CChar = extern

  @name("scalanative_atomic_store_char")
  def atomic_store_char(ptr: CAtomicChar, v: CChar): Unit = extern

  @name("scalanative_atomic_exchange_char")
  def atomic_exchange_char(ptr: CAtomicChar, v: CChar): CChar = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 33)
  @name("scalanative_atomic_load_csize")
  def atomic_load_csize(ptr: CAtomicCSize): CSize = extern

  @name("scalanative_atomic_store_csize")
  def atomic_store_csize(ptr: CAtomicCSize, v: CSize): Unit = extern

  @name("scalanative_atomic_exchange_csize")
  def atomic_exchange_csize(ptr: CAtomicCSize, v: CSize): CSize = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 42)

  // Compare and Swap
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 45)
  @name("scalanative_atomic_compare_and_swap_strong_byte")
  def compare_and_swap_strong_byte(value: CAtomicByte,
                                  expected: CAtomicByte,
                                  desired: Byte): CBool = extern

  @name("scalanative_atomic_compare_and_swap_weak_byte")
  def compare_and_swap_weak_byte(value: CAtomicByte,
                                  expected: CAtomicByte,
                                  desired: Byte): CBool = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 45)
  @name("scalanative_atomic_compare_and_swap_strong_short")
  def compare_and_swap_strong_short(value: CAtomicShort,
                                  expected: CAtomicShort,
                                  desired: CShort): CBool = extern

  @name("scalanative_atomic_compare_and_swap_weak_short")
  def compare_and_swap_weak_short(value: CAtomicShort,
                                  expected: CAtomicShort,
                                  desired: CShort): CBool = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 45)
  @name("scalanative_atomic_compare_and_swap_strong_int")
  def compare_and_swap_strong_int(value: CAtomicInt,
                                  expected: CAtomicInt,
                                  desired: CInt): CBool = extern

  @name("scalanative_atomic_compare_and_swap_weak_int")
  def compare_and_swap_weak_int(value: CAtomicInt,
                                  expected: CAtomicInt,
                                  desired: CInt): CBool = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 45)
  @name("scalanative_atomic_compare_and_swap_strong_long")
  def compare_and_swap_strong_long(value: CAtomicLong,
                                  expected: CAtomicLong,
                                  desired: CLong): CBool = extern

  @name("scalanative_atomic_compare_and_swap_weak_long")
  def compare_and_swap_weak_long(value: CAtomicLong,
                                  expected: CAtomicLong,
                                  desired: CLong): CBool = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 45)
  @name("scalanative_atomic_compare_and_swap_strong_ubyte")
  def compare_and_swap_strong_ubyte(value: CAtomicUnsignedByte,
                                  expected: CAtomicUnsignedByte,
                                  desired: UByte): CBool = extern

  @name("scalanative_atomic_compare_and_swap_weak_ubyte")
  def compare_and_swap_weak_ubyte(value: CAtomicUnsignedByte,
                                  expected: CAtomicUnsignedByte,
                                  desired: UByte): CBool = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 45)
  @name("scalanative_atomic_compare_and_swap_strong_ushort")
  def compare_and_swap_strong_ushort(value: CAtomicUnsignedShort,
                                  expected: CAtomicUnsignedShort,
                                  desired: CUnsignedShort): CBool = extern

  @name("scalanative_atomic_compare_and_swap_weak_ushort")
  def compare_and_swap_weak_ushort(value: CAtomicUnsignedShort,
                                  expected: CAtomicUnsignedShort,
                                  desired: CUnsignedShort): CBool = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 45)
  @name("scalanative_atomic_compare_and_swap_strong_uint")
  def compare_and_swap_strong_uint(value: CAtomicUnsignedInt,
                                  expected: CAtomicUnsignedInt,
                                  desired: CUnsignedInt): CBool = extern

  @name("scalanative_atomic_compare_and_swap_weak_uint")
  def compare_and_swap_weak_uint(value: CAtomicUnsignedInt,
                                  expected: CAtomicUnsignedInt,
                                  desired: CUnsignedInt): CBool = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 45)
  @name("scalanative_atomic_compare_and_swap_strong_ulong")
  def compare_and_swap_strong_ulong(value: CAtomicUnsignedLong,
                                  expected: CAtomicUnsignedLong,
                                  desired: CUnsignedLong): CBool = extern

  @name("scalanative_atomic_compare_and_swap_weak_ulong")
  def compare_and_swap_weak_ulong(value: CAtomicUnsignedLong,
                                  expected: CAtomicUnsignedLong,
                                  desired: CUnsignedLong): CBool = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 45)
  @name("scalanative_atomic_compare_and_swap_strong_char")
  def compare_and_swap_strong_char(value: CAtomicChar,
                                  expected: CAtomicChar,
                                  desired: CChar): CBool = extern

  @name("scalanative_atomic_compare_and_swap_weak_char")
  def compare_and_swap_weak_char(value: CAtomicChar,
                                  expected: CAtomicChar,
                                  desired: CChar): CBool = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 45)
  @name("scalanative_atomic_compare_and_swap_strong_csize")
  def compare_and_swap_strong_csize(value: CAtomicCSize,
                                  expected: CAtomicCSize,
                                  desired: CSize): CBool = extern

  @name("scalanative_atomic_compare_and_swap_weak_csize")
  def compare_and_swap_weak_csize(value: CAtomicCSize,
                                  expected: CAtomicCSize,
                                  desired: CSize): CBool = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 55)

  // Add and Sub
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_add_byte")
  def atomic_add_byte(ptr: CAtomicByte, value: Byte): Byte = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_sub_byte")
  def atomic_sub_byte(ptr: CAtomicByte, value: Byte): Byte = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_or_byte")
  def atomic_or_byte(ptr: CAtomicByte, value: Byte): Byte = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_and_byte")
  def atomic_and_byte(ptr: CAtomicByte, value: Byte): Byte = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_xor_byte")
  def atomic_xor_byte(ptr: CAtomicByte, value: Byte): Byte = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_add_short")
  def atomic_add_short(ptr: CAtomicShort, value: CShort): CShort = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_sub_short")
  def atomic_sub_short(ptr: CAtomicShort, value: CShort): CShort = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_or_short")
  def atomic_or_short(ptr: CAtomicShort, value: CShort): CShort = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_and_short")
  def atomic_and_short(ptr: CAtomicShort, value: CShort): CShort = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_xor_short")
  def atomic_xor_short(ptr: CAtomicShort, value: CShort): CShort = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_add_int")
  def atomic_add_int(ptr: CAtomicInt, value: CInt): CInt = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_sub_int")
  def atomic_sub_int(ptr: CAtomicInt, value: CInt): CInt = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_or_int")
  def atomic_or_int(ptr: CAtomicInt, value: CInt): CInt = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_and_int")
  def atomic_and_int(ptr: CAtomicInt, value: CInt): CInt = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_xor_int")
  def atomic_xor_int(ptr: CAtomicInt, value: CInt): CInt = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_add_long")
  def atomic_add_long(ptr: CAtomicLong, value: CLong): CLong = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_sub_long")
  def atomic_sub_long(ptr: CAtomicLong, value: CLong): CLong = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_or_long")
  def atomic_or_long(ptr: CAtomicLong, value: CLong): CLong = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_and_long")
  def atomic_and_long(ptr: CAtomicLong, value: CLong): CLong = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_xor_long")
  def atomic_xor_long(ptr: CAtomicLong, value: CLong): CLong = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_add_ubyte")
  def atomic_add_ubyte(ptr: CAtomicUnsignedByte, value: UByte): UByte = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_sub_ubyte")
  def atomic_sub_ubyte(ptr: CAtomicUnsignedByte, value: UByte): UByte = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_or_ubyte")
  def atomic_or_ubyte(ptr: CAtomicUnsignedByte, value: UByte): UByte = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_and_ubyte")
  def atomic_and_ubyte(ptr: CAtomicUnsignedByte, value: UByte): UByte = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_xor_ubyte")
  def atomic_xor_ubyte(ptr: CAtomicUnsignedByte, value: UByte): UByte = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_add_ushort")
  def atomic_add_ushort(ptr: CAtomicUnsignedShort, value: CUnsignedShort): CUnsignedShort = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_sub_ushort")
  def atomic_sub_ushort(ptr: CAtomicUnsignedShort, value: CUnsignedShort): CUnsignedShort = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_or_ushort")
  def atomic_or_ushort(ptr: CAtomicUnsignedShort, value: CUnsignedShort): CUnsignedShort = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_and_ushort")
  def atomic_and_ushort(ptr: CAtomicUnsignedShort, value: CUnsignedShort): CUnsignedShort = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_xor_ushort")
  def atomic_xor_ushort(ptr: CAtomicUnsignedShort, value: CUnsignedShort): CUnsignedShort = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_add_uint")
  def atomic_add_uint(ptr: CAtomicUnsignedInt, value: CUnsignedInt): CUnsignedInt = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_sub_uint")
  def atomic_sub_uint(ptr: CAtomicUnsignedInt, value: CUnsignedInt): CUnsignedInt = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_or_uint")
  def atomic_or_uint(ptr: CAtomicUnsignedInt, value: CUnsignedInt): CUnsignedInt = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_and_uint")
  def atomic_and_uint(ptr: CAtomicUnsignedInt, value: CUnsignedInt): CUnsignedInt = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_xor_uint")
  def atomic_xor_uint(ptr: CAtomicUnsignedInt, value: CUnsignedInt): CUnsignedInt = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_add_ulong")
  def atomic_add_ulong(ptr: CAtomicUnsignedLong, value: CUnsignedLong): CUnsignedLong = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_sub_ulong")
  def atomic_sub_ulong(ptr: CAtomicUnsignedLong, value: CUnsignedLong): CUnsignedLong = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_or_ulong")
  def atomic_or_ulong(ptr: CAtomicUnsignedLong, value: CUnsignedLong): CUnsignedLong = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_and_ulong")
  def atomic_and_ulong(ptr: CAtomicUnsignedLong, value: CUnsignedLong): CUnsignedLong = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_xor_ulong")
  def atomic_xor_ulong(ptr: CAtomicUnsignedLong, value: CUnsignedLong): CUnsignedLong = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_add_char")
  def atomic_add_char(ptr: CAtomicChar, value: CChar): CChar = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_sub_char")
  def atomic_sub_char(ptr: CAtomicChar, value: CChar): CChar = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_or_char")
  def atomic_or_char(ptr: CAtomicChar, value: CChar): CChar = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_and_char")
  def atomic_and_char(ptr: CAtomicChar, value: CChar): CChar = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_xor_char")
  def atomic_xor_char(ptr: CAtomicChar, value: CChar): CChar = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_add_csize")
  def atomic_add_csize(ptr: CAtomicCSize, value: CSize): CSize = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_sub_csize")
  def atomic_sub_csize(ptr: CAtomicCSize, value: CSize): CSize = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_or_csize")
  def atomic_or_csize(ptr: CAtomicCSize, value: CSize): CSize = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_and_csize")
  def atomic_and_csize(ptr: CAtomicCSize, value: CSize): CSize = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 59)
  @name("scalanative_atomic_xor_csize")
  def atomic_xor_csize(ptr: CAtomicCSize, value: CSize): CSize = extern
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 63)

  // Types
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 66)
  type CAtomicByte = Ptr[Byte]
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 66)
  type CAtomicShort = Ptr[CShort]
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 66)
  type CAtomicInt = Ptr[CInt]
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 66)
  type CAtomicLong = Ptr[CLong]
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 66)
  type CAtomicUnsignedByte = Ptr[UByte]
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 66)
  type CAtomicUnsignedShort = Ptr[CUnsignedShort]
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 66)
  type CAtomicUnsignedInt = Ptr[CUnsignedInt]
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 66)
  type CAtomicUnsignedLong = Ptr[CUnsignedLong]
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 66)
  type CAtomicChar = Ptr[CChar]
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 66)
  type CAtomicCSize = Ptr[CSize]
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 68)


  type memory_order = Int // enum
  object memory_order{
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 73)
    @name("scalanative_atomic_memory_order_relaxed")
    final def memory_order_relaxed: memory_order = extern    
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 73)
    @name("scalanative_atomic_memory_order_consume")
    final def memory_order_consume: memory_order = extern    
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 73)
    @name("scalanative_atomic_memory_order_acquire")
    final def memory_order_acquire: memory_order = extern    
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 73)
    @name("scalanative_atomic_memory_order_release")
    final def memory_order_release: memory_order = extern    
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 73)
    @name("scalanative_atomic_memory_order_acq_rel")
    final def memory_order_acq_rel: memory_order = extern    
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 73)
    @name("scalanative_atomic_memory_order_seq_cst")
    final def memory_order_seq_cst: memory_order = extern    
// ###sourceLocation(file: "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/scala/scala/scalanative/runtime/Atomic.scala.gyb", line: 76)
  }

  @name("scalanative_atomic_thread_fence")
  final def atomic_thread_fence(order: memory_order): Unit = extern
  @name("scalanative_atomic_signal_fence")
  final def atomic_signal_fence(order: memory_order): Unit = extern
}
