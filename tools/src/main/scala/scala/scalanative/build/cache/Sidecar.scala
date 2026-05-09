package scala.scalanative
package build
package cache

import java.nio.{ByteBuffer, ByteOrder}
import java.nio.channels.FileChannel
import java.nio.file.{Files, Path, StandardOpenOption}

/** Binary sidecar format for cached native libraries (version 1).
  *
  * File layout (little-endian):
  *   - 4 bytes magic `SNS1` (0x534e5331)
  *   - 4 bytes version = 1
  *   - 4 bytes count of exported symbol records
  *   - For each export: 4-byte UTF-8 byte length + bytes (global mangle), same for C symbol name
  *   - 4 bytes count of init descriptor entries (pairs of UTF-8 strings: fromModule -> toModule)
  *   - For each pair: two length-prefixed UTF-8 strings
  *   - 4 bytes count of vtable slot records (PoC: may be zero)
  *   - For each vtable record: UTF-8 owner top id, 4-byte slot index, 4-byte sig name length + sig bytes
  *   - 4 bytes count of ancestor id lists (PoC: may be zero)
  *   - For each ancestor list: UTF-8 class top id, 4-byte n, then n * 4-byte type ids
  *   - (optional tail, forward-compatible) 4 bytes count of META-INF/services provider class names
  *   - For each service: length-prefixed UTF-8
  *   - 4 bytes count of linktime property keys consumed in this library
  *   - For each key: length-prefixed UTF-8
  */
private[scalanative] object Sidecar {

  final val Magic: Int = 0x534e5331 // 'SNS1'

  final case class ExportedSymbol(globalMangle: String, cSymbolName: String)
  final case class InitEdge(fromModuleTop: String, toModuleTop: String)
  final case class VtableSlot(ownerTop: String, slot: Int, sigMangle: String)
  final case class AncestorList(classTop: String, typeIds: Seq[Int])

  final case class Data(
      exports: Seq[ExportedSymbol],
      initEdges: Seq[InitEdge],
      vtableSlots: Seq[VtableSlot],
      ancestorLists: Seq[AncestorList],
      services: Seq[String] = Nil,
      linktimeKeys: Seq[String] = Nil
  )

  def empty: Data = Data(Nil, Nil, Nil, Nil, Nil, Nil)

  def write(path: Path, data: Data): Unit = {
    val buf = ByteBuffer.allocate(estimateSize(data)).order(ByteOrder.LITTLE_ENDIAN)
    buf.putInt(Magic)
    buf.putInt(1)
    buf.putInt(data.exports.length)
    data.exports.foreach { e =>
      putUtf8(buf, e.globalMangle)
      putUtf8(buf, e.cSymbolName)
    }
    putInitEdges(buf, data.initEdges)
    putVtableSlots(buf, data.vtableSlots)
    putAncestorLists(buf, data.ancestorLists)
    putStringList(buf, data.services)
    putStringList(buf, data.linktimeKeys)
    buf.flip()
    Files.createDirectories(path.getParent)
    val tmp = path.resolveSibling(path.getFileName.toString + ".tmp")
    Files.write(tmp, buf.array(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
  }

  def read(path: Path): Option[Data] =
    if (Files.notExists(path)) None
    else {
      val chan = FileChannel.open(path, StandardOpenOption.READ)
      try {
        val size = chan.size().toInt
        if (size < 12) None
        else {
          val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
          chan.read(buf)
          buf.flip()
          if (buf.getInt != Magic) None
          else if (buf.getInt != 1) None
          else {
            val exports = {
              val n = buf.getInt
              (0 until n).map(_ => ExportedSymbol(getUtf8(buf), getUtf8(buf))).toSeq
            }
            val initEdges = getInitEdges(buf)
            val vtableSlots = getVtableSlots(buf)
            val ancestorLists = getAncestorLists(buf)
            val services =
              if (buf.hasRemaining) getStringList(buf) else Nil
            val linktimeKeys =
              if (buf.hasRemaining) getStringList(buf) else Nil
            Some(
              Data(
                exports,
                initEdges,
                vtableSlots,
                ancestorLists,
                services,
                linktimeKeys
              )
            )
          }
        }
      } finally chan.close()
    }

  private def estimateSize(d: Data): Int = {
    var n = 12
    n += d.exports.map(e => 8 + e.globalMangle.length + e.cSymbolName.length).sum
    n += 4 + d.initEdges.map(e => 8 + e.fromModuleTop.length + e.toModuleTop.length).sum
    n += 4 + d.vtableSlots.map(v => 8 + v.ownerTop.length + 4 + 4 + v.sigMangle.length).sum
    n += 4 + d.ancestorLists.map(a => 8 + a.classTop.length + 4 + 4 * a.typeIds.length).sum
    n += 4 + d.services.map(s => 4 + s.length).sum
    n += 4 + d.linktimeKeys.map(s => 4 + s.length).sum
    n + 1024
  }

  private def putStringList(buf: ByteBuffer, strings: Seq[String]): Unit = {
    buf.putInt(strings.length)
    strings.foreach(putUtf8(buf, _))
  }

  private def getStringList(buf: ByteBuffer): Seq[String] = {
    val n = buf.getInt
    (0 until n).map(_ => getUtf8(buf))
  }

  private def putInitEdges(buf: ByteBuffer, edges: Seq[InitEdge]): Unit = {
    buf.putInt(edges.length)
    edges.foreach { e =>
      putUtf8(buf, e.fromModuleTop)
      putUtf8(buf, e.toModuleTop)
    }
  }

  private def putVtableSlots(buf: ByteBuffer, slots: Seq[VtableSlot]): Unit = {
    buf.putInt(slots.length)
    slots.foreach { v =>
      putUtf8(buf, v.ownerTop)
      buf.putInt(v.slot)
      putUtf8(buf, v.sigMangle)
    }
  }

  private def putAncestorLists(buf: ByteBuffer, lists: Seq[AncestorList]): Unit = {
    buf.putInt(lists.length)
    lists.foreach { a =>
      putUtf8(buf, a.classTop)
      buf.putInt(a.typeIds.length)
      a.typeIds.foreach(buf.putInt)
    }
  }

  private def putUtf8(buf: ByteBuffer, s: String): Unit = {
    val bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8)
    buf.putInt(bytes.length)
    buf.put(bytes)
  }

  private def getUtf8(buf: ByteBuffer): String = {
    val len = buf.getInt
    val arr = new Array[Byte](len)
    buf.get(arr)
    new String(arr, java.nio.charset.StandardCharsets.UTF_8)
  }

  private def getInitEdges(buf: ByteBuffer): Seq[InitEdge] = {
    val n = buf.getInt
    (0 until n).map(_ => InitEdge(getUtf8(buf), getUtf8(buf)))
  }

  private def getVtableSlots(buf: ByteBuffer): Seq[VtableSlot] = {
    val n = buf.getInt
    (0 until n).map { _ =>
      val owner = getUtf8(buf)
      val slot = buf.getInt
      val sig = getUtf8(buf)
      VtableSlot(owner, slot, sig)
    }
  }

  private def getAncestorLists(buf: ByteBuffer): Seq[AncestorList] = {
    val n = buf.getInt
    (0 until n).map { _ =>
      val top = getUtf8(buf)
      val count = buf.getInt
      val ids = (0 until count).map(_ => buf.getInt).toSeq
      AncestorList(top, ids)
    }
  }
}
