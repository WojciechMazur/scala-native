package scala.scalanative.windows

import scala.scalanative.unsafe._

@link("shlwapi")
@extern()
object ShlwApi {
  def PathFileExistsW(path: CWString): Boolean = extern
}
