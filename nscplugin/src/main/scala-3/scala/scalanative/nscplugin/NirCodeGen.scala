package scala.scalanative.nscplugin

import scala.scalanative.util
import scala.scalanative.nir

import dotty.tools.dotc.{CompilationUnit, report}
import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.ast.Trees._
import dotty.tools.dotc.core
import core.Contexts._
import core.Symbols._
import dotty.tools.FatalError

import scala.collection.mutable
import scala.language.implicitConversions

class NirCodeGen()(using ctx: Context)
    extends NirGenDefn
    with NirGenExpr
    with NirGenType
    with NirGenName
    with NirGenUtil:
  import tpd._
  import nir._

  object SanityLevel {
    final val Defn = 4
    final val InputLog = 5
    final val OutputDefinitions = 5
  }
  def log(args: Any*): Unit = log(args:_*)()
  def log(args: Any*)(sanityLevel: Int = 0): Unit = {
    val Threashold = 1
    if (sanityLevel >= Threashold) {
      println(args.mkString(" | "))
    }
  }

  protected val defnNir = new NirDefinitions().nirDefinitions
  protected val nirPrimitives = new NirPrimitives(defnNir)
  protected val positionsConversions = new NirPositions()

  protected val curClassSym = new util.ScopedVar[Symbol]
  protected val curClassFresh = new util.ScopedVar[nir.Fresh]

  protected val curMethodSym = new util.ScopedVar[Symbol]
  protected val curMethodSig = new util.ScopedVar[nir.Type]
  protected val curMethodInfo = new util.ScopedVar[CollectMethodInfo]
  protected val curMethodEnv = new util.ScopedVar[MethodEnv]
  protected val curMethodThis = new util.ScopedVar[Option[nir.Val]]
  protected val curMethodIsExtern = new util.ScopedVar[Boolean]

  protected val curFresh = new util.ScopedVar[nir.Fresh]
  protected val curUnwindHandler = new util.ScopedVar[Option[nir.Local]]

  protected def unwind(implicit fresh: Fresh): Next =
    curUnwindHandler.get
      .fold[Next](Next.None) { handler =>
        val exc = Val.Local(fresh(), nir.Rt.Object)
        Next.Unwind(exc, Next.Label(handler, Seq(exc)))
      }

  def run(): Unit = {
    try {
      genCompilationUnit(ctx.compilationUnit)
    } finally {
      generatedDefns.clear()
      log("---------")(SanityLevel.InputLog)
    }
  }

  private def genCompilationUnit(cunit: CompilationUnit): Unit = {
    def collectTypeDefs(tree: Tree): List[TypeDef] = {
      tree match {
        case EmptyTree            => Nil
        case PackageDef(_, stats) => stats.flatMap(collectTypeDefs)
        case cd: TypeDef          => cd :: Nil
        case _: ValDef            => Nil // module instance
      }
    }

    collectTypeDefs(cunit.tpdTree)
      .foreach(genClass)

    def genClasses() = generatedDefns.toSeq
      .groupBy(_.name.top)
      .map(getFileFor(cunit, _) -> _)

    for {
      (path, defns) <- genClasses()
    } genIRFile(path, defns)
  }

  private def genIRFile(
      outfile: dotty.tools.io.AbstractFile,
      defns: Seq[nir.Defn]
  ): Unit = {
    import scalanative.nir.serialization.serializeBinary
    val output = outfile.bufferedOutput
    try {
      serializeBinary(defns, output)
      log(s"file: ${outfile}")(SanityLevel.OutputDefinitions)
      defns.foreach(defn => log(s"-\t${defn.name}\t@ ${defn.pos}")(SanityLevel.OutputDefinitions))
      log()(SanityLevel.OutputDefinitions)
    } finally {
      output.close()
    }
  }

  private def getFileFor(
      cunit: CompilationUnit,
      ownerName: nir.Global
  ): dotty.tools.io.AbstractFile = {
    val nir.Global.Top(className) = ownerName
    val outputDirectory = ctx.settings.outputDir.value
    val pathParts = className.split('.')
    val dir = pathParts.init.foldLeft(outputDirectory)(_.subdirectoryNamed(_))
    val filename = pathParts.last
    dir.fileNamed(filename + ".nir")
  }

  def fail(msg: => String): Nothing = {
    // new RuntimeException().printStackTrace
    throw FatalError(
      s"""
      Fatal failure: ${msg}
      currClassSym: ${curClassSym.get}
      currMethodSym: ${curMethodSym}
      curExprBuffer: 
      - ${curExprBuffer.get.toSeq.mkString("\n-\t")}
      """
    )

  }

  class MethodEnv(val fresh: nir.Fresh) {
    private val env = mutable.Map.empty[Symbol, nir.Val]

    def enter(sym: Symbol, value: nir.Val): Unit = env += sym -> value

    def enterLabel(ld: Labeled): nir.Local = {
      val local = fresh()
      enter(ld.symbol, nir.Val.Local(local, nir.Type.Ptr))
      local
    }

    def resolve(sym: Symbol): Val = env.get(sym) match {
      case Some(v) => v
      case None =>
        fail(s"Not found $sym in method env:\n${env.map { (k, v) =>
          s"-\t$k -> $v\n"
        }.mkString}")
    }

    def resolveLabel(ld: Labeled): Local = {
      val Val.Local(n, Type.Ptr) = resolve(ld.symbol)
      n
    }
  }

  class CollectMethodInfo extends TreeTraverser {
    var mutableVars = Set.empty[Symbol]
    var labels = Set.empty[Labeled]

    override def traverse(tree: Tree)(using Context): Unit = {
      tree match {
        case label: Labeled =>
          labels += label
        case Assign(id @ Ident(_), _) =>
          mutableVars += id.symbol
        case _ =>
          ()
      }
      traverseChildren(tree)
    }

    def collect(tree: Tree): CollectMethodInfo = {
      traverse(tree)
      this
    }
  }

  protected object LinktimeProperty {
    def unapply(tree: Tree): Option[(String, nir.Position)] = {
      if (tree.symbol == null) None
      else {
        tree.symbol
          .getAnnotation(defnNir.ResolvedAtLinktimeClass)
          .flatMap(_.argumentConstantString(0))
          .map(_ -> positionsConversions.fromSpan(tree.span))
          .orElse {
            report.error(
              "Name used to resolve link-time property needs to be non-null literal constant",
              tree.sourcePos
            )
            None
          }
      }
    }
  }

end NirCodeGen

// object NirCodeGen:

// end NirCodeGen
