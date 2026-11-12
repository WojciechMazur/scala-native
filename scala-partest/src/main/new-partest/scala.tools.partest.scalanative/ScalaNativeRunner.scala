package scala.tools.partest.scalanative

import scala.tools.partest.nest
import scala.tools.partest.nest.{AbstractRunner, DirectCompiler, TestInfo}

class ScalaNativeRunner(
    testInfo: TestInfo,
    suiteRunner: AbstractRunner,
    options: ScalaNativePartestOptions
) extends nest.Runner(testInfo, suiteRunner) {

  override def newCompiler: DirectCompiler = {
    new DirectCompiler(this) with ScalaNativeDirectCompiler
  }

  override def extraJavaOptions = {
    super.extraJavaOptions ++ options.javaOptions
  }

  /* With SCALANATIVE_PARTEST_IN_PROCESS, programs are built in this JVM
   * instead of a JVM started for each test, as build tools build them: the
   * toolchain is loaded and compiled by the JIT once, and builds of tests
   * running at once share one build cache in memory.
   */
  override protected def runCommand(
      args: Seq[String],
      outFile: java.io.File
  ): Boolean = {
    val runner = "scala.tools.nsc.MainGenericRunner"
    if (!ScalaNativeRunner.inProcess || !args.contains(runner))
      super.runCommand(args, outFile)
    else {
      args.collect {
        case arg if arg.startsWith("-Dscalanative.partest.") =>
          arg.stripPrefix("-D").split("=", 2) match {
            case Array(key, value) => System.setProperty(key, value)
            case _                 => ()
          }
      }
      val classpath = args(args.indexOf("-classpath") + 1)
      val program =
        args.drop(args.indexOf(runner) + 1).filterNot(_ == "-usejavacp")
      new scala.tools.nsc.MainGenericRunner()
        .process(("-classpath" +: classpath +: program).toArray, Some(outFile))
    }
  }
}

object ScalaNativeRunner {
  val inProcess: Boolean =
    sys.env.get("SCALANATIVE_PARTEST_IN_PROCESS").exists(_.toBoolean)
}
