package scala.scalanative
package unsigned

import scala.runtime.BoxesRunTime._
import scala.reflect.ClassTag

import scalanative.annotation.alwaysinline

import scalanative.runtime._
import scalanative.runtime.Intrinsics._
import scalanative.runtime.Boxes._
import unsafe._

import java.lang.{Long => JLong}

final class UWord(private[scalanative] val rawWord: RawWord) {
  @alwaysinline def toByte: Byte        = castRawWordToInt(rawWord).toByte
  @alwaysinline def toChar: Char        = castRawWordToInt(rawWord).toChar
  @alwaysinline def toShort: Short      = castRawWordToInt(rawWord).toShort
  @alwaysinline def toInt: Int          = castRawWordToInt(rawWord)
  @alwaysinline def toLong: Long        = castRawWordToLong(rawWord)
  @alwaysinline def toWord: unsafe.Word = new unsafe.Word(rawWord)

  @alwaysinline def toUByte: UByte   = toByte.toUByte
  @alwaysinline def toUShort: UShort = toShort.toUShort
  @alwaysinline def toUInt: UInt     = toInt.toUInt
  @alwaysinline def toULong: ULong   = toLong.toULong

  override def hashCode: Int = java.lang.Long.hashCode(toLong)

  override def equals(other: Any): Boolean =
    (this eq other.asInstanceOf[AnyRef]) || (other match {
      case other: UWord =>
        other.rawWord == rawWord
      case _ =>
        false
    })

  override def toString(): String = JLong.toUnsignedString(toLong)

  /**
   * Returns the bitwise negation of this value.
   * @example {{{
   * ~5 == 4294967290
   * // in binary: ~00000101 ==
   * //             11111010
   * }}}
   */
  @alwaysinline def unary_~ : UWord =
    (~toLong).toUWord // TODO(shadaj): intrinsify

  /**
   * Returns this value bit-shifted left by the specified number of bits,
   *         filling in the new right bits with zeroes.
   * @example {{{ 6 << 3 == 48 // in binary: 0110 << 3 == 0110000 }}}
   */
  @alwaysinline def <<(x: Int): UWord =
    (toLong << x).toUWord // TODO(shadaj): intrinsify

  /**
   * Returns this value bit-shifted left by the specified number of bits,
   *         filling in the new right bits with zeroes.
   * @example {{{ 6 << 3 == 48 // in binary: 0110 << 3 == 0110000 }}}
   */
  @alwaysinline def <<(x: Long): UWord =
    (toLong << x).toUWord // TODO(shadaj): intrinsify

  /**
   * Returns this value bit-shifted right by the specified number of bits,
   *         filling the new left bits with zeroes.
   * @example {{{ 21 >>> 3 == 2 // in binary: 010101 >>> 3 == 010 }}}
   * @example {{{
   * 4294967275 >>> 3 == 536870909
   * // in binary: 11111111 11111111 11111111 11101011 >>> 3 ==
   * //            00011111 11111111 11111111 11111101
   * }}}
   */
  @alwaysinline def >>>(x: Int): UWord =
    (toLong >>> x).toUWord // TODO(shadaj): intrinsify

  /**
   * Returns this value bit-shifted right by the specified number of bits,
   *         filling the new left bits with zeroes.
   * @example {{{ 21 >>> 3 == 2 // in binary: 010101 >>> 3 == 010 }}}
   * @example {{{
   * 4294967275 >>> 3 == 536870909
   * // in binary: 11111111 11111111 11111111 11101011 >>> 3 ==
   * //            00011111 11111111 11111111 11111101
   * }}}
   */
  @alwaysinline def >>>(x: Long): UWord =
    (toLong >>> x).toUWord // TODO(shadaj): intrinsify

