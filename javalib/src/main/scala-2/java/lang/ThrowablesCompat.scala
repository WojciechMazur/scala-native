package java.lang

class NullPointerException(s: String) extends RuntimeException(s) {
  def this() = this(null)
}
