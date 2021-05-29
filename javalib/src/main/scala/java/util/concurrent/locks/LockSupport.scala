package java.util
package concurrent.locks

private class LockSupport {}

object LockSupport {

  // To be implemented, with thread suspend/resume

  def unpark(thread: Thread): Unit                     = {}
  def park(): Unit                                     = {}
  def park(blocker: Object): Unit                      = {}
  def parkNanos(nanos: Long): Unit                     = {}
  def parkNanos(blocker: Object, nanos: Long): Unit    = {}
  def parkUntil(deadline: Long): Unit                  = {}
  def parkUntil(blocker: Object, deadline: Long): Unit = {}
  def setCurrentBlocker(blocker: Object): Unit         = {}
}