  /**
   * Returns this value bit-shifted left by the specified number of bits,
   *         filling in the right bits with the same value as the left-most bit of this.
   * @example {{{
   * 4294967275 >> 3 == 4294967293
   * // in binary: 11111111 11111111 11111111 11101011 >> 3 ==
   * //            11111111 11111111 11111111 11111101
   * }}}
   */
  @inline final def >>(x: Int): UWord = (toLong >> x).toUWord

  /**
   * Returns this value bit-shifted left by the specified number of bits,
   *         filling in the right bits with the same value as the left-most bit of this.
   * @example {{{
   * 4294967275 >> 3 == 4294967293
   * // in binary: 11111111 11111111 11111111 11101011 >> 3 ==
   * //            11111111 11111111 11111111 11111101
   * }}}
   */
  @inline final def >>(x: Long): UWord = (toLong >> x).toUWord

  /** Returns `true` if this value is equal to x, `false` otherwise. */
  @alwaysinline def ==(x: UByte): Boolean = this == x.toUWord

  /** Returns `true` if this value is equal to x, `false` otherwise. */
  @alwaysinline def ==(x: UShort): Boolean = this == x.toUWord

  /** Returns `true` if this value is equal to x, `false` otherwise. */
  @alwaysinline def ==(x: UInt): Boolean = this == x.toUWord

  /** Returns `true` if this value is equal to x, `false` otherwise. */
  @alwaysinline def ==(x: ULong): Boolean = this.toULong == x

  /** Returns `true` if this value is equal to x, `false` otherwise. */
  @alwaysinline def ==(other: UWord): Boolean =
    this.toULong == other.toULong // TODO(shadaj): intrinsify

  /** Returns `true` if this value is not equal to x, `false` otherwise. */
  @alwaysinline def !=(x: UByte): Boolean = this != x.toUWord

  /** Returns `true` if this value is not equal to x, `false` otherwise. */
  @alwaysinline def !=(x: UShort): Boolean = this != x.toUWord

  /** Returns `true` if this value is not equal to x, `false` otherwise. */
  @alwaysinline def !=(x: UInt): Boolean = this != x.toUWord

  /** Returns `true` if this value is not equal to x, `false` otherwise. */
  @alwaysinline def !=(x: ULong): Boolean = this.toULong != x

  /** Returns `true` if this value is not equal to x, `false` otherwise. */
  @alwaysinline def !=(other: UWord): Boolean =
    this.toULong != other.toULong // TODO(shadaj): intrinsify

  /** Returns `true` if this value is less than x, `false` otherwise. */
  @alwaysinline def <(x: UByte): Boolean = this < x.toUWord

  /** Returns `true` if this value is less than x, `false` otherwise. */
  @alwaysinline def <(x: UShort): Boolean = this < x.toUWord

  /** Returns `true` if this value is less than x, `false` otherwise. */
  @alwaysinline def <(x: UInt): Boolean = this < x.toUWord

  /** Returns `true` if this value is less than x, `false` otherwise. */
  @alwaysinline def <(x: ULong): Boolean = this.toULong < x

  /** Returns `true` if this value is less than x, `false` otherwise. */
  @alwaysinline def <(other: UWord): Boolean =
    this.toULong < other.toULong // TODO(shadaj): intrinsify

  /** Returns `true` if this value is less than or equal to x, `false` otherwise. */
  @alwaysinline def <=(x: UByte): Boolean = this <= x.toUWord

  /** Returns `true` if this value is less than or equal to x, `false` otherwise. */
  @alwaysinline def <=(x: UShort): Boolean = this <= x.toUWord

  /** Returns `true` if this value is less than or equal to x, `false` otherwise. */
  @alwaysinline def <=(x: UInt): Boolean = this <= x.toUWord

  /** Returns `true` if this value is less than or equal to x, `false` otherwise. */
  @alwaysinline def <=(x: ULong): Boolean = this.toULong <= x

  /** Returns `true` if this value is less than or equal to x, `false` otherwise. */
  @alwaysinline def <=(other: UWord): Boolean =
    this.toULong <= other.toULong // TODO(shadaj): intrinsify

