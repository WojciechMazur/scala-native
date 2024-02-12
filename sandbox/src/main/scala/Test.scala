import scala.scalanative.libc.stdatomic._
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
object Test {
  def testRefArrayAlloc(size: Int): Unit = {
    case class Elem(inner: Elem)
    val arr = Array.fill(size)(new Elem(new Elem(null)))
    println("Start GC")
    System.gc()
    println("GC done")
    locally {
      arr.zipWithIndex.foreach { case (elem, idx) =>
        assert(
          elem != null && elem.inner != null && elem.inner.inner == null,
          s"Invalid: idx=$idx, elem=$elem"
        )
      }
      println(s"checks done $size")
    }
  }

  def main(args: Array[String]): Unit = {
    println("Hello, World!")
    // Array(0, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384)
    //   .foreach(testRefArrayAlloc)
    // println("array alloc done")
    case class Foo(v: String)
    val x = new java.util.concurrent.ConcurrentHashMap[CUnsignedLong, Foo]
    println(x)
    val v1 = Foo("1")
    println(v1)
    println(x.computeIfAbsent(1.toUSize, _ => v1))
    println("first done")
    println(x.computeIfAbsent(1.toUInt, key => {
      val v = Foo(key.toString)
      println(v)
      v
    }))

    println("map done")

    val ex = new RuntimeException("foo")
    println(s"ex=$ex")
    try throw ex
    catch {case ex: Throwable => println(ex)}
    println("all done")
  }
}
