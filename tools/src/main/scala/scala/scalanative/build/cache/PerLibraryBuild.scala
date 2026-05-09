package scala.scalanative
package build
package cache

import java.nio.channels.Channels
import java.nio.file.{Files, Path}

import scala.scalanative.linker.{Link, ReachabilityAnalysis}
import scala.scalanative.util.Scope
import scala.scalanative.nir
import scala.scalanative.nir.serialization.serializeBinaryStripped

/** Closed-world compile of a single classpath jar into a cached dynamic library (experimental). */
private[scalanative] object PerLibraryBuild {

  /** Writes stripped NIR header and sidecar for the given reachability result into `destDir`. */
  def writeHeaderArtifacts(
      destDir: Path,
      analysis: ReachabilityAnalysis.Result,
      jarPath: Option[Path] = None
  ): Unit = {
    Files.createDirectories(destDir)
    val headerPath = destDir.resolve("header.nir")
    val chn = Channels.newChannel(Files.newOutputStream(headerPath))
    try serializeBinaryStripped(analysis.defns, chn)
    finally chn.close()

    val services = jarPath.toSeq.flatMap(ClasspathPartition.metaInfServiceProviders)
    val exports = analysis.entries.flatMap {
      case m: nir.Global.Member =>
        Some(Sidecar.ExportedSymbol(m.mangle, m.mangle))
      case t: nir.Global.Top =>
        Some(Sidecar.ExportedSymbol(t.mangle, t.mangle))
      case nir.Global.None =>
        None
    }
    val sidecar = Sidecar.Data(
      exports = exports,
      initEdges = Nil,
      vtableSlots = Nil,
      ancestorLists = Nil,
      services = services,
      linktimeKeys = Nil
    )
    Sidecar.write(destDir.resolve("header.sidecar"), sidecar)
  }

  /** Runs link for library roots over an in-memory classpath slice (caller supplies defns). */
  def linkLibrarySlice(
      config: Config,
      defns: Seq[nir.Defn],
      roots: Seq[nir.Global]
  )(implicit scope: Scope): ReachabilityAnalysis =
    Link(config, roots, defns)
}
