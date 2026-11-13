package scala.scalanative
package build

import java.io.{File, PrintWriter}
import java.nio.file.{Files, Path, Paths, StandardCopyOption}

import scala.concurrent._
import scala.sys.process._
import scala.util.{Failure, Success}

import scala.scalanative.build.IO.RichPath
import scala.scalanative.linker.ReachabilityAnalysis
import scala.scalanative.nir.Attr.Link

import _root_.java.io.IOException

/** Internal utilities to interact with LLVM command-line tools. */
private[scalanative] object LLVM {

  // C++14 or newer standard is needed to compile code using Windows API
  // shipped with Windows 10 / Server 2016+ (we do not plan supporting older versions)
  val defaultWinCppStd = "-std=c++14"
  val defaultCppStd = "-std=c++11"
  val defaultCStd = "-std=c11"

  /** Object file extension: ".o" */
  val oExt = ".o"

  /** C++ file extension: ".cpp" */
  val cppExt = ".cpp"

  /** LLVM intermediate file extension: ".ll" */
  val llExt = ".ll"

  /** List of source patterns used: ".c, .cpp, .S" */
  val srcExtensions = Seq(".c", cppExt, ".S")

  /** How to compile a source as a stored action, see `store.BuildCache`.
   *
   *  @param inputs
   *    Digest of everything the compilation reads besides system headers: the
   *    source itself, or the whole source tree of a native library.
   *  @param root
   *    Directory whose location is not part of the action key: paths below it
   *    are replaced by a placeholder in the compiler flags.
   *  @param source
   *    Path of the source relative to `root` when it affects the output, e.g.
   *    through `__FILE__`.
   *  @param mappedRoot
   *    Location `root` is mapped to in the object file, so that it does not
   *    depend on where the source was compiled, see `DiskStore.virtualSources`.
   */
  final case class StoredCompile(
      cache: store.BuildCache,
      inputs: store.Digest,
      root: Path,
      source: Option[String] = None,
      mappedRoot: Option[String] = None
  )

  /** Compile the given files to object files
   *
   *  @param config
   *    The configuration of the toolchain.
   *  @param analysis
   *    The output of the reachability analysis.
   *  @param path
   *    The directory path containing native files to compile.
   *  @param configChanged
   *    Whether `config` changed since `path` was last compiled, forcing a
   *    recompilation. Determined by the caller, as only it knows what to
   *    compare `config` against (e.g. native libraries compile with per-library
   *    options that the user config hash does not cover).
   *  @param stored
   *    Compile as a stored action instead, reusing the object compiled from the
   *    same inputs with the same flags by any build. `configChanged` is then
   *    irrelevant.
   *  @return
   *    The paths of the `.o` files.
   */
  def compile(
      config: Config,
      analysis: ReachabilityAnalysis.Result,
      path: Path,
      configChanged: Boolean,
      stored: Option[StoredCompile] = None
  )(implicit
      ec: ExecutionContext
  ): Future[Path] = {
    implicit val _config: Config = config
    implicit val _analysis: ReachabilityAnalysis.Result = analysis

    val inpath = path.abs
    val outpath = inpath + oExt
    val objPath = Paths.get(outpath)
    stored match {
      case Some(stored) => Future(compileStored(path, objPath, stored))
      // compile if out of date or no object file
      case None if needsCompiling(path, objPath, configChanged) =>
        Future(compileFile(path, objPath))
      case None => Future.successful(objPath)
    }
  }

  private def compileStored(
      srcPath: Path,
      objPath: Path,
      stored: StoredCompile
  )(implicit
      config: Config,
      analysis: ReachabilityAnalysis.Result
  ): Path = {
    val prefixMap = stored.mappedRoot.map { mapped =>
      s"-ffile-prefix-map=${stored.root.abs}=$mapped"
    }
    // Paths in debug information are absolute: the directory the compiler
    // runs in only needs to be the same for every build
    val compilationDir =
      s"-fdebug-compilation-dir=${store.DiskStore.VirtualSourceRoot}"
    val command =
      compileCommand(srcPath, objPath, prefixMap.toSeq :+ compilationDir)
    // Paths that differ between builds of the same inputs
    val placeholders = Seq(
      stored.root.abs -> "$ROOT",
      config.workDir.abs -> "$WORKDIR"
    )
    def normalize(arg: String): String =
      placeholders.foldLeft(arg) {
        case (arg, (path, placeholder)) => arg.replace(path, placeholder)
      }
    val (compiler, args) = (command.head, command.tail)
    val inputArgs = Set(srcPath.abs, objPath.abs)
    val key = store.ActionKey
      .builder("compile", version = 1)
      .add("toolchain", store.BuildCache.toolchain(Paths.get(compiler)))
      .add("inputs", stored.inputs)
      .add("source", stored.source.getOrElse(""))
      .addAll("flags", args.filterNot(inputArgs).map(normalize))
      .result()
    stored.cache.run(key, Seq("object" -> objPath)) {
      runCompiler(command, srcPath)
    }
    objPath
  }

  private def compileFile(srcPath: Path, objPath: Path)(implicit
      config: Config,
      analysis: ReachabilityAnalysis.Result
  ): Path = {
    runCompiler(compileCommand(srcPath, objPath, Nil), srcPath)
    objPath
  }

  // Compilers running at once in this JVM, whichever execution context runs
  // the builds: one per processor, each takes up to a few hundred MB
  private val compilers =
    new java.util.concurrent.Semaphore(Runtime.getRuntime.availableProcessors())

  private def runCompiler(command: Seq[String], srcPath: Path)(implicit
      config: Config
  ): Unit = {
    config.logger.running(command)
    compilers.acquire()
    val result =
      try
        Process(command, config.workDir.toFile) !
          Logger.toCompilerProcessLogger(config.logger)
      finally compilers.release()
    if (result != 0) {
      throw new BuildException(s"Failed to compile ${srcPath.abs}")
    }
  }

  private def compileCommand(
      srcPath: Path,
      objPath: Path,
      extraFlags: Seq[String]
  )(implicit
      config: Config,
      analysis: ReachabilityAnalysis.Result
  ): Seq[String] = {
    val inpath = srcPath.abs
    val outpath = objPath.abs
    val isCpp = inpath.endsWith(cppExt)
    val isLl = inpath.endsWith(llExt)

    val compiler = if (isCpp) config.clangPP.abs else config.clang.abs
    val langOptions = {
      if (isLl) llvmIrFeatures
      else if (isCpp) cppOptions(analysis)
      else cOptions
    }
    val platformFlags = {
      if (config.targetsMsys) msysExtras
      else Nil
    }

    val configFlags = {
      val multithreadingEnabled =
        if (config.compilerConfig.multithreadingSupport)
          Seq("-DSCALANATIVE_MULTITHREADING_ENABLED")
        else Nil
      val usingCppExceptions =
        if (config.usingCppExceptions)
          Seq("-DSCALANATIVE_USING_CPP_EXCEPTIONS")
        else Nil
      val allowTargetOverrrides =
        config.compilerConfig.targetTriple.map(_ => s"-Wno-override-module")
      multithreadingEnabled ++ usingCppExceptions ++ allowTargetOverrrides
    }
    val debugFlags = sourceLevelDebugFlags

    val flags: Seq[String] =
      buildTargetCompileOpts ++ flto(Some(inpath)) ++ sanitizer ++
        target ++ langOptions ++ platformFlags ++ debugFlags ++
        configFlags ++ Seq("-fvisibility=hidden", opt) ++
        Seq("-fomit-frame-pointer") ++ extraFlags ++
        config.compileOptions
    Seq(compiler, "-c", inpath, "-o", outpath) ++ flags
  }

  /** Creates the static library `out` from `objects`. The archive is
   *  deterministic: the same objects in the same order make the same bytes.
   */
  def archive(config: Config, objects: Seq[Path], out: Path): Path = {
    Files.createDirectories(out.toAbsolutePath().getParent())
    Files.deleteIfExists(out)
    // Archivers refuse to create empty archives
    if (objects.isEmpty) {
      IO.write(out, "!<arch>\n")
      return out
    }
    val list = out.resolveSibling(s"${out.getFileName()}.objects")
    IO.write(list, objects.map(_.abs).mkString("\n"))
    val (command, env) =
      if (config.targetsMac)
        Seq(
          "libtool",
          "-static",
          "-no_warning_for_no_symbols",
          "-o",
          out.abs,
          "-filelist",
          list.abs
        ) -> Seq("ZERO_AR_DATE" -> "1")
      else
        Seq(
          Discover.discover("ar").abs,
          "rcsD",
          out.abs,
          s"@${list.abs}"
        ) -> Nil
    config.logger.running(command)
    val result = Process(command, config.workDir.toFile(), env: _*) !
      Logger.toCompilerProcessLogger(config.logger)
    if (result != 0) throw new BuildException(s"Failed to archive ${out.abs}")
    out
  }

  /** The linker of granular builds on Linux: lld when clang can find it and the
   *  linking options choose none. Granular links read large unit archives and
   *  libraries, which lld links several times faster than GNU ld.
   */
  private def granularLinker(implicit config: Config): Seq[String] =
    if (!config.compilerConfig.useGranularBuild || !config.targetsLinux ||
        config.linkingOptions
          .exists(o => o.startsWith("-fuse-ld") || o.startsWith("--ld-path")) ||
        config.compilerConfig.lto != LTO.none) Nil
    else if (hasLld(config.clang)) Seq("-fuse-ld=lld")
    else Nil

  private val lldOf = scala.collection.concurrent.TrieMap.empty[Path, Boolean]

  // Clang looks for ld.lld next to itself, then on the PATH
  private def hasLld(clang: Path): Boolean =
    lldOf.getOrElseUpdate(
      clang.toAbsolutePath(), {
        val sibling = Option(clang.toAbsolutePath().getParent())
          .map(_.resolve("ld.lld"))
        sibling.exists(Files.isExecutable(_)) ||
          Discover.tryDiscover("ld.lld").isSuccess
      }
    )

  /** Links the shared library `out` of a library unit from every object of
   *  `archives` and `objects`, against the shared libraries `dependencies` of
   *  the same directory. Weak references to `weakSymbols` may stay undefined:
   *  they resolve to symbols of the program or of its other libraries, or to
   *  null. The library appears complete or not at all, programs may be loading
   *  the directory.
   */
  def linkSharedLibrary(
      config: Config,
      linkCppRuntime: Boolean,
      links: Seq[String],
      archives: Seq[Path],
      objects: Seq[Path],
      dependencies: Seq[Path],
      weakSymbols: Seq[String],
      out: Path
  ): Path = {
    implicit val _config: Config = config
    val name = out.getFileName.toString
    val allLinks =
      (Seq("pthread", "dl", "m") ++ config.gc.links ++ links).distinct
    val flags =
      if (config.targetsMac)
        Seq(
          "-dynamiclib",
          s"-Wl,-install_name,@rpath/$name",
          "-Wl,-rpath,@loader_path"
        ) ++ archives.map(a => s"-Wl,-force_load,${a.abs}") ++
          objects.map(_.abs) ++ dependencies.map(_.abs) ++
          weakSymbols.sorted.map(symbol => s"-Wl,-U,_$symbol")
      else
        Seq("-shared", "-fPIC", s"-Wl,-soname,$name", "-Wl,-rpath,$ORIGIN") ++
          Seq("-Wl,--whole-archive") ++ archives.map(_.abs) ++
          Seq("-Wl,--no-whole-archive") ++ objects.map(_.abs) ++
          Seq("-Wl,--as-needed") ++ dependencies.map(_.abs)
    Files.createDirectories(out.toAbsolutePath().getParent())
    val tmp = out.resolveSibling(s".$name.${java.util.UUID.randomUUID()}.tmp")
    val list = Files.createTempFile(config.workDir, name, ".link")
    try {
      IO.write(
        list,
        (flags ++ sourceLevelDebugFlags ++ sanitizer ++ target ++
          Seq("-o", tmp.abs) ++ granularLinker ++ config.linkingOptions ++
          allLinks.map("-l" + _))
          .map(flag => escapeWhitespaces(flag.replace("\\", "/")))
          .mkString("", "\n", "\n")
      )
      val compiler =
        if (config.usingCppExceptions || linkCppRuntime) config.clangPP.abs
        else config.clang.abs
      val command = Seq(compiler, s"@${list.abs}")
      config.logger.running(command)
      val result = Process(command, config.workDir.toFile()) !
        Logger.toCompilerProcessLogger(config.logger)
      if (result != 0) throw new BuildException(s"Failed to link ${out.abs}")
      Files.move(
        tmp,
        out,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING
      )
    } finally {
      Files.deleteIfExists(tmp)
      Files.deleteIfExists(list)
    }
    out
  }

  private val linkable =
    new java.util.concurrent.ConcurrentHashMap[Seq[String], Boolean]()

  /** Whether the linker finds the library `name`. */
  private[build] def isLinkable(config: Config, name: String): Boolean = {
    val kind = if (config.targetsMac) "-dynamiclib" else "-shared"
    val command = Seq(config.clang.abs, kind, "-x", "c", "-") ++
      config.linkingOptions :+ s"-l$name"
    linkable.computeIfAbsent(
      command,
      _ => {
        val out = Files.createTempFile(config.workDir, "linkable", ".so")
        val empty =
          new _root_.java.io.ByteArrayInputStream(Array.emptyByteArray)
        val process = Process(command ++ Seq("-o", out.abs)) #< empty
        try process.!(ProcessLogger(_ => ())) == 0
        finally Files.deleteIfExists(out)
      }
    )
  }

  /** Links a collection of `.ll.o` files and the `.o` files from the
   *  `nativelib`, other libaries, and the application project into the native
   *  binary.
   *
   *  @param config
   *    The configuration of the toolchain.
   *  @param linkerResult
   *    The results from the linker.
   *  @param objectPaths
   *    The paths to all the `.o` files.
   *  @return
   *    `outpath` The config.artifactPath
   */
  def link(
      config: Config,
      analysis: ReachabilityAnalysis.Result,
      objectsPaths: Seq[Path],
      archives: Seq[Path] = Nil,
      weakSymbols: Seq[String] = Nil,
      sharedLibraries: Seq[Path] = Nil
  ): Path = {
    implicit val _config: Config = config
    implicit val _analysis: ReachabilityAnalysis.Result = analysis
    val buildPath = config.buildPath

    // don't link if no changes
    if (!needsLinking(objectsPaths ++ archives ++ sharedLibraries, buildPath)) {
      return copyOutput(config, buildPath)
    }

    val command = config.compilerConfig.buildTarget match {
      case BuildTarget.Application | BuildTarget.LibraryDynamic =>
        prepareLinkCommand(objectsPaths, archives, weakSymbols, sharedLibraries)
      case BuildTarget.LibraryStatic =>
        prepareArchiveCommand(objectsPaths)
    }
    // link
    val result = command ! Logger.toCompilerProcessLogger(config.logger)
    if (result != 0) {
      throw new BuildException(s"Failed to link ${buildPath}")
    }

    copyOutput(config, buildPath)
  }

  /** Links the DWARF debug information found in the object file at `path`,
   *  reading toolchain configuations from `config`.
   */
  def dsymutil(config: Config, path: Path): Unit =
    Discover.tryDiscover("dsymutil", "LLVM_BIN").flatMap { dsymutil =>
      val proc = Process(Seq(dsymutil.abs, path.abs), config.workDir.toFile())
      val result = proc ! Logger.toCompilerProcessLogger(config.logger)
      if (result != 0) {
        Failure(
          new BuildException(
            s"Failed to link the debug information."
          )
        )
      } else Success(())
    } match {
      case Failure(e) => config.logger.warn(e.getMessage())
      case Success(_) =>
    }

  /** The files of the dSYM bundle of `binary` produced by `linkDebugInfo`: the
   *  DWARF of the binary and the bundle's `Info.plist`.
   */
  def dsymFiles(binary: Path): Seq[Path] = {
    val contents = Paths.get(binary.toString + ".dSYM", "Contents")
    Seq(
      contents.resolve("Resources/DWARF").resolve(binary.getFileName),
      contents.resolve("Info.plist")
    )
  }

  /** Links the DWARF debug information of the objects `binary` was linked from
   *  into its dSYM bundle, see `dsymFiles`: the bundle no longer needs the
   *  objects, so it stays valid when they are gone.
   */
  def linkDebugInfo(config: Config, dsymutil: Path, binary: Path): Unit = {
    val bundle = Paths.get(binary.toString + ".dSYM")
    val tmp = Files.createTempDirectory(config.workDir, "dsym")
    try {
      val linked = tmp.resolve(bundle.getFileName)
      val command = Seq(dsymutil.abs, binary.abs, "-o", linked.abs)
      config.logger.running(command)
      val result = Process(command, config.workDir.toFile()) !
        Logger.toCompilerProcessLogger(config.logger)
      if (result != 0)
        throw new BuildException(
          s"Failed to link the debug information of $binary"
        )
      // Only the files kept by the build cache, not the relocation maps
      // dsymutil may add
      dsymFiles(binary).foreach { file =>
        val source = linked.resolve(bundle.relativize(file))
        Files.createDirectories(file.getParent())
        Files.move(source, file, StandardCopyOption.REPLACE_EXISTING)
      }
    } finally IO.deleteRecursive(tmp)
  }

  /** Tells lldb where the sources of the virtual root are, see
   *  `DiskStore.virtualSources`: a `<UUID>.plist` with `DBGSourcePathRemapping`
   *  in the dSYM bundle of `binary`. It names the store of this machine, so it
   *  is written by each build instead of being stored.
   */
  def writeSourceRemapping(binary: Path, sourcesRoot: Path): Unit = {
    val dwarf = dsymFiles(binary).head
    machoUuid(dwarf).foreach { uuid =>
      val plist = dwarf.getParent().getParent().resolve(s"$uuid.plist")
      val content =
        s"""|<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            |<plist version="1.0">
            |<dict>
            |  <key>DBGVersion</key>
            |  <string>3</string>
            |  <key>DBGSourcePathRemapping</key>
            |  <dict>
            |    <key>${store.DiskStore.VirtualSourceRoot}</key>
            |    <string>${xmlEscape(sourcesRoot.abs)}</string>
            |  </dict>
            |</dict>
            |</plist>
            |""".stripMargin
      if (!IO.readFully(plist).contains(content)) {
        val tmp = Files.createTempFile(plist.getParent(), ".remap", ".tmp")
        try {
          IO.write(tmp, content)
          Files.move(tmp, plist, StandardCopyOption.REPLACE_EXISTING)
        } finally Files.deleteIfExists(tmp)
      }
    }
  }

  private def xmlEscape(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

  /** The UUID of the 64-bit Mach-O file at `path`, as dSYM bundles name it. */
  private def machoUuid(path: Path): Option[String] = {
    val LcUuid = 0x1b
    // The header and the load commands, not the debug information after them
    def read(channel: java.nio.channels.FileChannel, size: Int) = {
      val buffer = java.nio.ByteBuffer
        .allocate(size)
        .order(java.nio.ByteOrder.LITTLE_ENDIAN)
      channel.read(buffer, 0L)
      buffer
    }
    try {
      val channel = java.nio.channels.FileChannel.open(path)
      val buffer =
        try {
          val header = read(channel, 32)
          read(channel, 32 + math.max(header.getInt(20), 0))
        } finally channel.close()
      if (buffer.getInt(0) != 0xfeedfacf) None
      else {
        val commands = buffer.getInt(16)
        var offset = 32
        var uuid = Option.empty[String]
        var i = 0
        while (uuid.isEmpty && i < commands) {
          val size = buffer.getInt(offset + 4)
          if (buffer.getInt(offset) == LcUuid) {
            val hex = (0 until 16)
              .map(j => f"${buffer.get(offset + 8 + j) & 0xff}%02X")
              .mkString
            uuid = Some(
              Seq(0 -> 8, 8 -> 12, 12 -> 16, 16 -> 20, 20 -> 32)
                .map { case (from, to) => hex.substring(from, to) }
                .mkString("-")
            )
          }
          offset += size
          i += 1
        }
        uuid
      }
    } catch {
      // Not a Mach-O file, or a damaged one
      case _: java.io.IOException | _: IndexOutOfBoundsException => None
    }
  }

  /** This function allows a project to have multiple `main` files by copying
   *  the one selected to the same parent directory as the `workDir` which is by
   *  default named `native`. Since the directory is named `native`, having a
   *  project named `native` will by default produce an executable named
   *  `native` which will throw an exception since the copy command uses
   *  REPLACE_EXISTING.
   *
   *  Having a project or `baseName` named `native` conflicts with the build.
   */
  private def copyOutput(config: Config, buildPath: Path) = {
    val outPath = config.artifactPath
    try {
      config.compilerConfig.buildTarget match {
        case BuildTarget.Application =>
          Files.copy(buildPath, outPath, StandardCopyOption.REPLACE_EXISTING)
        case _: BuildTarget.Library => outPath
      }
    } catch {
      case ex: IOException if (outPath.toFile().exists()) =>
        throw new BuildException(
          s"""|Executable build module or `baseName` is named 'native'
              |which conflicts with the compiler `workDir`.
              |Please rename the build module or
              |use `withBaseName` to rename the executable.
              |Cause: ${ex}""".stripMargin
        )
    }
  }

  private def prepareLinkCommand(
      objectsPaths: Seq[Path],
      archives: Seq[Path],
      weakSymbols: Seq[String],
      sharedLibraries: Seq[Path]
  )(implicit config: Config, analysis: ReachabilityAnalysis.Result) = {
    val workDir = config.workDir
    val links = {
      val srclinks = analysis.links.map(_.name)
      // FreeBSD packages the thread-enabled Boehm collector separately.
      val gclinks =
        if (config.gc == GC.boehm && config.targetsFreeBSD) Seq("gc-threaded")
        else config.gc.links
      // We need extra linking dependencies for:
      // * libdl for our vendored libunwind implementation.
      // * libpthread for process APIs and parallel garbage collection.
      // * dbghelp for Windows symbol/line lookup (embedded DWARF is primary when it matches)
      val platformsLinks =
        if (config.targetsWindows) Seq("dbghelp")
        else if (config.targetsOpenBSD) Seq("pthread")
        else if (config.targetsNetBSD) Seq("pthread", "m")
        else Seq("pthread", "dl", "m")
      platformsLinks ++ srclinks ++ gclinks
    }.distinct
    config.logger.info(s"Linking with [${links.mkString(", ")}]")
    // GNU ld and ld.lld support the --as-needed flag which avoids linking
    // libraries (defined after the option) you don't use. LLVM intrinsics
    // call libm which is not added by default. However, the math functions
    // in libm as often inlined in release mode which makes it useless to
    // link it. The Mac OS linker doesn't support the flag and libm is not
    // in a separate library there, so we use it only in other UNIX targets
    // (also Windows on msys and cgwin)
    val asNeededLinkerFlags =
      if (config.targetsWindows || config.targetsMac) Nil
      else List("-Wl,--as-needed")
    val linkopts =
      asNeededLinkerFlags ++ config.linkingOptions ++ links.map("-l" + _)

    val debugFlags = sourceLevelDebugFlags

    val platformFlags =
      if (!config.targetsWindows) Nil
      else {
        // https://github.com/scala-native/scala-native/issues/2372
        // Clang 22+ defaults to lld-link; allow duplicate UCRT/CPMT symbols.
        Seq("-fuse-ld=lld", "-Wl,/force:multiple")
      }

    // This is to ensure that the load path of the resulting dynamic library
    // only contains the library filename, instead of the full path
    // (i.e. in the target folder of SBT build) - this would make the library
    // non-portable
    val linkNameFlags =
      if (config.compilerConfig.buildTarget == BuildTarget.LibraryDynamic)
        if (config.targetsLinux)
          List(s"-Wl,-soname,${config.artifactName}")
        else if (config.targetsMac)
          List(s"-Wl,-install_name,@rpath/${config.artifactName}")
        else Nil
      else Nil

    val output = Seq("-o", config.buildPath.abs)

    // it's a fix for passing too many file paths to the clang compiler,
    // If too many packages are compiled and the platform is windows, windows
    // terminal doesn't support too many characters, which will cause an error.
    val configFile = workDir.resolve("llvmLinkInfo").toFile
    locally {
      val pw = new PrintWriter(configFile)
      def add(str: String) =
        // Paths containg whitespaces needs to be escaped, otherwise
        // config file might be not interpretted correctly by the LLVM
        // in windows system, the file separator doesn't work very well, so we
        // replace it to linux file separator
        pw.println(escapeWhitespaces(str.replace("\\", "/")))

      try {
        buildTargetLinkOpts.foreach(add)
        granularLinker.foreach(add)
        flto().foreach(add)
        debugFlags.foreach(add)
        platformFlags.foreach(add)
        linkNameFlags.foreach(add)
        output.foreach(add)
        sanitizer.foreach(add)
        target.foreach(add)

        val useLdd = config.linkingOptions.contains("-fuse-ld=lld")

        // lld requires that object files are listed in the order
        // they require each other. We don't do that so we wrap
        // the files in --start-lib and --end-lib which consider
        // them like they were in a .a library and links all symbols
        // regardless of ordering
        if (useLdd) add("-Wl,--start-lib")
        objectsPaths.foreach(p => add(p.abs))
        if (useLdd) add("-Wl,--end-lib")

        // Libraries compiled apart, objects are pulled when referenced. They
        // refer to each other, GNU ld needs them grouped to resolve that
        if (archives.nonEmpty) {
          val group = !config.targetsMac
          if (group) add("-Wl,--start-group")
          archives.foreach(p => add(p.abs))
          if (group) add("-Wl,--end-group")
        }
        // Weak references to symbols defined nowhere are null at runtime, the
        // Apple linker must be told they may stay undefined
        if (config.targetsMac)
          weakSymbols.sorted.foreach(symbol => add(s"-Wl,-U,_$symbol"))
        // Libraries compiled apart and linked dynamically, found where they
        // were built. LD_LIBRARY_PATH, like DYLD_LIBRARY_PATH on macOS, takes
        // precedence over a RUNPATH, so they can be found elsewhere too
        sharedLibraries.foreach(p => add(p.abs))
        sharedLibraries
          .map(_.toAbsolutePath().getParent())
          .distinct
          .foreach(dir => add(s"-Wl,-rpath,${dir.abs}"))
        if (sharedLibraries.nonEmpty && !config.targetsMac)
          add("-Wl,--enable-new-dtags")

        linkopts.foreach(add)

      } finally pw.close()
    }

    val compiler =
      if (isCppRuntimeRequired(config, analysis)) config.clangPP.abs
      else config.clang.abs

    val command = Seq(compiler, s"@${configFile.getAbsolutePath()}")
    config.logger.running(command)
    Process(command, config.workDir.toFile())
  }

  private def prepareArchiveCommand(
      objectPaths: Seq[Path]
  )(implicit config: Config) = {
    val workDir = config.workDir

    val MRICompatibleAR =
      Discover.tryDiscover("llvm-ar", "LLVM_BIN").toOption orElse
        // MacOS ar command does not support -M flag...
        Discover.tryDiscover("ar").toOption.filter(_ => config.targetsLinux)

    def stageFiles(): Seq[String] = {
      objectPaths.map { path =>
        val uniqueName =
          workDir
            .relativize(path)
            .toString()
            .replace(File.separator, "_")
        val newPath = workDir.resolve(uniqueName)
        Files.move(path, newPath, StandardCopyOption.REPLACE_EXISTING)
        newPath.abs
      }
    }

    def useMRIScript(ar: Path) = {
      val MIRScriptFile = workDir.resolve("MIRScript").toFile
      val pw = new PrintWriter(MIRScriptFile)
      try {
        pw.println(s"CREATE ${escapeWhitespaces(config.buildPath.abs)}")
        stageFiles().foreach { path =>
          pw.println(s"ADDMOD ${escapeWhitespaces(path)}")
        }
        pw.println("SAVE")
        pw.println("END")
      } finally pw.close()

      val command = Seq(ar.abs, "-M")
      config.logger.running(command)

      Process(command, config.workDir.toFile()) #< MIRScriptFile
    }

    MRICompatibleAR match {
      case None =>
        val ar = Discover.discover("ar")
        val command = Seq(ar.abs, "rc", config.buildPath.abs) ++ stageFiles()
        config.logger.running(command)
        Process(command, config.workDir.toFile())
      case Some(path) => useMRIScript(path)
    }
  }

  /** Checks the input timestamp to see if the file needs compiling. The call to
   *  lastModified will return 0 for a non-existent output file but that makes
   *  the timestamp always less forcing a recompile.
   *
   *  @param in
   *    the source file
   *  @param out
   *    the object file
   *  @param configChanged
   *    whether the compilation config changed since the last build
   *  @return
   *    true if it needs compiling false otherwise.
   */
  @inline private def needsCompiling(
      in: Path,
      out: Path,
      configChanged: Boolean
  ): Boolean = {
    in.toFile().lastModified() > out.toFile().lastModified() ||
    configChanged
  }

  /** Looks at all the object files to see if one is newer than the output
   *  (executable). All object files will be compiled at this time so
   *  lastModified will always be a real time stamp. The output executable
   *  lastModified can be 0 but that forces the link to occur.
   *
   *  @param in
   *    the list of object file to link
   *  @param out
   *    the executable
   *  @return
   *    true if it needs linking
   */
  @inline private def needsLinking(in: Seq[Path], out: Path): Boolean = {
    val inmax = in.map(_.toFile().lastModified()).max
    val outmax = out.toFile().lastModified()
    inmax > outmax
  }

  /** DWARF debug sections embedded in the binary (PE or ELF), used by stack
   *  traces.
   */
  private def sourceLevelDebugFlags(implicit config: Config): Seq[String] =
    if (config.compilerConfig.sourceLevelDebuggingConfig.enabled) {
      // newer LLVM defaults to DWARFv5 on some targets; runtime supports only v4 for now
      Seq("-gdwarf-4")
    } else if (config.targetsWindows) {
      // CodeView/PDB symbols are needed for DbgHelp stack traces even when
      // source-level debugging (and embedded DWARF) is disabled.
      Seq("-g")
    } else Nil

  /** LTO flags for compiling `compiledFile`, or for linking if `None`. */
  private def flto(compiledFile: Option[String] = None)(implicit
      config: Config,
      analysis: ReachabilityAnalysis.Result
  ): Seq[String] = {
    def isNativeSource = compiledFile.exists(!_.endsWith(llExt))
    def isVendoredUnwind = compiledFile.exists(
      _.contains(s"${File.separator}libunwind${File.separator}")
    )
    def usesContinuations = analysis.preprocessorDefinitions.contains(
      nir.Attr.Define("__SCALANATIVE_DELIMCC")
    )
    config.compilerConfig.lto match {
      case LTO.None => Seq.empty
      // Inlined C/C++ could reuse a TLS address across a continuation suspend,
      // reading the old carrier's state after a virtual thread migrates.
      case _
          if isNativeSource && config.compilerConfig.multithreadingSupport && usesContinuations =>
        Seq("-fno-lto")
      // Apple ld does not materialize unw_* symbols that exist only in bitcode.
      case _ if config.targetsMac && isVendoredUnwind => Seq("-fno-lto")
      case lto => Seq(s"-flto=${lto.name}")
    }
  }

  private def sanitizer(implicit config: Config): Seq[String] =
    config.compilerConfig.sanitizer match {
      case Some(sanitizer) =>
        Seq(s"-fsanitize=${sanitizer.name}", "-fno-omit-frame-pointer")
      case _ => Seq.empty
    }

  private def target(implicit config: Config): Seq[String] =
    config.compilerConfig.targetTriple match {
      case Some(tt) => Seq("-target", tt)
      case None     => Seq("-Wno-override-module")
    }

  private def opt(implicit config: Config): String =
    config.mode match {
      case Mode.Debug       => "-O0"
      case Mode.ReleaseFast => "-O2"
      case Mode.ReleaseSize => "-Oz"
      case Mode.ReleaseFull => "-O3"
    }

  private def isStdFlag(option: String): Boolean =
    option.startsWith("-std=") || option.startsWith("--std=")

  private def cppOptions(
      analysis: ReachabilityAnalysis.Result
  )(implicit config: Config): Seq[String] = {
    val options = config.compilerConfig.cppOptions
    val defaultStd =
      if (config.targetsWindows) defaultWinCppStd else defaultCppStd
    val languageStandard = {
      val hasStdFlag = options.exists(isStdFlag)
      if (!hasStdFlag) List(defaultStd)
      else Nil
    }
    // this will change
    val exceptionsHandling =
      if (isCppRuntimeRequired(config, analysis)) { // checks for -fcxx-exceptions
        List("-fexceptions", "-funwind-tables")
      } else
        List("-fno-rtti", "-fno-exceptions", "-funwind-tables")

    languageStandard ++ options ++ exceptionsHandling
  }

  private def cOptions(implicit config: Config): Seq[String] = {

    val options = config.compilerConfig.cOptions

    val languageStandard = {
      val hasStdFlag = options.exists(isStdFlag)
      if (!hasStdFlag) List(defaultCStd)
      else Nil
    }
    // Prevent LTO from inferring nounwind for C exception handlers (#4190).
    val exceptionHandling = List("-fexceptions", "-funwind-tables")

    languageStandard ++ options ++ exceptionHandling
  }

  private def llvmIrFeatures(implicit config: Config): Seq[String] = {
    implicit def nativeConfig: NativeConfig = config.compilerConfig
    val opaquePointers = Discover.features.opaquePointers.requiredFlag.toList
      .flatMap(Seq("-mllvm", _))

    opaquePointers
  }

  private def buildTargetCompileOpts(implicit config: Config): Seq[String] =
    config.compilerConfig.buildTarget match {
      case BuildTarget.Application =>
        Nil
      case BuildTarget.LibraryStatic =>
        optionalPICflag ++ Seq("--emit-static-lib")
      case BuildTarget.LibraryDynamic =>
        optionalPICflag :+
          "-DSCALANATIVE_DYLIB" // allow to compile dynamic library constructor in dylib_init.c
    }

  private def buildTargetLinkOpts(implicit config: Config): Seq[String] = {
    val optRdynamic =
      if (config.targetsWindows) Nil
      else {
        if (config.linkingOptions.contains("-static")) Nil
        else Seq("-rdynamic")
      }
    config.compilerConfig.buildTarget match {
      case BuildTarget.Application =>
        optRdynamic
      case BuildTarget.LibraryStatic =>
        optionalPICflag ++ Seq("--emit-static-lib")
      case BuildTarget.LibraryDynamic =>
        val libFlag = if (config.targetsMac) "-dynamiclib" else "-shared"
        Seq(libFlag) ++ optionalPICflag ++ optRdynamic
    }
  }

  private def optionalPICflag(implicit config: Config): Seq[String] =
    if (config.targetsWindows) Nil
    else Seq("-fPIC")

  private def escapeWhitespaces(str: String): String = {
    if (str.exists(_.isWhitespace)) s""""$str""""
    else str
  }

  private def isCppRuntimeRequired(
      config: Config,
      analysis: ReachabilityAnalysis.Result
  ) = config.usingCppExceptions || analysis.linkCppRuntime

  lazy val msysExtras = Seq(
    "-D_WIN64",
    "-D__MINGW64__",
    "-D_X86_64_ -D__X86_64__ -D__x86_64",
    "-D__USING_SJLJ_EXCEPTIONS__",
    "-DNO_OLDNAMES",
    "-D_LIBUNWIND_BUILD_ZERO_COST_APIS"
  )

}
