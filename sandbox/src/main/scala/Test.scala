import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.scalanative.runtime.Intrinsics
import scala.scalanative.unsafe._
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicInteger

object Foo

object Test {
  def main(args: Array[String]): Unit = {
    def testConcurency(implicit ex: ExecutionContext) = {
      val start = System.currentTimeMillis()
      val threadState = new ThreadLocal[Option[Int]] {
        override protected def initialValue(): Option[Int] = None
      }
      val atomicCounter = new AtomicInteger(0)
      val f = Future
        .traverse(0.until(1000).toList) { v =>
          Future {
            val prevValue = threadState.get()
            val counterValue = atomicCounter.incrementAndGet()
            Foo.synchronized{
              println(s"$v in thread: ${Thread.currentThread()}, prevValue: ${prevValue}, counterValue: ${counterValue}")
            }
            threadState.set(Some(v))
            v
          }
        }
        .flatMap { seq =>
          Future.traverse(seq) { v => Future(v * 2) }
        }
        .map(_.size)
        .map(count => s"Received $count results")

      println(Await.result(f, 30.seconds))
      val took = System.currentTimeMillis() - start
      println(s"++ took ${took}ms")
    }

    0 until 100 foreach { idx =>
      println(s"----Do GC $idx------")
      System.gc()
      println(s"-----Execute global $idx-------")
      testConcurency(ExecutionContext.global)
      // println(s"-----Execute custom $idx-------")
      // val x = new java.util.concurrent.ForkJoinPool(4)
      // testConcurency(ExecutionContext.fromExecutor(x))
    }

    // // println("hello world")
    // // val foo = new Object(){}
    // // val x = new X()
    // // import x.Foo
    // val m = scalanative.runtime.getMonitor(this)
    // // println(m)
    // // println(scalanative.runtime.fromRawPtr[Long](Intrinsics.castObjectToRawPtr(Foo)))
    // println("enter this monitor")
    // m.enter()
    // m.exit()
    // println("entered")

    // def fail() = {
    //   println("failing")
    //   throw new RuntimeException("panic!")
    // }
    // println(s"this monitor - ${scalanative.runtime.getMonitor(this)}")
    // def doSomething(): Int = synchronized {
    //   println("in synchronized")
    //   fail()
    //   scala.util.Random.nextInt()
    //   // println("before")
    //   // try {
    //   //   val x = println("in Try")
    //   //
    //   // } finally println("done")
    // }
    // // fail()
    // println(doSomething())
  }
}
