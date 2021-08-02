object IsFileTest {
  import Files._

  def main(args: Array[String]): Unit = {
    assert(executableFile.isFile(), "executableFile.isFile()")
    assert(unexecutableFile.isFile(), "unexecutableFile.isFile()")
    assert(readableFile.isFile(), "readableFile.isFile()")
    assert(unreadableFile.isFile(), "unreadableFile.isFile()")
    assert(writableFile.isFile(), "writableFile.isFile()")
    assert(unwritableFile.isFile(), "unwritableFile.isFile()")

    assert(!executableDirectory.isFile(), "!executableDirectory.isFile()")
    assert(!unexecutableDirectory.isFile(), "!unexecutableDirectory.isFile()")
    assert(!readableDirectory.isFile(), "!readableDirectory.isFile()")
    assert(!unreadableDirectory.isFile(), "!unreadableDirectory.isFile()")
    assert(!writableDirectory.isFile(), "!writableDirectory.isFile()")
    assert(!unwritableDirectory.isFile(), "!unwritableDirectory.isFile()")

    assert(!nonexistentFile.isFile(), "!nonexistentFile.isFile()")
    assert(!nonexistentDirectory.isFile(), "!nonexistentDirectory.isFile()")
  }
}
