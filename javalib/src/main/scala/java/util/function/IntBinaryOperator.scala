// Ported from Scala.js commit: d028054 dated: 2022-05-16

package java.util.function

@FunctionalInterface
trait IntBinaryOperator {
  def applyAsInt(left: Int, right: Int): Int
}