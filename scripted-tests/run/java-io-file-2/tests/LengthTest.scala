object LengthTest {
  import Files._

  def main(args: Array[String]): Unit = {
    assert(fileWith3Bytes.exists(), "fileWith3Bytes.exists()")
    assert(fileWith3Bytes.length() == 3L, "fileWith3Bytes.length() == 3L")

    assert(!nonexistentFile.exists(), "!nonexistentFile.exists()")
    assert(nonexistentFile.length() == 0L, "nonexistentFile.length() == 0L")
  }
}
