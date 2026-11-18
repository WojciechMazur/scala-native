package build

import sbt._

import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport._

import Build._
import Keys._
import ScriptedPlugin.autoImport._

object Commands {
  lazy val values = Seq(
    testAll,
    testGC,
    testTools,
    compileAll,
    compileSbtPlugin,
    testCrossCompile,
    testRuntime,
    testRuntimeNative,
    testRuntimeJVM,
    testMima,
    testScripted,
    disableActionCache,
    publishLocalDev,
    publishReleaseForVersion
  )

  // Recommended for working with compiler plugins, the actions cache might make testing local changes harder.
  lazy val disableActionCache = Command.command("disable-action-cache") {
    "set Global / cacheStores := Seq.empty[sbt.util.ActionCacheStore]" :: _
  }

  lazy val testAll = Command.command("test-all") {
    "test-tools" ::
      "test-mima" ::
      "test-runtime" ::
      "test-scripted" :: _ // test-scripted will publish artifacts locally
  }

  // Compile and run the sandbox for each GC as a minimal check
  lazy val testGC = projectVersionCommand("test-gc") {
    case (version, state) =>
      import scala.scalanative.build.GC
      val isWindows =
        System.getProperty("os.name").toLowerCase.contains("windows")
      val targetsAppleEmbedded = sys.env
        .get("SCALANATIVE_TARGET_TRIPLE")
        .exists(t => Seq("-ios", "-tvos", "-watchos").exists(t.contains))
      val runs =
        for {
          gc <- List(GC.none, GC.immix, GC.commix, GC.boehm)
          // Exclude running Boehm GC on Windows for Scala 3 as deadlocks for unknown reasons
          if !(isWindows && gc == GC.boehm && version == "3")
          // No Boehm GC library for iOS, tvOS and watchOS SDKs
          if !(gc == GC.boehm && targetsAppleEmbedded)
          (project, command) <- Map(
            sandbox -> "run",
            testInterface -> "test",
            junitTestOutputsNative -> "test"
          )
        } yield {
          val projectId = project.forBinaryVersion(version).id
          // GC smoke checks do not need debug metadata or source-JAR downloads.
          // The full runtime suite still exercises source-level debugging.
          val configure =
            s"""_.withGC(scala.scalanative.build.GC("${gc.name}"))""" +
              ".withSourceLevelDebuggingConfig(_.disableAll)"
          s"""set ${project.name}.forBinaryVersion("${version}")/nativeConfig ~= ($configure); $projectId/$command"""
        }
      runs :::
        state
  }

  // Compile main and test sources of every project defined for given Scala binary version.
  // sbt plugins are excluded, they need the Scala version of their sbt, see compile-sbt-plugin
  lazy val compileAll = projectVersionCommand("compile-all") {
    case (version, state) =>
      val projects = Build.allMultiScalaProjects
        .filterNot(Build.sbtPlugins.contains)
        .flatMap(_.forBinaryVersionIfDefined(version))
        .map(p => s"${p.id}/Test/compile")
      projects.mkString("all ", " ", "") :: state
  }

  // Compile the sbt plugin for given Scala binary version using the Scala version of its sbt:
  // 2.12 - sbt 1.x, 3 - sbt 2.x
  lazy val compileSbtPlugin = projectVersionCommand("compile-sbt-plugin") {
    case (version, state) =>
      val sbtScalaVersion = ScalaVersions
        .sbtScalaVersion(version)
        .getOrElse(
          throw new MessageOnlyException(
            s"No sbt plugin for Scala binary version $version"
          )
        )
      val plugins = Build.sbtPlugins
        .flatMap(_.forBinaryVersionIfDefined(version))
        .map(p => s"${p.id}/compile")
      s"++$sbtScalaVersion" :: plugins ::: state
  }

  // Compile compiler plugins and the Scala standard library for each supported version of given binary version
  lazy val testCrossCompile = projectVersionCommand("test-cross-compile") {
    case (version, state) =>
      // Compiling the standard library builds the compiler plugin for the same Scala version
      val stdlib = (if (version == "3") scala3lib else scalalib)
        .forBinaryVersion(version)
        .id
      MultiScalaProject
        .scalaCrossVersions(version)
        .toList
        .flatMap(v => List(s"++$v", s"$stdlib/compile")) ::: state
  }

