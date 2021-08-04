object IsHiddenTest {
  import Files._
  import Utils._

  def main(args: Array[String]): Unit = {
    assert(currentDirectory.isHidden(), "currentDirectory.isHidden()")
    assert(existingHiddenFile.exists(), "existingHiddenFile.exists()")
    assert(
      existingHiddenFile.isHidden(),
      "existingHiddenFile.isHidden()"
    )
    assert(existingHiddenDirectory.exists(), "existingHiddenDirectory.exists()")
    assert(
      existingHiddenDirectory.isHidden(),
      "existingHiddenDirectory.isHidden()"
    )
    assert(!nonexistentHiddenFile.exists(), "!nonexistentHiddenFile.exists()")
    // On Windows (JVM) isHidden for non existing file returns false
    assertOsSpecific(
      nonexistentHiddenFile.isHidden(),
      "nonexistentHiddenFile.isHidden()"
    )(onUnix = true, onWindows = false)
  }
}
