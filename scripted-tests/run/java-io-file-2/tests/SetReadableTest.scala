object SetReadableTest {
  import Files._
  import Utils._
  def main(args: Array[String]): Unit = {

    assert(willBeSetReadableFile.exists(), "willBeSetReadableFile.exists()")
    // Winodws (JVM) complience
    assertOsSpecific(
      willBeSetReadableFile.canExecute(),
      "!willBeSetReadableFile.canExecute()"
    )(onUnix = false, onWindows = true)
    assertOsSpecific(
      willBeSetReadableFile.canRead(),
      "!willBeSetReadableFile.canRead()"
    )(onUnix = false, onWindows = true)
    assert(
      !willBeSetReadableFile.canWrite(),
      "!willBeSetReadableFile.canWrite()"
    )

    assert(
      willBeSetReadableFile.setReadable(true),
      "willBeSetReadableFile.setReadable(true)"
    )
    // Winodws (JVM) complience
    assertOsSpecific(
      willBeSetReadableFile.canExecute(),
      "!willBeSetReadableFile.canExecute()"
    )(onUnix = false, onWindows = true)
    assert(willBeSetReadableFile.canRead(), "willBeSetReadableFile.canRead()")
    assert(
      !willBeSetReadableFile.canWrite(),
      "!willBeSetReadableFile.canWrite()"
    )

    assert(
      willBeSetReadableFile.setReadable(false),
      "willBeSetReadableFile.setReadable(false)"
    )
    assertOsSpecific(
      willBeSetReadableFile.canExecute(),
      "!willBeSetReadableFile.canExecute()"
    )(onUnix = false, onWindows = true)
    assert(!willBeSetReadableFile.canRead(), "!willBeSetReadableFile.canRead()")
    assert(
      !willBeSetReadableFile.canWrite(),
      "!willBeSetReadableFile.canWrite()"
    )

    assert(
      willBeSetReadableDirectory.exists(),
      "willBeSetReadableDirectory.exists()"
    )
    // Winodws (JVM) complience
    assertOsSpecific(
      willBeSetReadableDirectory.canExecute(),
      "!willBeSetReadableDirectory.canExecute()"
    )(onUnix = false, onWindows = true)
    assertOsSpecific(
      willBeSetReadableDirectory.canRead(),
      "!willBeSetReadableDirectory.canRead()"
    )(onUnix = false, onWindows = true)
    assertOsSpecific(
      willBeSetReadableDirectory.canWrite(),
      "!willBeSetReadableDirectory.canWrite()"
    )(onUnix = false, onWindows = true)

    assert(
      willBeSetReadableDirectory.setReadable(true),
      "willBeSetReadableDirectory.setReadable(true)"
    )
    assertOsSpecific(
      willBeSetReadableDirectory.canExecute(),
      "!willBeSetReadableDirectory.canExecute()"
    )(onUnix = false, onWindows = true)
    assert(
      willBeSetReadableDirectory.canRead(),
      "willBeSetReadableDirectory.canRead()"
    )
    assertOsSpecific(
      willBeSetReadableDirectory.canWrite(),
      "!willBeSetReadableDirectory.canWrite()"
    )(onUnix = false, onWindows = true)

    assert(
      willBeSetReadableDirectory.setReadable(false),
      "willBeSetReadableDirectory.setReadable(false)"
    )
    assertOsSpecific(
      willBeSetReadableDirectory.canExecute(),
      "!willBeSetReadableDirectory.canExecute()"
    )(onUnix = false, onWindows = true)
    assert(
      !willBeSetReadableDirectory.canRead(),
      "!willBeSetReadableDirectory.canRead()"
    )
    assert(
      !willBeSetReadableDirectory.canWrite(),
      "!willBeSetReadableDirectory.canWrite()"
    )

    assert(!nonexistentFile.exists, "!nonexistentFile.exists")
    assert(
      !nonexistentFile.setReadable(true),
      "!nonexistentFile.setReadable(true)"
    )
  }
}
