object SetReadOnlyTest {
  import Files._
  def main(args: Array[String]): Unit = {
    assert(willBeSetReadOnlyFile.exists(), "willBeSetReadOnlyFile.exists()")
    assert(willBeSetReadOnlyFile.canRead(), "willBeSetReadOnlyFile.canRead()")
    assert(willBeSetReadOnlyFile.canWrite(), "willBeSetReadOnlyFile.canWrite()")
    assert(
      willBeSetReadOnlyFile.canExecute(),
      "willBeSetReadOnlyFile.canExecute()"
    )

    assert(
      willBeSetReadOnlyFile.setReadOnly(),
      "willBeSetReadOnlyFile.setReadOnly()"
    )
    assert(willBeSetReadOnlyFile.canRead(), "willBeSetReadOnlyFile.canRead()")
    assert(
      !willBeSetReadOnlyFile.canWrite(),
      "!willBeSetReadOnlyFile.canWrite()"
    )
    assert(
      willBeSetReadOnlyFile.canExecute(),
      "willBeSetReadOnlyFile.canExecute()"
    )

    assert(
      willBeSetReadOnlyDirectory.exists(),
      "willBeSetReadOnlyDirectory.exists()"
    )
    assert(
      willBeSetReadOnlyDirectory.canRead(),
      "willBeSetReadOnlyDirectory.canRead()"
    )
    assert(
      willBeSetReadOnlyDirectory.canWrite(),
      "willBeSetReadOnlyDirectory.canWrite()"
    )
    assert(
      willBeSetReadOnlyDirectory.canExecute(),
      "willBeSetReadOnlyDirectory.canExecute()"
    )

    assert(
      willBeSetReadOnlyDirectory.setReadOnly(),
      "willBeSetReadOnlyDirectory.setReadOnly()"
    )
    assert(
      willBeSetReadOnlyDirectory.canRead(),
      "willBeSetReadOnlyDirectory.canRead()"
    )
    assert(
      !willBeSetReadOnlyDirectory.canWrite(),
      "!willBeSetReadOnlyDirectory.canWrite()"
    )
    assert(
      willBeSetReadOnlyDirectory.canExecute(),
      "willBeSetReadOnlyDirectory.canExecute()"
    )

    assert(!nonexistentFile.exists, "!nonexistentFile.exists")
    assert(!nonexistentFile.setReadOnly(), "!nonexistentFile.setReadOnly()")
  }
}
