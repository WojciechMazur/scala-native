package build

import sbt._
import sbt.Keys._
import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport._
import scala.scalanative.sbtplugin.ScalaNativePlugin.autoImport._
import sbtbuildinfo.BuildInfoPlugin.autoImport._
import ScriptedPlugin.autoImport._

import scala.collection.mutable

object Settings {
  val fetchScalaSource =
    taskKey[File]("Fetches the scala source for the current scala version")

  lazy val shouldPartest = settingKey[Boolean](
    "Whether we should partest the current scala version (or skip if we can't)"
  )

  // Generate project name from project id.
  def projectName(project: sbt.ResolvedProject): String = {
    project.id
      .replaceAll(
        "([a-z])([A-Z]+)",
        "$1-$2"
      ) // Convert "SomeName" to "some-name".
      .toLowerCase
  }

  lazy val commonSettings = Def.settings(
    organization := "org.scala-native",
    version := nativeVersion,
    scalacOptions ++= Seq(
      "-deprecation",
      "-unchecked",
      "-feature",
      "-Xfatal-warnings",
      "-encoding",
      "utf8"
    ),
    nameSettings,
    publishSettings,
    mimaSettings
  )

  // Provide consistent project name pattern.
  lazy val nameSettings = Def.settings(
    name := projectName(thisProject.value) // Maven <name>
  )

  // Docs and API settings
  lazy val docsSettings: Seq[Setting[_]] = {
    val javaDocBaseURL: String = "https://docs.oracle.com/javase/8/docs/api/"
    // partially ported from Scala.js
    Def.settings(
      autoAPIMappings := true,
      exportJars := true, // required so ScalaDoc linking works
      Compile / doc / scalacOptions := {
        val prev = (Compile / doc / scalacOptions).value
        if (scalaVersion.value.startsWith("2.11."))
          prev.filter(_ != "-Xfatal-warnings")
        else prev
      },
      // Add Java Scaladoc mapping
      apiMappings ++= {
        val optRTJar = {
          val bootClasspath = System.getProperty("sun.boot.class.path")
          if (bootClasspath != null) {
            // JDK <= 8, there is an rt.jar (or classes.jar) on the boot classpath
            val jars = bootClasspath.split(java.io.File.pathSeparator)

            def matches(path: String, name: String): Boolean =
              path.endsWith(s"${java.io.File.separator}$name.jar")

            jars
              .find(matches(_, "rt")) // most JREs
              .map(file)
          } else {
            // JDK >= 9, maybe sbt gives us a fake rt.jar in `scala.ext.dirs`
            val scalaExtDirs = Option(System.getProperty("scala.ext.dirs"))
            scalaExtDirs.map(extDirs => file(extDirs) / "rt.jar")
          }
        }

        optRTJar.fold[Map[File, URL]] {
          Map.empty
        } { rtJar =>
          assert(rtJar.exists, s"$rtJar does not exist")
          Map(rtJar -> url(javaDocBaseURL))
        }
      },
      /* Add a second Java Scaladoc mapping for cases where Scala actually
       * understands the jrt:/ filesystem of Java 9.
       */
      apiMappings += file("/modules/java.base") -> url(javaDocBaseURL)
    )
  }

  lazy val disabledDocsSettings = Def.settings(
    Compile / doc / sources := Nil
  )

  // MiMa
  lazy val mimaSettings = {
    // The previous releases of Scala Native with which this version is binary compatible.
    val binCompatVersions = Set("0.4.0")
    // val neverPublishedProjects = Map(
    //   "2.11" -> Set(util, tools, nir, windowslib, testRunner),
    //   "2.12" -> Set(windowslib),
    //   "2.13" -> Set(util, tools, nir, windowslib, testRunner)
    // ).mapValues(_.map(_.id))

    Def.settings(
      mimaFailOnNoPrevious := false,
      mimaBinaryIssueFilters ++= BinaryIncompatibilities.moduleFilters(
        name.value
      )
      // mimaPreviousArtifacts ++= {
      //   val wasPreviouslyPublished = neverPublishedProjects
      //     .get(scalaBinaryVersion.value)
      //     .exists(!_.contains(thisProject.value.id))
      //   binCompatVersions
      //     .filter(_ => wasPreviouslyPublished)
      //     .map { version =>
      //       ModuleID(organization.value, moduleName.value, version)
      //         .cross(crossVersion.value)
      //     }
      // }
    )
  }

