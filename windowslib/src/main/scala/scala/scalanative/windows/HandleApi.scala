package scala.scalanative.windows

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

@extern
object HandleApi {
  type Handle = Ptr[Byte]

  @name("scalanative_win32_invalid_handle_value")
  def InvalidHandleValue: Handle = extern

  def CloseHandle(handle: Handle): Boolean = extern

  def DuplicateHandle(sourceProcess: Handle,
                      source: Handle,
                      targetProcess: Handle,
                      target: Ptr[Handle],
                      desiredAccess: DWord,
                      inheritHandle: Boolean,
                      options: DWord): Boolean = extern

  def GetHandleInformation(handle: Handle, flags: Ptr[DWord]): Boolean = extern

  def SetHandleInformation(handle: Handle, mask: DWord, flags: DWord): Boolean =
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
