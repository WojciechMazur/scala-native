import scala.scalanative.runtime.GC

/* A large object that fits neither in the room the heap has left to grow nor
 * in any free extent, but fits in the free extent at the end of the heap
 * together with that room. The heap has to grow by only what the extent
 * lacks, and merge both: growing by the whole size of the object is beyond
 * the limit, and an extent added next to the free one is too small on its
 * own. Without that, the allocation failed with most of the heap free.
 */
object Main {
  private final val MB = 1024 * 1024
  // A 32-block superblock, with room for the array header.
  private final val Chunk = MB - 64
  private final val Target = 64 * MB
  private final val TailChunks = 32
  private final val KeepEvery = 8

  private def check(condition: Boolean, message: => String): Unit =
    if (!condition) throw new AssertionError(message)

  private def heapSize: Long = GC.getUsedHeapSize().toLong

  def main(args: Array[String]): Unit = {
    val maxHeap = GC.getMaxHeapSize().toLong
    val chunks = new Array[Array[Byte]](4096)
    var count = 0
    // Fill the heap until it grows to within Target of its limit: the room
    // left is then too small for the object on its own.
    while (heapSize <= maxHeap - Target) {
      check(count < chunks.length, s"heap did not grow past ${heapSize / MB}M")
      chunks(count) = new Array[Byte](Chunk)
      count += 1
    }
    val grownTo = heapSize
    val room = maxHeap - grownTo
    check(
      room > 0 && room + TailChunks * MB > Target,
      s"heap grew to ${grownTo / MB}M of ${maxHeap / MB}M, the scenario needs room"
    )
    // Allocated now, these land in the blocks just added at the end; freed,
    // they leave a free extent there, smaller than the object.
    val interior = count
    for (_ <- 0 until TailChunks) {
      chunks(count) = new Array[Byte](Chunk)
      count += 1
    }
    check(heapSize == grownTo, "heap grew while filling its end")
    // Interior free extents of KeepEvery - 1 chunks, far too small.
    for (i <- 0 until count)
      if (i >= interior || i % KeepEvery != 0) chunks(i) = null

    val large =
      try new Array[Byte](Target)
      catch {
        case error: OutOfMemoryError =>
          throw new AssertionError(
            s"allocation of ${Target / MB}M failed in a heap of " +
              s"${heapSize / MB}M with a limit of ${maxHeap / MB}M",
            error
          )
      }
    large(Target - 1) = 1
    check(
      chunks.count(_ != null) == (interior + KeepEvery - 1) / KeepEvery,
      "kept chunks were lost"
    )
    println(
      s"Allocated ${Target / MB}M, heap grew from ${grownTo / MB}M " +
        s"to ${heapSize / MB}M of ${maxHeap / MB}M"
    )
  }
}
