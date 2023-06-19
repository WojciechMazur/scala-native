package scala.scalanative.testinterface

import java.io.File
import scala.concurrent.{Future, Promise}
import scala.scalanative.build.Logger

object Proxy {
  lazy val instance = {
    val proxyThread = new Thread(() => {
      println(
        s"starting sockets proxy on port 8888 in ${Thread.currentThread()}"
      )
      val proxy = new ProcessBuilder(
        "./lib/emsdk/upstream/emscripten/tools/websocket_to_posix_proxy/websocket_to_posix_proxy",
        "8888"
      ).inheritIO().redirectErrorStream(true).start()
    })
    proxyThread.setDaemon(true)
    proxyThread.start()
    proxyThread
  }
}

private[testinterface] object ProcessRunner {
  class Default(
      executableFile: File,
      envVars: Map[String, String],
      args: Seq[String],
      logger: Logger,
      port: Int
  ) extends ProcessRunner(executableFile, envVars, args, logger) {
    override protected val process: Process = {
      // Optional emualator config used internally for testing non amd64 architectures
      val emulatorOpts: List[String] = {
        val optEmulator =
          sys.props
            .get("scala.scalanative.testinterface.processrunner.emulator")
            .filter(_.nonEmpty)
        val optEmulatorOptions = sys.props
          .get("scala.scalanative.testinterface.processrunner.emulator-args")
          .map(_.split(";").toList)
          .getOrElse(Nil)
        optEmulator.toList ++ optEmulatorOptions
      }
      if (emulatorOpts.nonEmpty) {
        logger.info(
          s"Using test process emulator: ${emulatorOpts.mkString(" ")}"
        )
      }

      val builder = baseProcessBuilder(emulatorOpts, port)

      logger.info(s"Starting process '$executableFile' on port '$port'.")
      builder.start()
    }
  }

  class WasmtimeListener(
      executableFile: File,
      envVars: Map[String, String],
      args: Seq[String],
      logger: Logger
  ) extends ProcessRunner(executableFile, envVars, args, logger) {
    private val hostname = "localhost"
    import java.net.Socket
    val (process, socket): (Process, Socket) = tryCreate()

    Thread.sleep(500)
    println(socket)

    private def tryCreate(): (Process, Socket) = {
      val port = {
        val socket = new java.net.ServerSocket(0)
        try socket.getLocalPort()
        finally socket.close()
      }

      val endpoint = s"localhost:$port"

      val builder = baseProcessBuilder(
        Seq("wasmtime", s"--tcplisten=$endpoint"),
        port
      )
      logger.info(
        s"Starting listener process '$executableFile' on port '$port'."
      )

      val process: Process = builder.start()
      
      Thread.sleep(100)
      try {
        val socket = new java.net.Socket(hostname, port)
        process -> socket
      } catch {
        case ex: Exception => println(ex)
        tryCreate()
      }
    }
  }

}

private[testinterface] sealed abstract class ProcessRunner(
    executableFile: File,
    envVars: Map[String, String],
    args: Seq[String],
    logger: Logger
) extends AutoCloseable {
  protected def baseProcessBuilder(
      prependArgs: Seq[String],
      port: Int
  ): ProcessBuilder = {
    val builder = new ProcessBuilder(
      prependArgs ++:
        executableFile.getAbsolutePath() +:
        port.toString +:
        args: _*
    ).inheritIO()
      .redirectErrorStream(true)

    envVars.foreach {
      case (k, v) =>
        builder.environment().put(k, v)
    }
    builder
  }

  protected val process: Process

  // = {
  //   // Optional emualator config used internally for testing non amd64 architectures
  //   val emulatorOpts: List[String] = {
  //     val optEmulator =
  //       sys.props
  //         .get("scala.scalanative.testinterface.processrunner.emulator")
  //         .filter(_.nonEmpty)
  //     val optEmulatorOptions = sys.props
  //       .get("scala.scalanative.testinterface.processrunner.emulator-args")
  //       .map(_.split(";").toList)
  //       .getOrElse(Nil)
  //     optEmulator.toList ++ optEmulatorOptions
  //   }
  //   if (emulatorOpts.nonEmpty) {
  //     logger.info(s"Using test process emulator: ${emulatorOpts.mkString(" ")}")
  //   }

  //   val builder =
  //     new ProcessBuilder(
  //       "google-chrome",
  //       "--enable-features=SharedArrayBuffer",
  //       "http://localhost:3000/test-interface-test"
  //       // emulatorOpts ++:
  //       //   executableFile.getAbsolutePath() +:
  //       //   port.toString +:
  //       //   args: _*
  //     )
  //       .inheritIO()

  //   envVars.foreach {
  //     case (k, v) =>
  //       builder.environment().put(k, v)
  //   }

  //   assert(Proxy.instance.isAlive())
  //   logger.info(s"Starting process '$executableFile' on port '$port'.")
  //   builder.start()
  // }

  private[this] val runnerPromise: Promise[Unit] = Promise[Unit]()
  private[this] val runner = new Thread {
    setName("TestRunner")
    override def run(): Unit = {
      val exitCode = process.waitFor()
      if (exitCode == 0) runnerPromise.trySuccess(())
      else {
        runnerPromise.tryFailure(
          new RuntimeException(
            s"Process $executableFile finished with non-zero value $exitCode (0x${exitCode.toHexString})"
          )
        )
        // Similarly to Bash programs, exitcode values higher
        // than 128 signify program end by fatal signal
        if (exitCode > 128)
          logger.error(
            s"Test runner interrupted by fatal signal ${exitCode - 128}"
          )
      }
    }
  }
  runner.start()
  val future: Future[Unit] = runnerPromise.future

  override def close(): Unit = {
    process.destroyForcibly()
  }
}
