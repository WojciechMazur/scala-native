package scala.scalanative.windows

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.windows.HandleApi.Handle

@link("Advapi32")
@extern()
object Sddl {
  import MinWinBase._
  import SecurityBase._
  def ConvertSidToStringSidA(sid: SIDPtr, stringSid: Ptr[CString]): Boolean =
    extern

  def ConvertStringSidToSidA(sidString: CString, sidRef: Ptr[SIDPtr]): Boolean =
    extern
}
