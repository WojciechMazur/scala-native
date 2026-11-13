package scala.scalanative
package build
package units

import java.io.{
  ByteArrayInputStream, ByteArrayOutputStream, DataInputStream,
  DataOutputStream, IOException
}
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.{Files, Path, Paths, StandardOpenOption}

import scala.scalanative.build.store.Digest
import scala.scalanative.nir.serialization.BinaryDeserializer

/** Index of a class file in a library: everything known about the class without
 *  reading its definitions.
 *
 *  @param name
 *    the class, trait or module the file defines
 *  @param path
 *    the NIR file in the original classpath entry, as the linker names it
 *  @param digest
 *    digest of the NIR file
 *  @param abiDigest
 *    digest of what other classes can depend on, see `DeriveLibrary.abi`
 *  @param references
 *    other top-level classes, traits and modules the definitions refer to
 */
private[scalanative] final case class ClassIndex(
    name: nir.Global.Top,
    path: String,
    hasEntryPoints: Boolean,
    digest: Digest,
    abiDigest: Digest,
    references: Seq[nir.Global.Top]
)

/** The index of a library: one classpath entry in a form that is fast to load
 *  and carries the facts needed to partition the classpath and to tell which
 *  changes reach dependants.
 *
 *  @param digest
 *    digest of the classpath entry the library was derived from
 *  @param abiDigest
 *    digest of the ABI of every class: it changes only if a dependant may need
 *    to be compiled again
 */
private[scalanative] final case class LibraryIndex(
    digest: Digest,
    abiDigest: Digest,
    classes: Seq[ClassIndex],
    services: Map[nir.Global.Top, Seq[nir.Global.Top]]
)

/** A `.snl` library file, the derived form of a classpath entry.
 *
 *  {{{
 *  magic, format version, NIR compat version and revision: 4 ints
 *  index size: int
 *  index:  entry digest, ABI digest,
 *          classes: name, path, entry points flag, digest, ABI digest,
 *                   references, offset and size in the bodies section
 *          services: name, providers
 *  bodies: the NIR files, unchanged
 *  }}}
 *
 *  Bodies keep the NIR format, so classes load lazily with the regular
 *  deserializer, attributed to the original classpath entry.
 */
