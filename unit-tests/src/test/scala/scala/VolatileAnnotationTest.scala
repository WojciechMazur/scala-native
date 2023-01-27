package scala

import org.junit.Test
import org.junit.Assert._

class VolatileAnnotationTest {
  private val initialBool = true
  private val initialByte = 42.toByte
  private val initialShort = 43.toShort
  private val initialInt = 44
  private val initialLong = 45L
  private val initialFloat = 1.0f
  private val initialDouble = 2.0f
  private val initialRef: Object = new Object()

  @volatile private var bool = initialBool
  @volatile private var byte = initialByte
  @volatile private var short = initialShort
  @volatile private var int = initialInt
  @volatile private var long = initialLong
  @volatile private var float = initialLong
  @volatile private var double = initialDouble
  @volatile private var ref: Object = initialRef

  @Test def canLoadVolatileFields(): Unit = {
    assertEquals(initialBool, bool)
    assertEquals(initialByte, byte)
    assertEquals(initialShort, short)
    assertEquals(initialInt, int)
    assertEquals(initialLong, long)
    assertEquals(initialFloat, float)
    assertEquals(initialDouble, double)
    assertEquals(initialRef, ref)
  }

  @Test def canMutateVolatileFields(): Unit = {
    val newBool = true
    val newByte = (initialByte * 2).toByte
    val newShort = (initialShort * 2).toShort
    val newInt = initialInt * 2
    val newLong = initialLong * 2
    val newFloat = initialFloat * 2
    val newDouble = initialDouble * 2
    val newRef: Object = new Object()

    bool = newBool
    byte = newByte
    short = newShort
    int = newInt
    long = newLong
    float = newLong
    double = newDouble
    ref = newRef

    assertEquals(newBool, bool)
    assertEquals(newByte, byte)
    assertEquals(newShort, short)
    assertEquals(newInt, int)
    assertEquals(newLong, long)
    assertEquals(newFloat, float)
    assertEquals(newDouble, double)
    assertEquals(newRef, ref)
  }

  // Todo: Add async test checking for mutal mutation of volatile fields when multithreading would be supported

}
