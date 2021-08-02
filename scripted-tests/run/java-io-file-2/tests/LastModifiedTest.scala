object LastModifiedTest {
  import Files._

  def main(args: Array[String]): Unit = {
    assert(!nonexistentFile.exists(), "!nonexistentFile.exists()")
    assert(
      nonexistentFile.lastModified() == 0L,
      "nonexistentFile.lastModified() == 0L"
    )

    assert(fileWithLastModifiedSet.exists(), "fileWithLastModifiedSet.exists()")
    assert(
      fileWithLastModifiedSet.lastModified() == expectedLastModified,
      "fileWithLastModifiedSet.lastModified() == expectedLastModified"
    )
  }
}
