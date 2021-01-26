package scala.scalanative
package nscplugin
import scala.tools.nsc.Global

trait NirGenType[G <: Global with Singleton] { self: NirGenPhase[G] =>
  import SimpleType.{fromSymbol, fromType}
  import global._
  import definitions._
  import nirAddons._
  import nirDefinitions._

  sealed case class SimpleType(sym: Symbol,
                               targs: Seq[SimpleType] = Seq.empty) {
    def isInterface: Boolean =
      sym.isInterface

    def isScalaModule: Boolean =
      sym.isModuleClass && !isImplClass(sym) && !sym.isLifted

    def isExternModule: Boolean =
      isScalaModule && sym.annotations.exists(_.symbol == ExternClass)

    def isNamedStruct: Boolean =
      sym.baseClasses.tail.contains(CStructClass) &&
        !isAnonymousStruct

    def isAnonymousStruct: Boolean =
      CStructNClass.contains(sym) || {
        sym.info.parents.exists { parent =>
          CStructNClass.contains(parent.typeSymbol)
        }
      }

    def isField: Boolean =
      !sym.isMethod && sym.isTerm && !isScalaModule

    /** Tests if this type inherits from CFuncPtr */
    def isCFuncPtrClass: Boolean =
      sym == CFuncPtrClass ||
        sym.info.parents.exists(_.typeSymbol == CFuncPtrClass)

    /** Tests if this type is implementations of CFuncPtr */
    def isCFuncPtrNClass: Boolean =
      CFuncPtrNClass.contains(sym) || {
        sym.info.parents.exists { parent =>
          CFuncPtrNClass.contains(parent.typeSymbol)
        }
      }
  }

  object SimpleType {
    import scala.language.implicitConversions

    implicit def fromType(t: Type): SimpleType =
      t.normalize match {
        case ThisType(ArrayClass)  => SimpleType(ObjectClass, Seq.empty)
        case ThisType(sym)         => SimpleType(sym, Seq.empty)
        case SingleType(_, sym)    => SimpleType(sym, Seq.empty)
        case ConstantType(_)       => fromType(t.underlying)
        case TypeRef(_, sym, args) => SimpleType(sym, args.map(fromType))
        case ClassInfoType(_, _, ArrayClass) =>
          abort("ClassInfoType to ArrayClass!")
        case ClassInfoType(_, _, sym) => SimpleType(sym, Seq.empty)
        case t: AnnotatedType         => fromType(t.underlying)
        case tpe: ErasedValueType     => SimpleType(tpe.valueClazz, Seq())
      }

    implicit def fromSymbol(sym: Symbol): SimpleType =
      SimpleType(sym, Seq.empty)

    implicit def toSymbol(st: SimpleType): Symbol = st.sym
  }

  def genArrayCode(st: SimpleType): Char =
    genPrimCode(st.targs.head)

  def genBoxType(st: SimpleType): nir.Type = st.sym match {
    case CharClass    => genType(BoxedCharacterClass)
    case BooleanClass => genType(BoxedBooleanClass)
    case ByteClass    => genType(BoxedByteClass)
    case ShortClass   => genType(BoxedShortClass)
    case IntClass     => genType(BoxedIntClass)
    case LongClass    => genType(BoxedLongClass)
    case FloatClass   => genType(BoxedFloatClass)
    case DoubleClass  => genType(BoxedDoubleClass)
    case _            => genType(st)
  }

  def genExternType(st: SimpleType): nir.Type =
    genType(st) match {
      case _ if st.isCFuncPtrClass =>
        nir.Type.Ptr
      case _ if st.isNamedStruct =>
        /* Invalid when struct size > 4 bytes (in x86_64), depends on target
         * When larger or in other targets it may be passed using pointer to stack allocated value in caller,,
         * in LLVM IR pointer might need to be annotated with `sret` attribute.
         * Also type of struct might change based on alignment, eg.:
         *  - {Int, Int}            -> i64
         *  - {Int, Int, Int}       -> {i64, i32}
         *  - {Int, Long}           -> {i64, i64}
         *  - {Byte, Byte, Byte}    -> i24
         *  - {Float, Double}       -> {float, double}
         *  - {Float, Float}        -> <2 x float>    //vector
         *  - {Float, Float, Float} -> {<2 x float>, float}
         *  - {char [5]}            -> i40
         *  - {char [17]}           -> i8*            // pointer to struct
         */
        genStructType(st)
      case refty: nir.Type.Ref if nir.Type.boxClasses.contains(refty.name) =>
        nir.Type.unbox(nir.Type.Ref(refty.name))
      case ty =>
        ty
    }

