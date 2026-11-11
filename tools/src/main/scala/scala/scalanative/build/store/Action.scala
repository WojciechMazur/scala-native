package scala.scalanative
package build
package store

import java.nio.charset.StandardCharsets

/** Identity of an action: the hash of everything that determines its outputs.
 *
 *  A key covers the action kind and format version, the digests of its inputs
 *  and the execution context (command line, toolchain). It never covers
 *  timestamps or absolute paths of the build, so the same work done in another
 *  directory, worktree or project finds the same key.
 */
private[scalanative] final case class ActionKey(hash: String) {
  override def toString: String = hash
}

private[scalanative] object ActionKey {

  def builder(kind: String, version: Int): Builder =
    new Builder().add("kind", kind).add("version", version.toString)

  /** Feeds named fields to the hash. Every name and value is length-prefixed,
   *  so no two different field sequences hash the same bytes.
   */
  final class Builder private[ActionKey] () {
    private val md = Digest.newMessageDigest()

    private def write(text: String): Unit = {
      val bytes = text.getBytes(StandardCharsets.UTF_8)
      val length = bytes.length
      md.update((length >>> 24).toByte)
      md.update((length >>> 16).toByte)
      md.update((length >>> 8).toByte)
      md.update(length.toByte)
      md.update(bytes)
    }

    def add(name: String, value: String): Builder = {
      write(name)
      write(value)
      this
    }

    def add(name: String, digest: Digest): Builder =
      add(name, digest.toString)

    def addAll(name: String, values: Seq[String]): Builder = {
      add(name, values.size.toString)
      values.foreach(write)
      this
    }

    def result(): ActionKey = ActionKey(Digest.hex(md.digest()))
  }
}

/** Outputs of a completed action, by name, as stored in the action cache. Every
 *  output blob is in the content-addressed store before the result is.
 */
private[scalanative] final case class ActionResult(
    outputs: Seq[(String, Digest)]
) {
  def output(name: String): Option[Digest] =
    outputs.collectFirst { case (`name`, digest) => digest }

  def serialize: String = {
    val out = new java.lang.StringBuilder(ActionResult.Header).append('\n')
    outputs.foreach {
      case (name, digest) =>
        out.append("output\t").append(name).append('\t')
        out.append(digest.toString).append('\n')
    }
    out.toString
  }
}

private[scalanative] object ActionResult {
  private final val Header = "scala-native-action-result 1"

  def isValidName(name: String): Boolean =
    name.nonEmpty && !name.exists(c => c == '\t' || c == '\n' || c == '\r')

  def parse(text: String): Option[ActionResult] = {
    val lines = text.split('\n').toList
    if (lines.headOption.forall(_ != Header)) None
    else {
      val outputs = lines.tail.filter(_.nonEmpty).map { line =>
        line.split('\t') match {
          case Array("output", name, digest) =>
            Digest.parse(digest).map(name -> _)
          case _ => None
        }
      }
      if (outputs.forall(_.isDefined)) Some(ActionResult(outputs.map(_.get)))
      else None
    }
  }
}
