object IsAbsoluteTest {
  import Files._

  def main(args: Array[String]): Unit = {
    // On Windows conditions needs to be negated
    def osSpecific(pred: Boolean) = if (isWindows) !pred else pred
    assert(
      osSpecific(absoluteUnixStyle.isAbsolute),
      "absoluteUnixStyle.isAbsolute"
    )
    assert(
      osSpecific(!absoluteWinStyle0.isAbsolute),
      "absoluteWinStyle0.isAbsolute"
    )
    assert(
      osSpecific(!absoluteWinStyle1.isAbsolute),
      "absoluteWinStyle1.isAbsolute"
    )
    assert(
      osSpecific(!absoluteWinStyle2.isAbsolute),
      "absoluteWinStyle2.isAbsolute"
    )

    assert(!relative0.isAbsolute, "!relative0.isAbsolute")
    assert(!relative1.isAbsolute, "!relative1.isAbsolute")
    assert(!relative2.isAbsolute, "!relative2.isAbsolute")
  }
}