  private def runtimeJVMTests(version: String): List[String] =
    List(testsJVM, testsExtJVM, junitTestOutputsJVM)
      .map(_.forBinaryVersion(version).id)
      .map(id => s"$id/test")

  private def runtimeNativeTests(version: String): List[String] = {
    val run = s"${sandbox.forBinaryVersion(version).id}/run"
    val tests = List(
      Build.tests,
      testsExt,
      junitTestOutputsNative,
      scalaPartestJunitTests
    ).map(_.forBinaryVersion(version).id)
      .map(id => s"$id/test")
    run :: tests
  }

  lazy val testRuntime = projectVersionCommand("test-runtime") {
    case (version, state) =>
      runtimeJVMTests(version) ::: runtimeNativeTests(version) ::: state
  }

  // Native part of the runtime tests, the JVM part is executed once per OS in the CI gate
  lazy val testRuntimeNative =
    projectVersionCommand("test-runtime-native") {
      case (version, state) => runtimeNativeTests(version) ::: state
    }

  // JVM part of the runtime tests: validates test sources against JDK behaviour
  lazy val testRuntimeJVM = projectVersionCommand("test-runtime-jvm") {
    case (version, state) => runtimeJVMTests(version) ::: state
  }

  lazy val testTools = projectVersionCommand("test-tools") {
    case (version, state) =>
      val tests = List(
        nscPlugin, // compiler plugin
        // Toolchain JVM
        nirJVM,
        toolsJVM,
        // Testing infrastrucutre
        testRunner,
        testInterface,
        // Toolchain Native
        nir,
        tools
      )
        .map(_.forBinaryVersion(version).id)
        .map(id => s"$id/test")
      tests ::: state
  }

  lazy val testMima = projectVersionCommand("test-mima") {
    case (version, state) =>
      // Ensure all projects are using the Scala version of sbt, sbt plugins depend on them
      // (sbt might use older version then used by default in this project which would result in binary incompatibility)
      val prepareForMiMa = ScalaVersions
        .sbtScalaVersion(version)
        .map(v => s"++$v")
        .toList
      val tests = Build.publishedMultiScalaProjects
        .flatMap(_.forBinaryVersionIfDefined(version).map(_.id))
        .flatMap(id =>
          Seq(s"$id/mimaReportBinaryIssues", s"$id/nirExternReportIssues")
        )
        .toList

      prepareForMiMa ::: tests ::: state
  }

  lazy val testScripted = Command.args("test-scripted", "<args>") {
    case (state, args) =>
      val version = args.headOption
        .flatMap(MultiScalaProject.scalaVersions.get)
        .orElse(state.getSetting(scalaVersion))
        .map { version =>
          (
            CrossVersion.partialVersion(version),
            CrossVersion.partialVersion(ScalaVersions.sbt2ScalaVersion)
          ) match {
            case (Some((3, minor)), Some((3, sbtScalaVersionMinor)))
                if minor > sbtScalaVersionMinor =>
              System.err.println(
                s"Downgrading Scala ${version} version to ${ScalaVersions.sbt2ScalaVersion} for sbt scripted tests: cannot use forward incompatible artifacts"
              )
              ScalaVersions.sbt2ScalaVersion
            case _ => version
          }
        }
        .getOrElse(
          sys.error(
            "Used command needs explicit Scala version as an argument"
          )
        )

      val explicitTestsFilter = args.lift(1).map(_.trim).filterNot(_.isEmpty)

      case class Variant(
          prepareForMiMa: List[String],
          sbtProject: Project,
          testsFilter: Option[String] = None
      )
      val Variant(prepareTests, sbtProject, testsFilter) =
        CrossVersion.binaryScalaVersion(version) match {
          case "3" =>
            // Current limmitation test-infra limitation, we're testing sbt 2.x using the same version as it's used to build it's artifacts
            // scala3.version = sbt2ScalaVersion
            println(s"Testing sbt 2.x using Scala ${version}")
            Variant(
              prepareForMiMa = List(
                s"++${ScalaVersions.scala213}; publishLocal",
                s"++${ScalaVersions.sbt2ScalaVersion}; publishLocal",
                s"++${ScalaVersions.scriptedTestsScala3Version}; publishLocal",
                // Explicitlly to test using sbt 2.x
                s"++${ScalaVersions.sbt2ScalaVersion}"
              ),
              sbtProject = sbtScalaNative.v3
            )
          case "2.12" =>
            println(s"Testing sbt 1.x using Scala ${version}")
            Variant(
              prepareForMiMa = List(
                s"++${ScalaVersions.scala212}; publishLocal",
                s"++${ScalaVersions.scala213}; publishLocal",
                s"++${ScalaVersions.scriptedTestsScala3Version}; publishLocal",
                // Explicitlly to test using sbt 1.x
                s"++${ScalaVersions.scala212}"
              ),
              sbtProject = sbtScalaNative.v2_12
            )
          case binVersion =>
            throw new MessageOnlyException(
              s"Unsupported Scala binary version for sbt scripted tests: $binVersion (for Scala ${version})"
            )
        }

      val allCommands = prepareTests :::
        s"show ${sbtProject.id}/sbtVersion" ::
        s"show ${sbtProject.id}/scalaVersion" ::
        s"${sbtProject.id}/scripted" + explicitTestsFilter
          .orElse(testsFilter)
          .fold("")(" " + _) ::
        Nil

      println("Will execute following commands:")
      allCommands.foreach(println)
      allCommands ::: state
  }

