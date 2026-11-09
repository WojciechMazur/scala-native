package core

trait Left {
  def left: Int
}
trait Right { def right: Int }
trait Visitor { def visit(b: Base): Int }

class Base {
  def value: Int = 1
  def describe: String = "base:" + value
}

abstract class Figure {
  def area: Int
  def name: String = "figure"
}

sealed abstract class Token { def text: String }
final class Word(val text: String) extends Token
final class Numeral(n: Int) extends Token { def text: String = n.toString }

class CoreException(msg: String) extends RuntimeException(msg)

object Registry {
  private var entries: List[String] = Nil
  def register(name: String): Unit = entries = name :: entries
  def registered: List[String] = entries.reverse
}

object Core {
  @noinline def value(x: Base): Int = x.value
  @noinline def left(x: Left): Int = x.left
  @noinline def right(x: Right): Int = x.right
  @noinline def area(x: Figure): Int = x.area
  @noinline def isBase(x: Any): Boolean = x.isInstanceOf[Base]
  @noinline def isFigure(x: Any): Boolean = x.isInstanceOf[Figure]
  @noinline def isLeft(x: Any): Boolean = x.isInstanceOf[Left]
  @noinline def isRight(x: Any): Boolean = x.isInstanceOf[Right]
  @noinline def asLeft(x: Any): Left = x.asInstanceOf[Left]
  @noinline def accept(v: Visitor, b: Base): Int = v.visit(b)
  @noinline def tokens(ts: Seq[Token]): String = ts.map(_.text).mkString(",")
  @noinline def fail(msg: String): Nothing = throw new CoreException(msg)
}
