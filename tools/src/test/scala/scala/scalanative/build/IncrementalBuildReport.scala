package scala.scalanative
package build

import java.io.File
import java.nio.file.attribute.FileTime
import java.nio.file.{Files, Path, Paths}

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

import scala.scalanative.buildinfo.ScalaNativeBuildInfo
import scala.scalanative.util.Scope

/** Measures how much of the native pipeline reruns after typical edits.
 *
 *  Builds a small application against the full runtime classpath, then applies
 *  a sequence of edits, rebuilding after each one. For every build it reports
 *  the time of each toolchain phase and how many generated LLVM IR files and
 *  object files were rewritten. Edits are cumulative: each step is measured
 *  against the state left by the previous one.
 *
 *  Run with:
 *  {{{
 *  sbt 'toolsJVM3/Test/runMain scala.scalanative.build.IncrementalBuildReport mode=debug out=target/incremental-build-report.md'
 *  }}}
 *
 *  Arguments (all optional, `key=value`): `mode` (debug, release-fast,
 *  release-size, release-full), `optimize` (true, false), `incremental` (true,
 *  false), `out` (path of a Markdown report), `keep` (true keeps the build
 *  directory).
 */
object IncrementalBuildReport {

  /** One edit applied before a build. Files map paths relative to the sources
   *  directory to their new content.
   */
  final case class Step(
      name: String,
      description: String,
      files: Map[String, String]
  )

  final case class Measurement(
      step: Step,
      phases: Seq[(String, Long)],
      irFiles: Int,
      irRegenerated: Int,
      objects: Int,
      objectsRecompiled: Int,
      nativeObjects: Int,
      nativeObjectsRecompiled: Int,
      discovered: Option[String]
  ) {
    def phase(name: String): Option[Long] =
      phases.collect { case (`name`, ms) => ms }.reduceOption(_ + _)
  }

  object Fixture {
    val main = "app.Main"

    private val mainSource: String => String = body =>
      s"""|package app
          |
          |import model._
          |
          |object Main {
          |  def main(args: Array[String]): Unit = {
          |$body
          |  }
          |}
          |""".stripMargin

    val initial: Map[String, String] = Map(
      "app/Main.scala" -> mainSource(
        """|    val shapes: List[Shape] = List(new Circle(1), new Square(2))
           |    println(Report.render(shapes))
           |    println(Version.text)""".stripMargin
      ),
      "app/Version.scala" ->
        """|package app
           |
           |object Version {
           |  def text: String = "v1"
           |}
           |""".stripMargin,
      "model/Shapes.scala" ->
        """|package model
           |
           |trait Shape {
           |  def area: Double
           |  def name: String
           |}
           |class Circle(r: Double) extends Shape {
           |  def area: Double = math.Pi * r * r
           |  def name: String = "circle"
           |}
           |class Square(side: Double) extends Shape {
           |  def area: Double = side * side
           |  def name: String = "square"
           |}
           |""".stripMargin,
      "model/Report.scala" ->
        """|package model
           |
           |object Report {
           |  def render(shapes: List[Shape]): String =
           |    shapes.map(s => s.name + ": " + s.area).mkString("\n")
           |}
           |""".stripMargin
    )

    val steps: Seq[Step] = Seq(
      Step("cold", "first build, empty build directory", initial),
      Step("no-op", "rebuild without changes", Map.empty),
      Step(
        "method-body",
        "change the body of one application method",
        Map(
          "app/Version.scala" ->
            """|package app
               |
               |object Version {
               |  def text: String = "v2"
               |}
               |""".stripMargin
        )
      ),
      Step(
        "new-subclass",
        "add a subclass overriding methods of an existing class",
        Map(
          "model/Rect.scala" ->
            """|package model
               |
               |class Rect(w: Double, h: Double) extends Square(w) {
               |  override def area: Double = w * h
               |  override def name: String = "rect"
               |}
               |""".stripMargin,
          "app/Main.scala" -> mainSource(
            """|    val shapes: List[Shape] =
               |      List(new Circle(1), new Square(2), new Rect(2, 3))
               |    println(Report.render(shapes))
               |    println(Version.text)""".stripMargin
          )
        )
      ),
      Step(
        "new-trait",
        "add a trait and a class mixing it in",
        Map(
          "model/Labelled.scala" ->
            """|package model
               |
               |trait Labelled {
               |  def label: String
               |}
               |class Tagged(r: Double) extends Circle(r) with Labelled {
               |  def label: String = "tagged " + name
               |}
               |""".stripMargin,
          "app/Main.scala" -> mainSource(
            """|    val tagged = new Tagged(4)
               |    val shapes: List[Shape] =
               |      List(new Circle(1), new Square(2), new Rect(2, 3), tagged)
               |    println(Report.render(shapes))
               |    println(tagged.label)
               |    println(Version.text)""".stripMargin
          )
        )
      ),
      Step(
        "new-library-api",
        "start using standard library classes not used before",
        Map(
          "app/Main.scala" -> mainSource(
            """|    val tagged = new Tagged(4)
               |    val shapes: List[Shape] =
               |      List(new Circle(1), new Square(2), new Rect(2, 3), tagged)
               |    println(Report.render(shapes))
               |    println(tagged.label)
               |    val byName = new java.util.TreeMap[String, java.lang.Double]()
               |    shapes.foreach(s => byName.put(s.name, s.area))
               |    println(byName)
               |    println(Version.text)""".stripMargin
          )
        )
      )
    )
  }

