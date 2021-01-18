package scala.scalanative.unsafe

import org.junit.Assert._
import org.junit.Test

object CStructTest {
  case class ScalaPoint(var x: Int, var y: Int)
  case class Point(var x: Int, var y: Int) extends CStruct {
    def distanceTo(other: Point): Int = {
      Math.sqrt(Math.pow(x - other.x, 2) + Math.pow(y - other.y, 2)).toInt
    }
  }
  case class Line(from: Point, to: Point) extends CStruct {
    def length: Int = from.distanceTo(to)
  }
}

class CStructTest {
  import CStructTest._

  @Test
  def allocateWithNew(): Unit = {
    val point = new Point(0, 0)
    assertEquals(0, point.x)
    assertEquals(0, point.y)

    point.x = 10
    assertEquals(10, point.x)
    assertEquals(0, point.y)

    point.y = 20
    assertEquals(10, point.x)
    assertEquals(20, point.y)
  }

  @Test
  def allocateWithStackalloc(): Unit = {
    val point = !stackalloc[Point]
    assertEquals(0, point.x)
    assertEquals(0, point.y)

    point.x = 10
    assertEquals(10, point.x)
    assertEquals(0, point.y)

    point.y = 20
    assertEquals(10, point.x)
    assertEquals(20, point.y)
  }

  @Test
  def callsEquals(): Unit = {
    val p1 = Point(10, 20)
    val p2 = Point(10, 20)
    assertTrue(p1 == p2)
    assertTrue(p1.equals(p2))
  }

  @Test
  def callToString(): Unit = {
    val p = Point(10, 20)
    assertEquals("Point(10,20)", p.toString)
  }

  @Test
  def callHashCode: Unit = {
    val p1 = ScalaPoint(10, 20)
    val p2 = Point(10, 20)
    assertEquals(p1.hashCode, p2.hashCode)
  }

  @Test
  def callCustomMethod(): Unit = {
    val p1 = Point(0, 0)
    val p2 = Point(3, 4)
    assertEquals(5, p1.distanceTo(p2))
  }

  @Test
  def allowsNestedStructs(): Unit = {
    val line = Line(Point(0, 0), Point(0, 10))
    assertEquals(10, line.length)
    line.from.x = 3
    line.from.y = 4
    assertEquals("Line(Point(3,4),Point(0,10))", line.toString)
    assertEquals(6, line.length)
  }
}
