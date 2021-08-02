object MkdirTest {
  import Files._

  def main(args: Array[String]): Unit = {
    assert(!willBeCreatedDirectory.exists(), "!willBeCreatedDirectory.exists()")
    assert(willBeCreatedDirectory.mkdir(), "willBeCreatedDirectory.mkdir()")
    assert(!willBeCreatedDirectory.mkdir(), "!willBeCreatedDirectory.mkdir()")
    assert(willBeCreatedDirectory.exists(), "willBeCreatedDirectory.exists()")

    assert(
      !nestedWillBeCreatedDirectory.exists(),
      "!nestedWillBeCreatedDirectory.exists()"
    )
    assert(
      nestedWillBeCreatedDirectory.mkdirs(),
      "nestedWillBeCreatedDirectory.mkdirs()"
    )
    assert(
      !nestedWillBeCreatedDirectory.mkdir(),
      "!nestedWillBeCreatedDirectory.mkdir()"
    )
    assert(
      nestedWillBeCreatedDirectory.exists(),
      "nestedWillBeCreatedDirectory.exists()"
    )
  }
}
