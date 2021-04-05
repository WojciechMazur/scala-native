package scala.scalanative.windows

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import HandleApi.Handle

@extern()
object NamedPipeApi {
  @name("CreatePipe")
  def createPipe(
      readPipePtr: Ptr[Handle],
      writePipePtr: Ptr[Handle],
      securityAttributes: Ptr[SecurityAttributes],
      size: DWord
  ): Boolean = extern

  @name("PeekNamedPipe")
  def peekNamedPipe(pipe: Handle,
                    buffer: Ptr[Byte],
                    bufferSize: DWord,
                    bytesRead: Ptr[DWord],
                    totalBytesAvailable: Ptr[DWord],
                    bytesLeftThisMessage: Ptr[DWord]): Boolean = extern
}
