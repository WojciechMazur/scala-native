package scala.scalanative.windows

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

@extern
object HandleApi {
  type Handle = Ptr[Byte]

  @name("scalanative_win32_invalid_handle_value")
  def InvalidHandleValue: Handle = extern

  @name("CloseHandle")
  def closeHandle(handle: Handle): Boolean = extern

  @name("DuplicateHandle")
  def duplicateHandle(sourceProcess: Handle,
                      source: Handle,
                      targetProcess: Handle,
                      target: Ptr[Handle],
                      desiredAccess: DWord,
                      inheritHandle: Boolean,
                      options: DWord): Boolean = extern

  @name("SetHandleInformation")
  def setHandleInformation(handle: Handle, mask: DWord, flags: DWord): Boolean =
    extern
}

object DuplicateHandleOptions {
  final val CloseSource = 0x00000001.toUInt
  final val SameAccess  = 0x00000002.toUInt
}

object HandleFlags {
  final val Inherit          = 0x00000001.toUInt
  final val ProtectFromClose = 0x00000002.toUInt
}
