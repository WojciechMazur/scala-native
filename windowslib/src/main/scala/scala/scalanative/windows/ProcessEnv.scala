package scala.scalanative.windows

import scala.scalanative.unsafe._

@extern()
object ProcessEnv {
  @name("GetEnvironmentStringsA")
  def getEnvironmentStrings(): CString = extern

  @name("FreeEnvironmentStringsA")
  def freeEnvironmentStrings(envBlockPtr: CString): Boolean = extern
}
