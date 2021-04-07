package scala.scalanative.windows.winnt

import scala.scalanative.unsafe.extern
import scala.scalanative.unsafe._

import scala.scalanative.windows.SecurityBase.SIDPtr

@extern
object HelperMethods {
  @name("scalanative_win32_winnt_setupUsersGroupSid")
  def setupUserGroupSid(ref: Ptr[SIDPtr]): Boolean = extern
}
