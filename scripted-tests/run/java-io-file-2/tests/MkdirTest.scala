object MkdirTest {
  import Files._

  def main(args: Array[String]): Unit = {
    assert(!willBeCreatedDirectory.exists(), "not exists")
    assert(willBeCreatedDirectory.mkdir(), "mkdir")
    assert(!willBeCreatedDirectory.mkdir(), "mkdir repeat")
    assert(willBeCreatedDirectory.exists(), "check exists")

    assert(!nestedWillBeCreatedDirectory.exists(), "nested !exists")
    assert(nestedWillBeCreatedDirectory.mkdirs(), "nested mkdir")
    assert(!nestedWillBeCreatedDirectory.mkdir(), "nested repeat mkdir")
    assert(nestedWillBeCreatedDirectory.exists(), "check nested")
  }
}