  // Publishing
  lazy val publishSettings: Seq[Setting[_]] = Seq(
    homepage := Some(url("http://www.scala-native.org")),
    startYear := Some(2015),
    licenses := Seq(
      "BSD-like" -> url("http://www.scala-lang.org/downloads/license.html")
    ),
    developers += Developer(
      email = "denys.shabalin@epfl.ch",
      id = "densh",
      name = "Denys Shabalin",
      url = url("http://den.sh")
    ),
    scmInfo := Some(
      ScmInfo(
        browseUrl = url("https://github.com/scala-native/scala-native"),
        connection = "scm:git:git@github.com:scala-native/scala-native.git"
      )
    ),
    pomExtra := (
      <issueManagement>
        <system>GitHub Issues</system>
        <url>https://github.com/scala-native/scala-native/issues</url>
      </issueManagement>
    ),
    Compile / publishArtifact := true,
    Test / publishArtifact := false
  ) ++ nameSettings ++ mimaSettings

  lazy val mavenPublishSettings = Def.settings(
    publishSettings,
    publishMavenStyle := true,
    pomIncludeRepository := (_ => false),
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    credentials ++= {
      for {
        realm <- sys.env.get("MAVEN_REALM")
        domain <- sys.env.get("MAVEN_DOMAIN")
        user <- sys.env.get("MAVEN_USER")
        password <- sys.env.get("MAVEN_PASSWORD")
      } yield Credentials(realm, domain, user, password)
    }.toSeq
  )

  lazy val noPublishSettings = Def.settings(
    nameSettings,
    disabledDocsSettings,
    publishArtifact := false,
    packagedArtifacts := Map.empty,
    publish := {},
    publishLocal := {},
    publish / skip := true
  )

  // Build Info
  lazy val buildInfoJVMSettings = Def.settings(
    buildInfoPackage := "scala.scalanative.buildinfo",
    buildInfoObject := "ScalaNativeBuildInfo",
    buildInfoKeys := Seq[BuildInfoKey](
      version,
      sbtVersion,
      scalaVersion
    )
  )

  lazy val buildInfoSettings = Def.settings(
    buildInfoJVMSettings,
    buildInfoKeys +=
      "nativeScalaVersion" -> scalaVersion.value
  )

  // Tests
  lazy val testsCommonSettings = Def.settings(
    commonSettings,
    scalacOptions -= "-deprecation",
    scalacOptions ++= Seq("-deprecation:false"),
    Test / testOptions ++= Seq(
      Tests.Argument(TestFrameworks.JUnit, "-a", "-s", "-v")
    ),
    Test / envVars ++= Map(
      "USER" -> "scala-native",
      "HOME" -> System.getProperty("user.home"),
      "SCALA_NATIVE_ENV_WITH_EQUALS" -> "1+1=2",
      "SCALA_NATIVE_ENV_WITHOUT_VALUE" -> "",
      "SCALA_NATIVE_ENV_WITH_UNICODE" -> 0x2192.toChar.toString,
      "SCALA_NATIVE_USER_DIR" -> System.getProperty("user.dir")
    ),
    // Some of the tests are designed with an assumptions about default encoding
    // Make sure that tests run on JVM are using default defaults
    Test / javaOptions ++= Seq(
      "-Dfile.encoding=UTF-8" // Windows uses Cp1250 as default
    )
  )

  lazy val testsExtCommonSettings = Def.settings(
    commonSettings,
    Test / testOptions ++= Seq(
      Tests.Argument(TestFrameworks.JUnit, "-a", "-s", "-v")
    )
  )

