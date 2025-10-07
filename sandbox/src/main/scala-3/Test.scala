@main def Test = {
  println("Hello, World!")
  val x = IArray(3, 5, 7)
  x.foreach(println)
  
  given Conversion[Int, String] = _.toString()
  println(summon[Conversion[Int, String]](42))
  println(scala.runtime.Tuples.toIArray(("1", 1, 1d, 1f)).toList)
}
