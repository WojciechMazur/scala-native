package scala.scalanative.runtime

object MemoryLayout {

  private[scalanative] object Rtti {
    final val ClassOffset        = 0
    final val LockWordOffset     = 8
    final val IdOffset           = 16
    final val TraitIdOffset      = 20
    final val NameOffset         = 24
    final val SizeOffset         = 32
    final val IdRangeEndOffset   = 36
    final val ReferenceMapOffset = 40
  }

  // Needed in javalib, though public
  object Object {
    final val RttiOffset     = 0
    final val LockWordOffset = 8
    final val FieldsOffset   = 16
  }

  private[scalanative] object Array {
    final val RttiOffset     = 0
    final val LockWordOffset = 8
    final val LengthOffset   = 16
    final val StrideOffset   = 20
    final val ValuesOffset   = 24
  }

}
