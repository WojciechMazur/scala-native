package javalib.lang

import java.util.concurrent.TimeUnit
import java.io._
import java.nio.file.Files

import scala.io.Source

import org.junit.Test
import org.junit.Assert._
import org.junit.Assume._
import org.scalanative.testsuite.utils.Platform.isWindows

class ProcessTest {
  import javalib.lang.ProcessUtils._

  @Test def ls(): Unit = {
    val proc =
      if (isWindows) {
        processForCommand(Scripts.ls, "/b", resourceDir).start()
      } else {
        processForCommand(Scripts.ls, resourceDir).start()
      }
    assertProcessExitOrTimeout(proc)
    val out = readInputStream(proc.getInputStream())

    assertEquals(scripts, out.split(EOL).toSet)
  }

  private def checkPathOverride(pb: ProcessBuilder) = {
    val proc = pb.start()
    val out = readInputStream(proc.getInputStream) // must read before exit

    assertProcessExitOrTimeout(proc)

    assertEquals("1", out)
  }

  @Test def pathOverride(): Unit = {
    assumeFalse(
      "Not possible in Windows, would use dir keyword anyway",
      isWindows
    )
    val pb = processForCommand(Scripts.ls, resourceDir)
      .withPath(resourceDir, overwrite = true)
    checkPathOverride(pb)
  }

  @Test def pathPrefixOverride(): Unit = {
    assumeFalse(
      "Not possible in Windows, would use dir keyword anyway",
      isWindows
    )
    val pb = processForCommand(Scripts.ls, resourceDir)
      .withPath(resourceDir, overwrite = false)
    checkPathOverride(pb)
  }

  @Test def inputAndErrorStream(): Unit = {
    val pb = processForCommand(Scripts.err)
      .withPath(resourceDir, overwrite = true)
      .directory(new File(resourceDir))
    val proc = pb.start()

    assertProcessExitOrTimeout(proc)

    assertEquals("foo", readInputStream(proc.getErrorStream))
    assertEquals("bar", readInputStream(proc.getInputStream))
  }

  @Test def inputStreamWritesToFile(): Unit = {
    val file = File.createTempFile(
      "istest",
      ".tmp",
      new File(System.getProperty("java.io.tmpdir"))
    )

    val pb = processForCommand(Scripts.echo.filename)
      .withPath(resourceDir, overwrite = false)
      .redirectOutput(file)

    if (isWindows) {
      // Directory needed or else calling `echo.bat` would be equal to calling `echo bat`
      // Windows resolves PATH env variable as last one, so it prefers keyword in that case,
      // but it resolve current directory as the first one
      // If we would to try `echo` it would use keyword instead of `echo.bat` script
      pb.directory(new File(resourceDir))
    }

    try {
      val proc = pb.start()
      proc.getOutputStream.write(s"hello$EOL".getBytes)
      proc.getOutputStream.write(s"quit$EOL".getBytes)
      proc.getOutputStream.flush()
      proc.getOutputStream.close() // TODO Windows close should not be needed

      assertProcessExitOrTimeout(proc)

      val out = Source.fromFile(file).getLines.mkString(System.lineSeparator())
      assertEquals("hello", out)
    } finally {
      file.delete()
    }
  }

  @Test def outputStreamReadsFromFile(): Unit = {
    val file = File.createTempFile(
      "istest",
      ".tmp",
      new File(System.getProperty("java.io.tmpdir"))
    )
    val pb = processForCommand(Scripts.echo.filename)
      .withPath(resourceDir, overwrite = false)
      .redirectInput(file)

    if (isWindows) {
      // Workaround described in inputStreamWritesToFile
      pb.directory(new File(resourceDir))
    }

    try {
      val os = new FileOutputStream(file)
      os.write(s"hello$EOL".getBytes)
      os.write(s"quit$EOL".getBytes)
      os.flush()

      val proc = pb.start()
      assertProcessExitOrTimeout(proc)

      assertEquals("hello", readInputStream(proc.getInputStream).trim)
    } finally {
      file.delete()
    }
  }

  @Test def redirectErrorStream(): Unit = {
    val proc = processForCommand(Scripts.err)
      .withPath(resourceDir, overwrite = true)
      .redirectErrorStream(true)
      .start()

    assertProcessExitOrTimeout(proc)

    assertEquals("", readInputStream(proc.getErrorStream))
    assertEquals("foobar", readInputStream(proc.getInputStream))
  }

  @Test def waitForWithTimeoutCompletes(): Unit = {
    val proc = processSleep(0.1).start()

    assertTrue(
      "process should have exited but timed out",
      proc.waitFor(1, TimeUnit.SECONDS)
    )
    assertEquals(0, proc.exitValue)
  }

  // Design Notes:
  //   1) The timing on the next few tests is pretty tight and subject
  //      to race conditions.
  //
  //      The waitFor(100, TimeUnit.MILLISECONDS) assumes that the
  //      process has not lived its lifetime by the time it
  //      executes, a race condition.  Just because two instructions are
  //      right next to each other, does not mean they execute without
  //      intervening interruption or significant elapsed time.
  //
  //      This section has been hand tweaked for the __slow__ conditions
  //      of Travis CI. It may still show intermittent failures, requiring
  //      re-tweaking.
  //
  //   2) The code below has zombie process mitigation code.  That is,
  //      It assumes a competent destroyForcibly() and attempts to force
  //      processes which _should_have_ exited on their own to do so.
  //
  //      A number of other tests in this file have the potential to
  //      strand zombie processes and are candidates for a similar fix.

  @Test def waitForWithTimeoutTimesOut(): Unit = {
    val proc = processSleep(2.0).start()

    assertTrue(
      "process should have timed out but exited",
      !proc.waitFor(500, TimeUnit.MILLISECONDS)
    )
    assertTrue("process should be alive", proc.isAlive)

    // await exit code to release resources. Attempt to force
    // hanging processes to exit.
    if (!proc.waitFor(10, TimeUnit.SECONDS))
      proc.destroyForcibly()
  }

  @Test def destroy(): Unit = {
    val proc = processSleep(2.0).start()

    assertTrue("process should be alive", proc.isAlive)
    proc.destroy()
    assertTrue(
      "process should have exited but timed out",
      proc.waitFor(500, TimeUnit.MILLISECONDS)
    )
    assertEquals(0x80 + 9, proc.exitValue) // SIGKILL, excess 128
  }

  @Test def destroyForcibly(): Unit = {
    val proc = processSleep(2.0).start()

    assertTrue("process should be alive", proc.isAlive)
    val p = proc.destroyForcibly()
    assertTrue(
      "process should have exited but timed out",
      p.waitFor(500, TimeUnit.MILLISECONDS)
    )
    assertEquals(0x80 + 9, p.exitValue) // SIGKILL, excess 128
  }

  @Test def shellFallback(): Unit = {
    val proc = processForCommand(Scripts.hello)
      .withPath(resourceDir, overwrite = true)
      .start()

    assertProcessExitOrTimeout(proc)

    assertEquals(s"hello$EOL", readInputStream(proc.getInputStream))
  }
}
