package scala.scalanative.windows

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.windows.HandleApi.Handle

@link("Advapi32")
@extern()
object Sddl {
  import MinWinBase._
  import SecurityBase._
  @name("ConvertSidToStringSidA")
  def convertSidToStringSidA(sid: SIDPtr, stringSid: Ptr[CString]): Boolean =
    extern

  @name("ConvertStringSidToSidA")
  def convertStringSidToSidA(sidString: CString, sidRef: Ptr[SIDPtr]): Boolean =
    extern
}
