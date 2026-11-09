package text

import core._

trait Named {
  def name: String
  def greeting: String = "hello " + name
}

class Labelled(label: String) extends Base with Right with Named {
  override def value: Int = label.length
  def right: Int = 20
  def name: String = label
}

class TextException(msg: String) extends CoreException("text: " + msg)

object Text {
  Registry.register("text")

  @noinline def greet(n: Named): String = n.greeting
  @noinline def isNamed(x: Any): Boolean = x.isInstanceOf[Named]
  @noinline def parse(s: String): Int =
    try s.toInt
    catch { case _: NumberFormatException => throw new TextException(s) }
}
