object RenamedToTest {
  import Files._

  def main(args: Array[String]): Unit = {
    assert(willBeRenamedFrom.exists, "willBeRenamedFrom.exists")
    assert(!willBeRenamedTo.exists, "!willBeRenamedTo.exists")
    assert(
      willBeRenamedFrom.renameTo(willBeRenamedTo),
      "willBeRenamedFrom.renameTo(willBeRenamedTo)"
    )
    assert(!willBeRenamedFrom.exists, "!willBeRenamedFrom.exists")
    assert(willBeRenamedTo.exists, "willBeRenamedTo.exists")
  }
}
