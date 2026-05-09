package scala.scalanative
package build
package cache

import java.io.ByteArrayOutputStream
import java.nio.{ByteBuffer, ByteOrder}
import java.nio.channels.Channels
import java.nio.file.{Files, Path, Paths}
import java.util.zip.ZipFile

import scala.util.Using

import scala.scalanative.build.Logger
import scala.scalanative.nir.NIRSource
import scala.scalanative.nir.serialization.{
  BinaryDeserializer,
  serializeBinaryStripped
}

/** Materializes stripped `.nir` trees under `cacheDir/nir-headers`, mirroring JAR entry paths. */
private[cache] object RuntimeNirHeaders {

  /** Version suffix: bump when on-disk stripped NIR format changes (e.g. Define→Declare on wire). */
  private val CompleteMarker = ".nir-headers-complete-v2"

  def root(cacheDir: Path): Path = cacheDir.resolve("nir-headers")

  def isComplete(cacheDir: Path): Boolean =
    Files.exists(root(cacheDir).resolve(CompleteMarker))

  /** Writes stripped NIR for every `.nir` entry in `runtimeJars` into `cacheDir/nir-headers/`. */
  def ensure(cacheDir: Path, runtimeJars: Seq[Path], log: Logger): Unit = {
    val outRoot = root(cacheDir)
    val marker = outRoot.resolve(CompleteMarker)
    if (Files.exists(marker)) ()
    else {
      Files.createDirectories(outRoot)
      var count = 0
      runtimeJars.foreach { jar =>
        Using.resource(new ZipFile(jar.toFile)) { zf =>
          val entries = zf.entries()
          while (entries.hasMoreElements) {
            val ent = entries.nextElement()
            val name = ent.getName
            if (!ent.isDirectory && name.endsWith(".nir")) {
              val rel = Paths.get(name)
              val target = outRoot.resolve(rel).normalize()
              if (!target.startsWith(outRoot))
                throw new IllegalStateException(s"Bad NIR zip path $name")
              Files.createDirectories(target.getParent)
              val bytes = zf.getInputStream(ent).readAllBytes()
              val buf = ByteBuffer.wrap(bytes)
              val o = buf.order()
              buf.order(ByteOrder.BIG_ENDIAN)
              val defns =
                try new BinaryDeserializer(buf, new NIRSource(jar, rel)).deserialize()
                finally buf.order(o)
              val chn = Channels.newChannel(Files.newOutputStream(target))
              try serializeBinaryStripped(defns, chn)
              finally chn.close()
              count += 1
            }
          }
        }
      }
      Files.writeString(marker, s"ok $count nir files from ${runtimeJars.size} jars\n")
      log.info(
        s"[cached libraries] Materialized stripped runtime NIR headers ($count files) under ${outRoot.toAbsolutePath}"
      )
    }
  }

}
