package scala.scalanative
package linker

import java.nio.file.{Files, Path}
import java.util.jar.JarFile

import scala.jdk.CollectionConverters._
import scala.util.Using

/** Loads `META-INF/scala-native/ignored-symbols.txt` from classpath jars.
  *
  * Each non-empty line is a [[nir.Global.Member]] mangled name (as produced by
  * [[nir.Global.mangle]]), parsed with [[nir.Unmangle.unmangleGlobal]]. When that
  * member’s NIR is loaded, its body is replaced with an unreachable stub
  * ([[Reach.replaceIgnoredWithStub]]). This is independent of
  * [[UnreachableReferenceIgnores]] (missing-symbol suppression for **callers**).
  */
private[linker] object IgnoredSymbols {

  private val IgnoredResourcePath = "META-INF/scala-native/ignored-symbols.txt"

  def loadFromClasspath(classpath: Seq[Path]): Set[nir.Global.Member] = {
    val buf = scala.collection.mutable.LinkedHashSet.empty[nir.Global.Member]
    classpath.foreach { p =>
      if (Files.isRegularFile(p) && p.toString.endsWith(".jar"))
        loadFromJar(p, buf)
      else if (Files.isDirectory(p))
        loadFromDirectory(p, buf)
    }
    buf.toSet
  }

  private def loadFromJar(jar: Path, buf: scala.collection.mutable.LinkedHashSet[nir.Global.Member]): Unit =
    Using.resource(new JarFile(jar.toFile)) { jf =>
      val ent = jf.getJarEntry(IgnoredResourcePath)
      if (ent != null) {
        val lines = Using.resource(jf.getInputStream(ent)) { in =>
          scala.io.Source.fromInputStream(in, "UTF-8").getLines().toList
        }
        parseLines(lines, buf)
      }
    }

  private def loadFromDirectory(
      root: Path,
      buf: scala.collection.mutable.LinkedHashSet[nir.Global.Member]
  ): Unit = {
    val f = root.resolve(IgnoredResourcePath)
    if (Files.exists(f)) {
      val lines = Files.readAllLines(f).asScala.toList
      parseLines(lines, buf)
    }
  }

  private def parseLines(
      lines: Seq[String],
      buf: scala.collection.mutable.LinkedHashSet[nir.Global.Member]
  ): Unit =
    lines.iterator.foreach { rawLine =>
      val line = rawLine.trim
      if (line.nonEmpty && !line.startsWith("#")) {
        try {
          nir.Unmangle.unmangleGlobal(line) match {
            case m: nir.Global.Member => buf += m
            case _                    => ()
          }
        } catch {
          case _: Exception => ()
        }
      }
    }
}
