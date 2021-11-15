package build

import sbt._
import Keys._
import Build._
import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport._
import ScriptedPlugin.autoImport._

object Commands {
  lazy val values = Seq(testAll, testRuntime, testTools, testMima, testScripted)

  lazy val testScripted = UnsafeCommand("test-scripted")
    .withSimpleTask(sbtScalaNative / scripted / test)

  lazy val testAll = UnsafeCommand("test-all")
    .withCommand(testTools)
    .withCommand(testRuntime)
    // testMima is already part of testTools
    .withCommand(testScripted)

  lazy val testRuntime = UnsafeCommand("test-runtime")
    .withProjectsTasks(_ / Test / test)(
      tests,
      testsJVM,
      testsExt,
      testsExtJVM,
      junitTestOutputsJVM,
      junitTestOutputsNative,
      scalaPartestJunitTests
    )

  lazy val testTools = UnsafeCommand("test-tools")
    .withProjectsTasks(_ / Test / test)(testRunner, testInterface, tools)
    .withCommand(testMima)

  lazy val testMima = UnsafeCommand("test-mima")
    .withProjectsTasks(_ / mimaReportBinaryIssues)(
      Build.util,
      nir,
      tools,
      testRunner,
      testInterface,
      testInterfaceSbtDefs,
      junitRuntime,
      nativelib,
      clib,
      posixlib,
      windowslib,
      auxlib,
      javalib,
      scalalib
    )

  case class ProjectsTask(
      projects: Seq[MultiScalaProject],
      task: Project => TaskKey[_]
  ) {
    def forVersion(v: String): Seq[TaskKey[_]] =
      projects.map(p => task(p.forBinaryVersion(v)))
  }
  case class UnsafeCommand(
      name: String,
      tasks: Seq[ProjectsTask] = Nil,
      simpleTasks: Seq[TaskKey[_]] = Nil,
  ) {
    def withSimpleTask(task: TaskKey[_]) =
      copy(simpleTasks = simpleTasks :+ task)

    def withProjectsTasks(
        taskFn: Project => TaskKey[_]
    )(projects: MultiScalaProject*) = {
      copy(tasks = tasks :+ ProjectsTask(projects, taskFn))
    }

    def withCommand(other: UnsafeCommand) = {
      copy(tasks = tasks ++ other.tasks)
    }

    def toCommand = Command.args(name, "<args>") {
      case (state, args) =>
        val version = args.headOption
          .orElse(state.getSetting(scalaBinaryVersion))
          .getOrElse(
            "Used command needs explicit Sclaa binary verion as an argument"
          )

        for {
          task <- tasks.flatMap(_.forVersion(version)) ++ simpleTasks
        } state.unsafeRunTask(task)

        state
    }
  }

}
