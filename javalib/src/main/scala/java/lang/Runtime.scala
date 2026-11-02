package java.lang

import java.io.File
import java.util.{Comparator, Set => juSet}

import scala.scalanative.libc.{errno, signal, stdlib}
import scala.scalanative.meta.LinktimeInfo
import scala.scalanative.posix.errno.EINTR
import scala.scalanative.posix.fcntl
import scala.scalanative.posix.unistd._
import scala.scalanative.runtime.javalib.Proxy
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.windows.SysInfoApi._
import scala.scalanative.windows.SysInfoApiOps._

class Runtime private () {
  import Runtime._
  @volatile private var shutdownStarted = false
  private lazy val hooks: juSet[Thread] = new java.util.HashSet()

  lazy val setupAtExitHandler = {
    stdlib.atexit(() => Runtime.getRuntime().runHooks())
  }

  // https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html
  // Currently, we use C lib signals so SIGHUP is not covered for POSIX platforms.
  lazy val setupSignalHandler = {
    // Executing handler during GC might lead to deadlock
    // Make sure include any additional signals in `Synchronizer_init` and `sigset_t signalsBlockedDuringGC` in both Immix/Commix GC
    // Warning: We cannot safetly adapt Boehm GC - it can deadlock for the same reasons as above
    startSignalDispatcher()
    signal.signal(signal.SIGINT, handleSignal(_))
    signal.signal(signal.SIGTERM, handleSignal(_))
  }

  private def ensureCanModify(hook: Thread): Unit = if (shutdownStarted) {
    throw new IllegalStateException(
      s"Shutdown sequence started, cannot add/remove hook $hook"
    )
  }

  def addShutdownHook(thread: Thread): Unit = hooks.synchronized {
    ensureCanModify(thread)
    hooks.add(thread)
    setupAtExitHandler
    setupSignalHandler
  }

  def removeShutdownHook(thread: Thread): scala.Boolean = hooks.synchronized {
    ensureCanModify(thread)
    hooks.remove(thread)
  }

  private def runHooksConcurrent() = {
    val hooks = this.hooks
      .toArray()
      .asInstanceOf[Array[Thread]]
      .sorted(Ordering.by[Thread, Int](-_.getPriority()))
    hooks.foreach { t =>
      t.setUncaughtExceptionHandler(ShutdownHookUncaughtExceptionHandler)
    }
    // JDK specifies that hooks might run in any order.
    // However, for Scala Native it might be beneficial to support partial ordering
    // E.g. Zone/MemoryPool shutdownHook cleaning pools should be run after DeleteOnExit using `toCString`
    // Group the hooks by priority starting with the ones with the highest priority
    val limit = hooks.size
    var idx = 0
    while (idx < limit) {
      val groupStart = idx
      val groupPriority = hooks(groupStart).getPriority()
      while (idx < limit && hooks(idx).getPriority() == groupPriority) {
        hooks(idx).startInternal()
        idx += 1
      }
      for (i <- groupStart until limit) {
        hooks(i).join()
      }
    }
  }
  private def runHooksSequential() = {
    this.hooks
      .toArray()
      .asInstanceOf[Array[Thread]]
      .sorted(Ordering.by[Thread, Int](-_.getPriority()))
      .foreach { t =>
        try t.run()
        catch {
          case ex: Throwable =>
            ShutdownHookUncaughtExceptionHandler.uncaughtException(t, ex)
        }
      }
  }
  private def runHooks() = {
    import scala.scalanative.meta.LinktimeInfo.isMultithreadingEnabled
    hooks.synchronized {
      if (!shutdownStarted) {
        shutdownStarted = true
        if (isMultithreadingEnabled) runHooksConcurrent()
        else runHooksSequential()
      }
    }
  }

  /** Return the positive number of logical processors on which the process may
   *  run. At least 1 is returned.
   *
   *  On Linux, there are a number of ways (taskset, cpuset, etc.) to set the
   *  number less than sysconf(_SC_NPROCESSORS_ONLN). The underlying C code
   *  currently (2024) will return -1, if there are more than 1024 logical
   *  processors in the cpuset.
   *
   *  Windows also documents some conditions which may lower the number of
   *  available processors.
   *
   *  macOS is culturally adverse to an application lowering the number of
   *  processors below sysctl "hw.logicalcpu". There appear to be ways to
   *  accomplish such a reduction, but they are not programmatic.
   *
   *  FreeBSD and NetBSD use the _SC_NPROCESSORS_ONLN path in this code. Someday
   *  they could use os specific code to get finer granularity. FreeBSD has
   *  "cpuset_getaffinity". NetBSD has sched_getaffinity_np. Implementations for
   *  these operating systems are left as an exercise for the reader.
   */

