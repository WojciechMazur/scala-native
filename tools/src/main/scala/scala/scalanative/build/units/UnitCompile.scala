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

/** A symbol a unit refers to without defining it, and that no dependency
 *  exports: a definition left to programs, with its lowered type.
 */
private[scalanative] final case class UnitImport(
    isFunction: Boolean,
    ty: nir.Type
)

/** What a compiled library unit provides to programs and dependent units, the
 *  symbols its archive defines, and what it needs from programs.
 */
private[scalanative] final case class UnitManifest(
    exports: Set[nir.Global],
    imports: Map[nir.Global, UnitImport],
    externs: Set[String]
) {
  lazy val digest: Digest = Digest.of(serialize)

  def serialize: String = {
    val exported = exports.toSeq.map(name => s"export\t${name.mangle}")
    val imported = imports.toSeq.map {
      case (name, UnitImport(isFunction, ty)) =>
        val kind = if (isFunction) "function" else "variable"
        s"import\t${name.mangle}\t$kind\t${ty.mangle}"
    }
    val external = externs.toSeq.map(symbol => s"extern\t$symbol")
    (exported.sorted ++ imported.sorted ++ external.sorted)
      .mkString("", "\n", "\n")
  }
}

private[scalanative] object UnitManifest {
  def parse(text: String): UnitManifest = {
    val exports = Set.newBuilder[nir.Global]
    val imports = Map.newBuilder[nir.Global, UnitImport]
    val externs = Set.newBuilder[String]
    text.split('\n').foreach { line =>
      line.split('\t') match {
        case Array("export", name) =>
          exports += nir.Unmangle.unmangleGlobal(name)
        case Array("import", name, kind, ty) =>
          imports += nir.Unmangle.unmangleGlobal(name) ->
            UnitImport(kind == "function", nir.Unmangle.unmangleType(ty))
        case Array("extern", symbol) => externs += symbol
        case _                       => ()
      }
    }
    UnitManifest(exports.result(), imports.result(), externs.result())
  }
}

/** A compiled library unit: an archive with one object per source file. */
private[scalanative] final case class CompiledUnit(
    unit: BuildUnit,
    archive: Path,
    manifest: UnitManifest
)

/** Compiles every definition of a library unit, independently of the programs
 *  that will link it.
 *
 *  All definitions of the unit are reached, with whatever they need from its
 *  dependencies. Definitions that cannot be compiled without facts about a
 *  program, such as methods using classes missing from the classpath, are
 *  deferred: a program reaching one compiles it itself. Symbols the unit uses
 *  without defining them, and that no dependency exports, are imports: a
 *  program that does not define one provides a stub, which reachability
 *  guarantees is never called. Programs link the archive and pull only the
 *  objects they refer to.
 */
