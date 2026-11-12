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
  toolchainMarker(crossTarget.value) +:
    // sbt 1 runs link tasks every time, the checksum skips unchanged links
    (if (sbtVersion.value.startsWith("1.")) Nil
     else Seq(crossTarget.value / "native" / "build-checksum"))
)

// Only sbt 2 keeps the program after clean
lazy val checkToolchainSkipped =
  taskKey[Unit]("Check the last link did not run the toolchain")
checkToolchainSkipped := assert(
  sbtVersion.value.startsWith("1.") ||
    !toolchainMarker(crossTarget.value).exists,
  "the toolchain ran although its output was kept"
)

// Programs of platforms linking statically need no library
lazy val checkToolchainRan =
  taskKey[Unit]("Check the last link ran the toolchain")
checkToolchainRan := assert(
  !(buildCache(baseDirectory.value) / "lib").exists ||
    toolchainMarker(crossTarget.value).exists,
  "the toolchain did not run although libraries were missing"
)

lazy val deleteLibraries =
  taskKey[Unit]("Delete the libraries of the build cache")
deleteLibraries := IO.delete(
  Option((buildCache(baseDirectory.value) / "lib").listFiles()).toSeq.flatten
    .filter(_.isFile)
)

// Libraries of a moved build cache are found through the library path
lazy val runWithMovedLibraries =
  taskKey[Unit]("Run the program with its libraries in another directory")
runWithMovedLibraries := {
  val conv = Keys.fileConverter.value
  // sbt 1 links to a File, sbt 2 to a virtual file
  val program = ((Compile / nativeLink).value: Any) match {
    case f: File                   => f
    case ref: xsbti.VirtualFileRef => conv.toPath(ref).toFile
  }
  val libraries = buildCache(baseDirectory.value) / "lib"
  if (libraries.exists) {
    val moved = target.value / "moved-lib"
    IO.delete(moved)
    IO.move(libraries, moved)
    try {
      val variable =
        if (System.getProperty("os.name").startsWith("Mac"))
          "DYLD_LIBRARY_PATH"
        else "LD_LIBRARY_PATH"
      val output = scala.sys.process
        .Process(
          Seq(program.getAbsolutePath),
          None,
          variable -> moved.getAbsolutePath
        )
        .!!
      assert(output.trim == "hello", s"unexpected output: $output")
    } finally IO.move(moved, libraries)
  }
}
