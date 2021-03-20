package scala.scalanative.codegen.compat.os

import scala.scalanative.codegen.compat.GenericCodeGen
import scala.scalanative.nir.ControlFlow.Block
import scala.scalanative.nir.{Fresh, Next, Position, Val}
import scala.scalanative.util.ShowBuilder

private[codegen] class WindowsCompat(codegen: GenericCodeGen) extends OsCompat {
  val ehWrapperTy       = "\"\\01??_R0?AVExceptionWrapper@scalanative@@@8\""
  val ehWrapperName     = "c\\\".?AVExceptionWrapper@scalanative@@\\\\00\\\""
  val ehGlobWrapperTy   = "\"\\01?eglob@@3PEAVExceptionWrapper@scalanative@@EA\""
  val ehClassName       = "\"class.scalanative::ExceptionWrapper\""
  val stdExceptionClass = "\"class.std::exception\""
  val stdExceptionData  = "struct.__std_exception_data"
  val typeInfo          = "\"\\01??_7type_info@@6B@\""
  val ehVar             = "%eslot"

  override protected val osPersonalityType: String = "@__CxxFrameHandler3"

  override def genBlockAlloca(block: Block)(implicit sb: ShowBuilder): Unit = {
    import sb._
    if (block.pred.isEmpty) {
      line(s"$ehVar = alloca $ehClassName*")
    }
  }

  override def genPrelude()(implicit sb: ShowBuilder): Unit = {
    import sb._
    line("declare i32 @llvm.eh.typeid.for(i8*)")
    line(s"%$stdExceptionData = type { i8*, i8 }")
    line(s"%$stdExceptionClass = type { i32 (...)**, %$stdExceptionClass }")
    line(s"%$ehClassName = type { %$stdExceptionClass, i8* }")
    line("%rtti.TypeDescriptor34 = type { i8**, i8*, [35 x i8] }")
    line(s"@$typeInfo = external constant i8*")
    line(s"$$$ehWrapperTy = comdat any")
    line(
      s"@$ehWrapperTy = linkonce_odr global %rtti.TypeDescriptor34 { i8** @$typeInfo, i8* null, [35 x i8] $ehWrapperName }, comdat")
    line(s"@$ehGlobWrapperTy = external global %$ehClassName*")
  }

  override def genLandingPad(unwind: Next.Unwind)(implicit fresh: Fresh,
                                                  pos: Position,
                                                  sb: ShowBuilder): Unit = {
    import codegen._
    import sb._
    val Next.Unwind(Val.Local(excname, _), next) = unwind

    val excpad  = s"_${excname.id}.landingpad"
    val excsucc = excpad + ".succ"

    val exc               = "%_" + excname.id
    val rec, w1, w2, cpad = "%_" + fresh().id

    line(s"$excpad:")
    indent()
    line(s"$rec = catchswitch within none [label %$excsucc] unwind to caller")
    unindent()

    line(s"$excsucc:")
    indent()
    line(
      s"$cpad = catchpad within $rec [%rtti.TypeDescriptor34* @$ehWrapperTy, i32 8, %$ehClassName** $ehVar]")
    line(s"$w1 = load %$ehClassName*, %$ehClassName** $ehVar")
    line(
      s"$w2 = getelementptr inbounds %$ehClassName, %$ehClassName* $w1, i32 0, i32 1")
    line(s"$exc = load i8*, i8** $w2, align 8")
    line(s"catchret from $cpad to ")
    genNext(next)
    unindent()
  }
}
