package scala.scalanative.nscplugin
import scala.language.implicitConversions

import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.ast.Trees._
import dotty.tools.dotc.core._
import Periods._
import SymDenotations._
import Contexts._
import Decorators._
import Flags._
import dotty.tools.dotc.ast.Trees._
import Names._
import NameKinds.DefaultGetterName
import Types._
import Symbols._
import Denotations._
import Phases._
import StdNames._
import TypeErasure.ErasedValueType
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.transform.SymUtils._

import scala.scalanative.nir

trait NirGenType(using Context) {
  self: NirCodeGen =>

  private lazy val UnsignedTyped = Set(
    defnNir.UByteClass,
    defnNir.UShortClass,
    defnNir.UIntClass,
    defnNir.ULongClass
  )

  extension (sym: Symbol)
    def isTraitOrInterface: Boolean =
      sym.is(Trait) || sym.isAllOf(JavaInterface)

    def isScalaModule: Boolean =
      sym.is(ModuleClass, butNot = Lifted)

    def isField: Boolean =
      sym.isTerm && !sym.is(Method) && !isScalaModule

    def isExternModule: Boolean =
      isScalaModule && sym.hasAnnotation(defnNir.ExternClass)

    def isStruct: Boolean =
      sym.hasAnnotation(defnNir.StructClass)
    
    def isUnsignedType: Boolean = ???// UnsignedTypes.contains(sym)
  end extension

  sealed case class SimpleType(
      sym: Symbol,
      targs: Seq[SimpleType] = Seq.empty
  ) {
    def isInterface: Boolean = ???
    // sym.isInterface

    /** Tests if this type inherits from CFuncPtr */
    def isCFuncPtrClass: Boolean = ???
    // sym == CFuncPtrClass ||
    //   sym.info.parents.exists(_.typeSymbol == CFuncPtrClass)

    /** Tests if this type is implementations of CFuncPtr */
    def isCFuncPtrNClass: Boolean = ???
    // CFuncPtrNClass.contains(sym) || {
    //   sym.info.parents.exists { parent =>
    //     CFuncPtrNClass.contains(parent.typeSymbol)
    //   }s
    // }
  }

  given fromSymbol: Conversion[Symbol, SimpleType] = { sym =>
    SimpleType(sym, sym.typeParams.map(fromSymbol))
  }
  given fromType: Conversion[Type, SimpleType] = _.normalized match {
    case ThisType(tref) =>
      if (tref == defn.ArrayType)
        SimpleType(defn.ObjectClass, Nil)
      else
        SimpleType(tref.symbol, Nil)
    case ConstantType(c)     => fromType(c.tpe)
    case t @ TypeRef(tpe, _) => SimpleType(t.symbol, tpe.argTypes.map(fromType))
    case t @ TermRef(_, _) => SimpleType(t.symbol, Nil) 
    case t => throw new RuntimeException(s"unknown fromType($t)")
  }

//   object SimpleType {
//     import scala.language.implicitConversions

//     implicit def fromType(t: Type): SimpleType =
//       t.normalized match {
//         case ThisType(ArrayClass)  => SimpleType(ObjectClass, Seq.empty)
//         case ThisType(sym)         => SimpleType(sym, Seq.empty)
//         case SingleType(_, sym)    => SimpleType(sym, Seq.empty)
//         case ConstantType(_)       => fromType(t.underlying)
//         case TypeRef(_, sym, args) => SimpleType(sym, args.map(fromType))
//         case ClassInfoType(_, _, ArrayClass) =>
//           abort("ClassInfoType to ArrayClass!")
//         case ClassInfoType(_, _, sym) => SimpleType(sym, Seq.empty)
//         case t: AnnotatedType         => fromType(t.underlying)
//         case tpe: ErasedValueType     => SimpleType(tpe.valueClazz, Seq())
//       }

//     implicit def fromSymbol(sym: Symbol): SimpleType =
//       SimpleType(sym, Seq.empty)
//     implicit def fromCompatSymbol(
//         sym: nirDefinitions.compat.Symbol
//     ): SimpleType =
//       fromSymbol(sym.asInstanceOf[Symbol])
//   }

//   def genArrayCode(st: SimpleType): Char =
//     genPrimCode(st.targs.head)

  def genBoxType(st: SimpleType): nir.Type = 
    BoxTypesForSymbol.getOrElse(st.sym.asClass, genType(st))

  private lazy val BoxTypesForSymbol = Map(
    defn.CharClass    -> genType(defn.BoxedCharClass),
    defn.BooleanClass -> genType(defn.BoxedBooleanClass),
    defn.ByteClass    -> genType(defn.BoxedByteClass),
    defn.ShortClass   -> genType(defn.BoxedShortClass),
    defn.IntClass     -> genType(defn.BoxedIntClass),
    defn.LongClass    -> genType(defn.BoxedLongClass),
    defn.FloatClass   -> genType(defn.BoxedFloatClass),
    defn.DoubleClass  -> genType(defn.BoxedDoubleClass)
  )

