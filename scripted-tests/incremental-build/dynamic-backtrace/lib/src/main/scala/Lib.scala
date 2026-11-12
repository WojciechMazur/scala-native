object Lib {
  @noinline def trace(): List[String] =
    new Error("test").getStackTrace().toList.map(_.toString)
}