  def main(args: Array[String]): Unit = {
    val options = args.flatMap { arg =>
      arg.split("=", 2) match {
        case Array(key, value) => Some(key -> value)
        case _                 =>
          System.err.println(s"Ignoring argument '$arg', expected key=value")
          None
      }
    }.toMap
    val nativeConfig = defaultNativeConfig
      .withMode(options.get("mode").map(Mode(_)).getOrElse(Mode.debug))
      .withOptimize(options.get("optimize").forall(_.toBoolean))
      .withIncrementalCompilation(
        options.get("incremental").forall(_.toBoolean)
      )
      .withGranularBuild(options.get("granular").exists(_.toBoolean))
    val keep = options.get("keep").exists(_.toBoolean)

    val measurements = run(nativeConfig, Fixture.steps, keep)
    val report = render(nativeConfig, measurements)
    println(report)
    options.get("out").foreach { out =>
      val path = Paths.get(out).toAbsolutePath
      Option(path.getParent).foreach(Files.createDirectories(_))
      IO.write(path, report)
      println(s"Report written to $path")
    }
  }

  def defaultNativeConfig: NativeConfig = NativeConfig.empty
    .withClang(Discover.clang())
    .withClangPP(Discover.clangpp())
    .withCompileOptions(Discover.compileOptions())
    .withLinkingOptions(Discover.linkingOptions())
    .withLTO(Discover.LTO())
    .withGC(Discover.GC())
    .withBaseName("report")

  /** Builds the fixture after each step and measures every build. */
  def run(
      nativeConfig: NativeConfig,
      steps: Seq[Step],
      keep: Boolean = false
  ): Seq[Measurement] = {
    val baseDir = Files.createTempDirectory("incremental-build-report")
    val sourcesDir = Files.createDirectories(baseDir.resolve("sources"))
    val classesDir = Files.createDirectories(baseDir.resolve("classes"))
    val compiler = NIRCompiler.getCompiler(classesDir)
    val recorder = new RecordingLogger
    val classpath = ScalaNativeBuildInfo.nativeRuntimeClasspath
      .split(File.pathSeparator)
      .filter(_.nonEmpty)
      .map(Paths.get(_))
      .toSeq :+ classesDir
    val config = Config.empty
      .withBaseDir(baseDir.resolve("build"))
      .withModuleName("report")
      .withClassPath(classpath)
      .withMainClass(Some(Fixture.main))
      .withCompilerConfig(nativeConfig)
      .withLogger(recorder)

    try
      steps.map { step =>
        step.files.foreach {
          case (name, content) => IO.write(sourcesDir.resolve(name), content)
        }
        if (step.files.nonEmpty) compiler.compile(sourcesDir)

        val generatedBefore = objectsAndIR(config)
        recorder.clear()
        Scope { implicit in =>
          Await.result(Build.build(config), Duration.Inf)
        }
        val generatedAfter = objectsAndIR(config)
        measure(step, recorder, config, generatedBefore, generatedAfter)
      }
    finally
      if (keep) System.err.println(s"Build directory kept at $baseDir")
      else IO.deleteRecursive(baseDir)
  }

  private def objectsAndIR(config: Config): Map[Path, FileTime] =
    if (!Files.exists(config.workDir)) Map.empty
    else
      (IO.getAll(config.workDir, "glob:**.ll") ++
        IO.getAll(config.workDir, "glob:**.o"))
        .map(path => path -> Files.getLastModifiedTime(path))
        .toMap

