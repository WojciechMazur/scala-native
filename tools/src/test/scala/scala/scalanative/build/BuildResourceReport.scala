package scala.scalanative
package build

import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.{Files, Path, Paths}

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

import scala.scalanative.buildinfo.ScalaNativeBuildInfo
import scala.scalanative.util.Scope

/** Measures the memory and CPU a build uses, per phase.
 *
 *  Builds the fixture of `IncrementalBuildReport` `builds` times, each in a
 *  fresh build directory sharing one build cache, the way partests and fresh
 *  checkouts build. While building it samples the heap of this JVM, its CPU
 *  time and the resident memory of the processes it starts (compilers,
 *  linkers); phases are delimited by the timings the build logs. After each
 *  build it reports the heap still reachable once collected.
 *
 *  Run in a JVM of its own, e.g.
 *  {{{
 *  java -Xmx4g -cp <toolsJVM3 test classpath> \
 *    scala.scalanative.build.BuildResourceReport \
 *    kind=dynamic builds=2 cache=/tmp/sn-cache out=report.tsv
 *  }}}
 *  `kind`: `baseline` (today's builds), `static` or `dynamic` (granular).
 */
object BuildResourceReport {

  private final case class Sample(
      at: Long,
      heapUsed: Long,
      jvmCpu: Long,
      childRss: Long,
      children: Int
  )

  def main(args: Array[String]): Unit = {
    val options = args.flatMap { arg =>
      arg.split("=", 2) match {
        case Array(key, value) => Some(key -> value)
        case _                 => None
      }
    }.toMap
    val kind = options.getOrElse("kind", "dynamic")
    val builds = options.get("builds").fold(2)(_.toInt)
    options
      .get("cache")
      .foreach(System.setProperty("scala.scalanative.cache.dir", _))
    val nativeConfig = IncrementalBuildReport.defaultNativeConfig
      .withMode(options.get("mode").map(Mode(_)).getOrElse(Mode.debug))
      .withGranularBuild(kind != "baseline")
      .withDynamicLibraries(kind == "dynamic")
      .withSourceLevelDebuggingConfig(config =>
        if (options.get("debugInfo").exists(_.toBoolean)) config.enableAll
        else config
      )

    val root = Files.createTempDirectory("build-resource-report")
    val sources = Files.createDirectories(root.resolve("sources"))
    val classes = Files.createDirectories(root.resolve("classes"))
    IncrementalBuildReport.Fixture.initial.foreach {
      case (name, content) => IO.write(sources.resolve(name), content)
    }
    NIRCompiler.getCompiler(classes).compile(sources)
    val classpath = ScalaNativeBuildInfo.nativeRuntimeClasspath
      .split(File.pathSeparator)
      .filter(_.nonEmpty)
      .map(Paths.get(_))
      .toSeq :+ classes

    val out = new StringBuilder
    out.append(
      "kind\tbuild\tphase\tms\theap_peak_mb\tchild_rss_peak_mb\tchildren_peak\tjvm_cpu_s\n"
    )
    val totals = new StringBuilder
    try {
      (1 to builds).foreach { n =>
        val phases = new PhaseLogger
        val config = Config.empty
          .withBaseDir(root.resolve(s"build-$n"))
          .withModuleName("report")
          .withClassPath(classpath)
          .withMainClass(Some(IncrementalBuildReport.Fixture.main))
          .withCompilerConfig(nativeConfig)
          .withLogger(phases)
        val sampler = new Sampler
        sampler.start()
        val started = System.nanoTime()
        val cpuBefore = jvmCpu()
        Scope { implicit in =>
          Await.result(Build.build(config), Duration.Inf)
        }
        val wall = (System.nanoTime() - started) / 1000000
        val cpu = (jvmCpu() - cpuBefore) / 1e9
        sampler.finish()
        val samples = sampler.result
        // Phases end when their timing is logged and start where the previous
        // one ended
        var from = started
        phases.result.foreach {
          case (name, ms, endedAt) =>
            val in = samples.filter(s => s.at >= from && s.at <= endedAt)
            val cpuIn =
              if (in.size < 2) 0.0 else (in.last.jvmCpu - in.head.jvmCpu) / 1e9
            out.append(
              Seq(
                kind,
                n,
                name,
                ms,
                mb(in.map(_.heapUsed).foldLeft(0L)(math.max)),
                mb(in.map(_.childRss).foldLeft(0L)(math.max)),
                in.map(_.children).foldLeft(0)(math.max),
                f"$cpuIn%.1f"
              ).mkString("", "\t", "\n")
            )
            from = endedAt
        }
        System.gc()
        System.gc()
        val retained =
          ManagementFactory.getMemoryMXBean.getHeapMemoryUsage.getUsed
        totals.append(
          s"$kind build $n: wall ${wall} ms, JVM CPU ${f"$cpu%.1f"} s, " +
            s"heap peak ${mb(samples.map(_.heapUsed).foldLeft(0L)(math.max))} MB, " +
            s"heap retained after GC ${mb(retained)} MB, " +
            s"child RSS peak ${mb(samples.map(_.childRss).foldLeft(0L)(math.max))} MB, " +
            s"children peak ${samples.map(_.children).foldLeft(0)(math.max)}\n"
        )
      }
    } finally IO.deleteRecursive(root)

    print(out)
    print(totals)
    options.get("out").foreach { path =>
      IO.write(Paths.get(path), out.toString + "\n" + totals.toString)
    }
  }

