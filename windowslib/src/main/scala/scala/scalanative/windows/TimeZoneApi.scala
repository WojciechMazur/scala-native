package scala.scalanative.windows

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

@link("Kernel32")
@extern
object TimeZoneApi {
  import MinWinBaseApi._

  type WChar32 = CArray[CChar16, Nat.Digit2[Nat._3, Nat._2]]
  type CWideString = Ptr[CChar16]
  type TimeZoneInformation = CStruct7[
    Int,
    WChar32,
    SystemTime,
    Int,
    WChar32,
    SystemTime,
    Int
  ]

  def FileTimeToSystemTime(
      fileTime: Ptr[FileTime],
      systemTime: Ptr[SystemTime]
  ): Boolean = extern
  def SystemTimeToFileTime(
      systemTime: Ptr[SystemTime],
      fileTime: Ptr[FileTime]
  ): Boolean = extern

  def GetTimeZoneInformation(
      timeZoneInformation: Ptr[TimeZoneInformation]
  ): DWord = extern

  def SystemTimeToTzSpecificLocalTime(
      timeZoneInformation: Ptr[TimeZoneInformation],
      universalTime: Ptr[SystemTime],
      localTimme: Ptr[SystemTime]
  ): Boolean = extern

}

object TimeZoneApiExt {
  final val TIME_ZONE_ID_INVALID = -1.toUInt
}

object TimeZoneApiOps {
  import TimeZoneApi._
  import MinWinBaseApi._
  implicit class TimeZoneInformationOps(ref: Ptr[TimeZoneInformation]) {
    def bias: CInt = ref._1
    def standardName: WChar32 = ref._2
    def standardDate: Ptr[SystemTime] = ref.at3
    def standardBias: Int = ref._4
    def daylightName: WChar32 = ref._5
    def daylightDate: Ptr[SystemTime] = ref.at6
    def daylightBias: Int = ref._7
  }
}
