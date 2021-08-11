package scala.scalanative.posix.netinet

import scalanative.unsafe._

@externModule
object tcp {

  @name("scalanative_tcp_nodelay")
  def TCP_NODELAY: CInt = extern
}
