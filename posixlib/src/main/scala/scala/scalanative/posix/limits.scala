package scala.scalanative
package posix

import scalanative.unsafe._

@externModule
object limits {
  @name("scalanative_path_max")
  def PATH_MAX: CSize = extern
}
