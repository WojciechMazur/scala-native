package scala.scalanative
package build
package units

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.jar.{JarEntry, JarOutputStream}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.sys.process._

import org.junit.Assert._
import org.junit.Test

import scala.scalanative.buildinfo.ScalaNativeBuildInfo
import scala.scalanative.util.Scope

/* Library units, compiled apart in granular builds: what they export, what
 * they leave out and how programs using them link.
 */
class UnitCompileTest {

  private def withDir[T](body: Path => T): T = {
    val dir = Files.createTempDirectory("sn-unit-compile-test")
    try body(dir)
    finally IO.deleteRecursive(dir)
  }

  private val library =
    """package lib
      |
      |class Api {
      |  def answer(): Int = helper() + 1
      |  private def helper(): Int = 41
      |  @scala.scalanative.annotation.alwaysinline
      |  def inlined(): Int = inlinedHelper() * 2
      |  private def inlinedHelper(): Int = 21
      |  // Called directly: callers are left out too
      |  final def usesMissing(): Int = missing.Gone.value
      |  def callsMissing(): Int = usesMissing() + 1
      |  // Called through the virtual table: its slot calls a trap
      |  def virtualMissing(): Int = missing.Gone.value
      |  def callsVirtualMissing(): Int = virtualMissing() + 1
      |  def excludedByPattern(): Int = 7
      |}
      |""".stripMargin

  // The library refers to a class that is not on the classpath of programs
  private val missing =
    """package missing
      |object Gone { def value: Int = 1 }
      |""".stripMargin

  private def jar(classes: Path, target: Path): Path = {
    val out = new JarOutputStream(Files.newOutputStream(target))
    try {
      val stream = Files.walk(classes)
      try
        stream.forEach { file =>
          if (Files.isRegularFile(file)) {
            val name = classes.relativize(file).toString.replace('\\', '/')
            out.putNextEntry(new JarEntry(name))
            out.write(Files.readAllBytes(file))
            out.closeEntry()
          }
        }
      finally stream.close()
    } finally out.close()
    target
  }

  private def build(
      dir: Path,
      main: String,
      exclusions: Seq[String] = Nil
  ): (Config, Either[Throwable, Path]) = {
    // Compiled together, then split into the library jar and the program
    val classes = Files.createDirectories(dir.resolve("classes"))
    val sources = Files.createDirectories(dir.resolve("sources"))
    IO.write(sources.resolve("Api.scala"), library)
    IO.write(sources.resolve("Gone.scala"), missing)
    IO.write(sources.resolve("Main.scala"), main)
    NIRCompiler.getCompiler(classes).compile(sources)
    val libClasses = Files.createDirectories(dir.resolve("lib-classes"))
    Files.move(classes.resolve("lib"), libClasses.resolve("lib"))
    // Missing from the classpath of programs
    IO.deleteRecursive(classes.resolve("missing"))
    val libJar = jar(libClasses, dir.resolve("lib.jar"))
    val appClasses = classes

    val classpath = ScalaNativeBuildInfo.nativeRuntimeClasspath
      .split(File.pathSeparator)
      .filter(_.nonEmpty)
      .map(Paths.get(_))
      .toSeq :+ libJar :+ appClasses
    val config = Config.empty
      .withBaseDir(dir.resolve("build"))
      .withModuleName("app")
      .withClassPath(classpath)
      .withMainClass(Some("Main"))
      .withCompilerConfig(
        IncrementalBuildReport.defaultNativeConfig
          .withMode(Mode.debug)
          .withGranularBuild(true)
          .withLibraryExclusions(exclusions)
      )
      .withLogger(Logger.nullLogger)
    val result =
      try
        Right(Scope { implicit in =>
          Await.result(Build.build(config), Duration.Inf)
        })
      catch { case ex: Throwable => Left(ex) }
    config -> result
  }

  // The manifest of the unit made of lib.jar
  private def libraryManifest(config: Config): UnitManifest = {
    val manifests = Files
      .list(config.workDir.resolve("units"))
      .toArray
      .toSeq
      .map(_.asInstanceOf[Path].resolve("unit.exports"))
      .filter(Files.exists(_))
      .map(path =>
        UnitManifest.parse(
          new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
        )
      )
    manifests
      .find(_.defined.exists(_.top.id == "lib.Api"))
      .getOrElse(fail("No unit for lib.jar").asInstanceOf[UnitManifest])
  }

  private def member(manifest: UnitManifest, name: String): Seq[nir.Global] =
    (manifest.defined ++ manifest.excluded.keySet).toSeq.filter { global =>
      global.top.id == "lib.Api" && global.mangle.contains(name)
    }

  private val usesApi =
    """object Main {
      |  def main(args: Array[String]): Unit = {
      |    val api = new lib.Api
      |    println(api.answer() + api.inlined())
      |  }
      |}""".stripMargin

  @Test def exportsPublicMembersAndInlinedPrivates(): Unit = withDir { dir =>
    val (config, result) = build(dir, usesApi)
    val binary = result.fold(throw _, identity)
    assertEquals("84", Process(binary.toString).!!.trim)

    val manifest = libraryManifest(config)
    def exported(name: String) =
      member(manifest, name).forall(manifest.exports.contains)
    def hidden(name: String) =
      member(manifest, name).forall(manifest.hidden.contains)
    assertTrue("public method", exported("answer"))
    assertTrue("private method", hidden("helper"))
    assertTrue(
      "private method of an inlined body",
      exported("inlinedHelper")
    )
    assertTrue(
      "class metadata",
      manifest.exports.exists(g =>
        g.top.id == "lib.Api" && g.mangle.endsWith("G4type")
      )
    )
  }

  @Test def leavesOutDefinitionsUsingMissingSymbols(): Unit = withDir { dir =>
    val (config, result) = build(dir, usesApi)
    assertTrue(result.isRight)
    val manifest = libraryManifest(config)
    val usesMissing = member(manifest, "usesMissing")
    val callsMissing = member(manifest, "callsMissing")
    def describe = manifest.serialize.linesIterator
      .filter(_.contains("lib.Api"))
      .mkString("\n")
    assertTrue(describe, usesMissing.nonEmpty && callsMissing.nonEmpty)
    assertTrue(describe, usesMissing.forall(manifest.excluded.contains))
    // Excluded for calling an excluded method
    assertTrue(
      describe,
      callsMissing.forall(name =>
        manifest.excluded.get(name).exists(_.contains("usesMissing"))
      )
    )
    assertTrue(
      describe,
      member(manifest, "virtualMissing").forall(manifest.excluded.contains)
    )
    assertTrue(
      describe,
      member(manifest, "callsVirtualMissing").forall(manifest.exports.contains)
    )
  }

  @Test def reachingExcludedDefinitionsFailsToLink(): Unit = withDir { dir =>
    val (_, result) = build(
      dir,
      """object Main {
        |  def main(args: Array[String]): Unit =
        |    println(new lib.Api().excludedByPattern())
        |}""".stripMargin,
      exclusions = Seq("lib.Api#excludedByPattern")
    )
    result match {
      case Left(ex: BuildException) =>
        assertTrue(ex.getMessage, ex.getMessage.contains("excludedByPattern"))
        assertTrue(ex.getMessage, ex.getMessage.contains("libraryExclusions"))
      case other => fail(s"Expected a link failure, got $other")
    }
  }
}
