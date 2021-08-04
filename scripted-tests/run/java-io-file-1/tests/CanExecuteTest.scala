object CanExecuteTest {
  import Files._
  import Utils._

  def main(args: Array[String]): Unit = {
    assert(!emptyNameFile.canExecute(), "!emptyNameFile.canExecute()")

    assert(executableFile.canExecute(), "executableFile.canExecute()")
    assertOsSpecific(
      unexecutableFile.canExecute(),
      "unexecutableFile.canExecute"
    )(onUnix = false, onWindows = true)
    assert(!nonexistentFile.canExecute(), "!nonexistentFile.canExecute()")

    assert(executableDirectory.canExecute(), "executableDirectory.canExecute()")
    assertOsSpecific(
      unexecutableDirectory.canExecute(),
      "!unexecutableDirectory.canExecute"
    )(onUnix = false, onWindows = true)
    assert(
      !nonexistentDirectory.canExecute(),
      "!nonexistentDirectory.canExecute()"
    )
  }

}
