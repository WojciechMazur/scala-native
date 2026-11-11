package scala.scalanative
package build

import java.io.File
import java.nio.file.{Files, Path, Paths}

import org.junit.Assert._
import org.junit.Test

import scala.scalanative.buildinfo.ScalaNativeBuildInfo

class BuildPlanTest {

  private def withDir[T](body: Path => T): T = {
    val dir = Files.createTempDirectory("sn-build-plan-test")
    try body(dir)
    finally IO.deleteRecursive(dir)
  }

  private def config(classes: Path, baseDir: Path): Config =
    Config.empty
      .withBaseDir(baseDir)
      .withModuleName("app")
      .withClassPath(
        ScalaNativeBuildInfo.nativeRuntimeClasspath
          .split(File.pathSeparator)
          .filter(_.nonEmpty)
          .map(Paths.get(_))
          .toSeq :+ classes
      )
      .withMainClass(Some("Main"))
      .withCompilerConfig(IncrementalBuildReport.defaultNativeConfig)
      .withLogger(Logger.nullLogger)

  private def compile(classes: Path, greeting: String): Unit =
    NIRCompiler
      .getCompiler(classes)
      .compile(
        s"""object Main {
           |  def main(args: Array[String]): Unit = println("$greeting")
           |}""".stripMargin
      )

  @Test def outputKeyFollowsInputsNotLocations(): Unit = withDir { dir =>
    val classes = Files.createDirectories(dir.resolve("classes"))
    compile(classes, "hello")
    val first = Build.plan(config(classes, dir.resolve("first")))
    assertEquals(
      "same build",
      first.outputKey,
      Build.plan(config(classes, dir.resolve("first"))).outputKey
    )
    assertEquals(
      "other build directory",
      first.outputKey,
      Build.plan(config(classes, dir.resolve("second"))).outputKey
    )

    val releaseConfig = config(classes, dir.resolve("first"))
      .withCompilerConfig(_.withMode(Mode.releaseFast))
    val release = Build.plan(releaseConfig)
    assertNotEquals("config", first.outputKey, release.outputKey)
    assertEquals(
      Seq("config"),
      first.inputs.zip(release.inputs).collect {
        case ((name, a), (_, b)) if a != b => name
      }
    )

    compile(classes, "goodbye")
    val edited = Build.plan(config(classes, dir.resolve("first")))
    assertNotEquals("classpath", first.outputKey, edited.outputKey)
    assertEquals(
      Seq("classpath"),
      first.inputs.zip(edited.inputs).collect {
        case ((name, a), (_, b)) if a != b => name
      }
    )
  }

  @Test def canonicalConfigIgnoresMapOrder(): Unit = {
    val properties = (1 to 10).map(i => s"p$i" -> i)
    val a = NativeConfig.empty.withLinktimeProperties(properties.toMap)
    val b =
      NativeConfig.empty.withLinktimeProperties(properties.reverse.toMap)
    assertEquals(BuildPlan.canonical(a), BuildPlan.canonical(b))
    assertNotEquals(
      BuildPlan.canonical(a),
      BuildPlan.canonical(a.withLinktimeProperties(Map("p1" -> 2)))
    )
    assertTrue(
      BuildPlan
        .canonical(NativeConfig.empty.withClang(Paths.get("/a/clang")))
        .contains("Path(/a/clang)")
    )
  }
}
