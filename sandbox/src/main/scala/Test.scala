/*
 * Notes:
  - Boehm GC 7.6.x might have a bug in malloc impl
 */
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.scalanative.runtime.Intrinsics._
import scala.scalanative.unsafe._
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.AbstractOwnableSynchronizer
import java.util.concurrent.locks.LockSupport
import scala.scalanative.runtime.Intrinsics

object Foo extends AbstractOwnableSynchronizer {
  def start() = this.getExclusiveOwnerThread()
}

object Test {
  def simpleStartedThread(label: String)(block: => Unit) = {
    val t = new Thread {
      override def run(): Unit = {
        println(s"Started $label")
        block
        println(s"Finsished $label")
      }
    }
    t.setName(label)
    t.start()
    t
  }

  val availableCPU = java.lang.Runtime.getRuntime().availableProcessors()
  val testedThreads = Seq(2).toList.distinct
  val maxIterations = 100
  class X
  def main(args: Array[String]): Unit = {
    println(scala.scalanative.runtime.MemoryLayout.Rtti.ReferenceMapOffset)
    // println(Intrinsics.stackalloc(new CSize(castIntToRawSize(1))))
    @volatile var released = false
    @volatile var canRelease = false
    @volatile var done = false
    @volatile var startedThreads = 0
    val lock = new {}
    val thread = simpleStartedThread("t1") {
      // wait for start of t2 and inflation of object monitor
      startedThreads += 1
      while (startedThreads != 2) ()
      // should be inflated already
      lock.synchronized {
        lock.synchronized {
          canRelease = true
          while (!released) lock.wait()
        }
        lock.notify()
      }
      try {
        lock.notify()
        assert(false, "should throw")
      }      catch {case _: IllegalMonitorStateException => ()}
      done = true
    }

    simpleStartedThread("t2") {
      lock.synchronized {
        println("in t2")
        startedThreads += 1
        // Force inflation of object monitor
        // lock.wait(10)
        while (startedThreads != 2 && !canRelease) lock.wait(10)
        println("should be inflated")
        released = true
        lock.notify()
      }
    }
    Thread.sleep(500)
    thread.join(500)
    println(done)
    // assertTrue("done", done)
  }
}
