import java.util.concurrent.atomic.AtomicBoolean

import scala.scalanative.posix.sys.wait._
import scala.scalanative.posix.unistd.{_exit, fork}
import scala.scalanative.unsafe._

/** A second mutator spins in native code, so it is Managed and not at a
 *  safepoint. fork() keeps only the calling thread. The child must still be
 *  able to collect: the other mutator is not an OS thread anymore.
 */
object ForkScenario {
  def run(): Unit = {
    assert(
      !scala.scalanative.meta.LinktimeInfo.isWindows,
      "ForkScenario is only supported on POSIX platforms"
    )
    println("[Test] Running FORK scenario")

    val spinning = new AtomicBoolean(false)
    val worker = new Thread(
      () => {
        spinning.set(true)
        ZombieThread.spinForever()
      },
      "fork-sibling"
    )
    worker.setDaemon(true)
    worker.start()

    while (!spinning.get()) Thread.sleep(10)
    Thread.sleep(50)

    val pid = fork()
    if (pid < 0) {
      println("[Test] FAIL: fork")
      System.exit(1)
    } else if (pid == 0) {
      val retained = new Array[Int](32)
      retained(0) = 1
      System.gc()
      println("[Child] GC completed after fork")
      _exit(0)
    } else {
      val status = stackalloc[CInt]()
      !status = 0
      val waited = waitpid(pid, status, 0)
      if (waited != pid || !WIFEXITED(!status) || WEXITSTATUS(!status) != 0) {
        println(
          s"[Test] FAIL: child did not exit 0 (wait=$waited status=${!status})"
        )
        System.exit(1)
      }
      println("[Test] PASS: child collected after fork")
      System.exit(0)
    }
  }
}
