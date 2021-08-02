object SetWritableTest {
  import Files._
  import Utils._
  def main(args: Array[String]): Unit = {
    assert(willBeSetWritableFile.exists(), "willBeSetWritableFile.exists()")
    assertOsSpecific(
      willBeSetWritableFile.canExecute(),
      "!willBeSetWritableFile.canExecute()"
    )(onUnix = false, onWindows = true)
    assertOsSpecific(
      willBeSetWritableFile.canRead(),
      "!willBeSetWritableFile.canRead()"
    )(onUnix = false, onWindows = true)
    assert(
      !willBeSetWritableFile.canWrite(),
      "!willBeSetWritableFile.canWrite()"
    )

    assert(
      willBeSetWritableFile.setWritable(true),
      "willBeSetWritableFile.setWritable(true)"
    )
    assertOsSpecific(
      willBeSetWritableFile.canExecute(),
      "!willBeSetWritableFile.canExecute()"
    )(onUnix = false, onWindows = true)
    assertOsSpecific(
      willBeSetWritableFile.canRead(),
      "!willBeSetWritableFile.canRead()"
    )(onUnix = false, onWindows = true)
    assertOsSpecific(
      willBeSetWritableFile.canWrite(),
      "willBeSetWritableFile.canWrite()"
    )(onUnix = true, onWindows = false)

    assert(
      willBeSetWritableFile.setWritable(false),
      "willBeSetWritableFile.setWritable(false)"
    )
    assertOsSpecific(
      willBeSetWritableFile.canExecute(),
      "!willBeSetWritableFile.canExecute()"
    )(onUnix = false, onWindows = true)
    assert(!willBeSetWritableFile.canRead(), "!willBeSetWritableFile.canRead()")
    assert(
      !willBeSetWritableFile.canWrite(),
      "!willBeSetWritableFile.canWrite()"
    )

    assert(
      willBeSetWritableDirectory.exists(),
      "willBeSetWritableDirectory.exists()"
    )
    assertOsSpecific(
      willBeSetWritableDirectory.canExecute(),
      "!willBeSetWritableDirectory.canExecute()"
    )(onUnix = false, onWindows = true)
    assertOsSpecific(
      willBeSetWritableDirectory.canRead(),
      "!willBeSetWritableDirectory.canRead()"
    )(onUnix = false, onWindows = true)
    assertOsSpecific(
      willBeSetWritableDirectory.canWrite(),
      "!willBeSetWritableDirectory.canWrite()"
    )(onUnix = false, onWindows = true)

    assert(
      willBeSetWritableDirectory.setWritable(true),
      "willBeSetWritableDirectory.setWritable(true)"
    )
    assertOsSpecific(
      willBeSetWritableDirectory.canExecute(),
      "!willBeSetWritableDirectory.canExecute()"
    )(onUnix = false, onWindows = true)
    assertOsSpecific(
      willBeSetWritableDirectory.canRead(),
      "!willBeSetWritableDirectory.canRead()"
    )(onUnix = false, onWindows = true)
    assert(
      willBeSetWritableDirectory.canWrite(),
      "willBeSetWritableDirectory.canWrite()"
    )

    assert(
      willBeSetWritableDirectory.setWritable(false),
      "willBeSetWritableDirectory.setWritable(false)"
    )
    assertOsSpecific(
      willBeSetWritableDirectory.canExecute(),
      "!willBeSetWritableDirectory.canExecute()"
    )(onUnix = false, onWindows = true)
    assert(
      !willBeSetWritableDirectory.canRead(),
      "!willBeSetWritableDirectory.canRead()"
    )
    assert(
      !willBeSetWritableDirectory.canWrite(),
      "!willBeSetWritableDirectory.canWrite()"
    )

    assert(!nonexistentFile.exists, "!nonexistentFile.exists")
    assert(
      !nonexistentFile.setWritable(true),
      "!nonexistentFile.setWritable(true)"
    )
  }
}
