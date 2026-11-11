package scala.scalanative
package build

import java.nio.file.attribute.FileTime
import java.nio.file.{FileVisitOption, Files, Path, Paths, StandardOpenOption}
import java.util.Optional
import java.util.concurrent.Executors

import scala.collection.immutable
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.util.{Properties, Success, Try}

import scala.scalanative.codegen.llvm.CodeGen.IRGenerators
import scala.scalanative.linker.ReachabilityAnalysis
import scala.scalanative.util.Scope

import ScalaNative._

/** Utility methods for building code using Scala Native. */
object Build {

  /** What a build of `config` depends on, computed without building: see
   *  [[BuildPlan#outputKey]]. Hashes the classpath, reusing the digests of
   *  files unchanged since an earlier plan or build in the same `workDir`.
   */
  def plan(config: Config): BuildPlan = {
    checkWorkdirExists(config)
    BuildPlan(config)
  }

  /** Run the complete Scala Native pipeline, LLVM optimizer and system linker,
   *  producing a native binary in the end, same as `build` method.
   *
   *  This method skips the whole build and link process if the input hasn't
   *  changed from the previous build, and the previous build artifact is
   *  available at Config#artifactPath.
   *
   *  @param config
   *    The configuration of the toolchain.
   *  @return
   *    [[Config#artifactPath]], the path to the resulting native binary.
   */
  def buildCached(
      config: Config
  )(implicit scope: Scope, ec: ExecutionContext): Future[Path] = {
    val checksumPath = config.workDir.resolve("build-checksum")

    if (Files.exists(config.artifactPath) &&
        IO.readFully(checksumPath).contains(checkSum(config).toString)) {
      config.logger.info(
        "Build skipped: No changes detected in build configuration and class path contents since last build."
      )
      Future.successful(config.artifactPath)
    } else {
      build(config).andThen {
        case Success(_) =>
          // Need to re-calculate the checksum because the content of `output` have changed.
          IO.write(
            path = checksumPath,
            content = checkSum(config).toString
          )
      }
    }
  }

  /** Run the complete Scala Native pipeline, LLVM optimizer and system linker,
   *  producing a native binary in the end, same as `build` method.
   *
   *  This method skips the whole build and link process if the input hasn't
   *  changed from the previous build, and the previous build artifact is
   *  available at Config#artifactPath.
   *
   *  This method would block infinitly long for the result of
   *  `Build.buildCached` executed using dedicated ExecutionContext
   *
   *  @param config
   *    The configuration of the toolchain.
   *  @return
   *    [[Config#artifactPath]], the path to the resulting native binary.
   */
  @throws(classOf[InterruptedException])
  @throws(classOf[BuildException])
  @throws(classOf[linker.LinkingException])
  def buildCachedAwait(config: Config)(implicit scope: Scope): Path = {
    await { implicit ec: ExecutionContext =>
      Build.buildCached(config)
    }(logTrace = config.logger.trace(_))
  }

