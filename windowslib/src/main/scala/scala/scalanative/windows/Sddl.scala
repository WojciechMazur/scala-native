package scala.scalanative.windows

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.windows.HandleApi.Handle

@link("Advapi32")
@extern()
object Sddl {
  import MinWinBase._
  import SecurityBase._
  def ConvertSidToStringSidW(sid: SIDPtr, stringSid: Ptr[CWString]): Boolean =
    extern

  def ConvertStringSidToSidW(sidString: CWString,
                             sidRef: Ptr[SIDPtr]): Boolean =
    extern
}
