package java.lang

import java.io.InputStream

import scala.io.Source
import scala.scalanative.runtime.Platform.isWindows
import java.nio.file.Paths
import java.io.BufferedReader
import java.io.InputStreamReader

object ProcessUtils {
  def readInputStream(s: InputStream) = Source.fromInputStream(s).mkString

  def processForCommand(args: String*): ProcessBuilder = {
    if (isWindows)
      new ProcessBuilder((Seq("cmd", "/c") ++ args): _*)
    else
      new ProcessBuilder(args: _*)
  }

  def processForCommand(script: Scripts.Entry, args: String*): ProcessBuilder =
    processForCommand((script.cmd +: args): _*)

  implicit class ProcessBuilderOp(pb: ProcessBuilder) {
    def withPath(newEntry: String,
                 overwrite: Boolean = false): ProcessBuilder = {
      val previousPath  = pb.environment().get("PATH")
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
    val echo  = new Entry("echo")
    val err   = new Entry("err")
    val hello = new Entry("hello")
    val ls    = new Entry(if (isWindows) "dir" else "ls", noExt = true)

    val values = Set(echo, err, hello, ls)
  }

  val resourceDir = {
    val platform = if (isWindows) "windows" else "unix"
    Paths
      .get(System.getProperty("user.dir"),
           "unit-tests",
           "src",
           "test",
           "resources",
           "process",
           platform,
           "")
      .toString()
  }
}
