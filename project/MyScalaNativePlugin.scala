package build

import sbt._
import sbt.Keys._

import scala.scalanative.sbtplugin.ScalaNativePlugin
import scala.scalanative.sbtplugin.ScalaNativePlugin.autoImport._

object MyScalaNativePlugin extends AutoPlugin {
  override def requires: Plugins = ScalaNativePlugin

  override def projectSettings: Seq[Setting[_]] = Def.settings(
    /* Remove libraryDependencies on ourselves; we use .dependsOn() instead
     * inside this build.
     */
    libraryDependencies ~= { libDeps =>
      libDeps.filterNot(_.organization == "org.scala-native")
    },
    nativeConfig := Def.taskDyn {
      Def.task {
        val prev = nativeConfig.value
        import scala.scalanative.build._
        val commonConfig = prev
          .withLTO(LTO.default)
          .withMode(Mode.default)
          .withGC(GC.none)

        val usingWASI = {
          val wasiVersion = 19
          val wasiToolchain =
            java.nio.file.Path.of(s"lib/wasi-sdk-$wasiVersion.0").toAbsolutePath()
          val wasiSharedOpts = Seq(
            s"--sysroot=${wasiToolchain}/share/wasi-sysroot"
          )
          val wasiCompileOpts = wasiSharedOpts ++ Seq(
            "-D_WASI_EMULATED_MMAN",
            "-D_WASI_EMULATED_PROCESS_CLOCKS",
            "-D_WASI_EMULATED_SIGNAL"
          )
          val wasiLinkOpts = wasiSharedOpts ++ Seq(
            "-lwasi-emulated-mman",
            "-lwasi-emulated-process-clocks",
            "-lwasi-emulated-signal"
          )
          commonConfig
            .withTargetTriple("wasm32-unknown-wasi")
            .withClang(wasiToolchain.resolve("bin").resolve("clang"))
            .withClangPP(wasiToolchain.resolve("bin").resolve("clang++"))
            .withCompileOptions(wasiCompileOpts)
            .withLinkingOptions(wasiLinkOpts)
        }

        val usingEnscripten = {
          val emscriptenToolchain = java.nio.file.Path
            .of("lib/emsdk/upstream/emscripten")
            .toAbsolutePath()
          val emscriptenCommonOpts = Seq(
            "-g",
            "-sUSE_PTHREADS",
            "-sNO_DISABLE_EXCEPTION_CATCHING"
          )
          val emscriptenCompileOpts = emscriptenCommonOpts ++ Seq(
          )
          val emscriptenLinkOpts = emscriptenCommonOpts ++ Seq(
            "-sALLOW_MEMORY_GROWTH",
            "-sSAFE_HEAP=1",
            "-sASSERTIONS=1",
            "-sSTACK_OVERFLOW_CHECK=1",
            "-error-limit=0",
            "-lwebsocket.js",
            "-sPROXY_POSIX_SOCKETS",
            "-ssUSE_PTHREADS",
            "-sPROXY_TO_PTHREAD",
            "-sSOCKET_DEBUG",
            "-sWEBSOCKET_DEBUG",
            "-sEXIT_RUNTIME=1",
            "-o",
            s"../${Option(prev.basename).filter(_.nonEmpty).getOrElse(moduleName.value)}-test.html"
          )
          commonConfig
            .withTargetTriple("wasm32-unknown-emscripten")
            .withClang(emscriptenToolchain.resolve("emcc"))
            .withClangPP(emscriptenToolchain.resolve("em++"))
            .withCompileOptions(emscriptenCompileOpts)
            .withLinkingOptions(emscriptenLinkOpts)
        }

        // Choose ABI and run sandboxX/clean when switching
        // For Scala3 .wasm (and .js, .html for enscripten) file can be found in ./sandbox/.3/target/scala-3.1.3/sandbox.wasm
        // usingWASI
        usingEnscripten
      }
    }.value,
    run := {
      import sbt._
      import java.lang.ProcessBuilder
      import sbt.complete.DefaultParsers._
      import scala.sys.process.Process

      val env = (run / envVars).value.toSeq
      val logger = streams.value.log
      val binary = (Compile / nativeLink).value.toPath

      // Empty config just to get access to helper target triple methods
      val config = (Compile / nativeConfig).value
      val targetsWASM = config.targetTriple.exists(_ contains "wasm")
      val targetsEmscripten =
        targetsWASM && config.targetTriple.exists(_ contains "emscripten")
      
      assert(targetsWASM, "not a WASM setup")

      val startupCmd: Seq[String] = if (targetsEmscripten) {
        val node = sys.env.get("EMSDK_NODE").getOrElse("node")
        val jsBootstrapName =
          binary.toFile.getName().stripSuffix(".wasm") + ".js"
        val bootstrapper =
          binary.resolveSibling(jsBootstrapName).toAbsolutePath().toString()
        Seq(
          node,
          "--experimental-wasm-threads",
          "--experimental-wasm-bulk-memory",
          bootstrapper
        )
      } else {
        val wasmtime = sys.env
          .get("WASMTIME_HOME")
          .map(_.stripSuffix("/") + "/bin/wasmtime")
          .getOrElse("wasmtime")
        Seq(wasmtime, binary.toAbsolutePath().toString())
      }

      val args = spaceDelimited("<arg>").parsed
      val cmd = startupCmd ++ args
      // logger.running(cmd)
      println(s"Running: ${cmd.mkString("\n\t")}")

      val exitCode = {
        // It seems that previously used Scala Process has some bug leading
        // to possible ignoring of inherited IO and termination of wrapper
        // thread with an exception. We use java.lang ProcessBuilder instead
        val proc = new ProcessBuilder()
          .command(cmd: _*)
          .inheritIO()
        env.foreach((proc.environment().put(_, _)).tupled)
        proc.start().waitFor()
      }

      val message =
        if (exitCode == 0) None
        else Some("Nonzero exit code: " + exitCode)

      message.foreach(sys.error)
    }
  )
}
