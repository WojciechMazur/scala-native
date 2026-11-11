package scala.scalanative
package codegen

import scalanative.linker.{Class, Field}

private[codegen] class FieldLayout(cls: Class)(implicit meta: Metadata) {

  import meta.layouts.{ArrayHeader, Object, ObjectHeader}
  import meta.platform

  def index(fld: Field) = entries.indexOf(fld) + Object.ValuesOffset
  // Proxy fields due to cyclic dependency
  def entries: Seq[Field] = entries0
  def layout: MemoryLayout = layout0

  private lazy val (entries0, layout0) = {
    val entries: Seq[Field] = {
      val base = cls.parent.fold {
        Seq.empty[Field]
      } { parent => meta.layout(parent).entries }
      base ++ ownFields
    }
    val usesCustomAlignment = entries.exists(_.attrs.align.isDefined)
    val isArray = nir.Type.isArray(cls.name)
    if (usesCustomAlignment) {
      assert(!isArray) // Only regular object can have custom alignmet
      val fields = entries.sortBy(_.attrs.align.flatMap(_.group))
      val layout = MemoryLayout.ofAlignedFields(fields)
      (fields, layout)
    } else {
      val rttiHeader = if (isArray) ArrayHeader else ObjectHeader
      val layout = MemoryLayout(rttiHeader.layout +: entries.map(_.ty))
      (entries, layout)
    }
  }

  /* Fields of the class itself: the reached ones, in the order they were
   * reached, or in granular builds every declared field, in source order, so
   * the layout does not depend on the rest of the program. Classes whose layout the runtime mirrors keep their canonical
   * field order, which code generation otherwise gets by reaching them first.
   */
  private def ownFields: Seq[Field] =
    if (!meta.granular) cls.members.collect { case f: Field => f }.toSeq
    else {
      // Source order; fields without a position, such as generated ones,
      // follow in NIR order
      val declared = meta.analysis
        .declaredFields(cls)
        .zipWithIndex
        .sortBy {
          case (defn, index) =>
            val pos = defn.pos
            if (pos.isEmpty) (1, 0, 0, index)
            else (0, pos.line, pos.column, index)
        }
        .map(_._1)
      val canonical = FieldLayout.canonicalOrder.getOrElse(cls.name, Nil)
      val ordered =
        canonical.flatMap(name => declared.find(_.name == name)) ++
          declared.filterNot(defn => canonical.contains(defn.name))
      ordered.map { defn =>
        meta.analysis.infos.get(defn.name) match {
          case Some(field: Field) => field
          case _                  =>
            val nir.Defn.Var(attrs, name: nir.Global.Member, ty, rhs) =
              defn: @unchecked
            new Field(attrs, cls, name, isConst = false, ty, rhs)(defn.pos)
        }
      }
    }

  val struct = nir.Type.StructValue(layout.tys.map(_.ty))
  val size = layout.size
  /* Reference map used by the GC: offsets of reference fields, -1, then
   * offsets of raw pointer fields to scan conservatively, -1. Only boxed
   * pointers (unsafe.Ptr) scan raw pointers: one can point into the heap, for
   * example to an array element, and be the only thing keeping it alive.
   */
  val referenceOffsetsValue = {
    val conservative =
      if (cls.name == nir.Rt.BoxedPtr.name) layout.rawPointerFieldsOffsets
      else Nil
    val offsets = layout.referenceFieldsOffsets ++ (-1 +: conservative) :+ -1
    nir.Val.Const(nir.Val.ArrayValue(nir.Type.Int, offsets.map(nir.Val.Int(_))))
  }

}

private[codegen] object FieldLayout {

  /** Field order of classes whose layout is fixed elsewhere: java.lang.Class
   *  mirrors the RTTI emitted by code generation, java.lang.String is read by
   *  the C runtime.
   */
  val canonicalOrder: Map[nir.Global, Seq[nir.Global]] = Map(
    nir.Rt.ClassName -> nir.Rt.jlClassFields,
    nir.Rt.StringName -> Seq(
      nir.Rt.StringValueName,
      nir.Rt.StringOffsetName,
      nir.Rt.StringCountName,
      nir.Rt.StringCachedHashCodeName
    )
  )
}
