package scala.scalanative
package build
package units

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

import scala.scalanative.build.store.{ActionKey, BuildCache, Digest}
import scala.scalanative.codegen.llvm.CodeGen
import scala.scalanative.linker.{ClassLoader, Reach, ReachabilityAnalysis}

/** What a compiled library unit provides to programs and dependent units.
 *
 *  @param exports
 *    symbols other units and programs may refer to: public members, the
 *    metadata of classes, and private members used by inlined bodies
 *  @param hidden
 *    the other symbols defined by the archive
 *  @param excluded
 *    definitions left out of the unit, with the reason
 *  @param externs
 *    C symbols the archive refers to weakly
 *  @param links
 *    libraries of every `@link` of the unit
 *  @param linkCppRuntime
 *    whether the unit needs the C++ runtime
 */
private[scalanative] final case class UnitManifest(
    exports: Set[nir.Global],
    hidden: Set[nir.Global],
    excluded: Map[nir.Global, String],
    externs: Set[String],
    links: Set[String] = Set.empty,
    linkCppRuntime: Boolean = false
) {
  def defined: Set[nir.Global] = exports ++ hidden

  /** Changes only when what dependants may refer to changes. */
  lazy val exportsDigest: Digest =
    Digest.of(exports.toSeq.map(_.mangle).sorted.mkString("\n"))

  def serialize: String = {
    def lines(kind: String, names: Set[nir.Global]) =
      names.toSeq.map(name => s"$kind\t${name.mangle}").sorted
    val excludedLines = excluded.toSeq.map {
      case (name, reason) =>
        s"exclude\t${name.mangle}\t${reason.replaceAll("[\t\n]", " ")}"
    }.sorted
    val externLines = externs.toSeq.sorted.map(symbol => s"extern\t$symbol")
    val linkLines = links.toSeq.sorted.map(name => s"link\t$name") ++
      (if (linkCppRuntime) Seq("cpp") else Nil)
    (lines("export", exports) ++ lines("hidden", hidden) ++ excludedLines ++
      externLines ++ linkLines).mkString("", "\n", "\n")
  }
}

private[scalanative] object UnitManifest {
  def parse(text: String): UnitManifest = {
    val exports = Set.newBuilder[nir.Global]
    val hidden = Set.newBuilder[nir.Global]
    val excluded = Map.newBuilder[nir.Global, String]
    val externs = Set.newBuilder[String]
    val links = Set.newBuilder[String]
    var linkCppRuntime = false
    text.split('\n').foreach { line =>
      line.split('\t') match {
        case Array("export", name) =>
          exports += nir.Unmangle.unmangleGlobal(name)
        case Array("hidden", name) =>
          hidden += nir.Unmangle.unmangleGlobal(name)
        case Array("exclude", name, reason) =>
          excluded += nir.Unmangle.unmangleGlobal(name) -> reason
        case Array("extern", symbol) => externs += symbol
        case Array("link", name)     => links += name
        case Array("cpp")            => linkCppRuntime = true
        case _                       => ()
      }
    }
    UnitManifest(
      exports.result(),
      hidden.result(),
      excluded.result(),
      externs.result(),
      links.result(),
      linkCppRuntime
    )
  }
}

/** A compiled library unit: an archive with one object per source file.
 *
 *  @param library
 *    the shared library of the unit, with its native code, when units are
 *    linked dynamically
 *  @param sections
 *    the object registering the static sections of an image with the runtime,
 *    linked into every image when units are linked dynamically
 */
private[scalanative] final case class CompiledUnit(
    unit: BuildUnit,
    archive: Path,
    manifest: UnitManifest,
    library: Option[Path] = None,
    sections: Option[Path] = None
) {

  /** Whether the shared library holds the native code of `entry`. */
  def holdsNativeCode(entry: Path): Boolean =
    library.isDefined && unit.entries.exists {
      case (e, _) => e.toAbsolutePath() == entry.toAbsolutePath()
    }
}

