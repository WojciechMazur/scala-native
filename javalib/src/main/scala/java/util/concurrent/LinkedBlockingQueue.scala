package java.util.concurrent

// Ported from Harmony

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks._
import java.util
import java.util._
import java.util.function._
import scala.annotation.tailrec

@SerialVersionUID(-6903933977591709194L)
class LinkedBlockingQueue[E](private final val capacity: Int)
    extends java.util.AbstractQueue[E]
    with BlockingQueue[E]
    with java.io.Serializable {

  import LinkedBlockingQueue._

  if (capacity <= 0) throw new IllegalArgumentException()

  /** Current number of elements */
  private final val count: AtomicInteger = new AtomicInteger(0)

  /**
   * Head of linked list.
   * Invariant: head.item == null
   */
  //transient
  private var head: Node[E] = new Node[E](null.asInstanceOf[E])

  /**
   * Tail of linked list.
   * Invariant: last.next == null
   */
  //transient
  private var last: Node[E] = new Node[E](null.asInstanceOf[E])

  /** Lock held by take, poll, etc */
  private final val takeLock: ReentrantLock = new ReentrantLock()

  /** Wait queue for waiting takes */
  private final val notEmpty: Condition = takeLock.newCondition()

  /** Lock held by put, offer, etc */
  private final val putLock: ReentrantLock = new ReentrantLock()

  /** Wait queue for waiting puts */
  private final val notFull: Condition = putLock.newCondition()

  def this() {
    this(Integer.MAX_VALUE)
  }

  def this(c: util.Collection[_ <: E]) = {
    this()
    putLock.lock() // Never contended, but necessary for visibility
    try {
      var n: Int = 0
      val it     = c.iterator()
      while (it.hasNext) {
        val e: E = it.next()
        if (e == null)
          throw new NullPointerException()
        if (n == capacity)
          throw new IllegalStateException("Queue full")
        enqueue(e)
        n += 1
      }
      count.set(n)
    } finally {
      putLock.unlock()
    }
  }

  private def signalNotEmpty(): Unit = {
    takeLock.lock()
    try {
      notEmpty.signal()
    } finally {
      takeLock.unlock()
    }
  }

  private def signalNotFull(): Unit = {
    putLock.lock()
    try {
      notFull.signal()
    } finally {
      putLock.unlock()
    }
  }

  private def enqueue(x: E): Unit = {
    last.next = new Node[E](x)
    last = last.next
  }

  private def dequeue: E = {
    val h: Node[E]     = head
    val first: Node[E] = h.next
    h.next = h // help GC
    head = first
    val x: E = first.item
    first.item = null.asInstanceOf[E]
    x
  }

  def fullyLock(): Unit = {
    putLock.lock()
    takeLock.lock()
  }

  def fullyUnlock(): Unit = {
    takeLock.unlock()
    putLock.unlock()
  }

  override def size(): Int = count.get()

  override def remainingCapacity(): Int = capacity - count.get()

  @throws[InterruptedException]
  override def put(e: E): Unit = {
    if (e == null) throw new NullPointerException()
    // Note: convention in all put/take/etc is to preset local var
    // holding count negative to indicate failure unless set.
    var c: Int                 = -1
    val putLock: ReentrantLock = this.putLock
    val count: AtomicInteger   = this.count
    putLock.lockInterruptibly()
    try {
      /*
       * Note that count is used in wait guard even though it is
       * not protected by lock. This works because count can
       * only decrease at this point (all other puts are shut
       * out by lock), and we (or some other waiting put) are
       * signalled if it ever changes from capacity. Similarly
       * for all other uses of count in other wait guards.
       */
      while (count.get() == capacity) notFull.await()
      enqueue(e)
      c = count.getAndIncrement()
      if (c + 1 < capacity)
        notFull.signal()
    } finally {
      putLock.unlock()
    }
    if (c == 0)
      signalNotEmpty()
  }

  @throws[InterruptedException]
  override def offer(e: E, timeout: Long, unit: TimeUnit): Boolean = {
    if (e == null) throw new NullPointerException()
    var nanos: Long            = unit.toNanos(timeout)
    var c: Int                 = -1
    val putLock: ReentrantLock = this.putLock
    val count: AtomicInteger   = this.count
    putLock.lockInterruptibly()
    try {
      while (count.get() == capacity) {
        if (nanos <= 0)
          return false
        nanos = notFull.awaitNanos(nanos)
      }
      enqueue(e)
      c = count.getAndIncrement()
      if (c + 1 < capacity)
        notFull.signal()
    } finally {
      putLock.unlock()
    }
    if (c == 0)
      signalNotEmpty()
    true
  }

  override def offer(e: E): Boolean = {
    if (e == null) throw new NullPointerException()
    val count: AtomicInteger = this.count
    if (count.get() == capacity)
      return false
    var c: Int                 = -1
    val putLock: ReentrantLock = this.putLock
    putLock.lock()
    try {
      if (count.get() < capacity) {
        enqueue(e)
        c = count.getAndIncrement()
        if (c + 1 < capacity)
          notFull.signal()
      }
    } finally {
      putLock.unlock()
    }
    if (c == 0)
      signalNotEmpty()
    c >= 0
  }

  @throws[InterruptedException]
  override def take(): E = {
    var x: E                    = null.asInstanceOf[E]
    var c: Int                  = -1
    val count: AtomicInteger    = this.count
    val takeLock: ReentrantLock = this.takeLock
    takeLock.lockInterruptibly()
    try {
      while (count.get() == 0) notEmpty.await()
      x = dequeue
      c = count.getAndDecrement()
      if (c > 1)
        notEmpty.signal()
    } finally {
      takeLock.unlock()
    }
    if (c == capacity)
      signalNotFull()
    x
  }

  @throws[InterruptedException]
  override def poll(timeout: Long, unit: TimeUnit): E = {
    var x: E                    = null.asInstanceOf[E]
    var c: Int                  = -1
    var nanos: Long             = unit.toNanos(timeout)
    val count: AtomicInteger    = this.count
    val takeLock: ReentrantLock = this.takeLock
    takeLock.lockInterruptibly()
    try {
      while (count.get() == 0) {
        if (nanos <= 0)
          return null.asInstanceOf[E]
        nanos = notEmpty.awaitNanos(nanos)
      }
      x = dequeue
      c = count.getAndDecrement()
      if (c > 1)
        notEmpty.signal()
    } finally {
      takeLock.unlock()
    }
    if (c == capacity)
      signalNotFull()
    x
  }

  override def poll(): E = {
    val count: AtomicInteger = this.count
    if (count.get() == 0)
      return null.asInstanceOf[E]
    var x: E                    = null.asInstanceOf[E]
    var c: Int                  = -1
    val takeLock: ReentrantLock = this.takeLock
    takeLock.lock()
    try {
      if (count.get() > 0) {
        x = dequeue
        c = count.getAndDecrement()
        if (c > 1)
          notEmpty.signal()
      }
    } finally {
      takeLock.unlock()
    }
    if (c == capacity)
      signalNotFull()
    x
  }

  override def peek(): E = {
    if (count.get() == 0)
      return null.asInstanceOf[E]
    val takeLock: ReentrantLock = this.takeLock
    takeLock.lock()
    try {
      val first: Node[E] = head.next
      if (first == null)
        null.asInstanceOf[E]
      else
        first.item
    } finally {
      takeLock.unlock()
    }
  }

  def unlink(p: Node[E], trail: Node[E]): Unit = {
    // p.next is not changed, to allow iterators that are
    // traversing p to maintain their weak-consistency guarantee.
    p.item = null.asInstanceOf[E]
    trail.next = p.next
    if (last == p)
      last = trail
    if (count.getAndDecrement() == capacity)
      notFull.signal()
  }

  override def remove(o: Any): Boolean = {
    if (o == null) return false
    fullyLock()
    try {
      var trail: Node[E] = head
      var p: Node[E]     = trail.next
      while (p != null) {
        if (o.equals(p.item)) {
          unlink(p, trail)
          return true
        }

        trail = p
        p = p.next
      }
      false
    } finally {
      fullyUnlock()
    }
  }

  override def contains(o: Any): Boolean = {
    @tailrec
    def loop(current: Node[E]): Boolean = {
      if (current == null) false
      else if (o.equals(current.item)) true
      else loop(current.next)
    }

    if (o == null) false
    else {
      fullyLock()
      try {
        loop(head.next)
      } finally fullyUnlock()
    }
  }

  override def toArray: Array[Object] = {
    fullyLock()
    try {
      val size: Int        = count.get()
      val a: Array[Object] = new Array[Object](size)
      var k: Int           = 0
      var p: Node[E]       = head.next
      while (p != null) {
        a(k) = p.item.asInstanceOf[Object]

        k += 1
        p = p.next
      }
      a
    } finally {
      fullyUnlock()
    }
  }

  override def toArray[T](a: Array[T]): Array[T] = {
    fullyLock()
    try {
      val size: Int = count.get()
      if (a.length < size) {}
      //TODO grow input array

      var k: Int     = 0
      var p: Node[E] = head.next
      while (p != null) {
        a(k) = p.item.asInstanceOf[T]

        k += 1
        p = p.next
      }
      if (a.length > k)
        a(k) = null.asInstanceOf[T]
      a
    } finally {
      fullyUnlock()
    }
  }

  override def toString: String = {
    fullyLock()
    try {
      super.toString
    } finally {
      fullyUnlock()
    }
  }

  override def clear(): Unit = {
    fullyLock()
    try {
      var h          = head
      var p: Node[E] = h.next
      while (p != null) {
        h.next = h
        p.item = null.asInstanceOf[E]

        h = p
        p = h.next
      }
      head = last
      if (count.getAndSet(0) == capacity)
        notFull.signal()
    } finally {
      fullyUnlock()
    }
  }

  override def drainTo(c: util.Collection[_ >: E]): Int =
    drainTo(c, Integer.MAX_VALUE)

  override def drainTo(c: util.Collection[_ >: E], maxElements: Int): Int = {
    if (c == null)
      throw new NullPointerException()
    if (c == this)
      throw new IllegalArgumentException()
    var signalNotFull: Boolean  = false
    val takeLock: ReentrantLock = this.takeLock
    takeLock.lock()
    try {
      val n: Int = Math.min(maxElements, count.get())
      // count.get provides visibility to first n Nodes
      var h: Node[E] = head
      var i: Int     = 0
      try {
        while (i < n) {
          var p: Node[E] = h.next
          c.add(p.item)
          p.item = null.asInstanceOf[E]
          h.next = h
          h = p
          i += 1
        }
        n
      } finally {
        // Restore invariants even if c.add() threw
        if (i > 0) {
          head = h
          signalNotFull = count.getAndAdd(-i) == capacity
        }
      }
    } finally {
      takeLock.unlock()
      if (signalNotFull)
        this.signalNotFull()
    }
  }

  /**
   * Used for any element traversal that is not entirely under lock.
   * Such traversals must handle both:
   * - dequeued nodes (p.next == p)
   * - (possibly multiple) interior removed nodes (p.item == null)
   */
  def succ(p: Node[E]): Node[E] = {
    p.next match {
      case `p` => head.next
      case _   => p
    }
  }

  override def iterator(): util.Iterator[E] = new Itr()

  /**
   * Returns a {@link Spliterator} over the elements in this queue.
   *
   * <p>The returned spliterator is
   * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
   *
   * <p>The {@code Spliterator} reports {@link Spliterator#CONCURRENT},
   * {@link Spliterator#ORDERED}, and {@link Spliterator#NONNULL}.
   *
   * @implNote
   * The {@code Spliterator} implements {@code trySplit} to permit limited
   * parallelism.
   *
   * @return a {@code Spliterator} over the elements in this queue
   * @since 1.8
   */
  def spliterator(): util.Spliterator[E] = new LBQSpliterator()

  /**
   * @throws NullPointerException {@inheritDoc}
   */
  override def forEach(action: Consumer[_ >: E]) = {
    if (action == null) throw new NullPointerException
    forEachFrom(action, null)
  }

  /**
   * Runs action on each element found during a traversal starting at p.
   * If p is null, traversal starts at head.
   */
  private[concurrent] def forEachFrom(action: Consumer[_ >: E],
                                      p: Node[E]): Unit = {
    // Extract batches of elements while holding the lock; then
    // run the action on the elements while not
    val batchSize        = 64 // max number of elements per batch
    var es: Array[Any]   = null // container for batch of elements
    var n                = 0
    var len              = 0
    var current: Node[E] = p

    @tailrec
    def countFrom(q: Node[E], count: Int): Int = {
      q match {
        case null => count
        case q =>
          if (q.item != null) {
            val acc = count + 1
            if (acc == batchSize) acc
            else countFrom(succ(q), acc)
          } else countFrom(succ(q), count)
      }
    }

    do {
      fullyLock()
      try {
        if (es == null) {
          if (current == null) current = head.next
          len = countFrom(current, 0)
          es = new Array[Any](len)
          len
        }

        while (current != null && n < len) {
          es(n) = current.item
          if (current.item != null) n += 1
          current = succ(current)
        }
      } finally fullyUnlock

      for (i <- 0 until n) {
        val e = es(i).asInstanceOf[E]
        action.accept(e)
      }
    } while (n > 0 && p != null)
  }

  override def removeIf(filter: Predicate[_ >: E]): Boolean = {
    if (filter == null) throw new NullPointerException
    bulkRemove(filter)
  }

  override def removeAll(c: util.Collection[_]): Boolean = {
    if (c == null) throw new NullPointerException
    bulkRemove(e => c.contains(e))
  }

  override def retainAll(c: util.Collection[_]): Boolean = {
    if (c == null) throw new NullPointerException
    bulkRemove(e => !c.contains(e))
  }

  /**
   * Returns the predecessor of live node p, given a node that was
   * once a live ancestor of p (or head); allows unlinking of p.
   */
  def findPred(p: Node[E], ancestor: Node[E]): Node[E] = {
    @tailrec
    def loop(ancestor: Node[E]): Node[E] = {
      if (ancestor == null) null
      else {
        val current = ancestor.next
        if (current eq p) current
        else loop(current)
      }
    }

    loop(if (ancestor.item != null) ancestor else head)
  }

  /** Implementation of bulk remove methods. */
  private def bulkRemove(filter: Predicate[_ >: E]) = {
    var removed               = false
    var p: Node[E]            = null
    var nodes: Array[Node[E]] = null
    def len                   = nodes.length
    var ancestor              = head
    var n                     = 0

    do {
      // 1. Extract batch of up to 64 elements while holding the lock.
      fullyLock()
      @tailrec
      def countBatchElems(q: Node[E], count: Int): Int = {
        if (q == null) count
        else {
          val acc = count + 1
          if (q.item != null) {
            if (acc == 64) acc
            else countBatchElems(succ(q), acc)
          } else countBatchElems(succ(q), count)
        }
      }

      try {
        if (nodes == null) { // first batch; initialize
          p = head.next
          val len = countBatchElems(p, 0)
          nodes = new Array[Node[E]](len)
        }

        n = 0
        while (p != null && n < len) {
          nodes(n) = p
          n += 1
          p = succ(p)
        }
      } finally fullyUnlock()

      // 2. Run the filter on the elements while lock is free.
      var deathRow = 0L // "bitset" of size 64
      for (i <- 0 until n) {
        val e = nodes(i).item
        if (e != null && filter.test(e))
          deathRow |= 1L << i
      }

      // 3. Remove any filtered elements while holding the lock.
      if (deathRow != 0) {
        fullyLock()
        try {
          for (i <- 0 until n) {
            if ((deathRow & (1L << i)) != 0L) {
              val q = nodes(i)
              if (q.item != null) {
                ancestor = findPred(q, ancestor)
                unlink(q, ancestor)
                removed = true
              }
            }
            nodes(i) = null // help GC

          }
        } finally fullyUnlock()
      }
    } while (n > 0 && p != null)
    removed
  }

  private class Itr extends util.Iterator[E] {
    /*
     * Basic weakly-consistent iterator.  At all times hold the next
     * item to hand out so that if hasNext() reports true, we will
     * still have it to return even if lost race with a take etc.
     */
    private var current        = null.asInstanceOf[Node[E]]
    private var lastRet        = null.asInstanceOf[Node[E]]
    private var currentElement = null.asInstanceOf[E]

    fullyLock()
    try {
      current = head.next
      if (current != null)
        currentElement = current.item
    } finally {
      fullyUnlock()
    }

    override def hasNext(): Boolean = current != null

    private def nextNode(p: Node[E]) = {
      var s = p.next
      if (p == s)
        head.next
      // Skip over removed nodes.
      // May be necessary if multiple interior Nodes are removed.
      while (s != null && s.item == null) s = s.next
      s
    }

    override def next(): E = {
      fullyLock()
      try {
        if (current == null)
          throw new util.NoSuchElementException()
        val x = currentElement
        lastRet = current
        current = nextNode(current)
        currentElement =
          if (current == null) null.asInstanceOf[E] else current.item
        x
      } finally {
        fullyUnlock()
      }
    }

    override def remove(): Unit = {
      if (lastRet == null)
        throw new IllegalStateException()
      fullyLock()
      try {
        val node  = lastRet
        var trail = head
        var break = false
        var p     = trail.next
        while (p != null) {
          if (p == node) {
            unlink(p, trail)
            break = true
          }

          trail = p
          p = p.next
        }
      } finally {
        fullyUnlock()
      }
    }

  }

  /**
   * A customized variant of Spliterators.IteratorSpliterator.
   * Keep this class in sync with (very similar) LBDSpliterator.
   */
  private object LBQSpliterator {
    val MAX_BATCH: Int = 1 << 25 // max batch array size;
  }

  final private class LBQSpliterator() extends Spliterator[E] {
    var current: Node[E] = _ // current node; null until initialized

    var batch = 0 // batch size for splits

    var exhausted = false // true when no more nodes

    var est: Long = size // size estimate

    override def estimateSize(): Long = est

    override def trySplit(): Spliterator[E] = {
      var h = current
      def updateAndCheckHead(): Boolean = {
        def getNextH() = {
          { h = current; h != null } || { h = head.next; h != null }
        }
        getNextH() && h.next != null
      }

      if (!exhausted && updateAndCheckHead()) {
        batch = Math.min(batch + 1, LBQSpliterator.MAX_BATCH)
        val n = batch
        val a = new Array[Object](n)
        var i = 0
        var p = current

        fullyLock()
        try {
          if (p != null || { p = head.next; p != null }) {
            while (p != null && i < n) {
              a(i) = p.item.asInstanceOf[Object]
              if (p.item != null) i += 1
              p = succ(p)
            }
          }
        } finally fullyUnlock()

        current = p
        if (current == null) {
          est = 0L
          exhausted = true
        } else {
          est = (est - i).max(0)
        }

        if (i > 0) {
          return Spliterators.spliterator(a, 0, i, characteristics())
        }
      }
      null
    }

    def tryAdvance(action: Consumer[_ >: E]): Boolean = {
      @tailrec
      def getLast(p: Node[E]): (Node[E], E) = {
        val elem = p.item
        val next = succ(p)
        if (elem == null && next != null) getLast(next)
        else (next, elem)
      }

      if (action == null)
        throw new NullPointerException

      if (!exhausted) {
        fullyLock()
        val elem =
          try {
            Option(current).orElse(Option(head.next)) match {
              case Some(p) =>
                val (last, elem) = getLast(p)
                current = last
                if (current == null) exhausted = true
                Some(elem)
              case None =>
                current = null
                exhausted = true
                None
            }
          } finally fullyUnlock

        elem.foreach { e =>
          action.accept(e)
          return true
        }
      }
      false
    }

    override def forEachRemaining(action: Consumer[_ >: E]): Unit = {
      if (action == null) throw new NullPointerException
      if (!exhausted) {
        exhausted = true
        val p = current
        current = null
        forEachFrom(action, p)
      }
    }

    override def characteristics(): Int = {
      Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.CONCURRENT
    }
  }
}

object LinkedBlockingQueue {

  class Node[E](var item: E) {

    /**
     * One of:
     * - the real successor Node
     * - this Node, meaning the successor is head.next
     * - null, meaning there is no successor (this is the last node)
     */
    var next: Node[E] = _

  }

}