  /** Returns `true` if this value is greater than x, `false` otherwise. */
  @alwaysinline def >(x: UByte): Boolean = this > x.toUWord

  /** Returns `true` if this value is greater than x, `false` otherwise. */
  @alwaysinline def >(x: UShort): Boolean = this > x.toUWord

  /** Returns `true` if this value is greater than x, `false` otherwise. */
  @alwaysinline def >(x: UInt): Boolean = this > x.toUWord

  /** Returns `true` if this value is greater than x, `false` otherwise. */
  @alwaysinline def >(x: ULong): Boolean = this.toULong > x

  /** Returns `true` if this value is greater than x, `false` otherwise. */
  @alwaysinline def >(other: UWord): Boolean =
    this.toULong > other.toULong // TODO(shadaj): intrinsify

  /** Returns `true` if this value is greater than or equal to x, `false` otherwise. */
  @alwaysinline def >=(x: UByte): Boolean = this >= x.toUWord

  /** Returns `true` if this value is greater than or equal to x, `false` otherwise. */
  @alwaysinline def >=(x: UShort): Boolean = this >= x.toUWord

  /** Returns `true` if this value is greater than or equal to x, `false` otherwise. */
  @alwaysinline def >=(x: UInt): Boolean = this >= x.toUWord

  /** Returns `true` if this value is greater than or equal to x, `false` otherwise. */
  @alwaysinline def >=(x: ULong): Boolean = this.toULong >= x

  /** Returns `true` if this value is greater than or equal to x, `false` otherwise. */
  @alwaysinline def >=(other: UWord): Boolean =
    this.toULong >= other.toULong // TODO(shadaj): intrinsify

  /** Returns the bitwise AND of this value and `x`. */
  @alwaysinline def &(x: UByte): UWord = this & x.toUWord

  /** Returns the bitwise AND of this value and `x`. */
  @alwaysinline def &(x: UShort): UWord = this & x.toUWord

  /** Returns the bitwise AND of this value and `x`. */
  @alwaysinline def &(x: UInt): UWord = this & x.toUWord

  /** Returns the bitwise AND of this value and `x`. */
  @alwaysinline def &(x: ULong): ULong = this.toULong & x

  /** Returns the bitwise AND of this value and `x`. */
  @alwaysinline def &(other: UWord): UWord =
    new UWord(andRawWords(rawWord, other.rawWord))

  /** Returns the bitwise OR of this value and `x`. */
  @alwaysinline def |(x: UByte): UWord = this | x.toUWord

  /** Returns the bitwise OR of this value and `x`. */
  @alwaysinline def |(x: UShort): UWord = this | x.toUWord

  /** Returns the bitwise OR of this value and `x`. */
  @alwaysinline def |(x: UInt): UWord = this | x.toUWord

  /** Returns the bitwise OR of this value and `x`. */
  @alwaysinline def |(x: ULong): ULong = this.toULong | x

  /** Returns the bitwise OR of this value and `x`. */
  @alwaysinline def |(other: UWord): UWord =
    new UWord(orRawWords(rawWord, other.rawWord))

  /** Returns the bitwise XOR of this value and `x`. */
  @alwaysinline def ^(x: UByte): UWord = this ^ x.toUWord

  /** Returns the bitwise XOR of this value and `x`. */
  @alwaysinline def ^(x: UShort): UWord = this ^ x.toUWord

  /** Returns the bitwise XOR of this value and `x`. */
  @alwaysinline def ^(x: UInt): UWord = this ^ x.toUWord

  /** Returns the bitwise XOR of this value and `x`. */
  @alwaysinline def ^(x: ULong): ULong = this.toULong ^ x

  /** Returns the bitwise XOR of this value and `x`. */
  @alwaysinline def ^(other: UWord): UWord =
    new UWord(xorRawWords(rawWord, other.rawWord))

