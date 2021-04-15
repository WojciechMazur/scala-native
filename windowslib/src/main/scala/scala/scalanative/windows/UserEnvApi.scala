package scala.scalanative.windows

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.windows.HandleApi.Handle

@link("Userenv")
@extern()
object UserEnvApi {
  @name("GetUserProfileDirectoryA")
  def getUserProfileDirectortA(token: Handle,
                               profileDir: CString,
                               size: Ptr[DWord]): Boolean = extern

}
