object SetLastModifiedTest {
  import Files._

  def main(args: Array[String]): Unit = {
    assert(!nonexistentFile.exists(), "!nonexistentFile.exists()")
    assert(
      !nonexistentFile.setLastModified(10000),
      "!nonexistentFile.setLastModified(10000)"
    )

    assert(willBeSetLastModified.exists(), "willBeSetLastModified.exists()")
    assert(
      willBeSetLastModified.setLastModified(expectedLastModified),
      "willBeSetLastModified.setLastModified(expectedLastModified)"
    )
    assert(
      willBeSetLastModified.lastModified == expectedLastModified,
      "willBeSetLastModified.lastModified == expectedLastModified"
    )
  }
}
