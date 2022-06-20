package java.lang

// Scala Native implementation of Thread.State
// Moved to seperate file to mitigate enum issues when cross-compiling with Scala 3
sealed class ThreadState(name: String, ordinal: Int)
    extends Enum[ThreadState](name, ordinal)

object ThreadState {
  final val NEW: ThreadState = new ThreadState("NEW", 0)
  final val RUNNABLE: ThreadState = new ThreadState("RUNNABLE", 1)
  final val BLOCKED: ThreadState = new ThreadState("BLOCKED", 2)
  final val WAITING: ThreadState = new ThreadState("WAITING", 3)
  final val TIMED_WAITING: ThreadState = new ThreadState("TIMED_WAITING", 4)
  final val TERMINATED: ThreadState = new ThreadState("TERMINATED", 5)

  private[this] val cachedValues =
    Array(NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED)
  def values(): Array[ThreadState] = cachedValues.clone()
  def valueOf(name: String): ThreadState = {
    cachedValues.find(_.name() == name).getOrElse {
      throw new IllegalArgumentException(
        "No enum const Thread.ThreadState." + name
      )
    }
  }
}
