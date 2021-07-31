import java.io.{File, IOException}

object CreateNewFileTest {
  import Files._

  def main(args: Array[String]): Unit = {
    assert(!willBeCreatedFile.exists(), "not exists before")
    assert(willBeCreatedFile.createNewFile(), "was created")
    assert(willBeCreatedFile.exists(), "exists after")

    val exceptionThrown =
      try {
        new File(nonexistentDirectory, "somefile").createNewFile(); false
      } catch {
        case _: IOException => true
      }

    assert(exceptionThrown, "exceptionThrown")

  }
}
