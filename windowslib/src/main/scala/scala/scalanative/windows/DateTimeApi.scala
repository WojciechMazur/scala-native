package scala.scalanative.windows

import scala.scalanative.unsafe._

@link("Kernel32")
@extern
object DateTimeApi {
  import MinWinBaseApi._
  import TimeZoneApi._

  type LCID = DWord

  def GetDateFormatW(
      locale: LCID,
      flags: DWord,
      date: Ptr[SystemTime],
      format: CWString,
      buffer: CWString,
      bufferSize: CInt
  ): CInt = extern

  def GetTimeFormatW(
      locale: LCID,
      flags: DWord,
      time: Ptr[SystemTime],
      format: CWString,
      buffer: CWString,
      bufferSize: CInt
  ): CInt = extern

  @name("scalanative_win32_datetime_locale_user_default")
  final def LocaleUserDefault: LCID = extern
}
