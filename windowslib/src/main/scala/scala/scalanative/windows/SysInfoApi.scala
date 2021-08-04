package scala.scalanative.windows

import scala.scalanative.unsafe._

@extern
object SysInfoApi {
  import MinWinBaseApi._
  def GetLocalTime(timeStruct: Ptr[SystemTime]): Unit = extern
  def GetSystemTime(timeStruct: Ptr[SystemTime]): Unit = extern

  def SetLocalTime(timeStruct: Ptr[SystemTime]): Boolean = extern
  def SetSystemTime(timeStruct: Ptr[SystemTime]): Boolean = extern

}
