ThisBuild / organization := "example"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.6"

lazy val a = project
// b consumes a's published jar from ~/.ivy2/local, not via dependsOn
lazy val b = project.settings(libraryDependencies += "example" %% "a" % "0.1.0-SNAPSHOT")
