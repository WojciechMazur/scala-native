package scala.scalanative
package linker

import scala.collection.mutable

import scala.scalanative.build.Logger
import scalanative.io.VirtualDirectory
import scalanative.util.Scope

sealed abstract class ClassLoader {

  def classesWithEntryPoints: Iterable[nir.Global.Top]

  def definedServicesProviders: Map[nir.Global.Top, Iterable[nir.Global.Top]]

  def load(global: nir.Global.Top): Option[Seq[nir.Defn]]

}

object ClassLoader {

  def fromDisk(config: build.Config)(implicit in: Scope): ClassLoader =
    build.store.BuildCache.open(config) match {
      // Granular builds load classes from the libraries derived from the
      // classpath, see build.units.Library
      case Some(cache) =>
        try {
          val libraries = build.units.Libraries.open(config, cache)
          new FromDisk(libraries.map {
            case (_, library) => ClassPath(library)
          })
        } finally cache.close()
      case None =>
        val classpath = config.classPath.map { path =>
          ClassPath(VirtualDirectory.real(path), config.logger)
        }
        new FromDisk(classpath)
    }

  def fromMemory(defns: Seq[nir.Defn]): ClassLoader =
    new FromMemory(defns)

  final class FromDisk(classpath: Seq[ClassPath]) extends ClassLoader {
    lazy val classesWithEntryPoints: Iterable[nir.Global.Top] = {
      classpath.flatMap(_.classesWithEntryPoints)
    }
    lazy val definedServicesProviders
        : Map[nir.Global.Top, Iterable[nir.Global.Top]] =
      classpath.flatMap(_.definedServicesProviders).toMap

    def load(global: nir.Global.Top): Option[Seq[nir.Defn]] =
      classpath.collectFirst {
        case path if path.contains(global) =>
          path.load(global)
      }.flatten
  }

  final class FromMemory(defns: Seq[nir.Defn]) extends ClassLoader {

    private val scopes = {
      val out =
        mutable.Map.empty[nir.Global.Top, mutable.UnrolledBuffer[nir.Defn]]
      defns.foreach { defn =>
        val owner = defn.name.top
        val buf =
          out.getOrElseUpdate(owner, mutable.UnrolledBuffer.empty[nir.Defn])
        buf += defn
      }
      out
    }

    lazy val classesWithEntryPoints: Iterable[nir.Global.Top] = {
      scopes.filter {
        case (_, defns) => defns.exists(_.isEntryPoint)
      }.keySet
    }

    def definedServicesProviders
        : Map[nir.Global.Top, Iterable[nir.Global.Top]] =
      Map.empty

    def load(global: nir.Global.Top): Option[Seq[nir.Defn]] =
      scopes.get(global).map(_.toSeq)

  }

}
