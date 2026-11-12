package scala.scalanative
package codegen
package llvm

import java.io.File
import java.nio.file.{Files, Path, Paths, StandardCopyOption}

import scala.collection.mutable
import scala.concurrent._
import scala.util.Success

import scala.scalanative.build
import scala.scalanative.build.ScalaNative.{dumpDefns, encodedMainClass}
import scala.scalanative.build.{Build, Config, IO}
import scala.scalanative.codegen.llvm.compat.os.OsCompat
import scala.scalanative.codegen.{Metadata => CodeGenMetadata}
import scala.scalanative.io.VirtualDirectory
import scala.scalanative.linker.ReachabilityAnalysis
import scala.scalanative.util.{Scope, ShowBuilder, partitionBy, procs}

object CodeGen {
  type IRGenerator = Future[Path]
  type IRGenerators = Seq[IRGenerator]

  /** Lower and generate code for given assembly.
   *
   *  @param provided
   *    definitions compiled apart, by library units: they are only declared
   */
  def apply(
      config: build.Config,
      analysis: ReachabilityAnalysis.Result,
      provided: Set[nir.Global] = Set.empty
  )(implicit
      ec: ExecutionContext
  ): Future[IRGenerators] = {
    val defns = analysis.defns
    val granular = config.compilerConfig.useGranularBuild
    val proxies = GenerateReflectiveProxies(
      analysis.dynimpls,
      defns,
      withPositions = !granular
    )

    implicit def logger: build.Logger = config.logger
    implicit val platform: PlatformInfo = PlatformInfo(config)
    implicit val meta: CodeGenMetadata =
      new CodeGenMetadata(analysis, config, proxies)

    val generated = Generate(encodedMainClass(config), defns ++ proxies)
    val embedded = ResourceEmbedder(config)
    // Definitions that exist once per program, emitted apart from library code
    // in granular builds
    val program: Set[nir.Global] =
      if (!granular) Set.empty
      else
        (proxies ++ embedded).map(_.name).toSet ++ Generate.programDefinitions
    val lowered = lower(declareProvided(generated, provided) ++ embedded)
    lowered
      .andThen { case Success(defns) => dumpDefns(config, "lowered", defns) }
      .map { lowered =>
        if (provided.isEmpty) emit(config, lowered, program)
        else {
          // Library code compiled apart may refer to members the program
          // never reached, such as inherited methods in virtual tables
          val (declarations, _) = declareMissing(analysis, lowered, lowered)
          emit(
            config,
            lowered.filterNot(isProvided(_, provided)),
            program,
            references = lowered ++ declarations
          )
        }
      }
  }

  /** Declarations of the symbols `defns` refer to without defining or declaring
   *  them, taken from the classes the analysis loaded. Returns them lowered,
   *  and the symbols that could not be found.
   */
  private[scalanative] def declareMissing(
      analysis: ReachabilityAnalysis.Result,
      defns: Seq[nir.Defn],
      env: Seq[nir.Defn]
  )(implicit
      meta: CodeGenMetadata,
      logger: build.Logger
  ): (Seq[nir.Defn], Set[nir.Global]) = {
    val known = env.map(_.name: nir.Global).toSet
    val missing = mutable.HashSet.empty[nir.Global]
    val traverse = new nir.Traverse {
      override def onVal(value: nir.Val): Unit = {
        value match {
          case nir.Val.Global(name, _) if !known.contains(name) =>
            missing += name
          case _ => ()
        }
        super.onVal(value)
      }
    }
    defns.foreach {
      case _: nir.Defn.Declare => ()
      case defn                => traverse.onDefn(defn)
    }
    val found = missing.toSeq.flatMap { name =>
      analysis.declarations
        .get(name.top)
        .flatMap(_.get(name))
        .collect {
          case defn: nir.Defn.Define =>
            nir.Defn.Declare(defn.attrs, defn.name, defn.ty)(defn.pos)
          case defn: nir.Defn.Declare => defn
        }
    }
    // Lowering drops declarations of methods it knows of, such as declared
    // but unreached ones: lower them as externs, keeping their attributes
    val attrs = found.map(defn => defn.name -> defn.attrs).toMap
    val lowered = Lower(found.map { defn =>
      defn.copy(attrs = defn.attrs.withIsExtern(true))(defn.pos)
    }).map {
      case defn: nir.Defn.Declare =>
        defn.copy(attrs = attrs(defn.name))(defn.pos)
      case defn => defn
    }
    (lowered, missing.toSet -- found.map(_.name))
  }