  private def projectVersionCommand(
      name: String
  )(fn: (String, State) => State): Command = {
    Command.args(name, "<args>") {
      case (state, args) =>
        val version = args.headOption
          .map(CrossVersion.binaryScalaVersion)
          .orElse(state.getSetting(scalaBinaryVersion))
          .getOrElse(
            "Used command needs explicit Scala binary version as an argument"
          )

        fn(version, state)
    }
  }

  private def projectFullVersionCommand(
      name: String
  )(fn: (String, State) => State): Command = {
    Command.args(name, "<args>") {
      case (state, args) =>
        val arg = args.headOption
        val version = arg
          // Try translating 2.12, 2.13, 3 to full version string such as 3.3.1
          .flatMap(MultiScalaProject.scalaVersions.get)
          // Verify the argument full version string is supported by libCrossScalaVersions
          .orElse(
            arg.flatMap(a => ScalaVersions.libCrossScalaVersions.find(_ == a))
          )
          // Fallback to the current scalaVersion
          .orElse {
            val v = state.getSetting(scalaVersion)
            state.log.warn(
              s"${args.headOption.getOrElse("")} is not supported, fallback to ${v}"
            )
            v
          }
          .getOrElse(
            "Used command needs explicit full Scala version as an argument"
          )

        fn(version, state)
    }
  }

  lazy val publishLocalDev = {
    projectFullVersionCommand("publish-local-dev") {
      case (version, state) =>
        List(
          // Sbt plugin and its dependencies
          ScalaVersions.scala212,
          // Artifact for current version
          version
        ).distinct
          .map(v => s"++$v publishLocal") ::: state
    }
  }

  lazy val publishReleaseForVersion =
    projectVersionCommand("publish-release-for-version") {
      case (binVersion, state) =>
        val (scalaVersion, crossScalaVersions) = binVersion match {
          case "2.12" => ScalaVersions.scala212 -> ScalaVersions.crossScala212
          case "2.13" => ScalaVersions.scala213 -> ScalaVersions.crossScala213
          case "3"    =>
            ScalaVersions.scala3PublishVersion -> ScalaVersions.crossScala3
          case _ => sys.error(s"Invalid Scala binary version: '$binVersion'")
        }
        val publishCommand = "publishSigned"
        val publishBaseVersion = s"++$scalaVersion; $publishCommand"
        val publishCrossVersions = crossScalaVersions
          .diff(scalaVersion :: Nil) // exclude already published base version
          .toList
          .map { crossVersion =>
            Build.crossPublishedMultiScalaProjects
              .map(_.forBinaryVersion(binVersion))
              .map(project => s"${project.id}/$publishCommand")
              .mkString(s"++ $crossVersion; all ", " ", "")
          }
        val isSnapshotVersion =
          state.getSetting(ThisBuild / version).exists(_.endsWith("-SNAPSHOT"))
        val release = if (isSnapshotVersion) Nil else ("sonaUpload" :: Nil)
        val crossPublish = ScalaVersions
        val commandsToExecute =
          "clean" ::
            publishBaseVersion ::
            publishCrossVersions :::
            release

        println(
          s"Publish for Scala $binVersion would execute following commands:"
        )
        commandsToExecute.foreach(println)
        println("")

        commandsToExecute ::: state
    }
}
