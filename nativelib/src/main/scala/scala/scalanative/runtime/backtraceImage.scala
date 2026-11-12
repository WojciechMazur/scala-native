package scala.scalanative.runtime

import scalanative.unsafe._

@extern
@define("__SCALANATIVE_BACKTRACE_IMAGE")
private[runtime] object backtraceImage {

  /** The kind of the image holding `address`: 0 if unknown, 1 for the
   *  executable, 2 for a shared library. For a known image, stores its path and
   *  the bias to subtract from its addresses to get the addresses of its debug
   *  information.
   */
  @name("scalanative_backtrace_image")
  def imageOf(address: Long, path: Ptr[CString], bias: Ptr[Long]): Int =
    extern
}
