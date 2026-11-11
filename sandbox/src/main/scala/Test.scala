import java.util.concurrent._
import java.util.concurrent.atomic._

// Stress reproducer for the Windows `Test runtime` hang: a mutator that
// yields for a GC can miss the resume notification and stay blocked until
// the next collection. Workers allocate in rounds and then go idle, so a
// missed resume leaves the round unfinished (no further GC releases it).
//
// Usage: Test [rounds] [workers] [roundTimeoutMs]
object Test {
  @volatile var sink: AnyRef = null

  def main(args: Array[String]): Unit = {
    val cpus = Runtime.getRuntime().availableProcessors()
    val rounds = if (args.length > 0) args(0).toInt else 20000
    val workers = if (args.length > 1) args(1).toInt else cpus * 4
    val timeoutMs = if (args.length > 2) args(2).toLong else 10000L
    println(s"cpus=$cpus workers=$workers rounds=$rounds timeoutMs=$timeoutMs")

    val start = Array.fill(workers)(new Semaphore(0))
    val done = new Semaphore(0)
    val roundOf = new AtomicIntegerArray(workers)
    val inBurst = new AtomicIntegerArray(workers)
    var w = 0
    while (w < workers) {
      val id = w
      val t = new Thread(() => {
        val live = new Array[AnyRef](64)
        var r = 0
        while (true) {
          start(id).acquire()
          inBurst.set(id, 1)
          val rnd = ThreadLocalRandom.current()
          var i = 0
          while (i < 2000) {
            live(rnd.nextInt(live.length)) =
              new Array[Byte](16 + rnd.nextInt(512))
            i += 1
          }
          r += 1
          roundOf.set(id, r)
          inBurst.set(id, 0)
          done.release()
        }
      })
      t.setDaemon(true)
      t.start()
      w += 1
    }

    val t0 = System.nanoTime()
    var round = 1
    var hangs = 0
    while (round <= rounds) {
      var i = 0
      while (i < workers) { start(i).release(); i += 1 }
      if (!done.tryAcquire(workers, timeoutMs, TimeUnit.MILLISECONDS)) {
        hangs += 1
        val stuck = (0 until workers).filter(i => roundOf.get(i) < round)
        val stuckInBurst = stuck.filter(i => inBurst.get(i) == 1)
        System.err.println(
          s"HANG round=$round: ${stuck.size} worker(s) did not finish: ${stuck.mkString(",")} (inBurst=${stuckInBurst.mkString(",")})"
        )
        // Only a new collection resumes a mutator that missed the last
        // resume notification: force some.
        var k = 0
        while (k < 3) { System.gc(); k += 1 }
        // tryAcquire(n) takes nothing on timeout: wait for the whole round
        val released = done.tryAcquire(workers, 5000, TimeUnit.MILLISECONDS)
        System.err.println(
          s"  after System.gc(): released=$released, still stuck=${(0 until workers).filter(i => roundOf.get(i) < round).mkString(",")}"
        )
        System.err.flush()
        if (!released) {
          System.err.println("  not released by GC; exiting")
          sys.exit(3)
        }
      }
      if (round % 500 == 0) {
        val secs = (System.nanoTime() - t0) / 1e9
        println(f"round $round%d (${secs}%.1fs) hangs=$hangs")
      }
      round += 1
    }
    println(s"done hangs=$hangs")
    if (hangs > 0) sys.exit(2)
  }
}
