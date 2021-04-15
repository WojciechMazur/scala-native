package scala.scalanative.windows

import scala.scalanative.unsafe._

@extern
object TimeZoneApi {
  import MinWinBase._

  def FileTimeToSystemTime(fileTime: Ptr[FileTime],
                           systemTime: Ptr[SystemTime]): Boolean = extern
  def SystemTimeToFileTime(systemTime: Ptr[SystemTime],
                           fileTime: Ptr[FileTime]): Boolean = extern

}
