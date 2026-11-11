package scala.scalanative
package build
package store

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest

/** Content digest of a blob: its SHA-256 hash and its size.
 *
 *  Same shape as the remote execution API digest: the size is known for free
 *  when hashing and detects truncated or mismatched blobs before rehashing.
 */
private[scalanative] final case class Digest(hash: String, sizeBytes: Long) {
  override def toString: String = s"$hash/$sizeBytes"
}

private[scalanative] object Digest {
  final val Algorithm = "SHA-256"
  private final val HashLength = 64

  def of(bytes: Array[Byte]): Digest = {
    val md = newMessageDigest()
    md.update(bytes)
    Digest(hex(md.digest()), bytes.length)
  }

  def of(text: String): Digest = of(text.getBytes(StandardCharsets.UTF_8))

  def of(file: Path): Digest = {
    val in = Files.newInputStream(file)
    try of(in)
    finally in.close()
  }

  def of(in: InputStream): Digest = {
    val md = newMessageDigest()
    val buffer = new Array[Byte](64 * 1024)
    var size = 0L
    var read = in.read(buffer)
    while (read != -1) {
      md.update(buffer, 0, read)
      size += read
      read = in.read(buffer)
    }
    Digest(hex(md.digest()), size)
  }

  /** Parses the `hash/size` form produced by `toString`. */
  def parse(text: String): Option[Digest] = text.split('/') match {
    case Array(hash, size) if isHash(hash) =>
      try Some(Digest(hash, size.toLong))
      catch { case _: NumberFormatException => None }
    case _ => None
  }

  def isHash(text: String): Boolean =
    text.length == HashLength && text.forall { c =>
      (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
    }

  def newMessageDigest(): MessageDigest = MessageDigest.getInstance(Algorithm)

  def hex(bytes: Array[Byte]): String = {
    val digits = "0123456789abcdef"
    val out = new java.lang.StringBuilder(bytes.length * 2)
    bytes.foreach { b =>
      out.append(digits.charAt((b >> 4) & 0xf))
      out.append(digits.charAt(b & 0xf))
    }
    out.toString
  }
}
