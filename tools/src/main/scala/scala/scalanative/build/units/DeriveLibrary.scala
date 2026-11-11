package scala.scalanative
package build
package units

import java.io.{BufferedReader, ByteArrayInputStream, InputStreamReader}
import java.nio.ByteBuffer
import java.nio.file.{Files, Path, Paths}
import java.util.zip.ZipFile

import scala.collection.mutable

import scala.scalanative.build.store.Digest
import scala.scalanative.io.VirtualDirectory
import scala.scalanative.nir.serialization.{BinaryDeserializer, Prelude}
import scala.scalanative.util.Scope

/** Derives the `.snl` library of a classpath entry, see `Library`. */
private[scalanative] object DeriveLibrary {

  /** Reads every NIR file and service declaration of the classpath `entry`,
   *  whose content has `digest`, and writes its library to `target`.
   */
  def apply(entry: Path, digest: Digest, target: Path, logger: Logger)(implicit
      scope: Scope
  ): LibraryIndex = {
    val files = filesOf(entry).sortBy(_._1.toString)
    val contents = files.toMap
    def read(path: Path): Array[Byte] = contents(path)()
    val classes =
      files.map(_._1).filter(_.toString.endsWith(".nir")).map { path =>
        val bytes = read(path)
        val source = nir.NIRSource(entry, path)
        val name = nir.Global.Top(io.packageNameFromPath(path))
        Prelude.tryReadFrom(ByteBuffer.wrap(bytes), source.debugName) match {
          case Right(prelude) =>
            val defns = new BinaryDeserializer(ByteBuffer.wrap(bytes), source)
              .deserialize()
            val index = ClassIndex(
              name = name,
              path = path.toString,
              hasEntryPoints = prelude.hasEntryPoints,
              digest = Digest.of(bytes),
              abiDigest = abi(defns),
              references = references(name, defns)
            )
            Library.ClassFile(index, bytes)
          // Kept as is: the linker reports it if the class is ever loaded
          case Left(error) =>
            logger.warn(
              s"Can't check if file contains entrypoints, ${path} would be ignored: ${error.getMessage()}"
            )
            val fileDigest = Digest.of(bytes)
            val index = ClassIndex(
              name = name,
              path = path.toString,
              hasEntryPoints = false,
              digest = fileDigest,
              abiDigest = fileDigest,
              references = Nil
            )
            Library.ClassFile(index, bytes)
        }
      }
    val services = files.collect {
      case (path, bytes) if isServiceDeclaration(path) =>
        nir.Global.Top(path.getFileName().toString) ->
          serviceProviders(bytes())
    }.toMap
    val abiDigest = Digest.of(
      classes
        .map(c => s"${c.index.name.id}\t${c.index.abiDigest}\n")
        .sorted
        .mkString
    )
    Library.write(target, digest, abiDigest, classes, services)
    LibraryIndex(digest, abiDigest, classes.map(_.index), services)
  }

  /** Files of the classpath `entry`, named as the linker names them, with their
   *  content. Jars are read with a private `ZipFile`: zip file systems are
   *  shared by the whole JVM and may be closed by their other users.
   */
  private def filesOf(entry: Path)(implicit
      scope: Scope
  ): Seq[(Path, () => Array[Byte])] =
    if (Files.isDirectory(entry)) {
      val directory = VirtualDirectory.real(entry)
      directory.files.map(path => path -> (() => toArray(directory.read(path))))
    } else {
      val zip = new ZipFile(entry.toFile())
      scope.acquire(zip)
      val files = Seq.newBuilder[(Path, () => Array[Byte])]
      val entries = zip.entries()
      while (entries.hasMoreElements()) {
        val zipEntry = entries.nextElement()
        if (!zipEntry.isDirectory()) {
          // As paths of the jar's file system, rooted at /
          val path = Paths.get("/" + zipEntry.getName())
          files += path -> { () =>
            val in = zip.getInputStream(zipEntry)
            try {
              val out = new java.io.ByteArrayOutputStream()
              val buffer = new Array[Byte](64 * 1024)
              var read = in.read(buffer)
              while (read != -1) {
                out.write(buffer, 0, read)
                read = in.read(buffer)
              }
              out.toByteArray()
            } finally in.close()
          }
        }
      }
      files.result()
    }

  /** Whether `path` declares service providers. The first form is used in jars,
   *  the second one in directories.
   */
  def isServiceDeclaration(path: Path): Boolean = {
    val name = path.toString.replace('\\', '/')
    name.startsWith("/META-INF/services/") ||
      name.startsWith("META-INF/services/")
  }

  private def serviceProviders(bytes: Array[Byte]): Seq[nir.Global.Top] = {
    val b = Seq.newBuilder[nir.Global.Top]
    val reader = new BufferedReader(
      new InputStreamReader(new ByteArrayInputStream(bytes))
    )
    try
      reader
        .lines()
        .map[String](_.trim())
        .filter(_.nonEmpty)
        .forEach(b += nir.Global.Top(_))
    finally reader.close()
    b.result()
  }

  /** The digest of what code outside a class may depend on: its declarations
   *  with their attributes, the source order of its fields, which decides their
   *  layout, and the bodies of methods inlined across libraries. Body edits of
   *  other methods and moved lines leave it unchanged.
   */
  def abi(defns: Seq[nir.Defn]): Digest = {
    // Same order as the granular field layout, see codegen.FieldLayout
    val fieldRanks = defns
      .collect { case field: nir.Defn.Var => field }
      .zipWithIndex
      .sortBy {
        case (field, index) =>
          val pos = field.pos
          if (pos.isEmpty) (1, 0, 0, index)
          else (0, pos.line, pos.column, index)
      }
      .map(_._1.name)
      .zipWithIndex
      .toMap
    val shown = defns.map {
      case defn: nir.Defn.Define
          if defn.attrs.inlineHint == nir.Attr.AlwaysInline =>
        defn.show
      case defn: nir.Defn.Define =>
        nir.Defn.Declare(defn.attrs, defn.name, defn.ty)(defn.pos).show
      case field: nir.Defn.Var =>
        s"field ${fieldRanks(field.name)} ${field.show}"
      case defn => defn.show
    }
    Digest.of(shown.sorted.mkString("\n"))
  }

  /** Top-level classes, traits and modules referred to by `defns`, apart from
   *  `owner`, sorted by name.
   */
  def references(
      owner: nir.Global.Top,
      defns: Seq[nir.Defn]
  ): Seq[nir.Global.Top] = {
    val found = mutable.HashSet.empty[nir.Global.Top]
    def add(global: nir.Global): Unit = global match {
      case nir.Global.None => ()
      case global          =>
        val top = global.top
        if (top != owner) found += top
    }
    val traverse = new nir.Traverse {
      override def onDefn(defn: nir.Defn): Unit = {
        defn match {
          case nir.Defn.Class(_, _, parent, traits) =>
            parent.foreach(add)
            traits.foreach(add)
          case nir.Defn.Module(_, _, parent, traits) =>
            parent.foreach(add)
            traits.foreach(add)
          case nir.Defn.Trait(_, _, traits)    => traits.foreach(add)
          case nir.Defn.Define(_, _, ty, _, _) => onType(ty)
          case _                               => ()
        }
        super.onDefn(defn)
      }
      override def onOp(op: nir.Op): Unit = {
        op match {
          case nir.Op.Classalloc(name, _)       => add(name)
          case nir.Op.Fieldload(_, _, name)     => add(name)
          case nir.Op.Fieldstore(_, _, name, _) => add(name)
          case nir.Op.Field(_, name)            => add(name)
          case nir.Op.Module(name)              => add(name)
          case _                                => ()
        }
        super.onOp(op)
      }
      override def onVal(value: nir.Val): Unit = {
        value match {
          case nir.Val.Global(name, _) => add(name)
          case _                       => ()
        }
        super.onVal(value)
      }
      override def onType(ty: nir.Type): Unit = {
        ty match {
          case nir.Type.Ref(name, _, _) => add(name)
          case _                        => ()
        }
        super.onType(ty)
      }
    }
    traverse.onDefns(defns)
    found.toSeq.sortBy(_.id)
  }

  private def toArray(buffer: ByteBuffer): Array[Byte] =
    if (buffer.hasArray() && buffer.arrayOffset() == 0 &&
        buffer.remaining() == buffer.array().length) buffer.array()
    else {
      val bytes = new Array[Byte](buffer.remaining())
      buffer.duplicate().get(bytes)
      bytes
    }
}
