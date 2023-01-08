object MkdirTest {
  import Files._

  def main(args: Array[String]): Unit = {
    assert("not exists", !willBeCreatedDirectory.exists())
    assert("mkdir", willBeCreatedDirectory.mkdir())
    assert("mkdir repeat", !willBeCreatedDirectory.mkdir())
    assert("check exists", willBeCreatedDirectory.exists())

    assert("nested !exists", !nestedWillBeCreatedDirectory.exists())
    assert("nested mkdir", nestedWillBeCreatedDirectory.mkdirs())
    assert("nested repeat mkdir", !nestedWillBeCreatedDirectory.mkdir())
    assert("check nested", nestedWillBeCreatedDirectory.exists())
  }
}