  private def mb(bytes: Long): Long = bytes / (1024 * 1024)

  private def jvmCpu(): Long =
    ManagementFactory.getOperatingSystemMXBean match {
      case os: com.sun.management.OperatingSystemMXBean => os.getProcessCpuTime
      case _                                            => 0L
    }

  /** Samples the heap, CPU and child processes every 100 ms. */
  private class Sampler extends Thread("build-resource-sampler") {
    setDaemon(true)
    private val samples = mutable.ArrayBuffer.empty[Sample]
    @volatile private var running = true
    private val memory = ManagementFactory.getMemoryMXBean

    private val self =
      ManagementFactory.getRuntimeMXBean.getName.takeWhile(_ != '@').toLong

    // Resident memory and count of the processes started by this JVM, and
    // by them
    private def descendants(): (Long, Int) =
      try {
        val ps = new ProcessBuilder("ps", "-A", "-o", "pid=,ppid=,rss=,comm=")
          .redirectErrorStream(true)
          .start()
        val text =
          scala.io.Source.fromInputStream(ps.getInputStream).mkString
        ps.waitFor()
        val rows = text.linesIterator
          .map(_.trim.split("\\s+", 4))
          .collect {
            case Array(pid, ppid, rss, command) =>
              (pid.toLong, ppid.toLong, rss.toLong, command)
          }
          .toSeq
        val byParent = rows.groupBy(_._2)
        def walk(pid: Long): Seq[(Long, Long, Long, String)] =
          byParent.getOrElse(pid, Nil).flatMap(row => row +: walk(row._1))
        // Leaving out the sampling ps itself
        val found = walk(self).filterNot(_._4.endsWith("ps"))
        (found.map(_._3 * 1024).sum, found.size)
      } catch { case _: Exception => (0L, 0) }

    override def run(): Unit =
      while (running) {
        val (rss, children) = descendants()
        samples.synchronized {
          samples += Sample(
            System.nanoTime(),
            memory.getHeapMemoryUsage.getUsed,
            jvmCpu(),
            rss,
            children
          )
        }
        Thread.sleep(100)
      }

    def finish(): Unit = { running = false; join() }
    def result: Seq[Sample] = samples.synchronized(samples.toList)
  }

  /** Phase timings logged as `<phase> (<details>) (<n> ms)`, with the time each
   *  was logged.
   */
  private class PhaseLogger extends Logger {
    private val Timed = """^(.*) \((\d+) ms\)$""".r
    private val Detail = """^(.*?) \(.*\)$""".r
    private val buffer = mutable.ArrayBuffer.empty[(String, Long, Long)]

    def result: Seq[(String, Long, Long)] = synchronized(buffer.toList)

    def trace(msg: Throwable): Unit = ()
    def debug(msg: String): Unit = ()
    def info(msg: String): Unit = msg match {
      case Timed(phase, ms) =>
        val name = phase match {
          case Detail(name) => name
          case name         => name
        }
        synchronized(buffer += ((name, ms.toLong, System.nanoTime())))
      case _ => ()
    }
    def warn(msg: String): Unit = ()
    def error(msg: String): Unit = System.err.println(s"[error] $msg")
  }
}
