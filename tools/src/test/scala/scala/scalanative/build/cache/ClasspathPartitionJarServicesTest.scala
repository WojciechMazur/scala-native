package scala.scalanative
package build
package cache

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.{ZipEntry, ZipOutputStream}

import org.junit.Assert.*
import org.junit.Test

class ClasspathPartitionJarServicesTest {

  @Test def metaInfServiceProvidersReadsJar(): Unit = {
    val dir = Files.createTempDirectory("jar-svc")
    val jar = dir.resolve("demo.jar")
    val zos = new ZipOutputStream(Files.newOutputStream(jar))
    zos.putNextEntry(new ZipEntry("META-INF/services/java.nio.file.spi.FileSystemProvider"))
    zos.write("com.example.DummyProvider\n".getBytes(StandardCharsets.UTF_8))
    zos.closeEntry()
    zos.close()

    val got = ClasspathPartition.metaInfServiceProviders(jar)
    assertEquals(
      Seq("java.nio.file.spi.FileSystemProvider:com.example.DummyProvider"),
      got
    )
  }
}
