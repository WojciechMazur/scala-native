package scala.scalanative.windows

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.windows.HandleApi.Handle

@extern()
object Console {
  def GetStdHandle(handleNum: DWord): Handle                  = extern
  def SetStdHandle(stdHandle: DWord, handle: Handle): Boolean = extern

  @name("scalanative_win32_console_std_in_handle")
  def stdInput: DWord = extern

  @name("scalanative_win32_console_std_out_handle")
  def stdOutput: DWord = extern

  @name("scalanative_win32_console_std_err_handle")
  def stdError: DWord = extern
}

object ConsoleExt {
  import Console._

  def stdIn: Handle  = GetStdHandle(stdInput)
  def stdOut: Handle = GetStdHandle(stdOutput)
  def stdErr: Handle = GetStdHandle(stdError)
}
