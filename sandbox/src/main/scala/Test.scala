object Test {
  def main(args: Array[String]): Unit = {
    println("Hello, World 2!")
    println(scala.scalanative.meta.LinktimeInfo.isMultithreadingEnabled)
    0.until(10).foreach(println)
    Thread.ofPlatform().start(new Runnable {
      override def run(): Unit = {
        println("Hello, World 3!")
      }
    })
  }
}
