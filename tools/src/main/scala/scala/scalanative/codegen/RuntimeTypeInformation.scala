package scala.scalanative
package codegen

import scalanative.util.unreachable
import scalanative.nir._
import scalanative.linker.{ScopeInfo, Class, Trait}

class RuntimeTypeInformation(info: ScopeInfo)(implicit meta: Metadata) {
  val name: Global = info.name.member(Sig.Generated("type"))
  val const: Val.Global = Val.Global(name, Type.Ptr)
  val struct: Type.StructValue = info match {
    case cls: Class =>
      val dynmap =
        if (meta.linked.dynsigs.isEmpty) Nil
        else List(meta.dynmap(cls).ty)
      Type.StructValue(
        meta.Rtti ::
          Type.Int :: // size
          Type.Int :: // idRangeUntil
          meta.layout(cls).referenceOffsetsTy ::
          dynmap :::
          meta.vtable(cls).ty :: Nil
      )
    case _ =>
      meta.Rtti
  }
  val value: Val.StructValue = {
    val typeId = Val.Int(info match {
      case _: Class => meta.ids(info)
      case _: Trait => -(meta.ids(info) + 1)
    })
    val typeStr = Val.String(info.name.asInstanceOf[Global.Top].id)
    val traitId = Val.Int(info match {
      case info: Class =>
        meta.dispatchTable.traitClassIds.get(info).getOrElse(-1)
      case _ =>
        -1
    })
    val classConst =
      Val.Global(Rt.Class.name.member(Sig.Generated("type")), Type.Ptr)
    val base = Val.StructValue(
      classConst :: meta.lockWordField ::: typeId :: traitId :: typeStr :: Nil
    )
    info match {
      case cls: Class =>
        val dynmap =
          if (meta.linked.dynsigs.isEmpty) Nil
          else List(meta.dynmap(cls).value)
        val range = meta.ranges(cls)
        Val.StructValue(
          base ::
            Val.Int(meta.layout(cls).size.toInt) ::
            Val.Int(range.last) ::
            meta.layout(cls).referenceOffsetsValue ::
            dynmap :::
            meta.vtable(cls).value :: Nil
        )
      case _ =>
        base
    }
  }
}
