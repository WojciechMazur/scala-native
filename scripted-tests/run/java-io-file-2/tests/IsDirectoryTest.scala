object IsDirectoryTest {
  import Files._

  def main(args: Array[String]): Unit = {
    assert(!executableFile.isDirectory(), "!executableFile.isDirectory()")
    assert(!unexecutableFile.isDirectory(), "!unexecutableFile.isDirectory()")
    assert(!readableFile.isDirectory(), "!readableFile.isDirectory()")
    assert(!unreadableFile.isDirectory(), "!unreadableFile.isDirectory()")
    assert(!writableFile.isDirectory(), "!writableFile.isDirectory()")
    assert(!unwritableFile.isDirectory(), "!unwritableFile.isDirectory()")

    assert(
      executableDirectory.isDirectory(),
      "executableDirectory.isDirectory()"
    )
    assert(
      unexecutableDirectory.isDirectory(),
      "unexecutableDirectory.isDirectory()"
    )
    assert(readableDirectory.isDirectory(), "readableDirectory.isDirectory()")
    assert(
      unreadableDirectory.isDirectory(),
      "unreadableDirectory.isDirectory()"
    )
    assert(writableDirectory.isDirectory(), "writableDirectory.isDirectory()")
    assert(
      unwritableDirectory.isDirectory(),
      "unwritableDirectory.isDirectory()"
    )

    assert(!nonexistentFile.isDirectory(), "!nonexistentFile.isDirectory()")
    assert(
      !nonexistentDirectory.isDirectory(),
      "!nonexistentDirectory.isDirectory()"
    )
  }
}