private[scalanative] object Library {
  final val Magic = 0x534e4c00 // "SNL\0"
  final val FormatVersion = 1
  private final val HeaderSize = 5 * 4

  final case class ClassFile(index: ClassIndex, bytes: Array[Byte])

  def write(
      target: Path,
      digest: Digest,
      abiDigest: Digest,
      classes: Seq[ClassFile],
      services: Map[nir.Global.Top, Seq[nir.Global.Top]]
  ): Unit = {
    val indexBytes = new ByteArrayOutputStream()
    val index = new DataOutputStream(indexBytes)
    def writeDigest(digest: Digest): Unit = index.writeUTF(digest.toString)
    writeDigest(digest)
    writeDigest(abiDigest)
    index.writeInt(classes.size)
    var offset = 0L
    classes.foreach {
      case ClassFile(cls, bytes) =>
        index.writeUTF(cls.name.id)
        index.writeUTF(cls.path)
        index.writeBoolean(cls.hasEntryPoints)
        writeDigest(cls.digest)
        writeDigest(cls.abiDigest)
        index.writeInt(cls.references.size)
        cls.references.foreach(ref => index.writeUTF(ref.id))
        index.writeLong(offset)
        index.writeInt(bytes.length)
        offset += bytes.length
    }
    val sortedServices = services.toSeq.sortBy(_._1.id)
    index.writeInt(sortedServices.size)
    sortedServices.foreach {
      case (service, providers) =>
        index.writeUTF(service.id)
        index.writeInt(providers.size)
        providers.foreach(p => index.writeUTF(p.id))
    }
    index.close()

    Files.createDirectories(target.toAbsolutePath().getParent())
    val out = new DataOutputStream(
      new java.io.BufferedOutputStream(Files.newOutputStream(target))
    )
    try {
      out.writeInt(Magic)
      out.writeInt(FormatVersion)
      out.writeInt(nir.Versions.compat)
      out.writeInt(nir.Versions.revision)
      out.writeInt(indexBytes.size())
      indexBytes.writeTo(out)
      classes.foreach(file => out.write(file.bytes))
    } finally out.close()
  }

  /** An open library, loading class definitions on demand.
   *
   *  @param origin
   *    the classpath entry the library was derived from, which loaded
   *    definitions are attributed to
   */
  final class Reader private (
      file: Path,
      channel: FileChannel,
      contents: Contents,
      origin: Path
  ) extends AutoCloseable {
    def index: LibraryIndex = contents.index
    def classes: Map[nir.Global.Top, ClassIndex] = contents.classes

    def contains(name: nir.Global.Top): Boolean = classes.contains(name)

    def load(name: nir.Global.Top): Option[Seq[nir.Defn]] =
      classes.get(name).map { cls =>
        val (position, size) = contents.locations(name)
        val key = (index.digest.hash, origin.toString, name.id)
        Definitions.get(key).getOrElse {
          val buffer = read(channel, position, size)
          val defns = new BinaryDeserializer(
            buffer,
            nir.NIRSource(origin, Paths.get(cls.path))
          ).deserialize()
          Definitions.put(key, defns, size)
          defns
        }
      }

    def close(): Unit = channel.close()
  }

  /** The index of a library and where its classes are. */
  private final class Contents(
      val index: LibraryIndex,
      val locations: Map[nir.Global.Top, (Long, Int)]
  ) {
    val classes: Map[nir.Global.Top, ClassIndex] =
      index.classes.map(c => c.name -> c).toMap
  }

  // Libraries are named after the digest of what they were derived from, and
  // every build reaching the same classes of the same library decodes them
  // alike: builds in this JVM share the indexes and the decoded definitions
  private val contentsOf =
    new java.util.concurrent.ConcurrentHashMap[(String, Long), Contents]()
  private final val MaxContents = 64

  /** Definitions recently decoded, least recently used first, bounded by the
   *  size of their serialized form.
   */
  private object Definitions {
    private type Key = (String, String, String)
    private val budget: Long = sys.props
      .get("scala.scalanative.build.nirMemoBytes")
      .flatMap(v => scala.util.Try(v.toLong).toOption)
      .getOrElse(16L * 1024 * 1024)
    private val entries =
      new java.util.LinkedHashMap[Key, (Seq[nir.Defn], Int)](256, 0.75f, true)
    private var bytes = 0L

    def get(key: Key): Option[Seq[nir.Defn]] = synchronized {
      Option(entries.get(key)).map(_._1)
    }

    def put(key: Key, defns: Seq[nir.Defn], size: Int): Unit =
      if (size <= budget / 4) synchronized {
        val previous = entries.put(key, (defns, size))
        if (previous != null) bytes -= previous._2
        bytes += size
        val it = entries.values().iterator()
        while (bytes > budget && it.hasNext()) {
          bytes -= it.next()._2
          it.remove()
        }
      }
  }

  object Reader {

    /** Opens the library at `file`, `None` if it has another format or NIR
     *  version.
     */
    def open(file: Path, origin: Path): Option[Reader] = {
      val channel = FileChannel.open(file, StandardOpenOption.READ)
      try {
        val key = (file.getFileName().toString, channel.size())
        Option(contentsOf.get(key))
          .orElse(readContents(channel, file).map { contents =>
            if (contentsOf.size() >= MaxContents) contentsOf.clear()
            contentsOf.put(key, contents)
            contents
          }) match {
          case None =>
            channel.close()
            None
          case Some(contents) =>
            Some(new Reader(file, channel, contents, origin))
        }
      } catch {
        case ex: Throwable =>
          channel.close()
          throw ex
      }
    }

    private def readContents(
        channel: FileChannel,
        file: Path
    ): Option[Contents] = {
      val header = read(channel, 0, HeaderSize)
      val compatible =
        header.getInt() == Magic && header.getInt() == FormatVersion &&
          header.getInt() == nir.Versions.compat &&
          header.getInt() == nir.Versions.revision
      if (!compatible) None
      else {
        val indexSize = header.getInt()
        val bodies = HeaderSize.toLong + indexSize
        val (index, locations) =
          readIndex(read(channel, HeaderSize, indexSize), file)
        val absolute = locations.map {
          case (name, (offset, size)) => name -> (bodies + offset, size)
        }
        Some(new Contents(index, absolute))
      }
    }
  }

  private def readIndex(
      buffer: ByteBuffer,
      file: Path
  ): (LibraryIndex, Map[nir.Global.Top, (Long, Int)]) = {
    val in = new DataInputStream(
      new ByteArrayInputStream(buffer.array(), 0, buffer.limit())
    )
    def readDigest(): Digest =
      Digest
        .parse(in.readUTF())
        .getOrElse(throw new IOException(s"Malformed digest in $file"))
    val digest = readDigest()
    val abiDigest = readDigest()
    val classes = Seq.fill(in.readInt()) {
      val cls = ClassIndex(
        name = nir.Global.Top(in.readUTF()),
        path = in.readUTF(),
        hasEntryPoints = in.readBoolean(),
        digest = readDigest(),
        abiDigest = readDigest(),
        references = Seq.fill(in.readInt())(nir.Global.Top(in.readUTF()))
      )
      val offset = in.readLong()
      val size = in.readInt()
      cls -> (offset, size)
    }
    val services = Seq
      .fill(in.readInt()) {
        val service = nir.Global.Top(in.readUTF())
        service -> Seq.fill(in.readInt())(nir.Global.Top(in.readUTF()))
      }
      .toMap
    val index = LibraryIndex(digest, abiDigest, classes.map(_._1), services)
    val locations = classes.map { case (cls, location) => cls.name -> location }
    (index, locations.toMap)
  }

  // Positional reads leave the channel position alone, so concurrent loads
  // need no locking
  private def read(channel: FileChannel, position: Long, size: Int) = {
    val buffer = ByteBuffer.allocate(size)
    var at = position
    while (buffer.hasRemaining()) {
      val read = channel.read(buffer, at)
      if (read < 0) throw new IOException("Truncated library file")
      at += read
    }
    buffer.flip()
    buffer
  }
}