  /** Run the complete Scala Native pipeline, LLVM optimizer and system linker,
   *  producing a native binary in the end.
   *
   *  For example, to produce a binary one needs a classpath, a working
   *  directory and a main class entry point:
   *
   *  {{{
   *  val classpath: Seq[Path] = ...
   *  val basedir: Path        = ...
   *  val main: String         = ...
   *  val logger: Logger       = ...
   *
   *  val clang    = Discover.clang()
   *  val clangpp  = Discover.clangpp()
   *  val linkopts = Discover.linkingOptions()
   *  val compopts = Discover.compileOptions()
   *
   *  val config =
   *    Config.empty
   *      .withCompilerConfig{
   *        NativeConfig.empty
   *        .withGC(GC.default)
   *        .withMode(Mode.default)
   *        .withMultithreading(enabled = false)
   *        .withClang(clang)
   *        .withClangPP(clangpp)
   *        .withLinkingOptions(linkopts)
   *        .withCompileOptions(compopts)
   *        .withLinkStubs(true)
   *      }
   *      .withMainClass(main)
   *      .withClassPath(classpath)
   *      .withBaseDir(basedir)
   *      .withModuleName(moduleName)
   *      .withTestConfig(false)
   *      .withLogger(logger)
   *
   *  Build.build(config)
   *  }}}
   *
   *  @param config
   *    The configuration of the toolchain.
   *  @return
   *    [[Config#artifactPath]], the path to the resulting native binary.
   */
  def build(
      config: Config
  )(implicit scope: Scope, ec: ExecutionContext): Future[Path] = {
    val initialConfig = config
    import config.logger
    logger.timeAsync("Total") {
      // called each time for clean or directory removal
      checkWorkdirExists(initialConfig)

      // validate Config
      var config = Validator.validate(initialConfig)
      config.logger.debug(config.toString())
      def linkNIRForEntries = ScalaNative.link(config, entries(config))

      linkNIRForEntries
        .flatMap { linkerResult =>
          val (updatedConfig, needsToReload) =
            postReachabilityAnalysisConfigUpdate(config, linkerResult)
          config = updatedConfig
          if (needsToReload) linkNIRForEntries
          else Future.successful(linkerResult)
        }
        .flatMap(optimize(config, _))
        .flatMap { linkerResult =>
          val units = compileUnits(config)
          checkExclusions(units, linkerResult)
          val provided = units.flatMap(_.manifest.defined).toSet
          ScalaNative
            .codegen(config, linkerResult, provided)
            .flatMap { irGenerators =>
              compile(config, linkerResult, irGenerators, units)
            }
            .map { objects =>
              val linked = units.filter(_.manifest.exports.nonEmpty)
              val libraries = linked.flatMap(_.library)
              if (libraries.nonEmpty) {
                val artifact =
                  link(config, linkerResult, objects, libraries = libraries)
                // For build tools keeping the artifact, see BuildPlan. They
                // may leave sources, only found in debug information, out
                Seq(initialConfig, initialConfig.withSourcesClassPath(Nil))
                  .map(config => plan(config))
                  .map(plan => plan.outputKey -> plan)
                  .toMap
                  .values
                  .foreach(_.recordLibraries(libraries))
                artifact
              } else {
                // Dependent units first, for linkers resolving in one pass
                val archives = linked.reverse.map(_.archive)
                val weakSymbols = units.flatMap(_.manifest.externs).distinct
                link(config, linkerResult, objects, archives, weakSymbols)
              }
            }
            .map(artifact => postProcess(config, artifact))
        }
        .andThen { case Success(_) => dumpUserConfigHash(config) }
    }
  }

  /** Compiles `generatedIR`, which is a sequence of LLVM IR files. */
  private def compile(
      config: Config,
      analysis: ReachabilityAnalysis.Result,
      irGenerators: Seq[Future[Path]],
      compiledUnits: Seq[units.CompiledUnit] = Nil
  )(implicit ec: ExecutionContext): Future[Seq[Path]] =
    config.logger.timeAsync("Compiling to native code") {
      val cache = store.BuildCache.open(config)
      // compile generated LLVM IR
      val configChanged = userConfigHasChanged(config)
      val compileGeneratedIR = Future
        .sequence {
          irGenerators.map(irGenerator =>
            irGenerator.flatMap { generatedIR =>
              val stored = cache.map { cache =>
                LLVM.StoredCompile(
                  cache,
                  inputs = cache.digest(generatedIR),
                  root = config.workDir
                )
              }
              LLVM.compile(config, analysis, generatedIR, configChanged, stored)
            }
          )
        }

      /* Finds all the libraries on the classpath that contain native
       * code and then compiles them. Shared libraries of units hold their
       * native code, the executable registers its own sections.
       */
      val compileNativeLibs =
        findAndCompileNativeLibraries(
          config,
          analysis,
          cache,
          entry => compiledUnits.exists(_.holdsNativeCode(entry))
        ).map(_ ++ compiledUnits.flatMap(_.sections).headOption)

      Future
        .reduceLeft(
          immutable.Seq(compileGeneratedIR, compileNativeLibs)
        )(_ ++ _)
        .andThen { case _ => cache.foreach(_.close()) }
    }

  /** Links the given object files using the system's linker. */
  private def link(
      config: Config,
      analysis: ReachabilityAnalysis.Result,
      compiled: Seq[Path],
      archives: Seq[Path] = Nil,
      weakSymbols: Seq[String] = Nil,
      libraries: Seq[Path] = Nil
  ): Path = config.logger.time(
    s"Linking native code (${config.gc.name} gc, ${config.LTO.name} lto)"
  ) {
    LLVM.link(config, analysis, compiled, archives, weakSymbols, libraries)
  }

