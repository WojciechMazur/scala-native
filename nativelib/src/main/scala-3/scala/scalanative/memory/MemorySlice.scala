package scala.scalanative.memory

import scala.compiletime.ops.int.*
import scala.compiletime.*
import scala.scalanative.unsafe.*
import scala.scalanative.unsafe
import scala.scalanative.unsigned

opaque type MemorySlice[T, Size <: Int] <: Ptr[T] = Ptr[T]
object MemorySlice {
  inline def wrapUnsafe[T, Size <: Int](
      inline ptr: Ptr[T],
      inline length: Size
  ): MemorySlice[T, Size] =
    requireConst(length)
    ptr

  inline def wrapUnsafe[T, Size <: Int](
      inline ptr: Ptr[T]
  ): MemorySlice[T, Size] = ptr

  inline def stackalloc[T, Size <: Int] =
    unsafe.stackalloc[T](constValue[Size])
  inline def alloc[T, Size <: Int](using Zone) =
    unsafe.alloc[T](constValue[Size])

  extension [T, Size <: Int](ptr: MemorySlice[T, Size]) {
    transparent inline def length: Size = constValue[Size]

    inline def apply(inline idx: Int): T = {
      validateAccess(idx, length)
      ptr(idx)(using summonInline[Tag[T]])
    }
    inline def apply[Idx <: Int]: T = {
      inline val idx = constValue[Idx]
      validateAccess(idx, length)
      ptr(idx)(using summonInline[Tag[T]])
    }

    inline def update(idx: Int, value: T): Unit = {
      validateAccess(idx, length)
      (ptr(idx) = value)(using summonInline[Tag[T]])
    }
    inline def update[Idx <: Int](value: T): Unit = {
      inline val idx = constValue[Idx]
      validateAccess(idx, length)
      (ptr(idx) = value)(using summonInline[Tag[T]])
    }

    inline def narrow[NewSize <: Int](using
        MemorySlice.CanNarrowTo[Size, NewSize] =:= true
    ): MemorySlice[T, NewSize] = ptr

    transparent inline def reinterpret[As](using
        SizeConversion[Size, T, As] > 0 =:= true
    ): MemorySlice[As, SizeConversion[Size, T, As]] = ptr.asInstanceOf[Ptr[As]]

    inline def toArray: Array[T] = {
      import scala.scalanative.runtime.{toRawPtr, libc}
      import scala.scalanative.unsigned._
      val arr = new Array[T](length)(summonInline[scala.reflect.ClassTag[T]])
      libc.memcpy(
        toRawPtr(arr.at(0)),
        toRawPtr(ptr),
        (length * sizeOf[T]).toUSize
      )
      arr
    }

    // Use macro instead of constValueOpt which would allocate Option instance
    private inline def validateAccess(
        inline idx: Int,
        inline length: Int
    ): Unit = ${ Macros.validateAccess('idx, 'length) }
  }

  type CanNarrowTo[S1 <: Int, S2 <: Int] = S2 <= S1
  inline given narrow[T, S1 <: Int, S2 <: Int](using
      CanNarrowTo[S1, S2] =:= true
  ): Conversion[MemorySlice[T, S1], MemorySlice[T, S2]] with
    def apply(arg: MemorySlice[T, S1]) = arg.asInstanceOf

