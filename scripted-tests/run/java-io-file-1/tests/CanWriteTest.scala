object CanWriteTest {
  import Files._

  def main(args: Array[String]): Unit = {
    // setWritable cannot be used on Windows
    def osSpecific(pred: Boolean) = if (isWindows) !pred else pred

    assert(!emptyNameFile.canWrite())

    assert(writableFile.canWrite())
    assert(osSpecific(!unwritableFile.canWrite()))
    assert(!nonexistentFile.canWrite())

    assert(writableDirectory.canWrite())
    assert(osSpecific(!unwritableDirectory.canWrite()))
    assert(!nonexistentDirectory.canWrite())
  }

}
