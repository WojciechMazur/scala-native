package javalib.lang

import java.lang._

import java.util.concurrent.TimeUnit
import java.io.File

import org.junit.Test
import org.junit.Assert._
import org.junit.Assume._
import scala.scalanative.runtime.Platform.isWindows

class RuntimeTest {
  import ProcessUtils._
  private val EOL = System.lineSeparator()
  private def lsCommand =
    if (isWindows)
      Array("cmd", "/c", "dir", "/b")
    else
      Array("ls")

  @Test def execCommand(): Unit = {
    val proc = Runtime.getRuntime.exec(lsCommand :+ resourceDir)
    val out = readInputStream(proc.getInputStream)
    assertTrue(proc.waitFor(5, TimeUnit.SECONDS))
    assertEquals(Scripts.values.map(_.filename), out.split(EOL).toSet)
  }
  @Test def execEnvp(): Unit = {
    assumeFalse(
      "Not possible in Windows, would use dir keyword anyway",
      isWindows
    )
    val envp = Array(s"PATH=$resourceDir")
    val proc = Runtime.getRuntime.exec(Array("ls"), envp)
    val out = readInputStream(proc.getInputStream)
    assertTrue(proc.waitFor(5, TimeUnit.SECONDS))
    assertTrue(out == "1")
  }
  @Test def execDir(): Unit = {
    val proc =
      Runtime.getRuntime.exec(lsCommand, null, new File(resourceDir))
    val out = readInputStream(proc.getInputStream)
    assertTrue(proc.waitFor(5, TimeUnit.SECONDS))
    assertEquals(Scripts.values.map(_.filename), out.split(EOL).toSet)

  }
}
