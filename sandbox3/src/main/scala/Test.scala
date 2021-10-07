
// @main()
// def helloWorld(): Unit = 
//   helloWorldImpl("annotated entry")
  
object App {
  def main(args: Array[String]): Unit = 
    helloWorldImpl("main module")
}

def helloWorldImpl(from: String) =
  // val ns = for 
  //   n <- 10 until 0 by -1
  //   _ = println(n)
  // yield n
  // println(s"Hello world from $from")
  print("hello world from ")
  println(from)
end helloWorldImpl