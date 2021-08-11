package scala.scalanative.windows

import scala.scalanative.unsafe._

@externModule()
object ProcessEnvApi {
  def GetEnvironmentStringsW(): CWString = extern
  def FreeEnvironmentStringsW(envBlockPtr: CWString): Boolean = extern
}
