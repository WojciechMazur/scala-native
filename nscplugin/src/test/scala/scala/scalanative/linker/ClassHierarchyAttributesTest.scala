package scala.scalanative
package linker

import org.junit.Assert._
import org.junit.Test

/* Final and Sealed allow the toolchain to devirtualize calls without
 * whole-program knowledge, so they must never be set for definitions that can
 * be extended or overridden.
 */
class ClassHierarchyAttributesTest {

  private val source =
    """|sealed trait Shape { def area: Int; final def twice: Int = area * 2 }
       |final case class Square(side: Int) extends Shape { def area: Int = side * side }
       |case class Rect(w: Int, h: Int) extends Shape { def area: Int = w * h }
       |sealed abstract class Expr { def eval: Int }
       |class Open { def overridable: Int = 1; final def fixed: Int = 2 }
       |final class Closed extends Open {
       |  override def overridable: Int = 3
       |  class Nested { def nestedMethod: Int = 4 }
       |  class NestedChild extends Nested
       |}
       |object Holder {
       |  def inObject: Int = 5
       |  val anon: Open = new Open { override def overridable: Int = 6 }
       |}
       |""".stripMargin

  private def withDefns(fn: Seq[nir.Defn] => Unit): Unit =
    compileAndLoad("Test.scala" -> source)(fn)

  private def classAttrs(defns: Seq[nir.Defn], name: String): nir.Attrs =
    defns
      .collectFirst {
        case defn @ (_: nir.Defn.Class | _: nir.Defn.Module | _: nir.Defn.Trait)
            if defn.name == nir.Global.Top(name) =>
          defn.attrs
      }
      .getOrElse(throw new AssertionError(s"Class $name not found"))

  private def methodAttrs(
      defns: Seq[nir.Defn],
      owner: String,
      method: String
  ): nir.Attrs =
    defns
      .collectFirst {
        case defn @ nir.Defn.Define(_, nir.Global.Member(top, sig), _, _, _)
            if top.id == owner && (sig.unmangled match {
              case nir.Sig.Method(`method`, _, _) => true
              case _                              => false
            }) =>
          defn.attrs
      }
      .getOrElse(throw new AssertionError(s"Method $owner.$method not found"))

  private def assertFinal(what: String, expected: Boolean, attrs: nir.Attrs) =
    assertEquals(s"$what final", expected, attrs.isFinal)

  private def assertSealed(what: String, expected: Boolean, attrs: nir.Attrs) =
    assertEquals(s"$what sealed", expected, attrs.isSealed)

  @Test def finalClasses(): Unit = withDefns { defns =>
    assertFinal("Square", true, classAttrs(defns, "Square"))
    assertFinal("Closed", true, classAttrs(defns, "Closed"))
    assertFinal("Holder$", true, classAttrs(defns, "Holder$"))
    val anon = defns.collectFirst {
      case defn: nir.Defn.Class
          if defn.name.id.startsWith("Holder$") && defn.name.id.contains(
            "anon"
          ) =>
        defn
    }
    assertTrue("anonymous class not found", anon.isDefined)
    assertFinal("anonymous class", true, anon.get.attrs)
  }

  @Test def extensibleClasses(): Unit = withDefns { defns =>
    assertFinal("Rect", false, classAttrs(defns, "Rect"))
    assertFinal("Open", false, classAttrs(defns, "Open"))
    assertFinal("Expr", false, classAttrs(defns, "Expr"))
    assertFinal("Shape", false, classAttrs(defns, "Shape"))
    // Can be extended by other classes nested in Closed
    assertFinal("Closed$Nested", false, classAttrs(defns, "Closed$Nested"))
  }

  @Test def sealedClasses(): Unit = withDefns { defns =>
    assertSealed("Shape", true, classAttrs(defns, "Shape"))
    assertSealed("Expr", true, classAttrs(defns, "Expr"))
    assertSealed("Square", false, classAttrs(defns, "Square"))
    assertSealed("Open", false, classAttrs(defns, "Open"))
  }

  @Test def finalMethods(): Unit = withDefns { defns =>
    assertFinal("Open.fixed", true, methodAttrs(defns, "Open", "fixed"))
    assertFinal("Shape.twice", true, methodAttrs(defns, "Shape", "twice"))
    assertFinal("Square.area", true, methodAttrs(defns, "Square", "area"))
    assertFinal(
      "Closed.overridable",
      true,
      methodAttrs(defns, "Closed", "overridable")
    )
    assertFinal(
      "Holder.inObject",
      true,
      methodAttrs(defns, "Holder$", "inObject")
    )
  }

  @Test def overridableMethods(): Unit = withDefns { defns =>
    assertFinal(
      "Open.overridable",
      false,
      methodAttrs(defns, "Open", "overridable")
    )
    assertFinal("Rect.area", false, methodAttrs(defns, "Rect", "area"))
    assertFinal(
      "Closed$Nested.nestedMethod",
      false,
      methodAttrs(defns, "Closed$Nested", "nestedMethod")
    )
  }
}
