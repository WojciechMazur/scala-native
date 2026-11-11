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
      val index: LibraryIndex,
      locations: Map[nir.Global.Top, (Long, Int)],
      origin: Path
  ) extends AutoCloseable {
    val classes: Map[nir.Global.Top, ClassIndex] =
      index.classes.map(c => c.name -> c).toMap

    def contains(name: nir.Global.Top): Boolean = classes.contains(name)

    def load(name: nir.Global.Top): Option[Seq[nir.Defn]] =
      classes.get(name).map { cls =>
        val (position, size) = locations(name)
        val buffer = read(channel, position, size)
        new BinaryDeserializer(
          buffer,
          nir.NIRSource(origin, Paths.get(cls.path))
        ).deserialize()
      }

    def close(): Unit = channel.close()
  }

  object Reader {

    /** Opens the library at `file`, `None` if it has another format or NIR
     *  version.
     */
    def open(file: Path, origin: Path): Option[Reader] = {
      val channel = FileChannel.open(file, StandardOpenOption.READ)
      try {
        val header = read(channel, 0, HeaderSize)
        val compatible =
          header.getInt() == Magic && header.getInt() == FormatVersion &&
            header.getInt() == nir.Versions.compat &&
            header.getInt() == nir.Versions.revision
        if (!compatible) {
          channel.close()
          None
        } else {
          val indexSize = header.getInt()
          val bodies = HeaderSize.toLong + indexSize
          val (index, locations) =
            readIndex(read(channel, HeaderSize, indexSize), file)
          val absolute = locations.map {
            case (name, (offset, size)) => name -> (bodies + offset, size)
          }
          Some(new Reader(file, channel, index, absolute, origin))
        }
      } catch {
        case ex: Throwable =>
          channel.close()
          throw ex
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
