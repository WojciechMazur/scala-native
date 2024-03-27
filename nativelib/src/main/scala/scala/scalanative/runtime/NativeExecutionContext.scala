package scala.scalanative
package runtime

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContextExecutor
import scala.scalanative.meta.LinktimeInfo.isMultithreadingEnabled

object NativeExecutionContext {

  /** Single-threaded queue based execution context. Each runable is executed
   *  sequentially after termination of the main method
   */
  def queue(): ExecutionContextExecutor = QueueExecutionContext

  private object QueueExecutionContext extends ExecutionContextExecutor {
    private val queue: ListBuffer[Runnable] = new ListBuffer
    def execute(runnable: Runnable): Unit = queue += runnable
    def reportFailure(t: Throwable): Unit = t.printStackTrace()

    def hasNext: Boolean = queue.nonEmpty
    def scheduled: Int = queue.size

    def runNext(): Unit = if (hasNext) {
      val runnable = queue.remove(0)
      try runnable.run()
      catch {
        case t: Throwable =>
          QueueExecutionContext.reportFailure(t)
      }
    }

    def loop(): Unit = while (hasNext) runNext()
  }

  /** Execute all the tasks in the queue until there is none left */
  private[runtime] def loop(): Unit = QueueExecutionContext.loop()
}
