package scala.scalanative.windows

import scala.scalanative.unsafe._

@extern
object HandleApi {
  type Handle = Ptr[Byte]

  @name("scalanative_win32_invalid_handle_value")
  def InvalidHandleValue: Handle = extern

  @name("CloseHandle")
  def closeHandle(handle: Handle): Boolean = extern
}
