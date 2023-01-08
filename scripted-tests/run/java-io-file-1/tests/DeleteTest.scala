object DeleteTest {
  import Files._

  def main(args: Array[String]): Unit = {
    assert("exists", willBeDeletedFile.exists())
    assert("delete", willBeDeletedFile.delete())
    assert("check deleted", !willBeDeletedFile.exists())

    assert("dir exists", willBeDeletedDirectory.exists())
    assert("dir deleted", willBeDeletedDirectory.delete())
    assert("check dir deleted", !willBeDeletedDirectory.exists())
  }
}
