package scala.scalanative
package build
package units

import java.io.File
import java.nio.file.{Files, Path, Paths}

import org.junit.Assert._
import org.junit.Test

import scala.scalanative.build.store.Digest
import scala.scalanative.buildinfo.ScalaNativeBuildInfo
import scala.scalanative.io.VirtualDirectory
import scala.scalanative.linker.ClassPath
import scala.scalanative.util.Scope

class LibraryTest {

  private def withDir[T](body: Path => T): T = {
    val dir = Files.createTempDirectory("sn-library-test")
    try body(dir)
    finally IO.deleteRecursive(dir)
  }

  private lazy val runtimeClasspath: Seq[Path] =
    ScalaNativeBuildInfo.nativeRuntimeClasspath
      .split(File.pathSeparator)
      .filter(_.nonEmpty)
      .map(Paths.get(_))
      .toSeq

  private def derive(entry: Path, dir: Path)(implicit
      scope: Scope
  ): Library.Reader = {
    val target = dir.resolve(entry.getFileName().toString + ".snl")
    DeriveLibrary(entry, Digest.of(entry.toString), target, Logger.nullLogger)
    val reader = Library.Reader.open(target, origin = entry).get
    scope.acquire(reader)
    reader
  }

  /* Every class of the runtime loads the same definitions, attributed to the
   * same NIR file, from its library as from its jar.
   */
  @Test def librariesLoadWhatJarsLoad(): Unit = withDir { dir =>
    Scope { implicit in =>
      runtimeClasspath.foreach { jar =>
        val fromJar = ClassPath(VirtualDirectory.real(jar))
        val library = derive(jar, dir)
        val fromLibrary = ClassPath(library)
        assertEquals(
          s"entry points of $jar",
          fromJar.classesWithEntryPoints.toSet,
          fromLibrary.classesWithEntryPoints.toSet
        )
        assertEquals(
          s"services of $jar",
          fromJar.definedServicesProviders,
          fromLibrary.definedServicesProviders
        )
        library.index.classes.foreach { cls =>
          assertTrue(fromJar.contains(cls.name))
          val expected = fromJar.load(cls.name).get
          val actual = fromLibrary.load(cls.name).get
          assertEquals(cls.name.id, expected.map(_.show), actual.map(_.show))
          expected.zip(actual).foreach {
            case (e, a) =>
              // NIR file paths of jars belong to the jar's file system,
              // libraries name them with the same text
              assertEquals(e.pos.source, a.pos.source)
              assertEquals(e.pos.line, a.pos.line)
              assertEquals(e.pos.column, a.pos.column)
              assertEquals(e.pos.nirSource.directory, a.pos.nirSource.directory)
              assertEquals(
                e.pos.nirSource.path.toString,
                a.pos.nirSource.path.toString
              )
          }
        }
      }
    }
  }

  private def abiOf(source: String): Map[String, Digest] = withDir { dir =>
    NIRCompiler.getCompiler(dir).compile(source)
    Scope { implicit in =>
      val library = derive(dir, Files.createTempDirectory(dir, "lib"))
      library.index.classes.map(c => c.name.id -> c.abiDigest).toMap
    }
  }

