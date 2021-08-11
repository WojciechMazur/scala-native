package scala.scalanative
package runtime

import scala.scalanative.unsafe._

@externModule
object time {
  def scalanative_nano_time: CLongLong = extern
  def scalanative_current_time_millis: CLongLong = extern
}
