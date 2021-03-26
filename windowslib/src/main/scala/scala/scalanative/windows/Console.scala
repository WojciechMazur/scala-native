package scala.scalanative.windows

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.windows.HandleApi.Handle

@extern()
object Console {
  @name("GetStdHandle")
  def getStdHandle(handleNum: UInt): Handle = extern

  @name("scalanative_win32_console_std_in_handle")
  def stdInput: UInt = extern

  @name("scalanative_win32_console_std_out_handle")
  def stdOutput: UInt = extern

  @name("scalanative_win32_console_std_err_handle")
  def stdError: UInt = extern
}

object ConsoleExt {
  import Console._

  def stdIn: Handle  = getStdHandle(stdInput)
  def stdOut: Handle = getStdHandle(stdOutput)
  def stdErr: Handle = getStdHandle(stdError)
}
