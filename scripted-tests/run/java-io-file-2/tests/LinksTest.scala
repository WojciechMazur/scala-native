object LinksTest {
  import Files._
  import Utils._

  def main(args: Array[String]): Unit = {
    if (!isWindows) {
      // Not testing symbolic links on Windows, needs admin privileges
      assert(directoryLinkedTo.exists, "directoryLinkedTo.exists")
      assert(linkToDirectory.exists, "linkToDirectory.exists")
      assert(
        linkToDirectory.getCanonicalPath == directoryLinkedTo.getCanonicalPath
      )
      assert(
        linkToDirectory.getName != directoryLinkedTo.getName,
        "linkToDirectory.getName != directoryLinkedTo.getName"
      )
    }
  }
}
