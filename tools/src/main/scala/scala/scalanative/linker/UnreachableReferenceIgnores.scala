package scala.scalanative
package linker

import java.nio.file.{Files, Path}
import java.util.jar.JarFile

import scala.jdk.CollectionConverters._
import scala.util.Using

/** Loads per-library `META-INF/scala-native/ignore-unreachable-from.txt` resources.
  *
  * When the linker discovers a missing symbol while analysing a method body, it
  * normally records an [[Reach.UnreachableSymbol]] for the **missing** name. This
  * mechanism instead suppresses that report when the **defining method** (the
  * [[nir.Global.Member]] currently on the reachability stack) is listed here — i.e.
  * we ignore methods that reference undefined symbols, not the undefined symbols
  * themselves.
  *
  * Lines:
  *   - `# ...` comments
  *   - `@owner-prefix <prefix>` — ignore missing-symbol reports for any defining
  *     `Global.Member` whose `owner.id` starts with `<prefix>` (typical: a module or
  *     package object).
  *   - a mangled [[nir.Global]] string parsed with [[nir.Unmangle.unmangleGlobal]]; only
  *     [[nir.Global.Member]] rows are kept (exact defining methods).
  */
private[linker] object UnreachableReferenceIgnores {

  private val ResourcePath = "META-INF/scala-native/ignore-unreachable-from.txt"

  private val OwnerPrefix = raw"@owner-prefix\s+(\S+)".r

  final case class Bundle(
      ownerPrefixes: Seq[String],
      exactCallers: Set[nir.Global.Member]
  ) {
    def suppressesCaller(definingMember: nir.Global.Member): Boolean =
      ownerPrefixes.exists(definingMember.owner.id.startsWith) ||
        exactCallers.contains(definingMember)

    /** True when the reach stack head is a scope top whose id matches a prefix. */
    def suppressesDefiningTop(top: nir.Global.Top): Boolean =
      ownerPrefixes.exists(top.id.startsWith)
  }

  def loadFromClasspath(classpath: Seq[Path]): Bundle = {
    val prefixes = scala.collection.mutable.ArrayBuffer.empty[String]
    val exact = scala.collection.mutable.LinkedHashSet.empty[nir.Global.Member]
    classpath.foreach { p =>
      if (Files.isRegularFile(p) && p.toString.endsWith(".jar"))
        mergeJar(p, prefixes, exact)
      else if (Files.isDirectory(p))
        mergeDir(p, prefixes, exact)
    }
    Bundle(
      ownerPrefixes = dedupeSeq(prefixes.toSeq),
      exactCallers = exact.toSet
    )
  }

  private def dedupeSeq(s: Seq[String]): Seq[String] = {
    val seen = scala.collection.mutable.LinkedHashSet.empty[String]
    s.foreach(seen += _)
    seen.toSeq
  }

  private def mergeJar(
      jar: Path,
      prefixes: scala.collection.mutable.ArrayBuffer[String],
      exact: scala.collection.mutable.LinkedHashSet[nir.Global.Member]
  ): Unit =
    Using.resource(new JarFile(jar.toFile)) { jf =>
      val ent = jf.getJarEntry(ResourcePath)
      if (ent != null) {
        val lines = Using.resource(jf.getInputStream(ent)) { in =>
          scala.io.Source.fromInputStream(in, "UTF-8").getLines().toList
        }
        parseLines(lines, prefixes, exact)
      }
    }

  private def mergeDir(
      root: Path,
      prefixes: scala.collection.mutable.ArrayBuffer[String],
      exact: scala.collection.mutable.LinkedHashSet[nir.Global.Member]
  ): Unit = {
    val f = root.resolve(ResourcePath)
    if (Files.exists(f)) {
      val lines = Files.readAllLines(f).asScala.toList
      parseLines(lines, prefixes, exact)
    }
  }

  private def parseLines(
      lines: Seq[String],
      prefixes: scala.collection.mutable.ArrayBuffer[String],
      exact: scala.collection.mutable.LinkedHashSet[nir.Global.Member]
  ): Unit =
    lines.iterator.foreach { rawLine =>
      val line = rawLine.trim
      if (line.nonEmpty && !line.startsWith("#")) {
        line match {
          case OwnerPrefix(prefix) =>
            prefixes += prefix
          case _ =>
            try {
              nir.Unmangle.unmangleGlobal(line) match {
                case m: nir.Global.Member => exact += m
                case _: nir.Global.Top    => () // only members are defining methods
                case _                    => ()
              }
            } catch {
              case _: Exception => ()
            }
        }
      }
    }
}
