package scala.scalanative
package codegen

import org.junit.Assert._
import org.junit.Test

/** Granular builds generate the same code for a library whatever the rest of
 *  the program does with it: here an application adding a subclass and a trait
 *  implementation of library types.
 */
class GranularCodeGenTest extends CodeGenSpec {

  private val library = Map(
    "lib/Shapes.scala" ->
      """package lib
        |
        |trait Named { def name: String = getClass.getSimpleName }
        |abstract class Shape extends Named {
        |  var scale: Double = 1.0
        |  def area: Double
        |  def describe: String = s"$name: ${area * scale}"
        |}
        |final class Square(side: Double) extends Shape {
        |  def area: Double = side * side
        |}
        |object Geometry {
        |  private var calls = 0
        |  def total(shapes: Array[Shape]): Double = {
        |    calls += 1
        |    var sum = 0.0
        |    var i = 0
        |    while (i < shapes.length) { sum += shapes(i).area; i += 1 }
        |    sum
        |  }
        |  def names(items: Array[Named]): String = items.map(_.name).mkString(",")
        |  def isShape(x: AnyRef): Boolean = x.isInstanceOf[Shape]
        |}
        |""".stripMargin
  )

  private val baseMain =
    """object Test {
      |  def main(args: Array[String]): Unit = {
      |    val shapes: Array[lib.Shape] = Array(new lib.Square(2))
      |    println(lib.Geometry.total(shapes))
      |    println(lib.Geometry.names(shapes.map(s => s: lib.Named)))
      |    println(lib.Geometry.isShape(shapes(0)))
      |    println(shapes(0).describe)
      |  }
      |}
      |""".stripMargin

  private val extendedMain =
    """class Circle(radius: Double) extends lib.Shape {
      |  def area: Double = 3.14 * radius * radius
      |  override def name: String = "circle"
      |}
      |class Label(text: String) extends lib.Named {
      |  override def name: String = text
      |}
      |object Test {
      |  def main(args: Array[String]): Unit = {
      |    val shapes: Array[lib.Shape] = Array(new lib.Square(2), new Circle(1))
      |    println(lib.Geometry.total(shapes))
      |    println(lib.Geometry.names(Array(new Label("x"), shapes(1))))
      |    println(lib.Geometry.isShape(shapes(1)))
      |    println(shapes(1).describe)
      |  }
      |}
      |""".stripMargin

  /** Lowered definitions of the library, by name. */
  private def libraryCode(
      main: String,
      granular: Boolean
  ): Map[nir.Global, String] =
    lowered(
      "Test",
      library + ("Test.scala" -> main),
      _.withGranularBuild(granular)
    ) { defns =>
      defns.collect {
        case defn if defn.name.top.id.startsWith("lib.") =>
          defn.name -> defn.show
      }.toMap
    }

  private def changed(granular: Boolean): Seq[nir.Global] = {
    val base = libraryCode(baseMain, granular)
    val extended = libraryCode(extendedMain, granular)
    base.keySet
      .intersect(extended.keySet)
      .filter(name => base(name) != extended(name))
      .toSeq
  }

  @Test def libraryCodeDoesNotDependOnTheApplication(): Unit = {
    val differences = changed(granular = true)
    assertTrue(
      s"library code changed: ${differences.map(_.show).mkString(", ")}",
      differences.isEmpty
    )
  }

  // java.lang.Class is the RTTI emitted by code generation: its fields must
  // follow the RTTI layout, whatever order its source declares them in
  @Test def classFieldsFollowTheRttiLayout(): Unit =
    for (granular <- Seq(false, true))
      optimize(
        "Test",
        library + ("Test.scala" -> baseMain),
        _.withGranularBuild(granular)
      ) {
        case (config, optimized) =>
          implicit val platform: PlatformInfo = PlatformInfo(config)
          val meta = new Metadata(optimized, config, Nil)
          val jlClass =
            optimized.infos(nir.Rt.ClassName).asInstanceOf[linker.Class]
          assertEquals(
            s"granular=$granular",
            nir.Rt.jlClassFields,
            meta.layout(jlClass).entries.map(_.name)
          )
      }

  // Without the option the same change does affect library code, so the
  // test above exercises what granular builds change
  @Test def libraryCodeDependsOnTheApplicationByDefault(): Unit =
    assertTrue(changed(granular = false).nonEmpty)
}
