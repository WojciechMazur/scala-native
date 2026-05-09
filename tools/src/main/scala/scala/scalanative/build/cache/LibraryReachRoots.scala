package scala.scalanative
package build
package cache

import scala.scalanative.nir

/** Entry points for a per-library closed-world reachability pass. */
private[scalanative] object LibraryReachRoots {

  /** Public methods (non-private in NIR), constructors, clinit, and explicit service provider classes. */
  def fromDefns(
      defns: Seq[nir.Defn],
      serviceProviderTops: Set[nir.Global.Top]
  ): Seq[nir.Global] = {
    val buf = scala.collection.mutable.ArrayBuffer.empty[nir.Global]
    defns.foreach {
      case nir.Defn.Define(_, name: nir.Global.Member, _, _, _) =>
        name.sig.unmangled match {
          case _: nir.Sig.Ctor =>
            buf += name
          case _: nir.Sig.Method if !name.sig.isPrivate =>
            buf += name
          case nir.Sig.Clinit =>
            buf += name
          case _ => ()
        }
      case nir.Defn.Declare(_, name: nir.Global.Member, _) =>
        name.sig.unmangled match {
          case _: nir.Sig.Ctor =>
            buf += name
          case _: nir.Sig.Method if !name.sig.isPrivate =>
            buf += name
          case nir.Sig.Clinit =>
            buf += name
          case _ => ()
        }
      case _ => ()
    }
    serviceProviderTops.foreach { top =>
      buf += top
      buf += top.member(nir.Sig.Ctor(Seq.empty))
    }
    buf.distinct.toSeq
  }
}