  @Test def abiIgnoresBodiesAndLines(): Unit = {
    val original = abiOf(
      """class A {
        |  val x: Int = 1
        |  val y: String = "y"
        |  def f(a: Int): Int = a + 1
        |  @scala.scalanative.annotation.alwaysinline def g: Int = 2
        |}""".stripMargin
    )
    def assertAbi(change: String, same: Boolean, source: String) = {
      val changed = abiOf(source)
      if (same) assertEquals(change, original("A"), changed("A"))
      else assertNotEquals(change, original("A"), changed("A"))
    }
    assertAbi(
      "body edit",
      same = true,
      """class A {
        |  val x: Int = 1
        |  val y: String = "y"
        |  def f(a: Int): Int = a * 2 + 3
        |  @scala.scalanative.annotation.alwaysinline def g: Int = 2
        |}""".stripMargin
    )
    assertAbi(
      "moved lines",
      same = true,
      """
        |
        |class A {
        |
        |  val x: Int = 1
        |  val y: String = "y"
        |  def f(a: Int): Int = a + 1
        |  @scala.scalanative.annotation.alwaysinline def g: Int = 2
        |}""".stripMargin
    )
    assertAbi(
      "signature change",
      same = false,
      """class A {
        |  val x: Int = 1
        |  val y: String = "y"
        |  def f(a: Long): Int = a.toInt + 1
        |  @scala.scalanative.annotation.alwaysinline def g: Int = 2
        |}""".stripMargin
    )
    assertAbi(
      "field order",
      same = false,
      """class A {
        |  val y: String = "y"
        |  val x: Int = 1
        |  def f(a: Int): Int = a + 1
        |  @scala.scalanative.annotation.alwaysinline def g: Int = 2
        |}""".stripMargin
    )
    assertAbi(
      "inlined body",
      same = false,
      """class A {
        |  val x: Int = 1
        |  val y: String = "y"
        |  def f(a: Int): Int = a + 1
        |  @scala.scalanative.annotation.alwaysinline def g: Int = 3
        |}""".stripMargin
    )
  }

  private def library(classes: (String, Seq[String])*): LibraryIndex =
    LibraryIndex(
      digest = Digest.of(classes.toString),
      abiDigest = Digest.of(classes.toString),
      classes = classes.map {
        case (name, references) =>
          ClassIndex(
            nir.Global.Top(name),
            s"$name.nir",
            hasEntryPoints = false,
            digest = Digest.of(name),
            abiDigest = Digest.of(name),
            references = references.map(nir.Global.Top(_))
          )
      },
      services = Map.empty
    )

  @Test def cyclicEntriesFormOneUnit(): Unit = {
    val a = Paths.get("a.jar") -> library("A" -> Seq("B", "Unknown"))
    val b = Paths.get("b.jar") -> library("B" -> Seq("A1"), "A1" -> Nil)
    val c = Paths.get("c.jar") -> library("C" -> Seq("A", "D"))
    val d = Paths.get("d.jar") -> library("D" -> Nil, "A" -> Seq("C"))
    val units = UnitPartitioner(Seq(a, b, c, d))
    // `A` of d.jar is shadowed by a.jar: d.jar does not refer to c.jar
    assertEquals(
      Seq(Seq("b.jar"), Seq("a.jar"), Seq("d.jar"), Seq("c.jar")).map(_.toSet),
      units.map(_.entries.map(_._1.toString).toSet)
    )
    val cyclic = UnitPartitioner(
      Seq(a, Paths.get("b.jar") -> library("B" -> Seq("A")), c, d)
    )
    assertEquals(
      Seq(Set("a.jar", "b.jar"), Set("d.jar"), Set("c.jar")),
      cyclic.map(_.entries.map(_._1.toString).toSet)
    )
    assertEquals(Seq(Nil, Nil, Seq(0, 1)), cyclic.map(_.dependencies))
  }

  @Test def runtimeUnitsFollowDependencies(): Unit = withDir { dir =>
    Scope { implicit in =>
      val classpath = runtimeClasspath.map(e => e -> derive(e, dir).index)
      val units = UnitPartitioner(classpath)
      assertEquals(
        "every entry in one unit",
        classpath.map(_._1).toSet,
        units.flatMap(_.entries.map(_._1)).toSet
      )
      units.zipWithIndex.foreach {
        case (unit, index) =>
          assertTrue(unit.dependencies.forall(_ < index))
      }
      def unitOf(name: String) =
        units.indexWhere(
          _.entries.exists(_._1.getFileName.toString.startsWith(name))
        )
      // The runtime refers to the Java library and the other way around
      assertEquals(unitOf("nativelib"), unitOf("javalib"))
    }
  }
}