  def genExternType(st: SimpleType): nir.Type =
    genType(st) match {
      case _ if st.isCFuncPtrClass =>
        nir.Type.Ptr
      case refty: nir.Type.Ref if nir.Type.boxClasses.contains(refty.name) =>
        nir.Type.unbox(nir.Type.Ref(refty.name))
      case ty =>
        ty
    }

  @inline
  def genType(st: SimpleType): nir.Type = {
    PrimitiveSymbolToNirTypes.getOrElse(st.sym, genRefType(st))
  }

  private lazy val PrimitiveSymbolToNirTypes = Map(
    defn.CharClass -> nir.Type.Char,
    defn.BooleanClass -> nir.Type.Bool,
    defn.ByteClass -> nir.Type.Byte,
    defn.ShortClass -> nir.Type.Short,
    defn.IntClass -> nir.Type.Int,
    defn.LongClass -> nir.Type.Long,
    defn.FloatClass -> nir.Type.Float,
    defn.DoubleClass -> nir.Type.Double,
    defn.NullClass -> nir.Type.Null,
    defn.NothingClass -> nir.Type.Nothing,
    defnNir.RawPtrClass -> nir.Type.Ptr
  )

  def genRefType(st: SimpleType): nir.Type = {
    val SimpleType(sym, targs) = st
    if (sym == defn.ObjectClass) nir.Rt.Object
    else if (sym == defn.UnitClass) nir.Type.Unit
    else if (sym == defn.BoxedUnitClass) nir.Rt.BoxedUnit
    else if (sym == defn.NullClass) genRefType(defn.NullClass)
    else if (sym == defn.ArrayClass) nir.Type.Array(genType(targs.head))
    else if (sym.isStruct) genStruct(st)
    else nir.Type.Ref(genTypeName(sym))
  }

  def genTypeValue(st: SimpleType): nir.Val =
    if (st.sym == defn.UnitClass)
      genTypeValue(defnNir.RuntimePrimitive('U'))
    else if (st.sym == defn.ArrayClass)
      genTypeValue(defnNir.RuntimeArrayClass(genPrimCode(st.targs.head)))
    else
      genPrimCode(st) match {
        case 'O'  => nir.Val.ClassOf(genTypeName(st.sym))
        case code => genTypeValue(defnNir.RuntimePrimitive(code))
      }

  private def genStruct(st: SimpleType): nir.Type = {
    val fields = for {
      f <- st.sym.info.decls.toList
      if f.isField
    } yield genType(f)

    nir.Type.StructValue(fields)
  }

  def genPrimCode(st: SimpleType): Char =
    SymbolToPrimCode.getOrElse(st.sym.asClass, 'O')

  private lazy val SymbolToPrimCode = Map(
    defn.CharClass -> 'C',
    defn.BooleanClass -> 'B',
    defn.ByteClass -> 'Z',
    defn.ShortClass -> 'S',
    defn.IntClass -> 'I',
    defn.LongClass -> 'L',
    defn.FloatClass -> 'F',
    defn.DoubleClass -> 'D'
  )

  def genMethodSig(sym: Symbol): nir.Type.Function =
    genMethodSigImpl(sym, isExtern = false)

  def genExternMethodSig(sym: Symbol): nir.Type.Function =
    genMethodSigImpl(sym, isExtern = true)

  private def genMethodSigImpl(
      sym: Symbol,
      isExtern: Boolean
  ): nir.Type.Function = {
    require(sym.is(Method) || sym.isStatic, "symbol is not a method")

    val tpe = sym.typeRef
    val owner = sym.owner
    val paramtys = genMethodSigParamsImpl(sym, isExtern)
    val selfty =
      if (isExtern || owner.isExternModule) None
      else Some(genType(owner))
    val retty =
      if (sym.isClassConstructor) nir.Type.Unit
      else if (isExtern) genExternType(sym.typeRef.resultType)
      else genType(sym.typeRef.resultType)

    nir.Type.Function(selfty ++: paramtys, retty)
  }

  private def genMethodSigParamsImpl(
      sym: Symbol,
      isExtern: Boolean
  ): Seq[nir.Type] = {

    val wereRepeated = Map.empty[Name, Boolean]
    //  exitingPhase(currentRun.typerPhase) {
    //   for {
    //     params <- sym.paramSymss
    //     param <- params
    //   } yield {
    //     param.name -> isScalaRepeatedParamType(param.tpe)
    //   }
    // }.toMap

    sym.typeParams.map {
      case p
          if wereRepeated.getOrElse(p.name, false) &&
            sym.owner.isExternModule =>
        nir.Type.Vararg

      case p =>
        if (isExtern) genExternType(p)
        else genType(p)
    }
  }
}
