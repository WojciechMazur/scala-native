package scala.scalanative
package nscplugin

import scala.tools.nsc.{Global => NscGlobal, Phase}
import scala.reflect.internal.Flags._
import util.ScopedVar.scoped
import NirPrimitives._

abstract class PreNirSanityCheck[G <: NscGlobal with Singleton](
    override val global: G)
    extends NirPhase[G](global) {

  import global._
  import definitions._
  import nirAddons._
  import nirDefinitions._

  import SimpleType.{fromType, fromSymbol}

  val phaseName: String            = "sanitycheck"
  override def description: String = "sanity check ASTs for NIR"

  override def newPhase(prev: Phase): StdPhase =
    new SanityCheckPhase(prev)

  class SanityCheckPhase(prev: Phase) extends StdPhase(prev) {
    private val curClassSym = new util.ScopedVar[Symbol]
    private val curMethSym  = new util.ScopedVar[Symbol]
    private val curValSym   = new util.ScopedVar[Symbol]

    override def run(): Unit = {
      nirPrimitives.initPrepNativePrimitives()
      super.run()
    }

    override def apply(cunit: CompilationUnit): Unit = {
      def verifyDefs(tree: Tree): List[Tree] = {
        tree match {
          case EmptyTree            => Nil
          case PackageDef(_, stats) => stats flatMap verifyDefs
          case cd: ClassDef         => cd :: Nil
          case md: ModuleDef        => md :: Nil
        }
      }

      //println("CUNIT: " + cunit.body)

      cunit.body foreach verify
    }

    private val Skip: Unit = ()

    def verify(tree: Tree): Unit = tree match {
      case cd: ClassDef =>
        if (cd.symbol.isExtern) {
          verifyExternClassOrModule(cd)
        }
        verifyClassOrModule(cd)
      case md: ModuleDef =>
        if (md.symbol.isExtern) {
          verifyExternClassOrModule(md)
        }
        verifyClassOrModule(md)
      case _ => Skip
    }

    private def verifyExternClassOrModule(impl: ImplDef): Unit = {
      impl.symbol.tpe.parents
        .zip(impl.impl.parents)
        .foreach {
          case (parentTpe, implParent) =>
            val parentIsAnyRef = parentTpe == AnyRefTpe
            val parentIsExtern = implParent.symbol.isExtern

            if (parentIsAnyRef || parentIsExtern) Skip
            else {
              val thisKind   = symToKindPlural(impl.symbol)
              val parentKind = symToKind(implParent.symbol)
              reporter.error(
                impl.pos,
                s"extern $thisKind may only have extern parents, $parentKind ${implParent.symbol.nameString} is not extern")
            }
        }
    }

    def verifyClassOrModule(cd: ImplDef): Unit =
      scoped(
        curClassSym := cd.symbol
      ) {
        cd.impl.body.foreach {
          case dd: DefDef => verifyMethod(dd)
          case vd: ValDef => verifyVal(vd)
          case _          => Skip
        }
      }

    def verifyMethod(dd: DefDef): Unit =
      scoped(
        curMethSym := dd.symbol
      ) {
        dd.rhs match {
          // We don't care about the constructor at this phase
          case _: Block if dd.symbol.isConstructor => Skip
          case _ if curClassSym.get.isExtern       => verifyExternMethod(dd)
          case rhs                                 => verifyExpr(rhs)
        }
      }

    def verifyVal(dd: ValDef): Unit =
      scoped(
        curValSym := dd.symbol
      ) {
        if (curClassSym.get.isExtern) {
          verifyExternVal(dd)
        }
      }

    def verifyExternVal(dd: ValDef): Unit = {
      if (curValSym.isLazy) {
        reporter.error(
          dd.pos,
          s"fields in extern ${symToKindPlural(curClassSym)} must not be lazy")
      }

      if (curValSym.hasFlag(PARAMACCESSOR)) {
        reporter.error(
          dd.pos,
          s"parameters in extern ${symToKindPlural(curClassSym)} are not allowed - only extern fields and methods are allowed")
      }

      dd.rhs match {
        case sel: Select if sel.symbol == ExternMethod =>
          externMemberHasTpeAnnotation(dd)

        case _ =>
          reporter.error(
            dd.pos,
            s"fields in extern ${symToKindPlural(curClassSym)} must have extern body")
      }
    }

    def verifyExternMethod(ddef: DefDef): Unit = {
      ddef.rhs match {
        case Apply(ref: RefTree, Seq()) if ref.symbol == ExternMethod =>
          externMemberHasTpeAnnotation(ddef)
        case sel: Select if sel.symbol == ExternMethod =>
          externMemberHasTpeAnnotation(ddef)
        case _ if curMethSym.hasFlag(ACCESSOR) =>
          Skip
        case _ =>
          reporter.error(
            ddef.rhs.pos.focus,
            s"methods in extern ${symToKindPlural(curClassSym)} must have extern body")
      }
    }

    def verifyExpr(expr: Tree): Unit = expr match {
      case tree: Block  => verifyBlock(tree)
      case tree: Select => verifySelect(tree)
      case tree: Apply  => verifyApply(tree)

      case _ =>
        println("Unknown tree " + expr.getClass)
    }

    def verifyBlock(block: Block): Unit = {
      val Block(stats, last) = block
      stats.foreach(verifyExpr)
      verifyExpr(last)
    }

    def verifySelect(tree: Select): Unit = {
      val Select(qual, sel) = tree
    }

    def verifyApply(app: Apply): Unit = {
      val Apply(fun, args) = app
      fun match {
        case TypeApply(fun, targs) =>
          val sym = fun.symbol
          if (scalaPrimitives.isPrimitive(sym))
            verifyApplyPrimitive(app)
          else {
            val Select(receiverp, _) = fun
            verifyApplyMethod(fun.symbol, statically = false, receiverp, args)
          }
        //verifyApplyTypeApply(app)
        case Select(Super(_, _), _) =>
        //genApplyMethod(fun.symbol,
        //  statically = true,
        //  curMethodThis.get.get,
        //  args)
        case Select(New(_), nme.CONSTRUCTOR) =>
        //genApplyNew(app)
        case _ =>
          val sym = fun.symbol

          if (sym.isLabel) {
            //genApplyLabel(app)
          } else if (scalaPrimitives.isPrimitive(sym)) {
            verifyApplyPrimitive(app)
          } /* else if (currentRun.runDefinitions.isBox(sym)) {
            val arg = args.head
            genApplyBox(arg.tpe, arg)
          } else if (currentRun.runDefinitions.isUnbox(sym)) {
            genApplyUnbox(app.tpe, args.head)
          }*/
          else {
            // TODO: what exactly is checked here?
            fun match {
              case Select(receiverp, _) =>
                verifyApplyMethod(fun.symbol,
                                  statically = false,
                                  receiverp,
                                  args)
              case _ =>
                ()
            }
          }
      }
    }

    def verifyApplyTypeApply(app: Apply): Unit = {
      val Apply(TypeApply(fun @ Select(receiverp, _), targs), args) = app

      // verify Type
      verifyExpr(receiverp)
      args.foreach(verifyExpr)
    }

    def verifyApplyMethod(sym: Symbol,
                          statically: Boolean,
                          selfp: Tree,
                          argsp: Seq[Tree]): Unit = {
      if (sym.owner.isExternModule && sym.hasFlag(ACCESSOR)) {
        // TODO
        //verifyApplyExternAccessor(sym, argsp)
      } else {
        verifyExpr(selfp)
        argsp.foreach(verifyExpr)
        // verify method args
        //verifyApplyMethod(sym, statically, self, argsp)
      }
    }

    def verifyApplyPrimitive(app: Apply): Unit = {
      import scalaPrimitives._

      val (fun @ Select(receiver, _), args) = app match {
        case Apply(TypeApply(f, _), args) => (f, args)
        // Note that this fails to compile examples with implicit
        case Apply(Apply(f, args), _) => (f, args)
        case Apply(f, args)           => (f, args)
      }
      //val Apply(fun @ Select(receiver, _), args) = app

      val sym  = app.symbol
      val code = scalaPrimitives.getPrimitive(sym, receiver.tpe)

      if (isArithmeticOp(code) || isLogicalOp(code) || isComparisonOp(code)) {
        verifySimpleOp(app, args)
      } else if (code == CONCAT) {
        verifyStringConcat(receiver, args.head)
      } else if (code == HASH) {
        verifyHashCode(args.head)
      } else if (isArrayOp(code) || code == ARRAY_CLONE) {
        verifyArrayOp(app, code)
      } else if (nirPrimitives.isRawPtrOp(code)) {
        verifyRawPtrOp(app, code)
//      } else if (nirPrimitives.isFunPtrOp(code)) {
//        verifyFunPtrOp(app, code)
      } else if (isCoercion(code)) {
        verifyCoercion(app, receiver, code)
      } else if (code == SYNCHRONIZED) {
        verifySynchronized(app)
      } else if (nirPrimitives.isRawCastOp(code)) {
        verifyRawCastOp(app)
//      } else if (code == SIZEOF || code == TYPEOF) {
//        verifyOfOp(app, code)
      } else if (code == STACKALLOC) {
        verifyStackalloc(app)
      } else if (code == CQUOTE) {
        verifyCQuoteOp(app)
      } else if (code == BOXED_UNIT) {
        ()
      } else if (code >= DIV_UINT && code <= INT_TO_ULONG) {
        verifyUnsignedOp(app, code)
//      } else if (code == SELECT) {
//        verifySelectOp(app)
      } else {
        reporter.error(
          app.pos,
          s"Unknown primitive operation: ${sym.fullName} (${fun.symbol.simpleName}")
      }
    }

    def verifySimpleOp(app: Apply, args: List[Tree]): Unit = {
      args match {
        case Nil =>
          reporter.error(app.pos,
                         s"a non-empty primitive function requires arguments")
        case _ :: _ :: _ :: _ =>
          reporter.error(app.pos, s"too many arguments for primitive function")
        case _ =>
          ???
      }
    }

    def verifyStringConcat(leftp: Tree, rightp: Tree): Unit = ()
    def verifyHashCode(argp: Tree): Unit                    = ()
    def verifyArrayOp(app: Apply, code: Int): Unit          = ()
    def verifyRawPtrOp(app: Apply, code: Int): Unit         = ()
//    def verifyFunPtrOp(app: Apply, code: Int): Unit = {
//      def verifyBody(params: List[Symbol], body: Tree): Unit = {
//        val free = freeLocalVars(body) diff params
//        if (free.nonEmpty)
//          reporter.error(
//            app.pos,
//            s"can't infer a function pointer to a closure with captures: ${free.mkString(",")}"
//          )
//      }
//
//      code match {
//        case FUN_PTR_CALL =>
//          ()
//        case FUN_PTR_FROM =>
//          app match {
//            // TODO: We could accept Block statements here,
//            //       especially with the custom free vars check,
//            //       except that those need to be translated manually into
//            //       closures, if possible.
//            case Apply(_, Function(vparams, body) :: Nil) =>
//              verifyBody(vparams.map(_.symbol), body)
//            case Apply(
//                _,
//                Block(Nil, Function(vparams, body: Apply)) :: Nil
//                ) => // eta-expansion
//              val Apply(_, Block(Nil, fun @ Function(vparams1, body1)) :: Nil) =
//                app
//              verifyBody(fun.symbol :: vparams1.map(_.symbol), body1)
//            case _ =>
//              reporter.error(
//                app.pos,
//                s"(scala-native limitation): cannot infer a function pointer, lift the argument into a function")
//          }
//      }
//    }
    def verifyCoercion(app: Apply, receiver: Tree, code: Int): Unit = ()
    def verifySynchronized(app: Apply): Unit                        = ()
    def verifyRawCastOp(app: Apply): Unit                           = ()
    def verifyOfOp(app: Apply, code: Int): Unit                     = ()
    def verifyStackalloc(app: Apply): Unit                          = ()
    def verifyCQuoteOp(app: Apply): Unit                            = ()
    def verifyUnsignedOp(app: Tree, code: Int): Unit                = ()
    def verifySelectOp(app: Apply): Unit                            = ()

    def externMemberHasTpeAnnotation(df: ValOrDefDef): Unit = {
      df.tpt match {
        case t @ TypeTree() if t.original == null =>
          val kind =
            if (df.symbol.isField) "fields"
            else "methods"

          reporter.error(
            df.pos,
            s"extern $kind in ${symToKindPlural(curClassSym)} must have an explicit type annotation")
        case _: TypeTree => Skip
      }
    }

    // Capture free variables
    // Inspired by LambdaLift phase in scalac
    private class FreeVarTraverser extends Traverser {

      private var localVars: List[Symbol] = Nil
      private var freeVars: List[Symbol]  = Nil

      def allFreeVars: List[Symbol] = freeVars
      override def traverse(tree: Tree) {
        //       try { //debug
        val sym = tree.symbol
        tree match {
          case ClassDef(_, _, _, _) =>
            super.traverse(tree)
          case DefDef(_, _, _, vparams, _, _) =>
            withLocalSyms(vparams.flatten.map(_.symbol)) {
              super.traverse(tree)
            }
          case Block(stats, last) =>
            // special case for defs
            (stats ++ (last :: Nil)).foldLeft(Nil: List[Symbol])({
              case (syms, stat: ValDef) =>
                withLocalSyms(syms) { traverse(stat) }
                stat.symbol :: syms
              case (syms, stat: DefDef) =>
                withLocalSyms(syms) { traverse(stat) }
                stat.symbol :: syms
              case (syms, stat: ModuleDef) =>
                withLocalSyms(syms) { traverse(stat) }
                stat.symbol :: syms
              case (syms, stat: ClassDef) =>
                withLocalSyms(syms) { traverse(stat) }
                stat.symbol :: syms
              case (syms, stat) =>
                withLocalSyms(syms) { traverse(stat) }
                syms
            })
          case Ident(name) =>
            if (sym == NoSymbol) {
              assert(name == nme.WILDCARD)
            } else if (sym.isLocalToBlock && !localVars.contains(sym)) {
              val owner = logicallyEnclosingMember(currentOwner)
              if (sym.isTerm && !sym.isMethod) freeVars = sym :: freeVars
              else if (sym.isMethod) freeVars = sym :: freeVars
            }
            super.traverse(tree)
          case Function(vparams, body) =>
            withLocalSyms(vparams.map(_.symbol)) { super.traverse(tree) }
          case Select(receiver @ This(_), _)
              if (!localVars.contains(receiver.symbol)) =>
            freeVars = sym :: freeVars
          case Select(receiver, _) =>
            if (sym.isConstructor && sym.owner.isLocalToBlock && !localVars
                  .contains(sym.owner))
              freeVars = sym.owner :: freeVars
            super.traverse(tree)
          case _ =>
            super.traverse(tree)
        }
      }

      private def logicallyEnclosingMember(sym: Symbol): Symbol = {
        if (sym.isLocalDummy) {
          val enclClass = sym.enclClass
          //if (enclClass.isSubClass(DelayedInitClass))
          //  delayedInitDummies.getOrElseUpdate(enclClass, enclClass.newMethod(nme.delayedInit))
          //else
          enclClass.primaryConstructor
        } else if (sym.isMethod || sym.isClass || sym == NoSymbol) sym
        else logicallyEnclosingMember(sym.owner)
      }

      private def withLocalSyms[T](syms: List[Symbol])(op: => T): T = {
        val localVars0 = localVars
        localVars = localVars ++ syms
        val res = op
        localVars = localVars0
        res
      }
    }

    private def freeLocalVars(tree: Tree): List[Symbol] = {
      val trav = new FreeVarTraverser
      trav.traverse(tree)
      trav.allFreeVars
    }

    private def symToKindPlural(sym: Symbol): String =
      if (sym.isClass)
        if (sym.asClass.isTrait) "traits" else "classes"
      else "objects"

    private def symToKind(sym: Symbol): String =
      if (sym.isClass)
        if (sym.asClass.isTrait) "trait" else "class"
      else "object"
  }
}
