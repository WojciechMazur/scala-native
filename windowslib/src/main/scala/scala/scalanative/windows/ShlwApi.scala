package scala.scalanative.windows

import scala.scalanative.unsafe._

@link("shlwapi")
@extern()
object ShlwApi {
  def PathFileExistsA(path: CString): Boolean = extern
}