  /** Granular builds compile library units apart, see `units.UnitCompile`.
   *  Their definitions are then only declared by the program. Returns the
   *  compiled units, dependencies first.
   */
  private def compileUnits(
      config: Config
  )(implicit ec: ExecutionContext): Seq[units.CompiledUnit] =
    if (!usesUnits(config)) Nil
    else
      config.logger.time("Compiling library units") {
        val cache = store.BuildCache
          .open(config)
          .getOrElse(store.BuildCache.local(config.workDir, config.logger))
        try
          Scope { implicit scope =>
            val libraries = units.Libraries.open(config, cache)
            val loader = new linker.ClassLoader.FromDisk(libraries.map {
              case (_, library) => linker.ClassPath(library)
            })
            val partition = units.UnitPartitioner(libraries.map {
              case (entry, library) => entry -> library.index
            })
            units.UnitCompile
              .all(config, partition, loader, cache)
              .toSeq
              .sortBy(_._1)
              .map(_._2)
          }
        finally cache.close()
      }

  /** Reaching a definition left out of a library is an error, as reaching a
   *  missing symbol is, see `units.UnitCompile`.
   */
  private def checkExclusions(
      compiled: Seq[units.CompiledUnit],
      analysis: ReachabilityAnalysis.Result
  ): Unit = {
    val excluded = compiled.flatMap { unit =>
      unit.manifest.excluded.map {
        case (name, reason) => name -> (unit, reason)
      }
    }.toMap
    val reached = analysis.defns.flatMap { defn =>
      excluded.get(defn.name).map(defn.name -> _)
    }
    if (reached.nonEmpty) {
      val details = reached
        .sortBy(_._1.mangle)
        .map {
          case (name, (unit, reason)) =>
            val library = unit.unit.entries.map(_._1.getFileName).mkString(", ")
            s"  ${name.show} in $library: $reason"
        }
        .mkString("\n")
      throw new BuildException(
        s"Found ${reached.size} definitions left out of libraries compiled apart:\n$details"
      )
    }
  }

  private[build] def usesUnits(config: Config): Boolean = {
    val nativeConfig = config.compilerConfig
    nativeConfig.useGranularBuild && Discover.granularUnits() &&
      (config.targetsLinux || config.targetsMac) &&
      nativeConfig.buildTarget == BuildTarget.Application &&
      nativeConfig.lto == LTO.None
  }

  /** Based on reachability analysis check if config can be tuned for better
   *  performance
   */
  private def postReachabilityAnalysisConfigUpdate(
      config: Config,
      analysis: ReachabilityAnalysis.Result
  ): (Config, Boolean) = {
    var currentConfig = config
    var needsToReload = false

    // Each block can modify currentConfig stat,
    // modification should be lazy to not reconstruct object when not required
    locally { // disable unused mulithreading
      if (config.compilerConfig.multithreading.isEmpty) {
        // format: off
        val jlRunnable = nir.Global.Top("java.lang.Runnable")
        val jlThread = nir.Global.Top("java.lang.Thread")
        val jlThreadBuilder = nir.Global.Top("java.lang.Thread$Builder")
        val jlThreadBuildersOfPlatform = nir.Global.Top("java.lang.ThreadBuilders$PlatformThreadBuilder")

        val jlThreadStart = jlThread.member(nir.Sig.Method("start", Seq(nir.Type.Unit)))
        val jlThreadBuilderStart = jlThreadBuilder.member(nir.Sig.Method("start", Seq(jlRunnable, jlThread).map(nir.Type.Ref(_))))
        val jlThreadBuildersOfPlatformStart = jlThreadBuildersOfPlatform.member(nir.Sig.Method("start", Seq(jlRunnable, jlThread).map(nir.Type.Ref(_))))
        val usesSystemThreads =
          analysis.infos.get(jlThreadBuildersOfPlatformStart).isDefined ||
          analysis.infos.get(jlThreadBuilderStart).isDefined ||
          analysis.infos.get(jlThreadStart).isDefined
        // format: on
        if (!usesSystemThreads) {
          config.logger.info(
            "Multithreading was not explicitly enabled - initial class loading has not detected any usage of system threads. " +
              "Multithreading support will be disabled to improve performance."
          )
          currentConfig = currentConfig.withCompilerConfig(
            _.withMultithreading(false)
          )
          needsToReload = true
        }
      }
    }
    currentConfig -> needsToReload
  }

  /** Links the DWARF debug information found in the object files. */
  private def postProcess(config: Config, artifact: Path): Path =
    config.logger.time("Postprocessing") {
      if (config.targetsMac && config.compilerConfig.sourceLevelDebuggingConfig.generateFunctionSourcePositions) {
        LLVM.dsymutil(config, artifact)
      }
      artifact
    }