  // Method bodies of provided definitions are not lowered, only declared
  private def declareProvided(
      defns: Seq[nir.Defn],
      provided: Set[nir.Global]
  ): Seq[nir.Defn] =
    if (provided.isEmpty) defns
    else
      defns.map {
        case defn: nir.Defn.Define if provided.contains(defn.name) =>
          nir.Defn.Declare(defn.attrs, defn.name, defn.ty)(defn.pos)
        case defn => defn
      }

  // Kept for the types of references, never emitted
  private def isProvided(defn: nir.Defn, provided: Set[nir.Global]) =
    defn match {
      case _: nir.Defn.Declare => false
      case defn                => provided.contains(defn.name)
    }

  /** Lowers every definition of `analysis` together with the metadata of its
   *  classes, without the parts generated once per program: reflective proxies
   *  and embedded resources. Used to compile the definitions of a library unit,
   *  see `build.units.UnitCompile`.
   */
  private[scalanative] def lowerUnit(
      config: build.Config,
      analysis: ReachabilityAnalysis.Result,
      provided: Set[nir.Global]
  )(implicit
      ec: ExecutionContext
  ): Future[(CodeGenMetadata, Seq[nir.Defn], Map[nir.Global, String])] = {
    implicit def logger: build.Logger = config.logger
    implicit val platform: PlatformInfo = PlatformInfo(config)
    implicit val meta: CodeGenMetadata =
      new CodeGenMetadata(analysis, config, Nil, wholeProgram = false)
    val generated = declareProvided(Generate(None, analysis.defns), provided)
    // Definitions no program reaches may need facts the analysis lacks, such
    // as classes missing from the classpath: those are only declared
    val jobs = partitionBy(generated)(_.name).map {
      case (_, defns) =>
        Future {
          defns.map { defn =>
            try Right(Lower(Seq(defn)))
            catch {
              case failure @ (scala.util.control.NonFatal(_) |
                  _: util.UnreachableException.type) =>
                val declared = defn match {
                  case defn: nir.Defn.Define =>
                    Lower(
                      Seq(
                        nir.Defn.Declare(defn.attrs, defn.name, defn.ty)(
                          defn.pos
                        )
                      )
                    )
                  case _ => Nil
                }
                Left((defn.name, String.valueOf(failure), declared))
            }
          }
        }
    }
    Future.sequence(jobs).map { results =>
      val all = results.flatten.toSeq
      val lowered = all.flatMap {
        case Right(defns)           => defns
        case Left((_, _, declared)) => declared
      }
      val failed = all.collect {
        case Left((name, failure, _)) => name -> failure
      }.toMap
      (meta, lowered, failed)
    }
  }

  /** Writes `selected` to `outputDir`, one IR file per source file, with
   *  references to `env` declared, C symbols weakly. Returns the IR files with
   *  the C symbols each refers to. Units linked as shared libraries export
   *  their globals too.
   */
  private[scalanative] def emitUnit(
      config: build.Config,
      meta: CodeGenMetadata,
      env: Seq[nir.Defn],
      selected: Seq[nir.Defn],
      outputDir: Path,
      jarSources: Path => Option[(Path, String)]
  )(implicit ec: ExecutionContext): Seq[Future[(Path, Set[String])]] = {
    implicit val _meta: CodeGenMetadata = meta
    val envMap = env.map(defn => defn.name -> defn).toMap
    Files.createDirectories(outputDir)
    // Shared by the whole unit, the scope outlives the generation
    val scope = Scope.unsafe()
    val dir = VirtualDirectory.real(outputDir)(scope)
    val sourceCodeCache = new SourceCodeCache(config, jarSources)
    // Darwin's linker needs a compilation unit per source file, see emit
    def sourceOf(defn: nir.Defn): String = defn.pos.source match {
      case nir.SourceFile.Relative(path) => path
      case _                             => defn.name.top.id
    }
    val generators = selected.groupBy(sourceOf).toSeq.map {
      case (source, defns) =>
        Future {
          val id = build.store.Digest.of(source).hash.take(24)
          val codegen = Impl(
            envMap,
            defns.sortBy(_.name),
            sourceCodeCache,
            weak = true,
            shared = config.compilerConfig.useDynamicLibraries
          )
          val path = codegen.gen(id, dir)
          path -> codegen.weakExternSymbols.toSet
        }
    }
    Future.sequence(generators).onComplete(_ => scope.close())
    generators
  }

