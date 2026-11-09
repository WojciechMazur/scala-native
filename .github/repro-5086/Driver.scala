//> using scala 3.3.7
//> using dep org.scala-native:test-runner_3:0.5.12
//> using dep org.scala-sbt:test-interface:1.0

// Standalone driver: runs the Scala Native test binary through the same TestAdapter
// that sbt-scala-native uses (one native process per JVM thread), N iterations.
//
// usage: Driver <binary> <iterations> <threads> <suites: all|Name,Name,...> [env K=V ...]

import sbt.testing.*
import scala.scalanative.build.Logger
import scala.scalanative.testinterface.adapter.TestAdapter

import java.io.File
import java.util.concurrent.{ ConcurrentLinkedQueue, Executors, TimeUnit }
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters.*

object Driver {
  val scalatestSuites = List(
    "zio.interop.CatsSpec",
    "zio.interop.CatsMtlSpec",
    "zio.interop.CatsChunkSpec",
    "zio.interop.CatsNonEmptyChunkSpec",
    "zio.stream.interop.ZStreamSpec"
  )
  val zioSuites = List(
    "zio.interop.Fs2ZioSpec",
    "zio.interop.catzQueueSpec",
    "zio.interop.fs2StreamSpec",
    "zio.interop.CatsZManagedSyntaxSpec"
  )

  final class CapturingLogger(iter: Int, verbose: Boolean) extends Logger {
    val errors = new ConcurrentLinkedQueue[String]()
    def trace(msg: Throwable): Unit = ()
    def debug(msg: String): Unit   = ()
    def info(msg: String): Unit    = if (verbose) println(s"[$iter info] $msg")
    def warn(msg: String): Unit    = { errors.add("WARN " + msg); println(s"[$iter warn] $msg") }
    def error(msg: String): Unit   = { errors.add("ERROR " + msg); println(s"[$iter error] $msg") }
  }

  final class SbtLog(prefix: String, verbose: Boolean) extends sbt.testing.Logger {
    def ansiCodesSupported(): Boolean = false
    def error(msg: String): Unit      = println(s"$prefix ERR $msg")
    def warn(msg: String): Unit       = if (verbose) println(s"$prefix WRN $msg")
    def info(msg: String): Unit       = if (verbose) println(s"$prefix $msg")
    def debug(msg: String): Unit      = ()
    def trace(t: Throwable): Unit     = ()
  }

  def main(args: Array[String]): Unit = {
    val binary     = new File(args(0))
    val iterations = args(1).toInt
    val threads    = args(2).toInt
    val selected   = args(3)
    val env        = args.drop(4).map { kv => val i = kv.indexOf('='); kv.take(i) -> kv.drop(i + 1) }.toMap
    val verbose    = sys.env.get("DRIVER_VERBOSE").contains("1")

    val st = if (selected == "all") scalatestSuites else scalatestSuites.filter(s => selected.split(',').exists(s.endsWith))
    val zt = if (selected == "all") zioSuites else zioSuites.filter(s => selected.split(',').exists(s.endsWith))

    var failedIters = 0
    for (iter <- 1 to iterations) {
      val t0      = System.nanoTime()
      val logger  = new CapturingLogger(iter, verbose)
      val adapter = new TestAdapter(TestAdapter.Config().withBinaryFile(binary).withLogger(logger).withEnvVars(env))
      val events  = new AtomicInteger(0)
      val bad     = new ConcurrentLinkedQueue[String]()
      try {
        val fws = adapter.loadFrameworks(List(List("org.scalatest.tools.Framework"), List("zio.test.sbt.ZTestFramework")))
        val List(Some(scalatest), Some(ztest)) = fws: @unchecked
        val stRunner = scalatest.runner(Array.empty, Array.empty, null)
        val ztRunner = ztest.runner(Array.empty, Array.empty, null)
        def taskDefs(fw: Framework, names: List[String]) = {
          val fp = fw.fingerprints().head
          names.map(n => new TaskDef(n, fp, false, Array(new SuiteSelector)))
        }
        val tasks: List[(String, Task)] =
          (if (st.isEmpty) Nil else stRunner.tasks(taskDefs(scalatest, st).toArray).toList.map(t => t.taskDef().fullyQualifiedName() -> t)) ++
            (if (zt.isEmpty) Nil else ztRunner.tasks(taskDefs(ztest, zt).toArray).toList.map(t => t.taskDef().fullyQualifiedName() -> t))
        val queue = new ConcurrentLinkedQueue[(String, Task)](tasks.asJava)
        val pool  = Executors.newFixedThreadPool(threads)
        val handler: EventHandler = (e: Event) => {
          events.incrementAndGet()
          if (e.status() == Status.Error || e.status() == Status.Failure)
            bad.add(s"${e.fullyQualifiedName()} ${e.status()} ${if (e.throwable().isDefined) e.throwable().get() else ""}")
        }
        (1 to threads).foreach { w =>
          pool.submit(new Runnable {
            def run(): Unit = {
              var next = queue.poll()
              while (next != null) {
                val (name, task) = next
                try task.execute(handler, Array(new SbtLog(s"[$iter w$w $name]", verbose)))
                catch { case t: Throwable => bad.add(s"$name EXC $t") }
                next = queue.poll()
              }
            }
          })
        }
        pool.shutdown()
        pool.awaitTermination(30, TimeUnit.MINUTES)
        try { stRunner.done(); ztRunner.done() }
        catch { case t: Throwable => bad.add(s"done EXC $t") }
      } catch { case t: Throwable => bad.add(s"iteration EXC $t") }
      finally adapter.close()
      val dt   = (System.nanoTime() - t0) / 1e9
      val fail = !bad.isEmpty || !logger.errors.isEmpty
      if (fail) failedIters += 1
      println(f"ITER $iter%d ${if (fail) "FAIL" else "OK"} time=${dt}%.1fs events=${events.get()}")
      bad.asScala.foreach(b => println(s"  BAD $b"))
      logger.errors.asScala.foreach(b => println(s"  LOG $b"))
    }
    println(s"SUMMARY failed=$failedIters/$iterations")
    sys.exit(0)
  }
}