  /** Returns the sum of this value and `x`. */
  @alwaysinline def +(x: UByte): UWord = this + x.toUWord

  /** Returns the sum of this value and `x`. */
  @alwaysinline def +(x: UShort): UWord = this + x.toUWord

  /** Returns the sum of this value and `x`. */
  @alwaysinline def +(x: UInt): UWord = this + x.toUWord

  /** Returns the sum of this value and `x`. */
  @alwaysinline def +(x: ULong): ULong = this.toULong + x

  /** Returns the sum of this value and `x`. */
  @alwaysinline def +(other: UWord): UWord =
    new UWord(addRawWords(rawWord, other.rawWord))

  /** Returns the difference of this value and `x`. */
  @alwaysinline def -(x: UByte): UWord = this - x.toUWord

  /** Returns the difference of this value and `x`. */
  @alwaysinline def -(x: UShort): UWord = this - x.toUWord

  /** Returns the difference of this value and `x`. */
  @alwaysinline def -(x: UInt): UWord = this - x.toUWord

  /** Returns the difference of this value and `x`. */
  @alwaysinline def -(x: ULong): ULong = this.toULong - x

  /** Returns the difference of this value and `x`. */
  @alwaysinline def -(other: UWord): UWord =
    new UWord(subRawWords(rawWord, other.rawWord))

  /** Returns the product of this value and `x`. */
  @alwaysinline def *(x: UByte): UWord = this * x.toUWord

  /** Returns the product of this value and `x`. */
  @alwaysinline def *(x: UShort): UWord = this * x.toUWord

  /** Returns the product of this value and `x`. */
  @alwaysinline def *(x: UInt): UWord = this * x.toUWord

  /** Returns the product of this value and `x`. */
  @alwaysinline def *(x: ULong): ULong = this.toULong * x

  /** Returns the product of this value and `x`. */
  @alwaysinline def *(other: UWord): UWord =
    new UWord(multRawWords(rawWord, other.rawWord))

  /** Returns the quotient of this value and `x`. */
  @alwaysinline def /(x: UByte): UWord = this / x.toUWord

  /** Returns the quotient of this value and `x`. */
  @alwaysinline def /(x: UShort): UWord = this / x.toUWord

  /** Returns the quotient of this value and `x`. */
  @alwaysinline def /(x: UInt): UWord = this / x.toUWord

  /** Returns the quotient of this value and `x`. */
  @alwaysinline def /(x: ULong): ULong = this.toULong / x

  /** Returns the quotient of this value and `x`. */
  @alwaysinline def /(other: UWord): UWord =
    new UWord(divRawWords(rawWord, other.rawWord))

  /** Returns the remainder of the division of this value by `x`. */
  @alwaysinline def %(x: UByte): UWord = this % x.toUWord

  /** Returns the remainder of the division of this value by `x`. */
  @alwaysinline def %(x: UShort): UWord = this % x.toUWord

  /** Returns the remainder of the division of this value by `x`. */
  @alwaysinline def %(x: UInt): UWord = this % x.toUWord

  /** Returns the remainder of the division of this value by `x`. */
  @alwaysinline def %(x: ULong): ULong = this.toULong % x

  /** Returns the remainder of the division of this value by `x`. */
  @alwaysinline def %(other: UWord): UWord =
    new UWord(modRawWords(rawWord, other.rawWord))

}

object UWord {
  @alwaysinline implicit def byteToWord(x: Byte): UWord =
    new UWord(castIntToRawWord(x.toInt))
  @alwaysinline implicit def charToWord(x: Char): UWord =
    new UWord(castIntToRawWord(x.toInt))
  @alwaysinline implicit def ushortToWord(x: UShort): UWord =
    new UWord(castIntToRawWord(x.toInt))
  @alwaysinline implicit def uintToWord(x: UInt): UWord =
    new UWord(castIntToRawWord(x.toInt))
}
