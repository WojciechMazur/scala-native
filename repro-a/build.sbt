ThisBuild / organization := "example"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.6"

lazy val holdJar = taskKey[Unit]("Opens the published jar and keeps it open, like a classloader or zip FS would")

lazy val a = project
  .settings(
    holdJar := {
      val jar = (Path.userHome / ".ivy2" / "local" ** "a_3.jar").get().head
      println(s"holding open: $jar")
      // Stand-in for a jar kept open across commands by a classloader / ZipFileSystem / linker in the sbt JVM
      JarHolder.hold(new java.util.zip.ZipFile(jar))
    }
  )
