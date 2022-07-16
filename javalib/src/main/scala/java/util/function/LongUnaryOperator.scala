// Ported from Scala.js commit: d028054 dated: 2022-05-16

package java.util.function

import scala.scalanative.annotation.JavaDefaultMethod

@FunctionalInterface
trait LongUnaryOperator {
  def applyAsLong(operand: Long): Long

  @JavaDefaultMethod
  def andThen(after: LongUnaryOperator): LongUnaryOperator = { (i: Long) =>
    after.applyAsLong(applyAsLong(i))
  }

  @JavaDefaultMethod
  def compose(before: LongUnaryOperator): LongUnaryOperator = { (i: Long) =>
    applyAsLong(before.applyAsLong(i))
  }
}

object LongUnaryOperator {
  def identity(): LongUnaryOperator = (i: Long) => i
}