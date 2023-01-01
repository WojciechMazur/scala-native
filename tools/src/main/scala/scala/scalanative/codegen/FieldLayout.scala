package scala.scalanative
package codegen

import scalanative.nir._
import scalanative.linker.{Class, Field}

class FieldLayout(cls: Class)(implicit meta: Metadata) {

  def index(fld: Field) = entries.indexOf(fld) + meta.ObjectHeaderFieldsCount
  val entries: Seq[Field] = {
    val base = cls.parent.fold {
      Seq.empty[Field]
    } { parent => meta.layout(parent).entries }
    base ++ cls.members.collect { case f: Field => f }
  }
  val struct: Type.StructValue = {
    val data = entries.map(_.ty)
    Type.StructValue(meta.ObjectHeader ++ data)
  }
  val layout = MemoryLayout(struct.tys, meta.config.is32BitPlatform)
  val size = layout.size
  val referenceOffsetsTy =
    Type.StructValue(Seq(Type.Ptr))
  val referenceOffsetsValue =
    Val.StructValue(
      Seq(Val.Const(Val.ArrayValue(Type.Long, layout.offsetArray)))
    )
}
