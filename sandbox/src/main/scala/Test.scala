object Test {
  def main(args: Array[String]): Unit = {
    println("Hello, World!")
    val arr: Array[AnyRef] = Array.fill(16388)(new {}) 
    val t = new Thread(() => {System.gc(); println(arr)} )
    println(arr.size)
    t.start()
    t.join()
    println(arr)

  }
}
