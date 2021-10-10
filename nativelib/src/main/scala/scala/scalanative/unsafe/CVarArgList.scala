package scala.scalanative
package unsafe

import scala.language.implicitConversions
import scalanative.unsigned._
import scalanative.runtime.{intrinsic, RawPtr, toRawPtr, libc, LongArray}
import scalanative.meta.LinktimeInfo._

/** Type of a C-style vararg list (va_list in C). */
final class CVarArgList private[scalanative] (
    private[scalanative] val rawptr: RawPtr
)

object CVarArgList {
  // Implementation below is based on VarArgs.swift from apple/swift repo.
  // Currently we only support X86_64, we'll need to revisit the code below
  // if and when we add any more officially supported architectures.

  private type Header =
    CStruct4[CUnsignedInt, CUnsignedInt, Ptr[Long], Ptr[Long]]
  private implicit class HeaderOps(val ptr: Ptr[Header]) extends AnyVal {
    def gpOffset: CUnsignedInt = ptr._1
    def fpOffset: CUnsignedInt = ptr._2
    def overflowArgArea: Ptr[Long] = ptr._3
    def regSaveArea: Ptr[Long] = ptr._4

    def gpOffset_=(value: CUnsignedInt): Unit = ptr._1 = value
    def fpOffset_=(value: CUnsignedInt): Unit = ptr._2 = value
    def overflowArgArea_=(value: Ptr[Long]): Unit = ptr._3 = value
    def regSaveArea_=(value: Ptr[Long]): Unit = ptr._4 = value
  }

  private final val countGPRegisters = 6
  private final val countFPRegisters = 8
  private final val fpRegisterWords = 2
  private final val registerSaveWords =
    countGPRegisters + countFPRegisters * fpRegisterWords

  /** Construct C-style vararg list from Scala sequence. */
  private[scalanative] def fromSeq(
      varargs: Seq[CVarArg]
  )(implicit z: Zone): CVarArgList = {
    if (isWindows) {
      toCVarArgList_X86_64_Windows(varargs)
    } else {
      if (!is32BitPlatform) {
        toCVarArgList_X86_64_Unix(varargs)
      } else {
        toCVarArgList_X86_Unix(varargs)
      }
    }
  }

  @inline
  private def isPassedAsDouble(vararg: CVarArg): Boolean =
    vararg.tag == Tag.Float || vararg.tag == Tag.Double

  private def encode[T](value: T)(implicit tag: Tag[T]): Array[Long] =
    value match {
      case value: Byte =>
        encode(value.toLong)
      case value: Short =>
        encode(value.toLong)
      case value: Int =>
        encode(value.toLong)
      case value: UByte =>
        encode(value.toULong)
      case value: UShort =>
        encode(value.toULong)
      case value: UInt =>
        encode(value.toULong)
      case value: Float =>
        encode(value.toDouble)
      case _ =>
        val count =
          ((sizeof(tag) + sizeof[Long] - 1.toUSize) / sizeof[Long]).toInt
        val words = new Array[Long](count)
        val start = words.asInstanceOf[LongArray].at(0).asInstanceOf[Ptr[T]]
        tag.store(start, value)
        words
    }

