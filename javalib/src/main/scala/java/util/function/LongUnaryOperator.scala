// Ported from Scala.js commit: d028054 dated: 2022-05-16

package java.util.function

@FunctionalInterface
trait LongUnaryOperator {
  def applyAsLong(operand: Long): Long

  def andThen(after: LongUnaryOperator): LongUnaryOperator = { (i: Long) =>
    after.applyAsLong(applyAsLong(i))
  }

  def compose(before: LongUnaryOperator): LongUnaryOperator = { (i: Long) =>
    applyAsLong(before.applyAsLong(i))
  }
}

object LongUnaryOperator {
  def identity(): LongUnaryOperator = (i: Long) => i
}