/** Compiles every definition of a library unit, independently of the programs
 *  that will link it.
 *
 *  All definitions of the unit are reached, with whatever they need from its
 *  dependencies. A definition referring to a symbol missing from the classpath,
 *  unsupported in the configuration, listed in
 *  `NativeConfig.libraryExclusions`, or itself excluded, is left out: as when
 *  linking a whole program, reaching it is an error, so libraries never depend
 *  on the programs using them. Class metadata is kept: virtual table slots of
 *  excluded methods call a trap instead. Programs link the archive and pull
 *  only the objects they refer to.
 */
private[scalanative] object UnitCompile {
  final val FormatVersion = 15

  /** Compiles the library units of `partition`, dependencies first, as stored
   *  actions. Returns them by index in the partition, library units only.
   */
  def all(
      config: Config,
      partition: Seq[BuildUnit],
      loader: ClassLoader,
      cache: BuildCache
  )(implicit ec: ExecutionContext): Map[Int, CompiledUnit] = {
    val owners = ownersOf(partition)
    val compiled = mutable.LinkedHashMap.empty[Int, CompiledUnit]
    partition.zipWithIndex.foreach {
      case (unit, index) if isLibrary(unit) =>
        val dependencies = transitiveDependencies(partition, index)
        // Dependencies outside libraries are never compiled apart
        if (dependencies.forall(compiled.contains))
          compiled(index) = compileUnit(
            config,
            partition,
            index,
            dependencies.map(i => i -> compiled(i)).toMap,
            owners,
            loader,
            cache
          )
      case _ => ()
    }
    compiled.toMap
  }

  /** Units made only of jars, the classpath entries that do not change while
   *  developing an application.
   */
  def isLibrary(unit: BuildUnit): Boolean =
    unit.entries.forall { case (entry, _) => Files.isRegularFile(entry) }

  def ownersOf(partition: Seq[BuildUnit]): Map[nir.Global.Top, Int] = {
    val owners = mutable.HashMap.empty[nir.Global.Top, Int]
    // The first entry defining a class owns it, as when linking
    val byEntry = partition.zipWithIndex.flatMap {
      case (unit, index) => unit.entries.map { case (e, l) => (e, l, index) }
    }
    byEntry.foreach {
      case (_, library, index) =>
        library.classes.foreach { cls =>
          if (!owners.contains(cls.name)) owners(cls.name) = index
        }
    }
    owners.toMap
  }

  def transitiveDependencies(
      partition: Seq[BuildUnit],
      index: Int
  ): Seq[Int] = {
    val seen = mutable.LinkedHashSet.empty[Int]
    def visit(i: Int): Unit = partition(i).dependencies.foreach { dep =>
      if (seen.add(dep)) visit(dep)
    }
    visit(index)
    seen.toSeq.sorted
  }

  private def compileUnit(
      config: Config,
      partition: Seq[BuildUnit],
      index: Int,
      dependencies: Map[Int, CompiledUnit],
      owners: Map[nir.Global.Top, Int],
      loader: ClassLoader,
      cache: BuildCache
  )(implicit ec: ExecutionContext): CompiledUnit = {
    val unit = partition(index)
    val id = unit.digest.hash.take(16)
    val dir = config.workDir.resolve("units").resolve(id)
    val archive = dir.resolve("unit.a")
    val manifestPath = dir.resolve("unit.exports")
    // Classpath entries without Scala Native classes need no library
    val ownsClasses = unit.entries.exists {
      case (_, library) =>
        library.classes.exists(cls => owners.get(cls.name).contains(index))
    }
    val native =
      if (!config.compilerConfig.useDynamicLibraries || !ownsClasses) None
      else {
        // Units without the runtime native code reuse the object of a
        // dependency
        val dependencySections = dependencies.toSeq
          .sortBy(_._1)
          .flatMap(_._2.sections)
          .headOption
        Some(
          Native(
            dir.resolve("natives.a"),
            dir.resolve("sections.o"),
            dependencySections
          )
        )
      }
    val key = dependencies.toSeq
      .sortBy(_._1)
      .foldLeft(
        ActionKey
          .builder("compile-unit", FormatVersion)
          .add("unit", unit.digest)
          .add("plan", Digest.of(unitPlan(config)))
      ) {
        case (builder, (_, dep)) =>
          builder
            .add("dependency-abi", dep.unit.abiDigest)
            .add("dependency-exports", dep.manifest.exportsDigest)
      }
    native.flatMap(_.dependencySections).foreach { sections =>
      key.add("dependency-sections", cache.digest(sections))
    }
    val outputs = Seq("archive" -> archive, "manifest" -> manifestPath) ++
      native.toSeq.flatMap { native =>
        Seq("natives" -> native.archive, "sections" -> native.sections)
      }
    cache.run(key.result(), outputs) {
      val manifest = compileSources(
        config,
        unit,
        index,
        dependencies,
        owners,
        loader,
        cache,
        dir,
        archive,
        native
      )
      IO.write(manifestPath, manifest.serialize)
    }
    val manifest = UnitManifest.parse(
      new String(Files.readAllBytes(manifestPath), StandardCharsets.UTF_8)
    )
    CompiledUnit(
      unit,
      archive,
      manifest,
      native.map(link(config, manifest, archive, _, dependencies, cache)),
      native.map(_.sections)
    )
  }

  /** The native code of a unit linked dynamically: an archive of its objects,
   *  and the object registering the static sections of an image.
   */
  private case class Native(
      archive: Path,
      sections: Path,
      dependencySections: Option[Path]
  )

  /** Links the shared library of a unit into the library directory of the build
   *  cache, see `BuildCache.libraryDir`. Libraries are named after their
   *  inputs, dependencies included: they never change once linked, so builds
   *  and machines can share them, and they find each other in the same
   *  directory wherever it is.
   */
  private def link(
      config: Config,
      manifest: UnitManifest,
      archive: Path,
      native: Native,
      dependencies: Map[Int, CompiledUnit],
      cache: BuildCache
  ): Path = {
    val dependencyLibraries =
      dependencies.toSeq.sortBy(_._1).flatMap(_._2.library)
    // Libraries of other platforms are left out, Scala externs being weak
    val links =
      manifest.links.toSeq.sorted.filter(LLVM.isLinkable(config, _))
    val key = ActionKey
      .builder("link-unit", FormatVersion)
      .add("plan", Digest.of(unitPlan(config)))
      .add("archive", cache.digest(archive))
      .add("natives", cache.digest(native.archive))
      .add("sections", cache.digest(native.sections))
      .addAll("dependencies", dependencyLibraries.map(_.getFileName.toString))
      .addAll("links", links)
      .add("cpp", manifest.linkCppRuntime.toString)
      .result()
    val extension = if (config.targetsMac) "dylib" else "so"
    val library =
      cache.libraryDir.resolve(s"libsn-${key.hash.take(16)}.$extension")
    cache.run(key, Seq("library" -> library)) {
      LLVM.linkSharedLibrary(
        config,
        manifest.linkCppRuntime,
        links,
        Seq(archive, native.archive),
        Seq(native.sections),
        dependencyLibraries,
        manifest.externs.toSeq,
        library
      )
    }
    library
  }

  // What unit code depends on besides its definitions: the build plan without
  // the classpath and the artifact
  private def unitPlan(config: Config): String = {
    val plan = Build.plan(config.withClassPath(Nil).withMainClass(None))
    plan.inputs
      .filterNot { case (name, _) => Set("classpath", "artifact")(name) }
      .mkString("\n")
  }

  private def compileSources(
      config: Config,
      unit: BuildUnit,
      index: Int,
      dependencies: Map[Int, CompiledUnit],
      owners: Map[nir.Global.Top, Int],
      loader: ClassLoader,
      cache: BuildCache,
      dir: Path,
      archive: Path,
      native: Option[Native]
  )(implicit ec: ExecutionContext): UnitManifest = {
    // Shared libraries hold position independent code exporting its symbols
    val unitConfig =
      if (native.isEmpty) config
      else
        config.withCompilerConfig(
          _.withCompileOptions(_ ++ Seq("-fPIC", "-fvisibility=default"))
        )
    val owned: Seq[nir.Global.Top] = unit.entries.flatMap {
      case (_, library) =>
        library.classes.map(_.name).filter(owners.get(_).contains(index))
    }
    // Every definition of the unit, and what code generation needs
    val entries = owned.flatMap { cls =>
      cls +: loader.load(cls).toSeq.flatten.map(_.name)
    } ++ ScalaNative.entries(config.withMainClass(None))
    val analysis = config.logger.time(s"Reaching unit ${describe(unit)}") {
      Reach(config, entries, loader, allowUnavailable = true) match {
        case result: ReachabilityAnalysis.Result => result
        case _ => throw new BuildException("Unit analysis failed")
      }
    }
    val provided = dependencies.values.flatMap(_.manifest.exports).toSet
    val (analyzed, unanalyzed) = withoutUnanalyzed(analysis)
    val (meta, lowered, unlowered) = Await.result(
      CodeGen.lowerUnit(config, analyzed, provided),
      Duration.Inf
    )
    def isOwned(defn: nir.Defn) =
      owners.get(defn.name.top).contains(index)
    val (declarations, undeclared) =
      CodeGen.declareMissing(analysis, lowered.filter(isOwned), lowered)(
        meta,
        config.logger
      )
    val env = lowered ++ declarations :+ ExcludedMethodTrap
    val ignored = exclusionsOf(config, lowered.filter(isOwned))
    val failed = unanalyzed.map { case (n, r) => n -> s"unanalyzed, $r" } ++
      unlowered.map { case (n, r) => n -> s"cannot be lowered: $r" } ++
      ignored
    val Partition(kept, excluded, trapped) = partition(
      env,
      failed,
      analysis.unavailable ++ undeclared,
      provided,
      isOwned,
      owners
    )
    val selected = lowered.collect {
      case defn if kept.contains(defn.name) =>
        trapped.get(defn.name).fold(defn)(withTraps(defn, _))
    }

    // Objects of earlier compilations of the unit are reused through the
    // build cache, stale ones must not end up in the archive
    if (Files.isDirectory(dir)) IO.deleteRecursive(dir)
    Files.createDirectories(dir)
    val irFiles = CodeGen.emitUnit(
      config,
      meta,
      env,
      selected,
      dir.resolve("ir")
    )
    // Objects compiled from the same IR by an earlier version of the unit are
    // reused
    val compiled = irFiles.map(_.flatMap {
      case (ir, externs) =>
        val stored = LLVM.StoredCompile(
          cache,
          inputs = cache.digest(ir),
          root = config.workDir
        )
        LLVM
          .compile(
            unitConfig,
            analysis,
            ir,
            configChanged = false,
            Some(stored)
          )
          .map(_ -> externs)
    })
    val results = Await.result(Future.sequence(compiled), Duration.Inf)
    val objects = results.map(_._1)
    val externs = results.flatMap(_._2).toSet
    LLVM.archive(config, objects.sortBy(_.getFileName.toString), archive)
    native.foreach { native =>
      val nativeLibs = {
        val entries = unit.entries.map(_._1.toAbsolutePath()).toSet
        // With the build config: the native code directories follow from it
        NativeLib
          .findNativeLibs(config)
          .filter(lib => entries.contains(lib.src.toAbsolutePath()))
      }
      compileNatives(unitConfig, unit, analysis, cache, nativeLibs, native)
    }
    val exports = exported(lowered, kept)
    config.logger.debug(
      s"Unit ${describe(unit)}: ${exports.size} exported and " +
        s"${kept.size - exports.size} hidden definitions, " +
        s"${excluded.size} excluded"
    )
    UnitManifest(
      exports,
      kept -- exports,
      excluded,
      externs,
      analysis.links.map(_.name).toSet,
      analysis.linkCppRuntime
    )
  }

  /** Compiles the native code of the classpath entries of `unit`, with every
   *  definition of the unit reached, into the archive of `native`. The object
   *  registering the static sections of the library comes from that native code
   *  or from a dependency.
   */
  private def compileNatives(
      config: Config,
      unit: BuildUnit,
      analysis: ReachabilityAnalysis.Result,
      cache: BuildCache,
      nativeLibs: Seq[NativeLib],
      native: Native
  )(implicit ec: ExecutionContext): Unit = {
    val natives = Await.result(
      Future
        .traverse(nativeLibs)(
          NativeLib.compileNativeLibrary(config, analysis, _, Some(cache))
        )
        .map(_.flatten),
      Duration.Inf
    )
    val (own, others) =
      natives.partition(_.getFileName.toString.startsWith(SectionsSource))
    own.headOption.orElse(native.dependencySections) match {
      case Some(sections) => Files.copy(sections, native.sections)
      case None           =>
        throw new BuildException(
          s"No $SectionsSource for the library of ${describe(unit)}"
        )
    }
    LLVM.archive(config, others.sortBy(_.toString), native.archive)
  }

  private final val SectionsSource = "staticSections.c"

  /** Methods referring to symbols the analysis has no information about, such
   *  as members of missing classes, cannot be lowered. They are only declared,
   *  and so are methods calling them, and reported as unavailable.
   */
  private def withoutUnanalyzed(
      analysis: ReachabilityAnalysis.Result
  ): (ReachabilityAnalysis.Result, Map[nir.Global, String]) = {
    def isAnalyzed(name: nir.Global) = analysis.infos.get(name).exists {
      case _: linker.Unavailable => false
      case _                     => true
    }
    val references = analysis.defns.collect {
      case defn: nir.Defn.Define => defn.name -> nirReferences(defn)
    }.toMap
    val declared = mutable.LinkedHashMap.empty[nir.Global, String]
    var changed = true
    while (changed) {
      changed = false
      references.foreach {
        case (name, refs) =>
          if (!declared.contains(name))
            refs
              .find(r => declared.contains(r) || !isAnalyzed(r))
              .foreach { ref =>
                declared(name) = s"refers to ${ref.mangle}"
                changed = true
              }
      }
    }
    val defns = analysis.defns.map {
      case defn: nir.Defn.Define if declared.contains(defn.name) =>
        nir.Defn.Declare(defn.attrs, defn.name, defn.ty)(defn.pos)
      case defn => defn
    }
    (analysis.withDefns(defns), declared.toMap)
  }

  // Symbols that lowering looks up in the analysis
  private def nirReferences(defn: nir.Defn.Define): Set[nir.Global] = {
    val found = mutable.HashSet.empty[nir.Global]
    def refType(ty: nir.Type): Unit = ty match {
      case nir.Type.Ref(name, _, _) => found += name
      case _                        => ()
    }
    val traverse = new nir.Traverse {
      override def onOp(op: nir.Op): Unit = {
        op match {
          case nir.Op.Classalloc(name, _)       => found += name
          case nir.Op.Fieldload(_, _, name)     => found += name
          case nir.Op.Fieldstore(_, _, name, _) => found += name
          case nir.Op.Field(_, name)            => found += name
          case nir.Op.Module(name)              => found += name
          // Dispatch and type tests use the class of the receiver
          case nir.Op.Method(obj, _)    => refType(obj.ty)
          case nir.Op.Dynmethod(obj, _) => refType(obj.ty)
          case nir.Op.Is(ty, _)         => refType(ty)
          case nir.Op.As(ty, _)         => refType(ty)
          case _                        => ()
        }
        super.onOp(op)
      }
      override def onVal(value: nir.Val): Unit = {
        value match {
          case nir.Val.Global(name, _) if name != defn.name => found += name
          case _                                            => ()
        }
        super.onVal(value)
      }
    }
    traverse.onDefn(defn)
    found.toSet
  }

  private final case class Partition(
      kept: Set[nir.Global],
      excluded: Map[nir.Global, String],
      // Methods replaced by the trap in the metadata of a class, by metadata
      trapped: Map[nir.Global, Set[nir.Global]]
  )

  /** Called instead of excluded methods, see excludedMethod.c */
  private val ExcludedMethodTrap: nir.Defn.Declare = {
    implicit val pos: nir.SourcePosition = nir.SourcePosition.NoPosition
    nir.Defn.Declare(
      nir.Attrs.None.withIsExtern(true),
      nir.Global
        .Top("__")
        .member(nir.Sig.Extern("scalanative_excluded_method")),
      nir.Type.Function(Nil, nir.Type.Unit)
    )
  }

  /** Which definitions of the unit are compiled into it, and which are left
   *  out. `failed` definitions cannot be compiled, `unknown` symbols are
   *  missing or unsupported. A definition referring to an excluded or missing
   *  symbol, or to a dependency symbol that is not exported, is excluded too,
   *  except references of class metadata to methods: those call the trap.
   */
  private def partition(
      env: Seq[nir.Defn],
      failed: Map[nir.Global, String],
      unknown: Set[nir.Global],
      provided: Set[nir.Global],
      isOwned: nir.Defn => Boolean,
      owners: Map[nir.Global.Top, Int]
  ): Partition = {
    val byName = env.map(defn => defn.name -> defn).toMap
    // Declared C functions are provided by native code
    def isExtern(name: nir.Global) = byName.get(name).exists {
      case defn: nir.Defn.Declare =>
        defn.attrs.isExtern || defn.name.sig.isExtern
      case _ => false
    }
    val candidates = env.filter {
      case _: nir.Defn.Declare => false
      case defn                => isOwned(defn)
    }
    val references = candidates.map(d => d.name -> referencesOf(d)).toMap
    val excluded = mutable.LinkedHashMap.empty[nir.Global, String]
    failed.foreach {
      case (name, reason) =>
        if (byName.get(name).exists(isOwned)) excluded(name) = reason
    }
    def isAvailable(ref: nir.Global): Boolean =
      if (excluded.contains(ref) || unknown.contains(ref) ||
          unknown.contains(ref.top)) false
      else if (references.contains(ref) || provided.contains(ref)) true
      else if (isExtern(ref)) true
      // Symbols of no class are generated once per program
      else !owners.contains(ref.top)
    // Tables of the class, not generated code such as module accessors
    def isMetadata(name: nir.Global) = name match {
      case nir.Global.Member(_, sig) =>
        sig.unmangled.isInstanceOf[nir.Sig.Generated] &&
          byName.get(name).exists {
            case _: nir.Defn.Var | _: nir.Defn.Const => true
            case _                                   => false
          }
      case _ => false
    }
    def isMethod(name: nir.Global) = name match {
      case nir.Global.Member(_, sig) => sig.isMethod || sig.isCtor
      case _                         => false
    }
    var changed = true
    while (changed) {
      changed = false
      references.foreach {
        case (name, refs) if !excluded.contains(name) =>
          refs
            .find { ref =>
              !isAvailable(ref) && !(isMetadata(name) && isMethod(ref))
            }
            .foreach { ref =>
              val reason =
                excluded
                  .get(ref)
                  .fold(s"refers to ${ref.mangle}")(_ =>
                    s"refers to excluded ${ref.mangle}"
                  )
              excluded(name) = reason
              changed = true
            }
        case _ => ()
      }
    }
    val kept = references.keySet -- excluded.keySet
    val trapped = kept.iterator
      .filter(isMetadata)
      .map(name => name -> references(name).filter(r => !isAvailable(r)))
      .filter(_._2.nonEmpty)
      .toMap
    Partition(kept, excluded.toMap, trapped)
  }

  private def withTraps(defn: nir.Defn, methods: Set[nir.Global]): nir.Defn = {
    def rewrite(value: nir.Val): nir.Val = value match {
      case nir.Val.Global(name, _) if methods.contains(name) =>
        nir.Val.Global(ExcludedMethodTrap.name, nir.Type.Ptr)
      case nir.Val.StructValue(values) =>
        nir.Val.StructValue(values.map(rewrite))
      case nir.Val.ArrayValue(ty, values) =>
        nir.Val.ArrayValue(ty, values.map(rewrite))
      case nir.Val.Const(value) => nir.Val.Const(rewrite(value))
      case value                => value
    }
    defn match {
      case defn @ nir.Defn.Var(attrs, name, ty, rhs) =>
        nir.Defn.Var(attrs, name, ty, rewrite(rhs))(defn.pos)
      case defn @ nir.Defn.Const(attrs, name, ty, rhs) =>
        nir.Defn.Const(attrs, name, ty, rewrite(rhs))(defn.pos)
      case defn => defn
    }
  }

  /** Owned definitions matching `NativeConfig.libraryExclusions`. */
  private def exclusionsOf(
      config: Config,
      defns: Seq[nir.Defn]
  ): Map[nir.Global, String] = {
    val patterns = config.compilerConfig.libraryExclusions.map { pattern =>
      val (cls, member) = pattern.split("#", 2) match {
        case Array(cls, member) => (cls, Some(member))
        case Array(cls)         => (cls, None)
      }
      def glob(text: String) =
        text.split("\\*", -1).map(java.util.regex.Pattern.quote).mkString(".*")
      (pattern, glob(cls).r, member.map(glob(_).r))
    }
    if (patterns.isEmpty) Map.empty
    else
      defns.flatMap { defn =>
        val memberName = defn.name match {
          case nir.Global.Member(_, sig) => Some(memberNameOf(sig))
          case _                         => None
        }
        patterns.collectFirst {
          case (pattern, cls, member)
              if cls.pattern.matcher(defn.name.top.id).matches() &&
                member.forall(m =>
                  memberName.exists(m.pattern.matcher(_).matches())
                ) =>
            defn.name -> s"excluded by libraryExclusions: $pattern"
        }
      }.toMap
  }

  private def memberNameOf(sig: nir.Sig): String = sig.unmangled match {
    case nir.Sig.Method(id, _, _) => id
    case nir.Sig.Field(id, _)     => id
    case nir.Sig.Ctor(_)          => "<init>"
    case nir.Sig.Clinit           => "<clinit>"
    case nir.Sig.Proxy(id, _)     => id
    case nir.Sig.Extern(id)       => id
    case nir.Sig.Generated(id)    => id
    case nir.Sig.Duplicate(of, _) => memberNameOf(of)
  }

  /** Symbols other units and programs may refer to: public members, class
   *  metadata, and private members used by public `@alwaysinline` bodies, which
   *  granular builds inline across units.
   */
  private def exported(
      lowered: Seq[nir.Defn],
      kept: Set[nir.Global]
  ): Set[nir.Global] = {
    def isPrivate(name: nir.Global) = name match {
      case nir.Global.Member(_, sig) => sig.isPrivate
      case _                         => false
    }
    val byName = lowered.map(defn => defn.name -> defn).toMap
    val visible = mutable.HashSet.empty[nir.Global]
    kept.foreach(name => if (!isPrivate(name)) visible += name)
    def isInlined(name: nir.Global) = byName.get(name).exists {
      case defn: nir.Defn.Define =>
        defn.attrs.inlineHint == nir.Attr.AlwaysInline
      case _ => false
    }
    var todo = visible.filter(isInlined).toList
    while (todo.nonEmpty) {
      val name = todo.head
      todo = todo.tail
      byName.get(name).foreach { defn =>
        referencesOf(defn).foreach { ref =>
          if (kept.contains(ref) && visible.add(ref) && isInlined(ref))
            todo ::= ref
        }
      }
    }
    visible.toSet
  }

  private def referencesOf(defn: nir.Defn): Set[nir.Global] = {
    val found = mutable.HashSet.empty[nir.Global]
    val traverse = new nir.Traverse {
      override def onVal(value: nir.Val): Unit = {
        value match {
          case nir.Val.Global(name, _) if name != defn.name => found += name
          case _                                            => ()
        }
        super.onVal(value)
      }
    }
    traverse.onDefn(defn)
    found.toSet
  }

  private def describe(unit: BuildUnit): String =
    unit.entries.map(_._1.getFileName.toString).mkString(", ")
}
