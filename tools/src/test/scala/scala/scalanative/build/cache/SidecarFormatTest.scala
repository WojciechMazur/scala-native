package scala.scalanative
package build
package cache

import java.nio.file.Files

import org.junit.Assert.*
import org.junit.Test

class SidecarFormatTest {

  @Test def roundTripServicesAndLinktime(): Unit = {
    val dir = Files.createTempDirectory("sidecar-test")
    val path = dir.resolve("h.sidecar")
    val data = Sidecar.Data(
      exports = Seq(Sidecar.ExportedSymbol("M1", "M1")),
      initEdges = Seq(Sidecar.InitEdge("A", "B")),
      vtableSlots = Nil,
      ancestorLists = Seq(Sidecar.AncestorList("T", Seq(1, 2))),
      services = Seq("java.nio.file.spi.FileSystemProvider:com.example.X"),
      linktimeKeys = Seq("scala.scalanative.meta.linktimeinfo_isMultithreadingEnabled")
    )
    Sidecar.write(path, data)
    val read = Sidecar.read(path)
    assertTrue(read.isDefined)
    val d = read.get
    assertEquals(1, d.exports.length)
    assertEquals(1, d.initEdges.length)
    assertEquals(1, d.ancestorLists.length)
    assertEquals(Seq(1, 2), d.ancestorLists.head.typeIds)
    assertEquals(data.services, d.services)
    assertEquals(data.linktimeKeys, d.linktimeKeys)
  }

  @Test def legacyFileWithoutTailSections(): Unit = {
    val dir = Files.createTempDirectory("sidecar-legacy")
    val path = dir.resolve("legacy.sidecar")
    Sidecar.write(path, Sidecar.empty)
    val d = Sidecar.read(path).get
    assertEquals(Nil, d.services)
    assertEquals(Nil, d.linktimeKeys)
  }
}
