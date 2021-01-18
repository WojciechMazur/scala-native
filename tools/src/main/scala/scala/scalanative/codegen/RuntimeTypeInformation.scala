package scala.scalanative
package codegen

import scalanative.nir._
import scalanative.linker.{ScopeInfo, Class, Trait}

class RuntimeTypeInformation(meta: Metadata, info: ScopeInfo) {
  val name: Global      = info.name.member(Sig.Generated("type"))
  val const: Val.Global = Val.Global(name, Type.Ptr)
  val struct: Type.StructValue = info match {
    case cls: Class =>
      val dynmap =
        if (meta.linked.dynsigs.isEmpty) {
          Seq.empty
        } else {
          Seq(meta.dynmap(cls).ty)
        }
      Type.StructValue(
        Seq(
          Rt.Type,
          Type.Int, // size
          Type.Int, // idRangeUntil
          Type.Ptr, // type metadata
          meta.layout(cls).referenceOffsetsTy
        ) ++ dynmap ++ Seq(
          meta.vtable(cls).ty
        )
      )
    case _ =>
      Rt.Type
  }
  val value: Val.StructValue = {
    val typeId = Val.Int(info match {
      case _: Class => meta.ids(info)
      case _: Trait => -(meta.ids(info) + 1)
    })
    val typeStr = Val.String(info.name.asInstanceOf[Global.Top].id)
    val traitId = Val.Int(info match {
      case info: Class =>
        meta.dispatchTable.traitClassIds.getOrElse(info, -1)
      case _ =>
        -1
    })
    val base = Val.StructValue(
      Seq(typeId, traitId, typeStr, Val.Null)
    )

    val metadata = info.attrs.struct.fold(Val.Null: Val) { attr =>
      val asStruct = Type.StructValue(attr.tys)
      val structInfo = Val.StructValue(
        Seq(
          Val.Int(MemoryLayout.sizeOf(asStruct).toInt),
          Val.Int(MemoryLayout.alignmentOf(asStruct).toInt)
        ))
      Val.Const(structInfo)
    }

    info match {
      case cls: Class =>
        val dynmap =
          if (meta.linked.dynsigs.isEmpty) {
            Seq.empty
          } else {
            Seq(meta.dynmap(cls).value)
          }
        val range = meta.ranges(cls)
        Val.StructValue(
          Seq(
            base,
            Val.Int(meta.layout(cls).size.toInt),
            Val.Int(range.last),
            metadata,
            meta.layout(cls).referenceOffsetsValue
          ) ++ dynmap ++ Seq(
            meta.vtable(cls).value
          )
        )
      case _ =>
        base
    }
  }
}
