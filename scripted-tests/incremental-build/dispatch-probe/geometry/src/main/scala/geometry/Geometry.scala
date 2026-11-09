package geometry

import core._

trait Scalable { def scale(k: Int): Figure }

class Square(val side: Int) extends Figure with Scalable {
  def area: Int = side * side
  override def name: String = "square"
  def scale(k: Int): Figure = new Square(side * k)
}

class Derived extends Base with Left {
  override def value: Int = 42
  def left: Int = 10
}

object Geometry {
  Registry.register("geometry")

  @noinline def scaledArea(s: Scalable, k: Int): Int = Core.area(s.scale(k))
  @noinline def isScalable(x: Any): Boolean = x.isInstanceOf[Scalable]
  @noinline def describeAll(xs: Seq[Base]): String =
    xs.map(_.describe).mkString(";")
}
