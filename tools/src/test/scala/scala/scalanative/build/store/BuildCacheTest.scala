package scala.scalanative
package build
package store

import java.io.File
import java.nio.file.{Files, Path, Paths}

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

import org.junit.Assert._
import org.junit.Test

import scala.scalanative.buildinfo.ScalaNativeBuildInfo
import scala.scalanative.util.Scope

class BuildCacheTest {

  private def withDir(body: Path => Unit): Unit = {
    val dir = Files.createTempDirectory("sn-build-cache-test")
    try body(dir)
    finally IO.deleteRecursive(dir)
  }

  private def key(name: String) =
    ActionKey.builder("test", 1).add("name", name).result()

  @Test def outputsAreReusedFromWorkDirOrStore(): Unit = withDir { dir =>
    val store = new DiskStore(dir.resolve("store"))
    var executions = 0
    def build(workDir: Path, name: String = "a"): BuildCache.Outcome = {
      Files.createDirectories(workDir)
      val cache = BuildCache(store, workDir, Logger.nullLogger)
      val output = workDir.resolve("out.o")
      try
        cache.run(key(name), Seq("object" -> output)) {
          executions += 1
          IO.write(output, s"compiled $name")
        }
      finally cache.close()
    }
    val first = dir.resolve("first")
    assertEquals(BuildCache.Executed, build(first))
    assertEquals(BuildCache.UpToDate, build(first))
    assertEquals(1, executions)

    // Another build directory finds the result in the store
    val second = dir.resolve("second")
    assertEquals(BuildCache.Restored, build(second))
    assertEquals(
      "compiled a",
      new String(Files.readAllBytes(second.resolve("out.o")))
    )

    // A modified or deleted output is restored
    IO.write(first.resolve("out.o"), "modified output")
    assertEquals(BuildCache.Restored, build(first))
    Files.delete(first.resolve("out.o"))
    assertEquals(BuildCache.Restored, build(first))

    // Another action replaces the output
    assertEquals(BuildCache.Executed, build(first, name = "b"))
    assertEquals(2, executions)
    assertEquals(BuildCache.Restored, build(first, name = "a"))
  }

  @Test def inputDigestsAreReusedOnlyForUnmodifiedFiles(): Unit = withDir {
    dir =>
      val store = new DiskStore(dir.resolve("store"))
      val input = dir.resolve("input.ll")
      IO.write(input, "version 1")
      val oneMinuteAgo = System.currentTimeMillis() - 60000
      Files.setLastModifiedTime(
        input,
        java.nio.file.attribute.FileTime.fromMillis(oneMinuteAgo)
      )
      val first = BuildCache(store, dir, Logger.nullLogger)
      assertEquals(Digest.of("version 1"), first.digest(input))
      first.close()

      // Same size and mtime: the recorded digest is trusted
      IO.write(input, "version 2")
      Files.setLastModifiedTime(
        input,
        java.nio.file.attribute.FileTime.fromMillis(oneMinuteAgo)
      )
      assertEquals(
        Digest.of("version 1"),
        BuildCache(store, dir, Logger.nullLogger).digest(input)
      )

      // Modified: rehashed
      Files.setLastModifiedTime(
        input,
        java.nio.file.attribute.FileTime.fromMillis(oneMinuteAgo + 1000)
      )
      assertEquals(
        Digest.of("version 2"),
        BuildCache(store, dir, Logger.nullLogger).digest(input)
      )
  }

  /* Two builds of the same program in different directories: the second one
   * restores every object compiled by the first instead of compiling it.
   */
  @Test def granularBuildsShareCompiledObjects(): Unit = withDir { dir =>
    val classesDir = Files.createDirectories(dir.resolve("classes"))
    NIRCompiler
      .getCompiler(classesDir)
      .compile(
        """object Main {
          |  def main(args: Array[String]): Unit = println("hello")
          |}""".stripMargin
      )
    val classpath = ScalaNativeBuildInfo.nativeRuntimeClasspath
      .split(File.pathSeparator)
      .filter(_.nonEmpty)
      .map(Paths.get(_))
      .toSeq :+ classesDir

    def build(baseDir: Path): Seq[String] = {
      val compiled = mutable.ArrayBuffer.empty[String]
      val logger = new Logger {
        def trace(msg: Throwable): Unit = ()
        def debug(msg: String): Unit = ()
        def info(msg: String): Unit = ()
        def warn(msg: String): Unit = ()
        def error(msg: String): Unit = System.err.println(msg)
        override def running(cmd: Seq[String]): Unit =
          if (cmd.contains("-c")) compiled.synchronized {
            compiled += cmd(cmd.indexOf("-c") + 1)
          }
      }
      val config = Config.empty
        .withBaseDir(baseDir)
        .withModuleName("app")
        .withClassPath(classpath)
        .withMainClass(Some("Main"))
        .withCompilerConfig(
          IncrementalBuildReport.defaultNativeConfig
            .withMode(Mode.debug)
            .withGranularBuild(true)
        )
        .withLogger(logger)
      Scope { implicit in =>
        Await.result(Build.build(config), Duration.Inf)
      }
      compiled.toList
    }

    val property = "scala.scalanative.cache.dir"
    val previous = Option(System.getProperty(property))
    System.setProperty(property, dir.resolve("store").toString)
    try {
      assumeNoCacheDirInEnvironment()
      val first = build(dir.resolve("first"))
      assertTrue("nothing compiled", first.exists(_.endsWith(".c")))
      assertTrue("no IR compiled", first.exists(_.endsWith(".ll")))
      val second = build(dir.resolve("second"))
      assertEquals("compiled again", Nil, second)
    } finally
      previous match {
        case Some(value) => System.setProperty(property, value)
        case None        => System.clearProperty(property)
      }
  }

  private def assumeNoCacheDirInEnvironment(): Unit =
    org.junit.Assume.assumeTrue(
      "SCALANATIVE_CACHE_DIR overrides the store of the test",
      System.getenv("SCALANATIVE_CACHE_DIR") == null
    )
}
