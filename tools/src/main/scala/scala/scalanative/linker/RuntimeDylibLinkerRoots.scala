package scala.scalanative
package linker

import scala.scalanative.build.{BuildTarget, Config}
import scala.scalanative.nir
import scala.scalanative.util.Scope

/** Extra linker roots for the cached `libscala-native-runtime` shared library.
  *
  * The runtime DSO has no `main`, and [[scala.scalanative.codegen.llvm.CodeGen.depends]]
  * does not reach most of the standard library. Applications linked against stripped
  * runtime NIR can still reference stdlib members that never appear in that dependency
  * set, so we add a small explicit seed list for symbols known to be required across
  * typical app / sandbox links.
  *
  * Longer term this can be replaced by scanning stripped runtime NIR declares and
  * matching them to definitions on the full runtime classpath.
  */
private[linker] object RuntimeDylibLinkerRoots {

  private val jlObject = nir.Global.Top("java.lang.Object")

  private val predefPrintlnAny: nir.Global =
    nir.Global.Member(
      nir.Global.Top("scala.Predef$"),
      nir.Sig.Method(
        "println",
        Seq(nir.Type.Ref(jlObject), nir.Type.Unit)
      )
    )

  private val linktimeInfoIsMultithreadingEnabled: nir.Global =
    nir.Global.Member(
      nir.Global.Top("scala.scalanative.meta.LinktimeInfo$"),
      nir.Sig.Method("isMultithreadingEnabled", Seq(nir.Type.Bool))
    )

  def extraEntryGlobals(config: Config)(implicit scope: Scope): Seq[nir.Global] = {
    val native = config.compilerConfig
    if (native.buildTarget != BuildTarget.LibraryDynamic) return Nil
    if (native.baseName != "scala-native-runtime") return Nil

    Seq(predefPrintlnAny, linktimeInfoIsMultithreadingEnabled)
  }
}
