object CanReadTest {
  import Files._

  def main(args: Array[String]): Unit = {
    // setReadable cannot be used on Windows
    def osSpecific(pred: Boolean) = if (isWindows) !pred else pred

    assert(!emptyNameFile.canRead())

    assert(readableFile.canRead())
    assert(osSpecific(!unreadableFile.canRead()))
    assert(!nonexistentFile.canRead())

    assert(readableDirectory.canRead())
    assert(osSpecific(!unreadableDirectory.canRead()))
    assert(!nonexistentDirectory.canRead())

  }

}
