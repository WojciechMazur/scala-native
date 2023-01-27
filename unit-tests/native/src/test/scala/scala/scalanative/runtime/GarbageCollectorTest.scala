package scala.scalanative.runtime

import java.lang.Runtime

import org.junit.Test
import org.junit.Assert._
import scala.scalanative.junit.utils.AssumesHelper

class GarbageCollectorTest {

  @Test def `cleans stale mutator threads`: Unit = {
    val iterations = 10
    for (iter <- 0 until iterations) {
      val threadsCount = Runtime.getRuntime().availableProcessors() * 4
      var ids = new scala.Array[String](threadsCount)
      val threads = Seq.tabulate(threadsCount) { id =>
        simpleStartedThread {
          val _ = generateGarbage()
          ids(id) = Thread.currentThread().getName()
          Thread.sleep(10)
        }
      }
      threads.foreach(_.join())
      assertFalse(ids.contains(null))
      // Should not segfault when iteration over memory freed by other threads
      Seq.fill(iterations * 4)(generateGarbage())
    }
  }

  private def generateGarbage() = {
    scala.util.Random.alphanumeric.take(4096).mkString.take(10)
  }

  private def simpleStartedThread(fn: => Unit) = {
    AssumesHelper.assumeMultithreadingIsEnabled()
    val thread = new Thread(() => fn)
    thread.start()
    thread
  }
}
