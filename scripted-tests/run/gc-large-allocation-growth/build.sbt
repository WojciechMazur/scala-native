import java.util.concurrent.TimeUnit

enablePlugins(ScalaNativePlugin)

Compile / mainClass := Some("Main")

nativeConfig ~= {
  _.withMultithreading(true)
    .withGC(scala.scalanative.build.GC.immix)
}

scalaVersion := {
  val scalaVersion = System.getProperty("scala.version")
  if (scalaVersion == null)
    throw new RuntimeException(
      """|The system property 'scala.version' is not defined.
         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin
    )
  else scalaVersion
}

def nativeExecutable(
    linkOutput: Any
)(implicit conv: xsbti.FileConverter): java.io.File =
  linkOutput match {
    case f: java.io.File           => f
    case ref: xsbti.VirtualFileRef => conv.toPath(ref).toFile()
  }

lazy val runLargeAllocation = taskKey[Unit](
  "Allocate a large object in a fragmented heap close to its size limit"
)

runLargeAllocation := {
  implicit val conv: xsbti.FileConverter = Keys.fileConverter.value
  val binary = nativeExecutable((Compile / nativeLink).value)
  val proc = new ProcessBuilder(binary.getAbsolutePath)
  // The scenario relies on the heap growing from its default initial size
  // to between 192M and 256M.
  proc.environment().remove("GC_INITIAL_HEAP_SIZE")
  proc.environment().put("GC_MAXIMUM_HEAP_SIZE", "256M")
  proc.inheritIO()

  val running = proc.start()
  if (!running.waitFor(120, TimeUnit.SECONDS)) {
    running.destroyForcibly()
    throw new RuntimeException("Large allocation test did not finish within 120s")
  }
  val exitCode = running.exitValue()
  if (exitCode != 0)
    throw new RuntimeException(s"Large allocation test exited with $exitCode")
}
