import scala.scalanative.meta.LinktimeInfo.*

object Test {
  def main(args: Array[String]): Unit = {
    println("Hello, World!")
    println(s"is32=$is32BitPlatform, mt=${isMultithreadingEnabled}, triple=${target.arch}-${target.vendor}-${target.os}-${target.env}")
  }
}
