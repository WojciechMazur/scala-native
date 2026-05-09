package scala.scalanative
package linker

import org.junit.Assert._
import org.junit.Test

import scala.scalanative.nir

class UnreachableReferenceIgnoresSuite {

  @Test def ownerPrefixMatches(): Unit = {
    val b = UnreachableReferenceIgnores.Bundle(
      ownerPrefixes = Seq("scala.jdk."),
      exactCallers = Set.empty
    )
    val caller = nir.Global.Member(
      nir.Global.Top("scala.jdk.javaapi.DurationConverters$"),
      nir.Sig.Method("toJava", Seq(nir.Type.Unit))
    )
    assertTrue(b.suppressesCaller(caller))
    assertFalse(
      b.suppressesCaller(
        nir.Global.Member(
          nir.Global.Top("scala.Predef$"),
          nir.Sig.Method("println", Seq(nir.Type.Unit))
        )
      )
    )
  }
}
