package scala.scalanative.runtime

object MemoryLayout {

  private[scalanative] object Rtti {
    final val ClassOffset = 0
    final val LockWordOffset = ClassOffset + 8
    final val IdOffset = LockWordOffset + 8
    final val TraitIdOffset = IdOffset + 4
    final val NameOffset = TraitIdOffset + 4
    final val SizeOffset = NameOffset + 8
    final val IdRangeEndOffset = SizeOffset + 4
    final val ReferenceMapOffset = IdRangeEndOffset + 4
  }

  // Needed in javalib, though public
  object Object {
    final val RttiOffset = 0
    final val LockWordOffset = RttiOffset + 8
    final val FieldsOffset = LockWordOffset + 8
  }

  private[scalanative] object Array {
    final val RttiOffset = 0
    final val LockWordOffset = RttiOffset + 8
    final val LengthOffset = LockWordOffset + 8
    final val StrideOffset = LengthOffset + 4
    final val ValuesOffset = StrideOffset + 4
  }

}
