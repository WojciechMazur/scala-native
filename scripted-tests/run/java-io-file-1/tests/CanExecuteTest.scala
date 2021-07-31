object CanExecuteTest {
  import Files._

  def main(args: Array[String]): Unit = {
    // setExecutable cannot be used on Windows
    def osSpecific(pred: Boolean) = if (isWindows) !pred else pred

    assert(!emptyNameFile.canExecute())

    assert(executableFile.canExecute())
    assert(osSpecific(!unexecutableFile.canExecute()))
    assert(!nonexistentFile.canExecute())

    assert(executableDirectory.canExecute())
    assert(osSpecific(!unexecutableDirectory.canExecute()))
    assert(!nonexistentDirectory.canExecute())
  }

}