  private def measure(
      step: Step,
      recorder: RecordingLogger,
      config: Config,
      before: Map[Path, FileTime],
      after: Map[Path, FileTime]
  ): Measurement = {
    val dependencies = config.workDir.resolve("dependencies")
    def rewritten(path: Path): Boolean =
      !before.get(path).contains(after(path))
    def count(filter: Path => Boolean): (Int, Int) = {
      val files = after.keys.filter(filter)
      (files.size, files.count(rewritten))
    }
    def isNative(path: Path) = path.startsWith(dependencies)
    // Parts of an IR file are merged into it while it is generated
    def isIR(path: Path) = {
      val name = path.getFileName.toString
      name.endsWith(".ll") && !name.endsWith("-body.ll") &&
        !name.endsWith("-metadata.ll")
    }
    val (ir, irRewritten) = count(isIR)
    val (objects, objectsRewritten) =
      count(p => p.toString.endsWith(".o") && !isNative(p))
    val (native, nativeRewritten) =
      count(p => p.toString.endsWith(".o") && isNative(p))
    Measurement(
      step = step,
      phases = recorder.phases,
      irFiles = ir,
      irRegenerated = irRewritten,
      objects = objects,
      objectsRecompiled = objectsRewritten,
      nativeObjects = native,
      nativeObjectsRecompiled = nativeRewritten,
      discovered = recorder.discovered
    )
  }

  /** Phases reported by the toolchain, in pipeline order. */
  val Phases: Seq[(String, String)] = Seq(
    "Linking" -> "reach",
    "Optimizing" -> "optimize",
    "Generating intermediate code" -> "codegen",
    "Compiling to native code" -> "compile",
    "Linking native code" -> "link",
    "Total" -> "total"
  )

  def render(config: NativeConfig, measurements: Seq[Measurement]): String = {
    val sb = new StringBuilder
    def line(s: String) = sb.append(s).append('\n')
    line(
      s"Mode: ${config.mode}, optimize: ${config.optimize}, " +
        s"incremental: ${config.useIncrementalCompilation}, " +
        s"granular: ${config.useGranularBuild}, " +
        s"GC: ${config.gc}, LTO: ${config.lto}"
    )
    line("")
    line(
      "| Step | " + Phases.map(_._2 + " (ms)").mkString(" | ") +
        " | .ll regenerated | .o recompiled | native .o recompiled |"
    )
    line("|---" * (Phases.size + 4) + "|")
    measurements.foreach { m =>
      val times =
        Phases.map { case (phase, _) => m.phase(phase).fold("-")(_.toString) }
      line(
        s"| ${m.step.name} | ${times.mkString(" | ")} | " +
          s"${m.irRegenerated}/${m.irFiles} | " +
          s"${m.objectsRecompiled}/${m.objects} | " +
          s"${m.nativeObjectsRecompiled}/${m.nativeObjects} |"
      )
    }
    line("")
    measurements.foreach { m =>
      line(
        s"- ${m.step.name}: ${m.step.description}" +
          m.discovered.fold("")(d => s" ($d)")
      )
    }
    sb.toString
  }

  /** Collects phase timings logged as `<phase> (<details>) (<n> ms)`. */
  private class RecordingLogger extends Logger {
    private val Timed = """^(.*) \((\d+) ms\)$""".r
    private val Detail = """^(.*?) \(.*\)$""".r
    private val buffer = mutable.ArrayBuffer.empty[(String, Long)]
    @volatile private var lastDiscovered: Option[String] = None

    def clear(): Unit = synchronized {
      buffer.clear()
      lastDiscovered = None
    }
    def phases: Seq[(String, Long)] = synchronized(buffer.toList)
    def discovered: Option[String] = lastDiscovered

    def trace(msg: Throwable): Unit = ()
    def debug(msg: String): Unit = ()
    def info(msg: String): Unit = msg match {
      case Timed(phase, ms) =>
        val name = phase match {
          case Detail(name) => name
          case name         => name
        }
        synchronized(buffer += name -> ms.toLong)
      case _ if msg.startsWith("Discovered ") =>
        lastDiscovered = Some(msg.stripPrefix("Discovered "))
      case _ => ()
    }
    def warn(msg: String): Unit = ()
    def error(msg: String): Unit = System.err.println(s"[error] $msg")
  }
}
