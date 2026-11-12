import scala.scalanative.meta.LinktimeInfo.{isLinux, isMac}

object Main {
  def main(args: Array[String]): Unit = f()

  @noinline def f(): Unit = {
    val trace = Lib.trace()
    val expected =
      if (isLinux || isMac)
        List("Lib$.trace(Lib.scala:3)", "Main$.f(Main.scala:7)")
      else List("Lib$.trace(Unknown Source)", "Main$.f(Unknown Source)")
    val actual =
      trace.filter(e => e.startsWith("Lib$.") || e.startsWith("Main$.f"))
    assert(actual == expected, s"actual:\n${trace.mkString("\n")}")
  }
}
