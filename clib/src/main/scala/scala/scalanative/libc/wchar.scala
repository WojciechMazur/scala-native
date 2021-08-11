package scala.scalanative
package libc

import scalanative.unsafe._

@externModule
object wchar {
  type WString = CWideString

  def wcscpy(destination: WString, source: WString): WString = extern
}
