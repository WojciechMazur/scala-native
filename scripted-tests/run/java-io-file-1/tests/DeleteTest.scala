object DeleteTest {
  import Files._

  def main(args: Array[String]): Unit = {
    assert(willBeDeletedFile.exists(), "exists")
    assert(willBeDeletedFile.delete(), "delete")
    assert(!willBeDeletedFile.exists(), "check deleted")

    assert(willBeDeletedDirectory.exists(), "dir exists")
    assert(willBeDeletedDirectory.delete(), "dir deleted")
    assert(!willBeDeletedDirectory.exists(), "check dir deleted")
  }
}
