import core._
import geometry._

class BigSquare(side: Int) extends Square(side) {
  override def area: Int = super.area * 10
  override def name: String = "big"
}

class OnlyLeft extends Left {
  def left: Int = 7
}
