package scala.scalanative
package nir

/** Produces header-style NIR by removing method bodies while keeping signatures. */
object StripDefnBodies {

  def apply(defns: Seq[Defn]): Seq[Defn] =
    defns.map {
      case d: Defn.Define =>
        Defn.Declare(d.attrs, d.name, d.ty)(d.pos)
      case other => other
    }
}