  private[scalanative] def lower(
      defns: Seq[nir.Defn]
  )(implicit
      meta: CodeGenMetadata,
      logger: build.Logger,
      ec: ExecutionContext
  ): Future[Seq[nir.Defn]] = {

    val loweringJobs = partitionBy(defns)(_.name).map {
      case (_, defns) => Future(Lower(defns))
    }

    Future
      .foldLeft(loweringJobs)(mutable.UnrolledBuffer.empty[nir.Defn]) {
        case (buffer, defns) => buffer ++= defns
      }
      .map(_.toSeq)
  }

  private final val EmptyPath = "__empty"
  // Definitions that exist once per program, see CodeGen.apply
  private final val ProgramPartition = "__program"

  /** Generate code for given assembly. */
  private def emit(
      config: build.Config,
      assembly: Seq[nir.Defn],
      program: Set[nir.Global] = Set.empty,
      references: Seq[nir.Defn] = Nil
  )(implicit
      meta: CodeGenMetadata,
      ec: ExecutionContext
  ): IRGenerators =
    Scope { implicit in =>
      // Definitions compiled apart are declared where referenced
      val env = (references ++ assembly).map(defn => defn.name -> defn).toMap
      val outputDirPath = config.workDir.resolve("generated")
      if (Build.userConfigHasChanged(config))
        IO.deleteRecursive(outputDirPath)
      Files.createDirectories(outputDirPath)
      val outputDir = VirtualDirectory.real(outputDirPath)
      val sourceCodeCache = new SourceCodeCache(config)

      def outputFileId(defn: nir.Defn): String =
        if (program.contains(defn.name)) ProgramPartition
        else
          defn.pos.source.directory
            .getOrElse(EmptyPath)

      // Partition into multiple LLVM IR files proportional to number
      // of available processors. This prevents LLVM from optimizing
      // across IR module boundary unless LTO is turned on.
      def separate(): IRGenerators =
        partitionBy(assembly, procs)(outputFileId).toSeq.map {
          case (id, defns) =>
            Future {
              val sorted = defns.sortBy(_.name)
              Impl(env, sorted, sourceCodeCache).gen(id.toString, outputDir)
            }
        }

      // Incremental compilation code generation
      def separateIncrementally(): IRGenerators = {
        val ctx = new IncrementalCodeGenContext(config)
        ctx.collectFromPreviousState()

        // Partition into multiple LLVM IR files per Scala source file originated from.
        // We previously partitioned LLVM IR files by package.
        // However, this caused issues with the Darwin linker when generating N_OSO symbols,
        // if a single Scala source file generates multiple LLVM IR files with the compilation unit DIEs
        // referencing the same Scala source file.
        // Because, the Darwin linker distinguishes compilation unit DIEs (debugging information entries)
        // by their DW_AT_name, DW_comp_dir attribute, and the object files' timestamps.
        // If the CU DIEs and timestamps are duplicated, the Darwin linker cannot distinguish the DIEs,
        // and one of the duplicates will be ignored.
        // As a result, the N_OSO symbol (which points to the object file path) is missing in the final binary,
        // and dsymutil fails to link some debug symbols from object files.
        // see: https://github.com/scala-native/scala-native/issues/3458#issuecomment-1701036738
        //
        // To address this issue, we partition into multiple LLVM IR files per Scala source file originated from.
        // This will ensure that each LLVM IR file only references a single Scala source file,
        // which will prevent the Darwin linker failing to generate N_OSO symbols.
        val llvmIRGenerators = assembly.groupBy(outputFileId).toSeq.map {
          case (dir, defns) =>
            Future {
              val hash = dir.hashCode().toHexString
              val outFile = outputDirPath.resolve(s"$hash.ll")
              val ownerDirectory = outFile.getParent()

              ctx.addEntry(hash, defns)
              if (ctx.shouldCompile(hash)) {
                val sorted = defns.sortBy(_.name)
                if (!Files.exists(ownerDirectory))
                  Files.createDirectories(ownerDirectory)
                Impl(env, sorted, sourceCodeCache).gen(hash, outputDir)
              } else {
                assert(ownerDirectory.toFile.exists())
                config.logger.debug(
                  s"Content of directory in $dir has not changed, skiping generation of $hash.ll"
                )
                outFile
              }
            }
        }
        Future.sequence(llvmIRGenerators).andThen {
          case _ =>
            // Save current state for next compilation run
            ctx.dump()
            ctx.clear()
        }
        llvmIRGenerators
      }

      val maybeBuildInfoGenerator = new Impl.BuildInfoCodegen(env)
        .generateIfSupported(outputDir, config)
        .map(Future.successful)

      val llvmIRGenerators =
        if (config.compilerConfig.useIncrementalCompilation)
          separateIncrementally()
        else separate()
      llvmIRGenerators ++ maybeBuildInfoGenerator
    }

