package scala.scalanative.windows

import scala.scalanative.unsafe._

@extern()
object ProcessEnv {
  def GetEnvironmentStringsW(): CWideString = extern

  def FreeEnvironmentStringsW(envBlockPtr: CWideString): Boolean = extern
}
