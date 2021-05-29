package scala.runtime

import scala.math.ScalaNumber
import scala.scalanative.unsafe._
import scala.annotation.switch

class BoxesRunTime

object BoxesRunTime {
  def boxToBoolean(v: scala.Boolean): java.lang.Boolean =
    java.lang.Boolean.valueOf(v)
  def boxToCharacter(v: scala.Char): java.lang.Character =
    java.lang.Character.valueOf(v)
  def boxToByte(v: scala.Byte): java.lang.Byte =
    java.lang.Byte.valueOf(v)
  def boxToShort(v: scala.Short): java.lang.Short =
    java.lang.Short.valueOf(v)
  def boxToInteger(v: scala.Int): java.lang.Integer =
    java.lang.Integer.valueOf(v)
  def boxToLong(v: scala.Long): java.lang.Long =
    java.lang.Long.valueOf(v)
  def boxToFloat(v: scala.Float): java.lang.Float =
    java.lang.Float.valueOf(v)
  def boxToDouble(v: scala.Double): java.lang.Double =
    java.lang.Double.valueOf(v)

  def unboxToBoolean(o: java.lang.Object): scala.Boolean =
    if (o == null) false else o.asInstanceOf[java.lang.Boolean].booleanValue
  def unboxToChar(o: java.lang.Object): scala.Char =
    if (o == null) 0 else o.asInstanceOf[java.lang.Character].charValue
  def unboxToByte(o: java.lang.Object): scala.Byte =
    if (o == null) 0 else o.asInstanceOf[java.lang.Byte].byteValue
  def unboxToShort(o: java.lang.Object): scala.Short =
    if (o == null) 0 else o.asInstanceOf[java.lang.Short].shortValue
  def unboxToInt(o: java.lang.Object): scala.Int =
    if (o == null) 0 else o.asInstanceOf[java.lang.Integer].intValue
  def unboxToLong(o: java.lang.Object): scala.Long =
    if (o == null) 0 else o.asInstanceOf[java.lang.Long].longValue
  def unboxToFloat(o: java.lang.Object): scala.Float =
    if (o == null) 0 else o.asInstanceOf[java.lang.Float].floatValue
  def unboxToDouble(o: java.lang.Object): scala.Double =
    if (o == null) 0 else o.asInstanceOf[java.lang.Double].doubleValue

  // Intrinsified as primitives. They are never called.
  def hashFromObject(o: java.lang.Object): Int = ???
  def hashFromNumber(o: java.lang.Number): Int = ???
  def hashFromFloat(o: java.lang.Float): Int   = ???
  def hashFromDouble(o: java.lang.Double): Int = ???
  def hashFromLong(o: java.lang.Long): Int     = ???

  def equals(x: Any, y: Any): Boolean = {
    (x, y) match {
      case (x: AnyRef, y: AnyRef) => x eq y
      case _                      => equals2(x, y)
    }
  }

  /** Since all applicable logic has to be present in the equals method of a ScalaNumber
   * in any case, we dispatch to it as soon as we spot one on either side.
   */
  def equals2(x: Any, y: Any): Boolean = {
    x match {
      case number: Number  => equalsNumObject(number, y)
      case char: Character => equalsCharObject(char, y)
      case _ =>
        if (x == null) y == null
        else x == y
    }
  }

  def equalsNumObject(xn: Number, y: Any): Boolean = {
    y match {
      case number: Number       => equalsNumNum(xn, number)
      case character: Character => equalsNumChar(xn, character)
      case _ =>
        if (xn == null) y == null
        else xn == y
    }
  }

  private final val CHAR = 0
  /* BYTE = 1, SHORT = 2, */
  private final val INT    = 3
  private final val LONG   = 4
  private final val FLOAT  = 5
  private final val DOUBLE = 6
  private final val OTHER  = 7

  /** We don't need to return BYTE and SHORT, as everything which might
   * care widens to INT.
   */
  private def typeCode(a: Any): Int = {
    a match {
      case _: Integer         => INT
      case _: Double          => DOUBLE
      case _: Long            => LONG
      case _: Character       => CHAR
      case _: Float           => FLOAT
      case _: Byte | _: Short => INT
      case _                  => OTHER
    }
  }

  def equalsNumNum(xn: Number, yn: Number): Boolean = {
    val xcode = typeCode(xn)
    val ycode = typeCode(yn)
    ((if (ycode > xcode) ycode else xcode): @switch) match {
      case INT    => xn.intValue == yn.intValue
      case LONG   => xn.longValue == yn.longValue
      case FLOAT  => xn.floatValue == yn.floatValue
      case DOUBLE => xn.doubleValue == yn.doubleValue
      case _ =>
        if (yn.isInstanceOf[ScalaNumber] && !xn.isInstanceOf[ScalaNumber])
          yn == xn
        else if (xn == null) yn == null
        else xn == yn
    }
  }

  def equalsCharObject(xc: Character, y: Any): Boolean = {
    y match {
      case character: Character => return xc.charValue == character.charValue
      case number: Number       => equalsNumChar(number, xc)
      case _ =>
        if (xc == null) y == null
        else xc == y
    }
  }

  def equalsNumChar(xn: Number, yc: Character): Boolean = {
    if (yc == null) return xn == null
    val ch = yc.charValue
    (typeCode(xn): @switch) match {
      case INT    => xn.intValue == ch
      case LONG   => xn.longValue == ch
      case FLOAT  => xn.floatValue == ch
      case DOUBLE => xn.doubleValue == ch
      case _      => xn == yc
    }
  }
}
