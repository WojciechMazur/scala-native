package scala.scalanative
package optimizer

import org.junit.Assert._
import org.junit._

import scala.scalanative.OptimizerSpec
import scala.scalanative.build.Mode
import scala.scalanative.codegen.Lower
import scala.scalanative.linker.ScopeInfo

class SafeZoneAllocTest extends OptimizerSpec {

  // Allocation in a zone is lowered to a call that no instruction mentions
  @Test def allocImplRemainsReachable(): Unit =
    optimize(
      "Test",
      Map(
        "Test.scala" ->
          """|import scala.language.experimental.captureChecking
             |import scala.scalanative.memory.SafeZone
             |import scala.scalanative.runtime.SafeZoneAllocator.allocate
             |object Test {
             |  class A(val v: Int)
             |  def main(args: Array[String]): Unit =
             |    SafeZone { sz ?=> println(allocate(sz, new A(args.length))) }
             |}
             |""".stripMargin
      ),
      _.withMode(Mode.releaseFull)
    ) {
      case (_, result) =>
        val targets = result
          .infos(Lower.SafeZone.name)
          .asInstanceOf[ScopeInfo]
          .targets(Lower.safeZoneAllocImpl.sig)
        val defined = result.defns.map(_.name).toSet
        assertTrue(
          s"allocImpl targets: $targets",
          targets.nonEmpty && targets.forall(defined.contains)
        )
    }
}
