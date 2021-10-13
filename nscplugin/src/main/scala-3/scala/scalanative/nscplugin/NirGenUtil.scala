package scala.scalanative.nscplugin

import dotty.tools.dotc.ast.tpd
import tpd._
import dotty.tools.dotc.core
import core.Symbols._
import core.Contexts._
import core.Types._

trait NirGenUtil(using Context) { self: NirCodeGen =>
  protected def genParamSyms(
      dd: DefDef,
      isStatic: Boolean
  ): Seq[Option[Symbol]] = {
    val params = for {
      paramList <- dd.paramss.take(1)
      param <- paramList
    } yield Some(param.symbol)

    if (isStatic) params
    else None +: params
  }

  protected def qualifierOf(fun: Tree): Tree = fun match {
    case fun: Ident =>
      fun.tpe match {
        case TermRef(prefix: TermRef, _)  => tpd.ref(prefix)
        case TermRef(prefix: ThisType, _) => tpd.This(prefix.cls)
      }
    case Select(qualifier, _) => qualifier
    case TypeApply(fun, _)    => qualifierOf(fun)
  }

  // def unwrapClassTagOption(tree: Tree): Option[Symbol] =
  //   tree match {
  //     case Typed(Apply(ref: RefTree, args), _) =>
  //       ref.symbol match {
  //         case ByteClassTag    => Some(ByteClass)
  //         case ShortClassTag   => Some(ShortClass)
  //         case CharClassTag    => Some(CharClass)
  //         case IntClassTag     => Some(IntClass)
  //         case LongClassTag    => Some(LongClass)
  //         case FloatClassTag   => Some(FloatClass)
  //         case DoubleClassTag  => Some(DoubleClass)
  //         case BooleanClassTag => Some(BooleanClass)
  //         case UnitClassTag    => Some(UnitClass)
  //         case AnyClassTag     => Some(AnyClass)
  //         case ObjectClassTag  => Some(ObjectClass)
  //         case AnyValClassTag  => Some(ObjectClass)
  //         case AnyRefClassTag  => Some(ObjectClass)
  //         case NothingClassTag => Some(NothingClass)
  //         case NullClassTag    => Some(NullClass)
  //         case ClassTagApply =>
  //           val Seq(Literal(const: Constant)) = args
  //           Some(const.typeValue.typeSymbol)
  //         case _ =>
  //           None
  //       }

  //     case tree =>
  //       None
  //   }

  // def unwrapTagOption(tree: Tree): Option[SimpleType] = {
  //   tree match {
  //     case Apply(ref: RefTree, args) =>
  //       def allsts = {
  //         val sts = args.flatMap(unwrapTagOption(_).toSeq)
  //         if (sts.length == args.length) Some(sts) else None
  //       }
  //       def just(sym: Symbol) = Some(SimpleType(sym))
  //       def wrap(sym: Symbol) = allsts.map(SimpleType(sym, _))

  //       ref.symbol match {
  //         case UnitTagMethod    => just(UnitClass)
  //         case BooleanTagMethod => just(BooleanClass)
  //         case CharTagMethod    => just(CharClass)
  //         case ByteTagMethod    => just(ByteClass)
  //         case UByteTagMethod   => just(UByteClass)
  //         case ShortTagMethod   => just(ShortClass)
  //         case UShortTagMethod  => just(UShortClass)
  //         case IntTagMethod     => just(IntClass)
  //         case UIntTagMethod    => just(UIntClass)
  //         case LongTagMethod    => just(LongClass)
  //         case ULongTagMethod   => just(ULongClass)
  //         case FloatTagMethod   => just(FloatClass)
  //         case DoubleTagMethod  => just(DoubleClass)
  //         case PtrTagMethod     => just(PtrClass)
  //         case ClassTagMethod   => just(unwrapClassTagOption(args.head).get)
  //         case sym if CStructTagMethod.contains(sym) =>
  //           wrap(CStructClass(args.length))
  //         case CArrayTagMethod =>
  //           wrap(CArrayClass)
  //         case sym if NatBaseTagMethod.contains(sym) =>
  //           just(NatBaseClass(NatBaseTagMethod.indexOf(sym)))
  //         case sym if NatDigitTagMethod.contains(sym) =>
  //           wrap(NatDigitClass(NatDigitTagMethod.indexOf(sym)))
  //         case _ =>
  //           None
  //       }
  //     case _ => None
  //   }
  // }

  // def unwrapTag(tree: Tree): SimpleType =
  //   unwrapTagOption(tree).getOrElse {
  //     unsupported(s"can't recover runtime tag from $tree")
  //   }

  // def unwrapClassTag(tree: Tree): Symbol =
  //   unwrapClassTagOption(tree).getOrElse {
  //     unsupported(s"can't recover runtime class tag from $tree")
  //   }
}
