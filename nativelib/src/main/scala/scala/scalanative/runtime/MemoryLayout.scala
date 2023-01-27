package scala.scalanative.runtime

import scala.scalanative.annotation.alwaysinline
import scala.scalanative.runtime.Intrinsics.castRawSizeToInt
import scala.scalanative.meta.LinktimeInfo.isMultithreadingEnabled

object MemoryLayout {

  /** Even though it might seem non-idiomatic to use `def` instead of `final
   *  val` for the constants it actual can faster at runtime. Vals would require
   *  a fieldload operation and loading the module instance. Def would be
   *  evaluated and inlined in the optimizer - it would result with replacing
   *  method call with a constant value.
   */

  private def requiresEnabledMulithreading = throw new IllegalStateException(
    "Field available only in multithreading mode"
  )

  @alwaysinline private def PtrSize = castRawSizeToInt(SizeOfPtr)
  // private[scalanative]
  object Rtti {
    @alwaysinline def ClassOffset = 0
    @alwaysinline def LockWordOffset =
      if (isMultithreadingEnabled) PtrSize
      else requiresEnabledMulithreading
    @alwaysinline def IdOffset =
      if (isMultithreadingEnabled) LockWordOffset + PtrSize
      else PtrSize
    @alwaysinline def TraitIdOffset = IdOffset + 4
    @alwaysinline def NameOffset = TraitIdOffset + 4
    @alwaysinline def SizeOffset = NameOffset + PtrSize
    @alwaysinline def IdRangeEndOffset = SizeOffset + 4
    @alwaysinline def ReferenceMapOffset = IdRangeEndOffset + 4
  }

  // Needed in javalib, though public
  object Object {
    @alwaysinline def RttiOffset = 0
    @alwaysinline def LockWordOffset =
      if (isMultithreadingEnabled) PtrSize
      else requiresEnabledMulithreading
    @alwaysinline def FieldsOffset =
      if (isMultithreadingEnabled) LockWordOffset + PtrSize
      else PtrSize
  }

  private[scalanative] object Array {
    @alwaysinline def RttiOffset = 0
    @alwaysinline def LockWordOffset =
      if (isMultithreadingEnabled) PtrSize
      else requiresEnabledMulithreading
    @alwaysinline def LengthOffset =
      if (isMultithreadingEnabled) LockWordOffset + PtrSize
      else PtrSize
    @alwaysinline def StrideOffset = LengthOffset + 4
    @alwaysinline def ValuesOffset = StrideOffset + 4
  }

}
