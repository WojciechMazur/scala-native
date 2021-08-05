package javalib.lang

import java.lang._
import java.io.InputStream
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import scala.io.Source

import org.junit.Assert._
import org.scalanative.testsuite.utils.Platform.isWindows

object ProcessUtils {
  def readInputStream(s: InputStream) = Source.fromInputStream(s).mkString

  final val EOL = System.lineSeparator()

  val resourceDir = {
    val platform = if (isWindows) "windows" else "unix"
    Paths
      .get(
        System.getProperty("user.dir"),
        "unit-tests",
        "shared",
        "src",
        "test",
        "resources",
        "process",
        platform,
        ""
      )
      .toString()
  }

  val scripts = Scripts.values.map(_.filename)

  def processForCommand(args: String*): ProcessBuilder = {
    if (isWindows)
      new ProcessBuilder((Seq("cmd", "/c") ++ args): _*)
    else
      new ProcessBuilder(args: _*)
  }

  def processSleep(seconds: Double): ProcessBuilder = {
    if (isWindows)
      // Use Powershell Start-Sleep, cmd timeout can cause problems and
      // does support only integer seconds arguments
      new ProcessBuilder(
        Seq("powershell", "Start-Sleep", "-Seconds", seconds.toString()): _*
      )
    else processForCommand("sleep", seconds.toString())
  }

  def processForCommand(script: Scripts.Entry, args: String*): ProcessBuilder =
    processForCommand((script.cmd +: args): _*)

  def assertProcessExitOrTimeout(process: Process): Unit = {
    // Suspend execution of the test until either the specified
    // process has exited or a reasonable wait period has timed out.
    //
    // A waitFor() prevents zombie processes and makes the exit value
    // available. A timed waitfor means the test will eventually complete,
    // even if there is a problem with the underlying process.
    //
    // In the normal case, the process will exit within milliseconds or less.
    // The timeout will not increase the expected execution time of the test.
    //
    // Ten seconds is an order of magnitude guess for a "reasonable"
    // completion time.  If a process expected to exit in milliseconds
    // takes that three orders of magnitude longer, it must be reported.

    val tmo = 10
    val tmUnit = TimeUnit.SECONDS

    assertTrue(
      s"Process took more than $tmo ${tmUnit.name} to exit.",
      process.waitFor(tmo, tmUnit)
    )
  }
  implicit class ProcessBuilderOp(pb: ProcessBuilder) {
    def withPath(
        newEntry: String,
        overwrite: Boolean = false
    ): ProcessBuilder = {
      val previousPath = pb.environment().get("PATH")
      val pathDelimiter = if (isWindows) ";" else ":"
      val newPath =
        if (overwrite) newEntry
        else s"$newEntry$pathDelimiter$previousPath"
      pb.environment().put("PATH", newPath)
      pb
    }
  }
  object Scripts {
    final class Entry(name: String, noExt: Boolean = false) {
      def filename =
        if (noExt) name
        else withScriptExt(name)

      def cmd =
        if (isWindows) name
        else filename

      private def withScriptExt(str: String) =
        if (isWindows) str + ".bat"
        else str + ".sh"

    }
    val echo = new Entry("echo")
    val err = new Entry("err")
    val hello = new Entry("hello")
    val ls = new Entry(if (isWindows) "dir" else "ls", noExt = true)

    val values = Set(echo, err, hello, ls)
  }

}
