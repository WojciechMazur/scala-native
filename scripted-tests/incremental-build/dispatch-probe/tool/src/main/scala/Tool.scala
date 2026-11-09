import core._
import geometry._

object Probe extends Base {
  override def value: Int = 5
}

object Tool {
  def check(cond: Boolean, what: String): Unit =
    if (!cond) throw new AssertionError(what)

  def main(args: Array[String]): Unit = {
    check(Core.value(Probe) == 5, "module overriding a library class")
    check(Core.value(new Derived) == 42, "library subclass in another program")
    check(Geometry.scaledArea(new Square(2), 3) == 36, "library dispatch")
    check(Core.isBase(Probe) && !Core.isLeft(Probe), "type tests")
    check(Registry.registered == List("geometry"), "only geometry initialized")
    println("DISPATCH_PROBE_TOOL_OK " + args.mkString(" "))
  }
}
