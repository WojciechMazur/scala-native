object JarHolder {
  @volatile private var held: List[java.util.zip.ZipFile] = Nil
  def hold(z: java.util.zip.ZipFile): Unit = held ::= z
}