  def genType(st: SimpleType): nir.Type = st.sym match {
    case CharClass    => nir.Type.Char
    case BooleanClass => nir.Type.Bool
    case ByteClass    => nir.Type.Byte
    case ShortClass   => nir.Type.Short
    case IntClass     => nir.Type.Int
    case LongClass    => nir.Type.Long
    case FloatClass   => nir.Type.Float
    case DoubleClass  => nir.Type.Double
    case NullClass    => nir.Type.Null
    case NothingClass => nir.Type.Nothing
    case RawPtrClass  => nir.Type.Ptr
    case _            => genRefType(st)
  }

  def genRefType(st: SimpleType): nir.Type = st.sym match {
    case ObjectClass    => nir.Rt.Object
    case UnitClass      => nir.Type.Unit
    case BoxedUnitClass => nir.Rt.BoxedUnit
    case NullClass      => genRefType(RuntimeNullClass)
    case ArrayClass     => nir.Type.Array(genType(st.targs.head))
    case _              => nir.Type.Ref(genTypeName(st.sym))
  }

  def genTypeValue(st: SimpleType): nir.Val =
    genPrimCode(st.sym) match {
      case _ if st.sym == UnitClass =>
        genTypeValue(RuntimePrimitive('U'))
      case _ if st.sym == ArrayClass =>
        genTypeValue(RuntimeArrayClass(genPrimCode(st.targs.head)))
      case 'O' =>
        nir.Val.ClassOf(genTypeName(st.sym))
      case code =>
        genTypeValue(RuntimePrimitive(code))
    }

  def getClassFields(st: SimpleType): Seq[Symbol] =
    st.sym.info.decls.filter(_.isField).toSeq

  def genStructFieldsTypes(st: SimpleType): Seq[nir.Type] = {
    def toStructFieldType(tpe: Type): nir.Type = tpe match {
      case tpe if tpe.isNamedStruct => genStructType(tpe)
      case tpe if tpe.isAnonymousStruct =>
        val tpes = tpe.paramTypes.map(toStructFieldType)
        nir.Type.StructValue(tpes)
      case tpe => genType(tpe)
    }

    getClassFields(st)
      .map(_.tpe)
      .map(toStructFieldType)
      .toList
  }

  def genStructType(st: SimpleType): nir.Type = {
    val fields = genStructFieldsTypes(st)

    nir.Type.StructValue(fields)
  }

  def genPrimCode(st: SimpleType): Char = st.sym match {
    case CharClass    => 'C'
    case BooleanClass => 'B'
    case ByteClass    => 'Z'
    case ShortClass   => 'S'
    case IntClass     => 'I'
    case LongClass    => 'L'
    case FloatClass   => 'F'
    case DoubleClass  => 'D'
    case _            => 'O'
  }

  def genMethodSig(sym: Symbol): nir.Type.Function =
    genMethodSigImpl(sym, isExtern = false)

  def genExternMethodSig(sym: Symbol): nir.Type.Function =
    genMethodSigImpl(sym, isExtern = true)

  def getMethodSig(
      sym: Symbol,
      isExtern: Boolean): (Seq[(Boolean, SimpleType)], SimpleType) = {
    require(sym.isMethod || sym.isStaticMember, "symbol is not a method")

    val owner    = sym.owner
    val paramtys = getMethodSigParams(sym)
    val selfty =
      if (isExtern || owner.isExternModule || isImplClass(owner)) None
      else Some((false, SimpleType.fromType(owner.tpe)))
    val retty: SimpleType =
      if (sym.isClassConstructor) UnitClass
      else sym.tpe.resultType

    (selfty ++: paramtys, retty)
  }

  private def genMethodSigImpl(sym: Symbol,
                               isExtern: Boolean): nir.Type.Function = {
    val (params, ret) = getMethodSig(sym, isExtern)

    val paramTys = params.map {
      case (true, _) if sym.owner.isExternModule => nir.Type.Vararg
      case (_, p) if isExtern                    => genExternType(p)
      case (_, p)                                => genType(p)
    }

    val retty =
      if (sym.isClassConstructor) nir.Type.Unit
      else if (isExtern) genExternType(ret)
      else genType(ret)

    nir.Type.Function(paramTys, retty)
  }

  private def getMethodSigParams(sym: Symbol): Seq[(Boolean, SimpleType)] = {
    val wereRepeated = exitingPhase(currentRun.typerPhase) {
      for {
        params <- sym.tpe.paramss
        param  <- params
      } yield {
        param.name -> isScalaRepeatedParamType(param.tpe)
      }
    }.toMap

    sym.tpe.params.map { p =>
      val paramRepeated = wereRepeated.getOrElse(p.name, false)
      (paramRepeated, SimpleType.fromType(p.tpe))
    }
  }
}