  import Runtime.ProcessBuilderOps
  def availableProcessors(): Int = {
    val available = if (LinktimeInfo.isWindows) {
      val sysInfo = stackalloc[SystemInfo]()
      GetSystemInfo(sysInfo)
      sysInfo.numberOfProcessors.toInt
    } else {
      val nLogicalCPUs =
        if (LinktimeInfo.isLinux)
          RuntimeLinuxOsSpecific.sched_cpuset_cardinality()
        else -1

      if (nLogicalCPUs > 0) nLogicalCPUs
      else sysconf(_SC_NPROCESSORS_ONLN).toInt
    }

    // By contract returned value cannot be lower then 1
    available max 1
  }

  def exit(status: Int): Unit = stdlib.exit(status)
  def gc(): Unit = System.gc()

  def exec(cmdarray: Array[String]): Process =
    new ProcessBuilder(cmdarray).start()
  def exec(cmdarray: Array[String], envp: Array[String]): Process =
    new ProcessBuilder(cmdarray).setEnv(envp).start()
  def exec(cmdarray: Array[String], envp: Array[String], dir: File): Process =
    new ProcessBuilder(cmdarray).setEnv(envp).directory(dir).start()
  def exec(cmd: String): Process = exec(Array(cmd))
  def exec(cmd: String, envp: Array[String]): Process = exec(Array(cmd), envp)
  def exec(cmd: String, envp: Array[String], dir: File): Process =
    exec(Array(cmd), envp, dir)
}

private object ShutdownHookUncaughtExceptionHandler
    extends Thread.UncaughtExceptionHandler {
  def uncaughtException(t: Thread, e: Throwable): Unit = {
    System.err.println(s"Shutdown hook $t failed, reason: $e")
    t.getThreadGroup().uncaughtException(t, e)
  }
}

object Runtime extends Runtime() {
  def getRuntime(): Runtime = this

  @extern private object SignalPipe {
    // unistd.write is @blocking, its GC state transition is not signal safe
    @name("write")
    def write(fildes: CInt, buf: CVoidPtr, nbyte: CSize): CInt = extern
  }

  private var signalPipeWriteEnd: CInt = -1

  /* Hooks start and join threads, which must not happen in a signal handler:
   * it can interrupt the thread inside of park or while holding any lock. As
   * in the JVM, the handler only passes the signal to a dedicated thread.
   */
  private def startSignalDispatcher(): Unit =
    if (LinktimeInfo.isMultithreadingEnabled && !LinktimeInfo.isWindows) {
      val fds = stackalloc[CInt](2)
      if (pipe(fds) == 0) {
        val readEnd = fds(0)
        fcntl.fcntl(readEnd, fcntl.F_SETFD, fcntl.FD_CLOEXEC)
        fcntl.fcntl(fds(1), fcntl.F_SETFD, fcntl.FD_CLOEXEC)
        signalPipeWriteEnd = fds(1)
        val dispatcher = new Thread(
          new Runnable { def run(): Unit = dispatchSignals(readEnd) },
          "Signal Dispatcher"
        )
        dispatcher.setDaemon(true)
        dispatcher.startInternal()
      }
    }

  private def dispatchSignals(readEnd: CInt): Unit = {
    val sig = stackalloc[scala.Byte]()
    var bytesRead = 0
    while ({
      bytesRead = read(readEnd, sig, 1.toCSize)
      bytesRead < 0 && errno.errno == EINTR
    }) ()
    if (bytesRead == 1) runHooksAndExit((!sig).toInt)
  }

  private def runHooksAndExit(sig: CInt): Unit = {
    Runtime.getRuntime().runHooks()
    exit(128 + sig)
  }

  private def handleSignal(sig: CInt): Unit =
    if (signalPipeWriteEnd < 0) {
      Proxy.disableGracefullShutdown()
      runHooksAndExit(sig)
    } else {
      val savedErrno = errno.errno
      val buf = stackalloc[scala.Byte]()
      !buf = sig.toByte
      SignalPipe.write(signalPipeWriteEnd, buf, 1.toCSize)
      errno.errno = savedErrno
    }

  private implicit class ProcessBuilderOps(val pb: ProcessBuilder)
      extends AnyVal {
    def setEnv(envp: Array[String]): ProcessBuilder = {
      val env = pb.environment()
      env.clear()
      envp match {
        case null =>
          env.putAll(System.getenv())
        case a =>
          envp.foreach {
            case null =>
            case a    =>
              a.split("=") match {
                case Array(k, v) => env.put(k, v)
                case _           =>
              }
          }
      }
      pb
    }
  }
}
