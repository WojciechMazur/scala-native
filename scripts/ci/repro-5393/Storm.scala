// #5393 reproducer: System.gc() storm while other threads allocate and one
// thread keeps entering blocking (Unmanaged) native calls.
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

object Test {
  def main(args: Array[String]): Unit = {
    val seconds = args.headOption.map(_.toInt).getOrElse(20)
    val stop = new AtomicBoolean(false)
    val allocs = new AtomicLong
    val gcs = new AtomicLong

    def thread(name: String)(body: => Unit): Thread = {
      val t = new Thread(() => body, name)
      t.start()
      t
    }

    val allocators = (1 to 4).map { i =>
      thread(s"alloc-$i") {
        var keep: List[Array[Byte]] = Nil
        var n = 0L
        var x = 0.0
        while (!stop.get()) {
          keep = new Array[Byte](64 + (n % 512).toInt) :: keep
          x = x * 1.0000001 + n.toDouble
          if (n % 1000 == 0) keep = Nil
          n += 1
        }
        allocs.addAndGet(n + (x.toLong & 1))
      }
    }
    val blocker = thread("blocking") {
      while (!stop.get()) Thread.sleep(0, 50000)
    }
    val collectors = (1 to 2).map { i =>
      thread(s"gc-$i") {
        while (!stop.get()) { System.gc(); gcs.incrementAndGet() }
      }
    }

    Thread.sleep(seconds * 1000L)
    stop.set(true)
    (allocators ++ collectors :+ blocker).foreach(_.join())
    println(s"STORM-OK gcs=${gcs.get()} allocs=${allocs.get()}")
  }
}