  type SizeOf[T] <: Int = T match {
    case Byte                => 1
    case Short               => 2
    case Int                 => 4
    case Long                => 8
    case Float               => 4
    case Double              => 8
    case Boolean             => 1
    case Char                => 2
    case unsigned.UByte      => 1
    case unsigned.UShort     => 2
    case unsigned.UInt       => 4
    case unsigned.ULong      => 8
    case unsafe.CArray[t, n] => SizeOf[t] * ParseNat[n]
    // format: off
    // needs to be inlined, otherwise match type explodes
    case unsafe.CStruct1[n1] => SizeOf[n1]
    case unsafe.CStruct2[n1, n2] => SizeOf[n1] + SizeOf[n2]
    case unsafe.CStruct3[n1, n2, n3] => SizeOf[n1] + SizeOf[n2] + SizeOf[n3]
    case unsafe.CStruct4[n1, n2, n3, n4] => SizeOf[n1] + SizeOf[n2] + SizeOf[n3] + SizeOf[n4]
    case unsafe.CStruct5[n1, n2, n3, n4, n5] => SizeOf[n1] + SizeOf[n2] + SizeOf[n3] + SizeOf[n4] + SizeOf[n5]
    case unsafe.CStruct6[n1, n2, n3, n4, n5, n6] => SizeOf[n1] + SizeOf[n2] + SizeOf[n3] + SizeOf[n4] + SizeOf[n5] + SizeOf[n6]
    case unsafe.CStruct7[n1, n2, n3, n4, n5, n6, n7] => SizeOf[n1] + SizeOf[n2] + SizeOf[n3] + SizeOf[n4] + SizeOf[n5] + SizeOf[n6] + SizeOf[n7]
    case unsafe.CStruct8[n1, n2, n3, n4, n5, n6, n7, n8] => SizeOf[n1] + SizeOf[n2] + SizeOf[n3] + SizeOf[n4] + SizeOf[n5] + SizeOf[n6] + SizeOf[n7] + SizeOf[n8]
    case unsafe.CStruct9[n1, n2, n3, n4, n5, n6, n7, n8, n9] => SizeOf[n1] + SizeOf[n2] + SizeOf[n3] + SizeOf[n4] + SizeOf[n5] + SizeOf[n6] + SizeOf[n7] + SizeOf[n8] + SizeOf[n9]
    case unsafe.CStruct10[n1, n2, n3, n4, n5, n6, n7, n8, n9, n10] => SizeOf[n1] + SizeOf[n2] + SizeOf[n3] + SizeOf[n4] + SizeOf[n5] + SizeOf[n6] + SizeOf[n7] + SizeOf[n8] + SizeOf[n9] + SizeOf[n10]
    case unsafe.CStruct11[n1, n2, n3, n4, n5, n6, n7, n8, n9, n10, n11] => SizeOf[n1] + SizeOf[n2] + SizeOf[n3] + SizeOf[n4] + SizeOf[n5] + SizeOf[n6] + SizeOf[n7] + SizeOf[n8] + SizeOf[n9] + SizeOf[n10] + SizeOf[n11]
    case unsafe.CStruct12[n1, n2, n3, n4, n5, n6, n7, n8, n9, n10, n11, n12] => SizeOf[n1] + SizeOf[n2] + SizeOf[n3] + SizeOf[n4] + SizeOf[n5] + SizeOf[n6] + SizeOf[n7] + SizeOf[n8] + SizeOf[n9] + SizeOf[n10] + SizeOf[n11] + SizeOf[n12]
    case unsafe.CStruct13[n1, n2, n3, n4, n5, n6, n7, n8, n9, n10, n11, n12, n13] => SizeOf[n1] + SizeOf[n2] + SizeOf[n3] + SizeOf[n4] + SizeOf[n5] + SizeOf[n6] + SizeOf[n7] + SizeOf[n8] + SizeOf[n9] + SizeOf[n10] + SizeOf[n11] + SizeOf[n12] + SizeOf[n13]
    case unsafe.CStruct14[n1, n2, n3, n4, n5, n6, n7, n8, n9, n10, n11, n12, n13, n14] => SizeOf[n1] + SizeOf[n2] + SizeOf[n3] + SizeOf[n4] + SizeOf[n5] + SizeOf[n6] + SizeOf[n7] + SizeOf[n8] + SizeOf[n9] + SizeOf[n10] + SizeOf[n11] + SizeOf[n12] + SizeOf[n13] + SizeOf[n14]
    case unsafe.CStruct15[n1, n2, n3, n4, n5, n6, n7, n8, n9, n10, n11, n12, n13, n14, n15] => SizeOf[n1] + SizeOf[n2] + SizeOf[n3] + SizeOf[n4] + SizeOf[n5] + SizeOf[n6] + SizeOf[n7] + SizeOf[n8] + SizeOf[n9] + SizeOf[n10] + SizeOf[n11] + SizeOf[n12] + SizeOf[n13] + SizeOf[n14] + SizeOf[n15]
    case unsafe.CStruct16[n1, n2, n3, n4, n5, n6, n7, n8, n9, n10, n11, n12, n13, n14, n15, n16] => SizeOf[n1] + SizeOf[n2] + SizeOf[n3] + SizeOf[n4] + SizeOf[n5] + SizeOf[n6] + SizeOf[n7] + SizeOf[n8] + SizeOf[n9] + SizeOf[n10] + SizeOf[n11] + SizeOf[n12] + SizeOf[n13] + SizeOf[n14] + SizeOf[n15] + SizeOf[n16]
    case unsafe.CStruct17[n1, n2, n3, n4, n5, n6, n7, n8, n9, n10, n11, n12, n13, n14, n15, n16, n17] => SizeOf[n1] + SizeOf[n2] + SizeOf[n3] + SizeOf[n4] + SizeOf[n5] + SizeOf[n6] + SizeOf[n7] + SizeOf[n8] + SizeOf[n9] + SizeOf[n10] + SizeOf[n11] + SizeOf[n12] + SizeOf[n13] + SizeOf[n14] + SizeOf[n15] + SizeOf[n16] + SizeOf[n17]
    case unsafe.CStruct18[n1, n2, n3, n4, n5, n6, n7, n8, n9, n10, n11, n12, n13, n14, n15, n16, n17, n18] => SizeOf[n1] + SizeOf[n2] + SizeOf[n3] + SizeOf[n4] + SizeOf[n5] + SizeOf[n6] + SizeOf[n7] + SizeOf[n8] + SizeOf[n9] + SizeOf[n10] + SizeOf[n11] + SizeOf[n12] + SizeOf[n13] + SizeOf[n14] + SizeOf[n15] + SizeOf[n16] + SizeOf[n17] + SizeOf[n18]
    case unsafe.CStruct19[n1, n2, n3, n4, n5, n6, n7, n8, n9, n10, n11, n12, n13, n14, n15, n16, n17, n18, n19] => SizeOf[n1] + SizeOf[n2] + SizeOf[n3] + SizeOf[n4] + SizeOf[n5] + SizeOf[n6] + SizeOf[n7] + SizeOf[n8] + SizeOf[n9] + SizeOf[n10] + SizeOf[n11] + SizeOf[n12] + SizeOf[n13] + SizeOf[n14] + SizeOf[n15] + SizeOf[n16] + SizeOf[n17] + SizeOf[n18] + SizeOf[n19]
    case unsafe.CStruct20[n1, n2, n3, n4, n5, n6, n7, n8, n9, n10, n11, n12, n13, n14, n15, n16, n17, n18, n19, n20] => SizeOf[n1] + SizeOf[n2] + SizeOf[n3] + SizeOf[n4] + SizeOf[n5] + SizeOf[n6] + SizeOf[n7] + SizeOf[n8] + SizeOf[n9] + SizeOf[n10] + SizeOf[n11] + SizeOf[n12] + SizeOf[n13] + SizeOf[n14] + SizeOf[n15] + SizeOf[n16] + SizeOf[n17] + SizeOf[n18] + SizeOf[n19] + SizeOf[n20]
    case unsafe.CStruct21[n1, n2, n3, n4, n5, n6, n7, n8, n9, n10, n11, n12, n13, n14, n15, n16, n17, n18, n19, n20, n21] => SizeOf[n1] + SizeOf[n2] + SizeOf[n3] + SizeOf[n4] + SizeOf[n5] + SizeOf[n6] + SizeOf[n7] + SizeOf[n8] + SizeOf[n9] + SizeOf[n10] + SizeOf[n11] + SizeOf[n12] + SizeOf[n13] + SizeOf[n14] + SizeOf[n15] + SizeOf[n16] + SizeOf[n17] + SizeOf[n18] + SizeOf[n19] + SizeOf[n20] + SizeOf[n21]
    case unsafe.CStruct22[n1, n2, n3, n4, n5, n6, n7, n8, n9, n10, n11, n12, n13, n14, n15, n16, n17, n18, n19, n20, n21, n22] => SizeOf[n1] + SizeOf[n2] + SizeOf[n3] + SizeOf[n4] + SizeOf[n5] + SizeOf[n6] + SizeOf[n7] + SizeOf[n8] + SizeOf[n9] + SizeOf[n10] + SizeOf[n11] + SizeOf[n12] + SizeOf[n13] + SizeOf[n14] + SizeOf[n15] + SizeOf[n16] + SizeOf[n17] + SizeOf[n18] + SizeOf[n19] + SizeOf[n20] + SizeOf[n21] + SizeOf[n22]
    // format: on
    // sizeOf[Ptr] / sizeOf[Size] is not correct on x86 and other 32-bit architectures, impossible at compile time
    case _ => 8
  }

