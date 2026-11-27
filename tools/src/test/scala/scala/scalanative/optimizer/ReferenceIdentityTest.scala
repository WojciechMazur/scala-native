package scala.scalanative
package optimizer

import org.junit.Assert._
import org.junit._

import scala.scalanative.OptimizerSpec
import scala.scalanative.build.Mode

class ReferenceIdentityTest extends OptimizerSpec {

  private val sources = Map(
    "Test.scala" ->
      """|import scala.scalanative.annotation.alwaysinline
         |object Test {
         |  @alwaysinline def foo(): String = "foo"
         |  @noinline def sameLiteral(): Boolean = foo() eq "foo"
         |  @noinline def notSameLiteral(): Boolean = foo() ne "foo"
         |  @noinline def otherLiteral(): Boolean = foo() eq "bar"
         |  @noinline def escapedLiteral(): String = {
         |    val literal = "foo"
         |    println(literal.toCharArray().length) // the chars escape
         |    literal
         |  }
         |  def main(args: Array[String]): Unit = println(
         |    Seq(sameLiteral(), notSameLiteral(), otherLiteral(), escapedLiteral())
         |  )
         |}
         |""".stripMargin
  )

  @Test def stringLiterals(): Unit =
    optimize("Test", sources, _.withMode(Mode.releaseFull)) {
      case (_, result) =>
        def check(name: String, expected: nir.Val) = {
          val defn = optimizedMethod(result.defns, "Test$", name)
          assertEquals(defn.show, Seq(expected), returned(defn))
        }
        check("sameLiteral", nir.Val.True)
        check("notSameLiteral", nir.Val.False)
        check("otherLiteral", nir.Val.False)
        check("escapedLiteral", nir.Val.String("foo"))
    }
}
