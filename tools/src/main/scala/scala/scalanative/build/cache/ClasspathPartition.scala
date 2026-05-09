package scala.scalanative
package build
package cache

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.jar.JarFile

import scala.util.Using

/** Classifies classpath entries for the scala-native-runtime bundle vs other libraries. */
private[scalanative] object ClasspathPartition {

  private val RuntimeJarPatterns: Seq[String => Boolean] = Seq(
    _.contains("nativelib_"),
    _.contains("javalib_"),
    _.contains("scalalib_"),
    _.contains("scala3lib_"),
    _.contains("clib_"),
    _.contains("posixlib_"),
    _.contains("windowslib_"),
    _.contains("auxlib_")
  )

  /** Jars that are merged into the cached `scala-native-runtime` dynamic library. */
  def runtimeJars(classpath: Seq[Path]): Seq[Path] =
    classpath.filter(p => Files.isRegularFile(p) && isRuntimeJarName(p.getFileName.toString))

  /** All other classpath entries (including directories and non-runtime jars). */
  def nonRuntimeJars(classpath: Seq[Path]): Seq[Path] =
    classpath.filterNot(p => Files.isRegularFile(p) && isRuntimeJarName(p.getFileName.toString))

  def isRuntimeJarName(fileName: String): Boolean =
    fileName.endsWith(".jar") && RuntimeJarPatterns.exists(_(fileName))

  /** SHA-256 of entire JAR bytes (deterministic, stable for immutable artifacts). */
  def jarContentSha256(path: Path): String = {
    val allBytes = Using.resource(Files.newInputStream(path)) { in =>
      val out = new java.io.ByteArrayOutputStream()
      val buf = new Array[Byte](8192)
      var n = in.read(buf)
      while (n != -1) {
        out.write(buf, 0, n)
        n = in.read(buf)
      }
      out.toByteArray
    }
    CacheKey.sha256HexBytes(allBytes)
  }

  /** Maven-style coordinates from `META-INF/maven` group-artifact `pom.properties` when present. */
  def mavenCoordinates(path: Path): Option[(String, String, String)] =
    if (!Files.isRegularFile(path) || !path.toString.endsWith(".jar")) None
    else
      Using.resource(new JarFile(path.toFile)) { jar =>
        var found: Option[(String, String, String)] = None
        val entries = jar.entries()
        while (entries.hasMoreElements && found.isEmpty) {
          val e = entries.nextElement()
          val name = e.getName
          if (name.startsWith("META-INF/maven/") && name.endsWith("/pom.properties")) {
            val parts = name.stripPrefix("META-INF/maven/").stripSuffix("/pom.properties").split('/')
            if (parts.length == 2) {
              val props = new java.util.Properties()
              Using.resource(jar.getInputStream(e)) { in =>
                props.load(in)
              }
              val version = Option(props.getProperty("version")).getOrElse("")
              found = Some((parts(0), parts(1), version))
            }
          }
        }
        found
      }

  def cachePathSegmentForJar(path: Path): (String, String, String) =
    mavenCoordinates(path).getOrElse {
      val hash = jarContentSha256(path)
      ("local", path.getFileName.toString.stripSuffix(".jar"), hash.take(16))
    }

  /** Lines from each `META-INF/services` resource as `interfaceBinaryName:implementationBinaryName`. */
  def metaInfServiceProviders(path: Path): Seq[String] =
    if (!Files.isRegularFile(path) || !path.toString.endsWith(".jar")) Nil
    else
      Using.resource(new JarFile(path.toFile)) { jar =>
        val out = scala.collection.mutable.ArrayBuffer.empty[String]
        val entries = jar.entries()
        while (entries.hasMoreElements) {
          val e = entries.nextElement()
          val name = e.getName
          if (name.startsWith("META-INF/services/") && !e.isDirectory) {
            val iface = name.stripPrefix("META-INF/services/")
            Using.resource(
              new BufferedReader(
                new InputStreamReader(jar.getInputStream(e), StandardCharsets.UTF_8)
              )
            ) { reader =>
              var line = reader.readLine()
              while (line != null) {
                val t = line.trim
                if (t.nonEmpty && !t.startsWith("#"))
                  out += s"$iface:$t"
                line = reader.readLine()
              }
            }
          }
        }
        out.distinct.toSeq
      }
}
