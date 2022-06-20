package java.lang

// Scala Native implementation of Thread.State
// Moved to seperate file to mitigate enum issues when cross-compiling with Scala 3
enum ThreadState extends Enum[ThreadState]():
  case NEW extends ThreadState
  case RUNNABLE extends ThreadState
  case BLOCKED extends ThreadState
  case WAITING extends ThreadState
  case TIMED_WAITING extends ThreadState
  case TERMINATED extends ThreadState
