enablePlugins(ScalaNativePlugin)

scalaVersion := sys.props("scala.version")

// Executables loading the libraries of library units from the build cache
nativeConfig ~= (_.withGranularBuild(true).withDynamicLibraries(true))

// A build cache that clean keeps
def buildCache(base: File): File = base / "build-cache"
Global / onLoad := {
  val dir = buildCache((ThisBuild / baseDirectory).value)
  System.setProperty("scala.scalanative.cache.dir", dir.getAbsolutePath)
  (Global / onLoad).value
}

// Created by the toolchain in the native working directory of each link
def toolchainMarker(crossTarget: File): File =
  crossTarget / "native" / "userConfigHash"

// Without the checksum, a link task that runs builds again and leaves the
// marker
lazy val forgetToolchainRun =
  taskKey[Unit]("Delete the marker of a toolchain run")
forgetToolchainRun := IO.delete(
  Seq(
    toolchainMarker(crossTarget.value),
    crossTarget.value / "native" / "build-checksum"
  )
)

lazy val checkToolchainSkipped =
  taskKey[Unit]("Check the last link did not run the toolchain")
checkToolchainSkipped := assert(
  !toolchainMarker(crossTarget.value).exists,
  "the toolchain ran although its output was kept"
)

lazy val checkToolchainRan =
  taskKey[Unit]("Check the last link ran the toolchain")
checkToolchainRan := assert(
  toolchainMarker(crossTarget.value).exists,
  "the toolchain did not run although libraries were missing"
)

lazy val deleteLibraries =
  taskKey[Unit]("Delete the libraries of the build cache")
deleteLibraries := IO.delete(
  (buildCache(baseDirectory.value) / "lib").listFiles().filter(_.isFile)
)