  lazy val disabledTestsSettings = {
    def testsTaskUnsupported[T] = Def.task[T] {
      throw new MessageOnlyException(
        s"""Usage of this task in ${projectName(thisProject.value)} project is not supported in this build.
             |To run tests use explicit syntax containing name of project: <project_name>/<task>.
             |You can also use one of predefined aliases: test-all, test-tools, test-runtime, test-scripted.
             |""".stripMargin
      )
    }

    Def.settings(
      commonSettings,
      inConfig(Test) {
        Seq(
          test / aggregate := false,
          test := testsTaskUnsupported.value,
          testOnly / aggregate := false,
          testOnly := testsTaskUnsupported.value,
          testQuick / aggregate := false,
          testQuick := testsTaskUnsupported.value,
          executeTests / aggregate := false,
          executeTests := testsTaskUnsupported[Tests.Output].value
        )
      }
    )
  }

  // Get all blacklisted tests from a file
  def blacklistedFromFile(file: File) =
    IO.readLines(file)
      .filter(l => l.nonEmpty && !l.startsWith("#"))
      .toSet

  // Get all scala sources from a directory
  def allScalaFromDir(dir: File): Seq[(String, java.io.File)] =
    (dir ** "*.scala").get.flatMap { file =>
      file.relativeTo(dir) match {
        case Some(rel) => List((rel.toString.replace('\\', '/'), file))
        case None      => Nil
      }
    }

  // Check the coherence of the blacklist against the files found.
  def checkBlacklistCoherency(
      blacklist: Set[String],
      sources: Seq[(String, File)]
  ) = {
    val allClasses = sources.map(_._1).toSet
    val nonexistentBlacklisted = blacklist.diff(allClasses)
    if (nonexistentBlacklisted.nonEmpty) {
      throw new AssertionError(
        s"Sources not found for blacklisted tests:\n$nonexistentBlacklisted"
      )
    }
  }

  def sharedTestSource(withBlacklist: Boolean) = Def.settings(
    Test / unmanagedSources ++= {
      val blacklist: Set[String] =
        if (withBlacklist)
          blacklistedFromFile(
            (Test / resourceDirectory).value / "BlacklistedTests.txt"
          )
        else Set.empty

      val sharedSources = allScalaFromDir(
        baseDirectory.value.getParentFile / "shared/src/test"
      )

      checkBlacklistCoherency(blacklist, sharedSources)

      sharedSources.collect {
        case (path, file) if !blacklist.contains(path) => file
      }
    }
  )

  lazy val testInterfaceCommonSourcesSettings: Seq[Setting[_]] = Seq(
    Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "test-interface-common/src/main/scala",
    Test / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "test-interface-common/src/test/scala"
  )

  // Projects
  lazy val compilerPluginSettings = Def.settings(
    crossVersion := CrossVersion.full,
    libraryDependencies ++= Deps.scalaCompilerDependency(scalaVersion.value),
    mavenPublishSettings,
    exportJars := true
  )

