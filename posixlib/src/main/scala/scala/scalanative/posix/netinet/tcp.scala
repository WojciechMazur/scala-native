package scala.scalanative.posix.netinet

import scalanative.unsafe._

object tcp {

  @name("scalanative_TCP_NODELAY")
  def TCP_NODELAY: CInt = extern
}
