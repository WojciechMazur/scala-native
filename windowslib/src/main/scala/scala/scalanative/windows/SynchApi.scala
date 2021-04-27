package scala.scalanative.windows

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import HandleApi.Handle

@extern()
object SynchApi {
  type CallbackContext     = Ptr[Byte]
  type WaitOrTimerCallback = CFuncPtr2[CallbackContext, Boolean, Unit]

  def WaitForSingleObject(
      ref: Handle,
      miliseconds: DWord
  ): DWord = extern

}

object Synch {
  object WaitResult {
    final val Abandondoned = 0x00000080L.toUInt
    final val Object0      = 0x00000000L.toUInt
    final val Timeout      = 0x00000102L.toUInt
    final val Failed       = 0xFFFFFFFF.toUInt
  }
}