  /** Returns a checksum of a compilation pipeline with the given `config`. */
  private def checkSum(config: Config): Int = {
    // skip the whole nativeLink process if the followings are unchanged since the previous build
    // - build configuration
    // - class paths' mtime
    // - the output native binary ('s mtime)
    // Since the NIR code is shipped in jars, we should be able to detect the changes in NIRs.
    // One thing we miss is, we cannot detect changes in c libraries somewhere in `/usr/lib`.
    (
      config,
      config.classPath.map(getLastModifiedChild(_)),
      getLastModified(config.artifactPath)
    ).hashCode()
  }

  /** Finds and compiles native libaries.
   *
   *  @param config
   *    the compiler configuration
   *  @param analysis
   *    the result from the linker
   *  @param cache
   *    the build cache to compile through, if any
   *  @return
   *    the paths to the compiled objects
   */
  private[scala] def findAndCompileNativeLibraries(
      config: Config,
      analysis: ReachabilityAnalysis.Result,
      cache: Option[store.BuildCache] = None,
      compiledApart: Path => Boolean = _ => false
  )(implicit ec: ExecutionContext): Future[Seq[Path]] = {
    import NativeLib.{findNativeLibs, compileNativeLibrary}
    Future
      .traverse(
        findNativeLibs(config).filterNot(lib => compiledApart(lib.src))
      )(
        compileNativeLibrary(config, analysis, _, cache)
      )
      .map(_.flatten)
  }

  /** Creates a directory at `config.workDir` if one doesn't exist. */
  private def checkWorkdirExists(config: Config): Unit = {
    val workDir = config.workDir
    if (Files.notExists(workDir)) {
      Files.createDirectories(workDir)
    }
  }

  /** Returns the last time the file at `path` was modified, or the epoch
   *  (1970-01-01T00:00:00Z) if such a file doesn't exist.
   */
  private def getLastModified(path: Path): FileTime =
    if (Files.exists(path))
      Try(Files.getLastModifiedTime(path)).getOrElse(FileTime.fromMillis(0L))
    else FileTime.fromMillis(0L)

  /** Returns the last time a file rooted at `path` was modified.
   *
   *  `path` is the root of a file tree, expanding symbolic links. The result is
   *  the most recent value returned by `getLastModified` a node of this tree or
   *  `empty` if there is no file at `path`.
   */
  private def getLastModifiedChild(path: Path): Optional[FileTime] =
    if (Files.exists(path))
      Files
        .walk(path, FileVisitOption.FOLLOW_LINKS)
        .map[FileTime](getLastModified(_))
        .max(_.compareTo(_))
    else Optional.empty()

  private[scalanative] final val userConfigHashFile = "userConfigHash"

  private[scalanative] def userConfigHasChanged(config: Config): Boolean =
    IO.readFully(config.workDir.resolve(userConfigHashFile))
      .forall(_.trim() != config.compilerConfig.##.toString())

  private[scalanative] def dumpUserConfigHash(config: Config): Unit =
    IO.write(
      path = config.workDir.resolve(userConfigHashFile),
      content = config.compilerConfig.##.toString()
    )

  private def await[T](
      task: ExecutionContext => Future[T]
  )(logTrace: Throwable => Unit): T = {
    // Fatal errors, e.g. StackOverflowErrors are not propagated by Futures
    // Use a helper promise to get notified about the underlying problem
    val promise = Promise[T]()
    val executor = Executors.newFixedThreadPool(
      Runtime.getRuntime().availableProcessors(),
      (task: Runnable) => {
        val thread = Executors.defaultThreadFactory().newThread(task)
        val defaultExceptionHandler = thread.getUncaughtExceptionHandler()
        thread.setUncaughtExceptionHandler { (thread: Thread, ex: Throwable) =>
          promise.tryFailure(ex)
          ex match {
            case _: InterruptedException => logTrace(ex)
            case _ => defaultExceptionHandler.uncaughtException(thread, ex)
          }
        }
        thread
      }
    )
    implicit val ec: ExecutionContext =
      ExecutionContext.fromExecutor(executor, logTrace(_))

    // Schedule the task and record completion
    task(ec).onComplete(promise.complete)
    try Await.result(promise.future, Duration.Inf)
    finally executor.shutdown()
  }
}
