import scala.scalanative.annotation.allocationHint._
import scala.scalanative.unsafe.Zone

object Test {
  case class Foo(v: String, next: Option[Foo] = None)
  def main(args: Array[String]): Unit = {
    @stack val x = new Foo("foo")
    println(x.getClass())
    @stack val x2 = new Foo("bar", Some(x))
    println(x2.getClass())
    @gc val x3 = new Foo("baz", None)
    println(x3.getClass())
    locally{
      import scala.scalanative.runtime._
      println(fromRawPtr(Intrinsics.castObjectToRawPtr(x)))
      println(fromRawPtr(Intrinsics.castObjectToRawPtr(x2)))
      println(fromRawPtr(Intrinsics.castObjectToRawPtr(x3)))
    }
    println(x)
    println(x2)
    @gc val y = new {override def toString(): String = "2"}
    println(y)
    Zone{implicit zone: Zone =>
      @zone val z = new {override def toString(): String = "3"}
      foo(new {}: @stack)
    }
    // @stack val arr = new Array[Int](4) // TODO
    
    println(new String("string with multiple"): @stack)
    println(new {}: @stack)
    { val x = new {}; val y = new {}}: @stack
  }

  def foo(arg: Any)(using outer: Zone): Unit = {
    val x = Integer.valueOf(1)
    locally {
      val y = Integer.valueOf(2)
    }
  }: @zone
}