  type SizeConversion[BaseSize <: Int, FromType, ToType] =
    BaseSize * SizeOf[FromType] / SizeOf[ToType]

  type ParseNat[T <: Nat] <: Int = T match {
    case unsafe.Nat._0 => 0
    case unsafe.Nat._1 => 1
    case unsafe.Nat._2 => 2
    case unsafe.Nat._3 => 3
    case unsafe.Nat._4 => 4
    case unsafe.Nat._5 => 5
    case unsafe.Nat._6 => 6
    case unsafe.Nat._7 => 7
    case unsafe.Nat._8 => 8
    case unsafe.Nat._9 => 9
    // format: off
    case unsafe.Nat.Digit2[n1, n2] => ParseNat[n1] * 10 + ParseNat[n2]
    case unsafe.Nat.Digit3[n1, n2, n3] => ParseNat[n1] * 100 + ParseNat[n2] * 10 + ParseNat[n3]
    case unsafe.Nat.Digit4[n1, n2, n3, n4] => ParseNat[n1] * 1000 + ParseNat[n2] * 100 + ParseNat[n3] * 10 + ParseNat[n4]
    case unsafe.Nat.Digit5[n1, n2, n3, n4, n5] => ParseNat[n1] * 10000 + ParseNat[n2] * 1000 + ParseNat[n3] * 100 + ParseNat[n4] * 10 + ParseNat[n5]
    case unsafe.Nat.Digit6[n1, n2, n3, n4, n5, n6] => ParseNat[n1] * 100000 + ParseNat[n2] * 10000 + ParseNat[n3] * 1000 + ParseNat[n4] * 100 + ParseNat[n5] * 10 + ParseNat[n6]
    case unsafe.Nat.Digit7[n1, n2, n3, n4, n5, n6, n7] => ParseNat[n1] * 1000000 + ParseNat[n2] * 100000 + ParseNat[n3] * 10000 + ParseNat[n4] * 1000 + ParseNat[n5] * 100 + ParseNat[n6] * 10 + ParseNat[n7]
    case unsafe.Nat.Digit8[n1, n2, n3, n4, n5, n6, n7, n8] => ParseNat[n1] * 10000000 + ParseNat[n2] * 1000000 + ParseNat[n3] * 100000 + ParseNat[n4] * 10000 + ParseNat[n5] * 1000 + ParseNat[n6] * 100 + ParseNat[n7] * 10 + ParseNat[n8]
    case unsafe.Nat.Digit9[n1, n2, n3, n4, n5, n6, n7, n8, n9] => ParseNat[n1] * 100000000 + ParseNat[n2] * 10000000 + ParseNat[n3] * 1000000 + ParseNat[n4] * 100000 + ParseNat[n5] * 10000 + ParseNat[n6] * 1000 + ParseNat[n7] * 100 + ParseNat[n8] * 10 + ParseNat[n9]
    // format: on
  }

  private[MemorySlice] object Macros {
    private final def OutOfBounds(idx: Int, limit: Int) =
      s"Refering to index out of slice bounds at index $idx, limit=$limit"
    import scala.quoted.*

    def validateAccess(idxExpr: Expr[Int], limitExpr: Expr[Int])(using
        Quotes
    ): Expr[Unit] = {
      import quotes.*
      import quotes.reflect.*
      idxExpr.value.zip(limitExpr.value) match {
        case Some(idx, limit) =>
          if idx < 0 || idx >= limit
          then report.warning(OutOfBounds(idx, limit))
        case _ =>
          '{
            if ($idxExpr < 0 || $idxExpr >= $limitExpr)
            then
              throw new IllegalArgumentException(
                OutOfBounds($idxExpr, $limitExpr)
              )
          }
      }
      '{ () }
    }
  }
}