  private def toCVarArgList_X86_64_Unix(
      varargs: Seq[CVarArg]
  )(implicit z: Zone): CVarArgList = {
    var storage = new Array[Long](registerSaveWords)
    var wordsUsed = storage.size
    var gpRegistersUsed = 0
    var fpRegistersUsed = 0

    def appendWord(word: Long): Unit = {
      if (wordsUsed == storage.size) {
        val newstorage = new Array[Long](storage.size * 2)
        System.arraycopy(storage, 0, newstorage, 0, storage.size)
        storage = newstorage
      }
      storage(wordsUsed) = word
      wordsUsed += 1
    }

    varargs.foreach { vararg =>
      val encoded = encode(vararg.value)(vararg.tag)
      val isDouble = isPassedAsDouble(vararg)

      if (isDouble && fpRegistersUsed < countFPRegisters) {
        var startIndex =
          countGPRegisters + (fpRegistersUsed * fpRegisterWords)
        encoded.foreach { w =>
          storage(startIndex) = w
          startIndex += 1
        }
        fpRegistersUsed += 1
      } else if (encoded.size == 1 && !isDouble && gpRegistersUsed < countGPRegisters) {
        val startIndex = gpRegistersUsed
        storage(startIndex) = encoded(0)
        gpRegistersUsed += 1
      } else {
        encoded.foreach(appendWord)
      }
    }
    val resultStorage =
      z.alloc(sizeof[Long] * storage.size.toUSize).asInstanceOf[Ptr[Long]]
    val storageStart = storage.asInstanceOf[LongArray].at(0)
    libc.memcpy(
      toRawPtr(resultStorage),
      toRawPtr(storageStart),
      wordsUsed.toUSize * sizeof[Long]
    )

    val resultHeader = z.alloc(sizeof[Header]).asInstanceOf[Ptr[Header]]
    resultHeader.gpOffset = 0.toUInt
    resultHeader.fpOffset = (countGPRegisters.toUSize * sizeof[Long]).toUInt
    resultHeader.regSaveArea = resultStorage
    resultHeader.overflowArgArea = resultStorage + registerSaveWords
    new CVarArgList(toRawPtr(resultHeader))
  }

  private def toCVarArgList_X86_Unix(
      varargs: Seq[CVarArg]
  )(implicit z: Zone) = {
    val resizedArgs = varargs.map { arg =>
      arg.value match {
        case value: Byte =>
          value.toInt: CVarArg
        case value: Short =>
          value.toInt: CVarArg
        case value: Long =>
          value.toInt: CVarArg
        case value: UByte =>
          value.toUInt: CVarArg
        case value: UShort =>
          value.toUInt: CVarArg
        case value: ULong =>
          value.toUInt: CVarArg
        case value: Float =>
          value.toDouble: CVarArg
        case o => arg
      }
    }

    var totalSize = 0.toUSize
    resizedArgs.foreach { vararg =>
      totalSize = Tag.align(totalSize, vararg.tag.alignment) + vararg.tag.size
    }

    val argListStorage = z.alloc(totalSize).asInstanceOf[Ptr[Byte]]
    var currentIndex = 0.toUSize
    resizedArgs.foreach { vararg =>
      currentIndex = Tag.align(currentIndex, vararg.tag.alignment)
      vararg.tag.store(
        (argListStorage + currentIndex).asInstanceOf[Ptr[Any]],
        vararg.value
      )
      currentIndex += vararg.tag.size
    }

    new CVarArgList(toRawPtr(argListStorage))
  }

  private def toCVarArgList_X86_64_Windows(
      varargs: Seq[CVarArg]
  )(implicit z: Zone) = {
    import scalanative.runtime.libc.realloc
    import scalanative.runtime.{fromRawPtr, toRawPtr}
    var storage: Ptr[Long] = null
    var count = 0
    var allocated = 0

    varargs.foreach { vararg =>
      val encoded = encode(vararg.value)(vararg.tag)
      val requiredSize = count + encoded.size
      if (requiredSize > allocated) {
        allocated = requiredSize.max(allocated * 2)
        storage = fromRawPtr(
          realloc(
            toRawPtr(storage),
            allocated.toUInt * sizeof[Size]
          )
        )
      }
      encoded.foreach { word =>
        !(storage + count) = word
        count += 1
      }
    }

    val resultStorage = toRawPtr(z.alloc(count.toUInt * sizeof[Size]))
    libc.memcpy(
      resultStorage,
      toRawPtr(storage),
      count.toUInt * sizeof[Size]
    )
    libc.free(toRawPtr(storage))
    new CVarArgList(resultStorage)
  }

}
