// Ported from Scala.js commit: d028054 dated: 2022-05-16

package java.util.function

import scala.scalanative.annotation.JavaDefaultMethod

@FunctionalInterface
trait LongBinaryOperator {
  def applyAsLong(left: Long, right: Long): Long
}
