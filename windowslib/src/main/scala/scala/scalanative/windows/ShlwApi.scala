package scala.scalanative.windows

import scala.scalanative.unsafe._

@extern()
object ShlwApi {
  @name("PathFileExistsA")
  def pathFileExistsA(path: CString): Boolean = extern
}
