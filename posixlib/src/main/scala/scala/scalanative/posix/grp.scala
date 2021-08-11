package scala.scalanative
package posix

import scalanative.unsafe._
import scalanative.posix.sys.stat.gid_t

@externModule
object grp {
  type group = CStruct3[
    CString, // gr_name
    gid_t, // gr_gid
    Ptr[CString] // gr_mem
  ]

  @name("scalanative_getgrgid")
  def getgrgid(gid: gid_t, buf: Ptr[group]): CInt = extern

  @name("scalanative_getgrnam")
  def getgrnam(name: CString, buf: Ptr[group]): CInt = extern
}
