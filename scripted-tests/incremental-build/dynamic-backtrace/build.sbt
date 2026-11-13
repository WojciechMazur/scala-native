import scala.scalanative.build._

ThisBuild / scalaVersion := sys.props("scala.version")

// A build cache that clean keeps
Global / onLoad := {
  val dir = (ThisBuild / baseDirectory).value / "build-cache"
  System.setProperty("scala.scalanative.cache.dir", dir.getAbsolutePath)
  (Global / onLoad).value
}

// Positions of frames in the shared library of a library unit
lazy val lib = project.enablePlugins(ScalaNativePlugin)

lazy val app = project
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(lib)
  .settings(
    nativeConfig ~= {
      _.withSourceLevelDebuggingConfig(_.enableAll)
        .withMode(Mode.debug)
        .withGranularBuild(true)
        .withDynamicLibraries(true)
    }
  )
