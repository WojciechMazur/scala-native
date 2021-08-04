object GetParentTest {
  import Files._
  def main(args: Array[String]): Unit = {
    assert(
      children0.getParent == expectedParent0,
      s"expectedParent0,expected $expectedParent0, got ${children0.getParent}"
    )
    assert(
      children1.getParent == expectedParent1,
      s"expectedParent1,expected $expectedParent1, got ${children1.getParent}"
    )
    assert(
      children2.getParent == expectedParent2,
      s"expectedParent2,expected $expectedParent2, got ${children2.getParent}"
    )
    assert(
      children3.getParent == expectedParent3,
      s"expectedParent3,expected $expectedParent3, got ${children3.getParent}"
    )
    assert(
      children4.getParent == expectedParent4,
      s"expectedParent4,expected $expectedParent4, got ${children4.getParent}"
    )
    assert(
      children5.getParentFile == expectedParent5,
      s"expectedParent5,expected $expectedParent5, got ${children5.getParentFile}"
    )
  }
}
