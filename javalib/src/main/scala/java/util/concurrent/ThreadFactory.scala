package java.util.concurrent

trait ThreadFactory {
  def newThread(runnable: Runnable): Thread
}
