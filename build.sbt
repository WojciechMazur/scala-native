import build.Build

// scalafmt: { align.preset = most}
lazy val scalaNative              = Build.root
lazy val nscPlugin                = Build.nscPlugin
lazy val junitPlugin              = Build.junitPlugin
lazy val sbtScalaNative           = Build.sbtScalaNative
lazy val nir                      = Build.nir
lazy val util                     = Build.util
lazy val tools                    = Build.tools
lazy val nativelib                = Build.nativelib
lazy val clib                     = Build.clib
lazy val posixlib                 = Build.posixlib
lazy val windowslib               = Build.windowslib
lazy val javalib                  = Build.javalib
lazy val javalibExtDummies        = Build.javalibExtDummies
lazy val auxlib                   = Build.auxlib
lazy val scalalib                 = Build.scalalib
lazy val testingCompilerInterface = Build.testingCompilerInterface
lazy val testingCompiler          = Build.testingCompiler
lazy val testInterface            = Build.testInterface
lazy val testInterfaceSbtDefs     = Build.testInterfaceSbtDefs
lazy val testRunner               = Build.testRunner
lazy val junitRuntime             = Build.junitRuntime
lazy val junitTestOutputsNative   = Build.junitTestOutputsNative
lazy val junitTestOutputsJVM      = Build.junitTestOutputsJVM
lazy val junitAsyncNative         = Build.junitAsyncNative
lazy val junitAsyncJVM            = Build.junitAsyncJVM
lazy val tests                    = Build.tests
lazy val testsJVM                 = Build.testsJVM
lazy val testsExt                 = Build.testsExt
lazy val testsExtJVM              = Build.testsExtJVM
lazy val sandbox                  = Build.sandbox
lazy val scalaPartest             = Build.scalaPartest
lazy val scalaPartestTests        = Build.scalaPartestTests
lazy val scalaPartestRuntime      = Build.scalaPartestRuntime

addCommandAlias(
  "test-all",
  Seq(
    "test-tools",
    "test-runtime",
    "test-scripted",
    "test-mima"
  ).mkString(";")
)

addCommandAlias(
  "test-tools",
  Seq(
    "testRunner/test",
    "testInterface/test",
    "tools/test",
    "test-mima"
  ).mkString(";")
)

addCommandAlias(
  "test-runtime",
  Seq(
    "sandbox/run",
    "tests/test",
    "testsJVM/test",
    "testsExt/test",
    "testsExtJVM/test",
    "junitTestOutputsJVM/test",
    "junitTestOutputsNative/test",
    "scalaPartestJunitTests/test"
  ).mkString(";")
)

addCommandAlias(
  "test-mima", {
    Seq("util", "nir", "tools") ++
      Seq("testRunner", "testInterface", "testInterfaceSbtDefs") ++
      Seq("junitRuntime") ++
      Seq("nativelib", "clib", "posixlib", "windowslib") ++
      Seq("auxlib", "javalib", "scalalib")
  }.map(_ + "/mimaReportBinaryIssues")
    .mkString(";")
)

addCommandAlias(
  "test-scripted",
  Seq(
    "sbtScalaNative/scripted"
  ).mkString(";")
)