  private object Impl {
    import scala.scalanative.codegen.llvm.AbstractCodeGen
    def apply(
        env: Map[nir.Global, nir.Defn],
        defns: Seq[nir.Defn],
        sourcesCache: SourceCodeCache,
        weak: Boolean = false,
        shared: Boolean = false
    )(implicit
        meta: CodeGenMetadata
    ): AbstractCodeGen =
      new StdCodeGen(env, defns, sourcesCache, weak, shared)

    private class StdCodeGen(
        env: Map[nir.Global, nir.Defn],
        defns: Seq[nir.Defn],
        sourcesCache: SourceCodeCache,
        weak: Boolean,
        shared: Boolean
    )(implicit
        meta: CodeGenMetadata
    ) extends AbstractCodeGen(env, defns) {
      override def sourceCodeCache: SourceCodeCache = sourcesCache
      override protected def weakExterns: Boolean = weak
      override protected def sharedLibrary: Boolean = shared
    }

    class BuildInfoCodegen(env: Map[nir.Global, nir.Defn])(implicit
        meta: CodeGenMetadata
    ) extends AbstractCodeGen(env, Nil) {
      import meta.config
      val buildInfos: Map[String, Any] = Map(
        "Sanitizer" -> config.sanitizer.map(_.name).getOrElse("disabled"),
        "Debug metadata" -> config.sourceLevelDebuggingConfig.enabled,
        "Embed resources" -> config.embedResources,
        "GC" -> config.gc,
        "LTO" -> config.lto,
        "Link stubs" -> config.linkStubs,
        "Mode" -> config.mode,
        "Multithreading" -> config.multithreadingSupport,
        "Optimize" -> config.optimize
      )

      /* Enable feature only where known to work. Add to list as experience grows
       * FreeBSD uses elf format so it _should_ work, but it has not been
       * exercised.
       */

      def generateIfSupported(
          dir: VirtualDirectory,
          config: build.Config
      ): Option[Path] =
        if (config.targetsLinux) Some(gen("", dir))
        else None

      override def gen(unused: String, dir: VirtualDirectory): Path = {
        dir.write(Paths.get(s"__buildInfo.ll")) { writer =>
          implicit val metadata: MetadataCodeGen.Context =
            new MetadataCodeGen.Context(
              this,
              new ShowBuilder.FileShowBuilder(writer)
            )

          val snVersion = scala.scalanative.nir.Versions.current
          val compilerInfo = s"Scala Native v$snVersion"
          val buildInfo = buildInfos
            .map { case (key, value) => s"$key: $value" }
            .mkString(", ")

          import Metadata.conversions.{tuple, stringToStr}
          // From lld.llvm.org doc: readelf --string-dump .comment <output-file>
          dbg("llvm.ident")(tuple(s"$compilerInfo ($buildInfo)"))
        }
      }
      override def sourceCodeCache: SourceCodeCache =
        throw new UnsupportedOperationException()
    }
  }

  private[scalanative] def depends(implicit
      platform: PlatformInfo
  ): Seq[nir.Global] = {
    val buf = mutable.UnrolledBuffer.empty[nir.Global]
    buf ++= Lower.depends
    buf ++= Generate.depends
    buf.toSeq
  }
}
