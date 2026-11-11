enablePlugins(ScalaNativePlugin)

scalaVersion := sys.props("scala.version")

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
  "the toolchain ran although nothing changed"
)

lazy val checkToolchainRan =
  taskKey[Unit]("Check the last link ran the toolchain")
checkToolchainRan := assert(
  toolchainMarker(crossTarget.value).exists,
  "the toolchain did not run after a change"
)