  lazy val sbtPluginSettings = Def.settings(
    toolSettings,
    mavenPublishSettings,
    sbtVersion := ScalaVersions.sbt10Version,
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq(
          "-Xmx1024M",
          "-XX:MaxMetaspaceSize=256M",
          "-Dplugin.version=" + version.value,
          "-Dscala.version=" + scalaVersion.value,
          "-Dfile.encoding=UTF-8" // Windows uses Cp1250 as default
        ) ++
        ivyPaths.value.ivyHome.map(home => s"-Dsbt.ivy.home=$home").toSeq
    }
  )

  lazy val toolSettings: Seq[Setting[_]] =
    Def.settings(
      commonSettings,
      javacOptions ++= Seq("-encoding", "utf8")
    )

  lazy val commonJavalibSettings = Def.settings(
    commonSettings,
    disabledDocsSettings,
    // This is required to have incremental compilation to work in javalib.
    // We put our classes on scalac's `javabootclasspath` so that it uses them
    // when compiling rather than the definitions from the JDK.
    Compile / scalacOptions := {
      val previous = (Compile / scalacOptions).value
      val javaBootClasspath =
        scala.tools.util.PathResolver.Environment.javaBootClassPath
      val classDir = (Compile / classDirectory).value.getAbsolutePath
      val separator = sys.props("path.separator")
      "-javabootclasspath" +: s"$classDir$separator$javaBootClasspath" +: previous
    },
    // Don't include classfiles for javalib in the packaged jar.
    Compile / packageBin / mappings := {
      val previous = (Compile / packageBin / mappings).value
      previous.filter {
        case (_, path) =>
          !path.endsWith(".class")
      }
    },
    exportJars := true
  )

  def commonScalalibSettings(
      libraryName: String,
      scalalibCrossVersions: Seq[String]
  ): Seq[Setting[_]] =
    Def.settings(
      commonSettings,
      mavenPublishSettings,
      disabledDocsSettings,
      // Code to fetch scala sources adapted, with gratitude, from
      // Scala.js Build.scala at the suggestion of @sjrd.
      // https://github.com/scala-js/scala-js/blob/\
      //    1761f94ee31902b61c579d5cb121117c9dc08295/\
      //    project/Build.scala#L1125-L1233
      //
      // By intent, the Scala Native code below is as identical as feasible.
      // Scala Native build.sbt uses a slightly different baseDirectory
      // than Scala.js. See commented starting with "SN Port:" below.

      // `update/skip` was used instead of `update` task due to its
      // depenendenies triggering update execution and leading to failure
      // when wrong Scala version is used
      update / skip := {
        val version = scalaVersion.value
        if (!scalalibCrossVersions.contains(version)) {
          throw new Exception(
            s"Cannot use ${name.value} project with uncompattible Scala version ${version}"
          )
        }
        (update / skip).value
      },
      libraryDependencies += "org.scala-lang" % libraryName % scalaVersion.value classifier "sources",
      fetchScalaSource / artifactPath :=
        target.value / "scalaSources" / scalaVersion.value,
      // Scala.js original comment modified to clarify issue is Scala.js.
      /* Work around for https://github.com/scala-js/scala-js/issues/2649
       * We would like to always use `update`, but
       * that fails if the scalaVersion we're looking for happens to be the
       * version of Scala used by sbt itself. This is clearly a bug in sbt,
       * which we work around here by using `updateClassifiers` instead in
       * that case.
       */
      fetchScalaSource / update := Def.taskDyn {
        val version = scalaVersion.value
        val usedScalaVersion = scala.util.Properties.versionNumberString
        if (version == usedScalaVersion) updateClassifiers
        else update
      }.value,
      fetchScalaSource := {
        val version = scalaVersion.value
        if (!scalalibCrossVersions.contains(version)) {
          throw new Exception(
            s"Cannot compile ${name.value} project with uncompattible Scala version ${version}"
          )
        }
        val trgDir = (fetchScalaSource / artifactPath).value
        val s = streams.value
        val cacheDir = s.cacheDirectory
        val report = (fetchScalaSource / update).value
        val scalaLibSourcesJar = report
          .select(
            configuration = configurationFilter("compile"),
            module = moduleFilter(name = libraryName),
            artifact = artifactFilter(classifier = "sources")
          )
          .headOption
          .getOrElse {
            throw new Exception(
              s"Could not fetch $libraryName sources for version $version"
            )
          }

        FileFunction.cached(
          cacheDir / s"fetchScalaSource-$version",
          FilesInfo.lastModified,
          FilesInfo.exists
        ) { dependencies =>
          s.log.info(s"Unpacking Scala library sources to $trgDir...")

          if (trgDir.exists)
            IO.delete(trgDir)
          IO.createDirectory(trgDir)
          IO.unzip(scalaLibSourcesJar, trgDir)
        }(Set(scalaLibSourcesJar))
        trgDir
      },
      Compile / unmanagedSourceDirectories := {
        // Calculates all prefixes of the current Scala version
        // (including the empty prefix) to construct override
        // directories like the following:
        // - override-2.13.0-RC1
        // - override-2.13.0
        // - override-2.13
        // - override-2
        // - override

        val ver = scalaVersion.value

        // SN Port: sjs uses baseDirectory.value.getParentFile here.
        val base = baseDirectory.value
        val parts = ver.split(Array('.', '-'))
        val verList = parts.inits.map { ps =>
          val len = ps.mkString(".").length
          // re-read version, since we lost '.' and '-'
          ver.substring(0, len)
        }
        def dirStr(v: String) =
          if (v.isEmpty) "overrides" else s"overrides-$v"
        val dirs = verList.map(base / dirStr(_)).filter(_.exists)
        dirs.toSeq // most specific shadow less specific
      },
      // Compute sources
      // Files in earlier src dirs shadow files in later dirs
      Compile / sources := {
        // Sources coming from the sources of Scala
        val scalaSrcDir = fetchScalaSource.value

        // All source directories (overrides shadow scalaSrcDir)
        val sourceDirectories =
          (Compile / unmanagedSourceDirectories).value :+ scalaSrcDir

        // Filter sources with overrides
        def normPath(f: File): String =
          f.getPath.replace(java.io.File.separator, "/")

        val sources = mutable.ListBuffer.empty[File]
        val paths = mutable.Set.empty[String]

        val s = streams.value

        for {
          srcDir <- sourceDirectories
          normSrcDir = normPath(srcDir)
          src <- (srcDir ** "*.scala").get
        } {
          val normSrc = normPath(src)
          val path = normSrc.substring(normSrcDir.length)
          val useless =
            path.contains("/scala/collection/parallel/") ||
              path.contains("/scala/util/parsing/")
          if (!useless) {
            if (paths.add(path))
              sources += src
            else
              s.log.debug(s"not including $src")
          }
        }

        sources.result()
      },
      // Don't include classfiles/tasty for scalalib in the packaged jar.
      Compile / packageBin / mappings := {
        val previous = (Compile / packageBin / mappings).value
        val ignoredExtensions = Set(".class", ".tasty")
        previous.filter {
          case (file, path) => !ignoredExtensions.exists(path.endsWith)
        }
      },
      // Sources in scalalib are only internal overrides, we don't include them in the resulting sources jar
      Compile / packageSrc / mappings := Seq.empty,
      exportJars := true
    )

  lazy val commonJUnitTestOutputsSettings = Def.settings(
    nameSettings,
    noPublishSettings,
    Compile / publishArtifact := false,
    Test / parallelExecution := false,
    Test / unmanagedSourceDirectories +=
      baseDirectory.value.getParentFile / "shared/src/test/scala",
    Test / testOptions ++= Seq(
      Tests.Argument(TestFrameworks.JUnit, "-a", "-s", "-v"),
      Tests.Filter(_.endsWith("Assertions"))
    ),
    Test / scalacOptions --= Seq("-deprecation", "-Xfatal-warnings"),
    Test / scalacOptions += "-deprecation:false"
  )

// Partests
  def shouldPartestSetting: Seq[Def.Setting[_]] = {
    Def.settings(
      shouldPartest := {
        baseDirectory.value.getParentFile / "scala-partest-tests" / "src" / "test" / "resources" /
          "scala" / "tools" / "partest" / "scalanative" / scalaVersion.value
      }.exists()
    )
  }

// Compat
  lazy val scala3CompatSettings = Def.settings(
    scalacOptions := {
      val prev = scalacOptions.value
      scalaVersionsDependendent(scalaVersion.value)(prev) {
        case (3, 0) =>
          prev.map {
            case "-target:jvm-1.8" => "-Xtarget:8"
            case v                 => v
          }
      }
    }
  )

  def scalaVersionsDependendent[T](scalaVersion: String)(default: T)(
      matching: PartialFunction[(Long, Long), T]
  ): T =
    CrossVersion
      .partialVersion(scalaVersion)
      .collect(matching)
      .getOrElse(default)
}
