package scala.scalanative
package build

import org.junit.Assert._
import org.junit.Test

/* Keeps the incremental build report runnable and checks the invariant it is
 * meant to track: a method body edit in one source directory does not
 * regenerate the LLVM IR of the whole program.
 */
class IncrementalBuildReportTest {
  import IncrementalBuildReport._

  @Test def methodBodyEditRegeneratesFewIRFiles(): Unit = {
    val steps = Fixture.steps.filter(s => Set("cold", "method-body")(s.name))
    val measurements = run(
      defaultNativeConfig.withMode(Mode.debug).withOptimize(false),
      steps
    )
    val cold = measurements(0)
    val body = measurements(1)
    assertTrue("no IR generated", cold.irFiles > 0)
    assertEquals("cold build", cold.irFiles, cold.irRegenerated)
    assertTrue("no total time reported", body.phase("Total").isDefined)
    assertTrue(
      s"method body edit regenerated ${body.irRegenerated}/${body.irFiles} IR files",
      body.irRegenerated < body.irFiles / 2
    )
    assertEquals(
      "native library objects recompiled",
      0,
      body.nativeObjectsRecompiled
    )
  }
}
