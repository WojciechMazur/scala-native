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
    Array(0, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384)
      .foreach(testRefArrayAlloc)
    println("done, test exceptions")
    val ex = new RuntimeException("foo")
    println(s"ex=$ex")
    try throw ex
    catch {case ex: Throwable => println(ex)}
    println("all done")
  }
}