private[scalanative] object UnitCompile {
  final val FormatVersion = 11

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
    val dir = config.workDir.resolve("units").resolve(unit.digest.hash.take(16))
    val archive = dir.resolve("unit.a")
    val manifestPath = dir.resolve("unit.exports")
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
            .add("dependency-exports", dep.manifest.digest)
      }
      .result()
    cache.run(key, Seq("archive" -> archive, "manifest" -> manifestPath)) {
      val manifest = compileSources(
        config,
        unit,
        index,
        dependencies,
        owners,
        loader,
        cache,
        dir,
        archive
      )
      IO.write(manifestPath, manifest.serialize)
    }
    val manifest = UnitManifest.parse(
      new String(Files.readAllBytes(manifestPath), StandardCharsets.UTF_8)
    )
    CompiledUnit(unit, archive, manifest)
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
      archive: Path
  )(implicit ec: ExecutionContext): UnitManifest = {
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
    val env = lowered ++ declarations
    val (kept, imports, deferred) = keep(
      env,
      analysis.unavailable ++ undeclared,
      provided,
      isOwned,
      owners
    )
    val selected = lowered.filter(defn => kept.contains(defn.name))

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
          .compile(config, analysis, ir, configChanged = false, Some(stored))
          .map(_ -> externs)
    })
    val results = Await.result(Future.sequence(compiled), Duration.Inf)
    val objects = results.map(_._1)
    val externs = results.flatMap(_._2).toSet
    LLVM.archive(config, objects.sortBy(_.getFileName.toString), archive)
    IO.write(
      dir.resolve("deferred.txt"),
      deferred
        .map { case (name, ref) => s"${name.mangle} <- ${ref.mangle}" }
        .mkString("\n")
    )
    config.logger.debug(
      s"Unit ${describe(unit)}: ${kept.size} definitions, " +
        s"${lowered.count(isOwned) - kept.size} deferred"
    )
    UnitManifest(kept, imports, externs)
  }

  /** Methods referring to symbols the analysis has no information about, such
   *  as members of missing classes, cannot be lowered. They are only declared,
   *  and so are methods calling them, and reported as unavailable.
   */
  private def withoutUnanalyzed(
      analysis: ReachabilityAnalysis.Result
  ): (ReachabilityAnalysis.Result, Set[nir.Global]) = {
    def isAnalyzed(name: nir.Global) = analysis.infos.get(name).exists {
      case _: linker.Unavailable => false
      case _                     => true
    }
    val references = analysis.defns.collect {
      case defn: nir.Defn.Define => defn.name -> nirReferences(defn)
    }.toMap
    val declared = mutable.HashSet.empty[nir.Global]
    var changed = true
    while (changed) {
      changed = false
      references.foreach {
        case (name, refs) =>
          if (!declared.contains(name) &&
              refs.exists(r => declared.contains(r) || !isAnalyzed(r))) {
            declared += name
            changed = true
          }
      }
    }
    val defns = analysis.defns.map {
      case defn: nir.Defn.Define if declared.contains(defn.name) =>
        nir.Defn.Declare(defn.attrs, defn.name, defn.ty)(defn.pos)
      case defn => defn
    }
    (analysis.withDefns(defns), declared.toSet)
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

  /** Definitions of the unit that can be compiled into it, and the symbols they
   *  import. A definition referring to a symbol of unknown type, one the
   *  analysis never found, is deferred.
   */
  private def keep(
      env: Seq[nir.Defn],
      unknown: Set[nir.Global],
      provided: Set[nir.Global],
      isOwned: nir.Defn => Boolean,
      owners: Map[nir.Global.Top, Int]
  ): (
      Set[nir.Global],
      Map[nir.Global, UnitImport],
      Seq[(nir.Global, nir.Global)]
  ) = {
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
    val reasons = mutable.LinkedHashMap.empty[nir.Global, nir.Global]
    references.foreach {
      case (name, refs) =>
        refs
          .find(ref => unknown.contains(ref) || !byName.contains(ref))
          .foreach(reasons(name) = _)
    }
    // Class list entries are always linked: one whose class information is
    // left to programs would list a stub, see Generate.classListEntry
    val defined = references.keySet -- reasons.keySet
    references.foreach {
      case (name @ nir.Global.Member(_, sig), refs)
          if sig.unmangled == ClassListSig =>
        refs
          .find(ref => !defined.contains(ref) && !provided.contains(ref))
          .foreach(reasons(name) = _)
      case _ => ()
    }
    val kept = references.keySet -- reasons.keySet
    val imports = kept.iterator
      .flatMap(references)
      .filter { ref =>
        !kept.contains(ref) && !provided.contains(ref) && !isExtern(ref) &&
        // Symbols of no class are generated once per program
        owners.contains(ref.top)
      }
      .map { ref =>
        ref -> (byName(ref) match {
          case defn: nir.Defn.Define  => UnitImport(isFunction = true, defn.ty)
          case defn: nir.Defn.Declare => UnitImport(isFunction = true, defn.ty)
          case defn: nir.Defn.Var     => UnitImport(isFunction = false, defn.ty)
          case defn: nir.Defn.Const   => UnitImport(isFunction = false, defn.ty)
          case defn => throw new IllegalStateException(s"Cannot import $defn")
        })
      }
      .toMap
    (kept.toSet, imports, reasons.toSeq)
  }

  private val ClassListSig = nir.Sig.Generated("classlist")

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
