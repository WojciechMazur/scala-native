/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent

import java.util
import java.util._
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks._
import java.util.function._

/** An optionally-bounded {@linkplain BlockingQueue blocking queue} based on
 *  linked nodes. This queue orders elements FIFO (first-in-first-out). The
 *  <em>head</em> of the queue is that element that has been on the queue the
 *  longest time. The <em>tail</em> of the queue is that element that has been
 *  on the queue the shortest time. New elements are inserted at the tail of the
 *  queue, and the queue retrieval operations obtain elements at the head of the
 *  queue. Linked queues typically have higher throughput than array-based
 *  queues but less predictable performance in most concurrent applications.
 *
 *  <p>The optional capacity bound constructor argument serves as a way to
 *  prevent excessive queue expansion. The capacity, if unspecified, is equal to
 *  {@link Integer#MAX_VALUE}. Linked nodes are dynamically created upon each
 *  insertion unless this would bring the queue above capacity.
 *
 *  <p>This class and its iterator implement all of the <em>optional</em>
 *  methods of the {@link Collection} and {@link Iterator} interfaces.
 *
 *  <p>This class is a member of the <a
 *  href="{@docRoot}/java.base/java/util/package-summary.html#CollectionsFramework">
 *  Java Collections Framework</a>.
 *
 *  @since 1.5
 *  @author
 *    Doug Lea
 *  @param <E>
 *    the type of elements held in this queue
 */
@SerialVersionUID(-6903933977591709194L)
object LinkedBlockingQueue {

  /** Linked list node class.
   */
  private[concurrent] class Node[E] private[concurrent] (var item: E) {

    /** One of:
     *    - the real successor Node
     *    - this Node, meaning the successor is head.next
     *    - null, meaning there is no successor (this is the last node)
     */
    private[concurrent] var next: Node[E] = _
  }
}
@SerialVersionUID(-6903933977591709194L)
class LinkedBlockingQueue[E <: AnyRef](
    /** The capacity bound, or Integer.MAX_VALUE if none */
    val capacity: Int
) extends util.AbstractQueue[E]
    with BlockingQueue[E]
    with Serializable {
  import LinkedBlockingQueue._

  if (capacity <= 0) throw new IllegalArgumentException

  /** Head of linked list. Invariant: head.item == null
   */
  private[concurrent] var head = new Node[E](null.asInstanceOf[E])

  /** Tail of linked list. Invariant: last.next == null
   */
  private var last = head

  /** Current number of elements */
  final private val count = new AtomicInteger()

  /** Lock held by take, poll, etc */
  final private val takeLock = new ReentrantLock()

  /** Wait queue for waiting takes */
  final private val notEmpty: Condition = takeLock.newCondition()

  /** Lock held by put, offer, etc */
  final private val putLock = new ReentrantLock()

  /** Wait queue for waiting puts */
  final private val notFull = putLock.newCondition()

  /** Signals a waiting take. Called only from put/offer (which do not otherwise
   *  ordinarily lock takeLock.)
   */
  private def signalNotEmpty(): Unit = {
    val takeLock = this.takeLock
    takeLock.lock()
    try notEmpty.signal()
    finally takeLock.unlock()
  }

  /** Signals a waiting put. Called only from take/poll.
   */
  private def signalNotFull(): Unit = {
    val putLock = this.putLock
    putLock.lock()
    try notFull.signal()
    finally putLock.unlock()
  }

  /** Links node at end of queue.
   *
   *  @param node
   *    the node
   */
  private def enqueue(node: Node[E]): Unit = {
    // assert putLock.isHeldByCurrentThread();
    // assert last.next == null;
    last.next = node
    last = node
  }

  /** Removes a node from head of queue.
   *
   *  @return
   *    the node
   */
  private def dequeue() = {
    // assert takeLock.isHeldByCurrentThread();
    // assert head.item == null;
    val h = head
    val first = h.next
    h.next = h // help GC

    head = first
    val x = first.item
    first.item = null.asInstanceOf[E]
    x
  }

  /** Locks to prevent both puts and takes.
   */
  private[concurrent] def fullyLock(): Unit = {
    putLock.lock()
    takeLock.lock()
  }

  /** Unlocks to allow both puts and takes.
   */
  private[concurrent] def fullyUnlock(): Unit = {
    takeLock.unlock()
    putLock.unlock()
  }

  /** Creates a {@code LinkedBlockingQueue} with a capacity of {@link
   *  Integer#MAX_VALUE}.
   */
  def this() = this(Integer.MAX_VALUE)

  /** Creates a {@code LinkedBlockingQueue} with a capacity of {@link
   *  Integer#MAX_VALUE}, initially containing the elements of the given
   *  collection, added in traversal order of the collection's iterator.
   *
   *  @param c
   *    the collection of elements to initially contain
   *  @throws NullPointerException
   *    if the specified collection or any of its elements are null
   */
  def this(c: util.Collection[_ <: E]) = {
    this(Integer.MAX_VALUE)
    val putLock = this.putLock
    putLock.lock() // Never contended, but necessary for visibility

    try {
      var n = 0
      c.forEach { e =>
        if (e == null) throw new NullPointerException
        if (n == capacity) throw new IllegalStateException("Queue full")
        enqueue(new Node[E](e))
        n += 1
      }
      count.set(n)
    } finally putLock.unlock()
  }

  /** Returns the number of elements in this queue.
   *
   *  @return
   *    the number of elements in this queue
   */
  override def size(): Int = count.get()

  /** Returns the number of additional elements that this queue can ideally (in
   *  the absence of memory or resource constraints) accept without blocking.
   *  This is always equal to the initial capacity of this queue less the
   *  current {@code size} of this queue.
   *
   *  <p>Note that you <em>cannot</em> always tell if an attempt to insert an
   *  element will succeed by inspecting {@code remainingCapacity} because it
   *  may be the case that another thread is about to insert or remove an
   *  element.
   */
  override def remainingCapacity(): Int = capacity - count.get()

  /** Inserts the specified element at the tail of this queue, waiting if
   *  necessary for space to become available.
   *
   *  @throws InterruptedException
   *    {@inheritDoc}
   *  @throws NullPointerException
   *    {@inheritDoc}
   */
  @throws[InterruptedException]
  override def put(e: E): Unit = {
    if (e == null) throw new NullPointerException
    var c = 0
    val node = new Node[E](e)
    val putLock = this.putLock
    val count = this.count
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
      enqueue(node)
      c = count.getAndIncrement()
      if (c + 1 < capacity) notFull.signal()
    } finally putLock.unlock()
    if (c == 0) signalNotEmpty()
  }

  /** Inserts the specified element at the tail of this queue, waiting if
   *  necessary up to the specified wait time for space to become available.
   *
   *  @return
   *    {@code true} if successful, or {@code false} if the specified waiting
   *    time elapses before space is available
   *  @throws InterruptedException
   *    {@inheritDoc}
   *  @throws NullPointerException
   *    {@inheritDoc}
   */
  @throws[InterruptedException]
  override def offer(e: E, timeout: Long, unit: TimeUnit): Boolean = {
    if (e == null) throw new NullPointerException
    var nanos = unit.toNanos(timeout)
    var c = 0
    val putLock = this.putLock
    val count = this.count
    putLock.lockInterruptibly()
    try {
      while (count.get() == capacity) {
        if (nanos <= 0L) return false
        nanos = notFull.awaitNanos(nanos)
      }
      enqueue(new Node[E](e))
      c = count.getAndIncrement()
      if (c + 1 < capacity) notFull.signal()
    } finally putLock.unlock()
    if (c == 0) signalNotEmpty()
    true
  }

  /** Inserts the specified element at the tail of this queue if it is possible
   *  to do so immediately without exceeding the queue's capacity, returning
   *  {@code true} upon success and {@code false} if this queue is full. When
   *  using a capacity-restricted queue, this method is generally preferable to
   *  method {@link BlockingQueue#add add}, which can fail to insert an element
   *  only by throwing an exception.
   *
   *  @throws NullPointerException
   *    if the specified element is null
   */
  override def offer(e: E): Boolean = {
    if (e == null) throw new NullPointerException
    val count = this.count
    if (count.get() == capacity) return false
    var c = 0
    val node = new Node[E](e)
    val putLock = this.putLock
    putLock.lock()
    try {
      if (count.get() == capacity) return false
      enqueue(node)
      c = count.getAndIncrement()
      if (c + 1 < capacity) notFull.signal()
    } finally putLock.unlock()
    if (c == 0) signalNotEmpty()
    true
  }
  @throws[InterruptedException]
  override def take(): E = {
    var x: E = null.asInstanceOf[E]
    var c = 0
    val count = this.count
    val takeLock = this.takeLock
    takeLock.lockInterruptibly()
    try {
      while (count.get() == 0) notEmpty.await()
      x = dequeue()
      c = count.getAndDecrement()
      if (c > 1) notEmpty.signal()
    } finally takeLock.unlock()
    if (c == capacity) signalNotFull()
    x
  }

  @throws[InterruptedException]
  override def poll(timeout: Long, unit: TimeUnit): E = {
    var x = null.asInstanceOf[E]
    var c = 0
    var nanos = unit.toNanos(timeout)
    val count = this.count
    val takeLock = this.takeLock
    takeLock.lockInterruptibly()
    try {
      while (count.get() == 0) {
        if (nanos <= 0L) return null.asInstanceOf[E]
        nanos = notEmpty.awaitNanos(nanos)
      }
      x = dequeue()
      c = count.getAndDecrement()
      if (c > 1) notEmpty.signal()
    } finally takeLock.unlock()
    if (c == capacity) signalNotFull()
    x
  }

  override def poll(): E = {
    val count = this.count
    if (count.get() == 0) return null.asInstanceOf[E]
    var x = null.asInstanceOf[E]
    var c = 0
    val takeLock = this.takeLock
    takeLock.lock()
    try {
      if (count.get() == 0) return null.asInstanceOf[E]
      x = dequeue()
      c = count.getAndDecrement()
      if (c > 1) notEmpty.signal()
    } finally takeLock.unlock()
    if (c == capacity) signalNotFull()
    x
  }
  override def peek(): E = {
    val count = this.count
    if (count.get() == 0) return null.asInstanceOf[E]
    val takeLock = this.takeLock
    takeLock.lock()
    try
      if (count.get() > 0) head.next.item
      else null.asInstanceOf[E]
    finally takeLock.unlock()
  }

  /** Unlinks interior Node p with predecessor pred.
   */
  private[concurrent] def unlink(
      p: Node[E],
      pred: Node[E]
  ): Unit = { // p.next is not changed, to allow iterators that are
    // traversing p to maintain their weak-consistency guarantee.
    p.item = null.asInstanceOf[E]
    pred.next = p.next
    if (last eq p) last = pred
    if (count.getAndDecrement() == capacity) notFull.signal()
  }

  /** Removes a single instance of the specified element from this queue, if it
   *  is present. More formally, removes an element {@code e} such that {@code
   *  o.equals(e)}, if this queue contains one or more such elements. Returns
   *  {@code true} if this queue contained the specified element (or
   *  equivalently, if this queue changed as a result of the call).
   *
   *  @param o
   *    element to be removed from this queue, if present
   *  @return
   *    {@code true} if this queue changed as a result of the call
   */
  override def remove(o: Any): Boolean = {
    if (o == null) return false
    fullyLock()
    try {
      var pred = head
      var p = pred.next
      while ({ p != null }) {
        if (o == p.item) {
          unlink(p, pred)
          return true
        }

        pred = p
        p = p.next
      }
      false
    } finally fullyUnlock()
  }

  /** Returns {@code true} if this queue contains the specified element. More
   *  formally, returns {@code true} if and only if this queue contains at least
   *  one element {@code e} such that {@code o.equals(e)}.
   *
   *  @param o
   *    object to be checked for containment in this queue
   *  @return
   *    {@code true} if this queue contains the specified element
   */
  override def contains(o: Any): Boolean = {
    if (o == null) return false
    fullyLock()
    try {
      var p = head.next
      while ({ p != null }) {
        if (o == p.item) return true
        p = p.next
      }
      false
    } finally fullyUnlock()
  }

  /** Returns an array containing all of the elements in this queue, in proper
   *  sequence.
   *
   *  <p>The returned array will be "safe" in that no references to it are
   *  maintained by this queue. (In other words, this method must allocate a new
   *  array). The caller is thus free to modify the returned array.
   *
   *  <p>This method acts as bridge between array-based and collection-based
   *  APIs.
   *
   *  @return
   *    an array containing all of the elements in this queue
   */
  override def toArray(): Array[AnyRef] = {
    fullyLock()
    try {
      val size = count.get()
      val a = new Array[AnyRef](size)
      var k = 0
      var p = head.next
      while (p != null) {
        val idx = k
        k += 1
        a(idx) = p.item
        p = p.next
      }
      a
    } finally fullyUnlock()
  }

  /** Returns an array containing all of the elements in this queue, in proper
   *  sequence; the runtime type of the returned array is that of the specified
   *  array. If the queue fits in the specified array, it is returned therein.
   *  Otherwise, a new array is allocated with the runtime type of the specified
   *  array and the size of this queue.
   *
   *  <p>If this queue fits in the specified array with room to spare (i.e., the
   *  array has more elements than this queue), the element in the array
   *  immediately following the end of the queue is set to {@code null}.
   *
   *  <p>Like the {@link #toArray()} method, this method acts as bridge between
   *  array-based and collection-based APIs. Further, this method allows precise
   *  control over the runtime type of the output array, and may, under certain
   *  circumstances, be used to save allocation costs.
   *
   *  <p>Suppose {@code x} is a queue known to contain only strings. The
   *  following code can be used to dump the queue into a newly allocated array
   *  of {@code String}:
   *
   *  <pre> {@code String[] y = x.toArray(new String[0]);}</pre>
   *
   *  Note that {@code toArray(new Object[0])} is identical in function to
   *  {@code toArray()}.
   *
   *  @param a
   *    the array into which the elements of the queue are to be stored, if it
   *    is big enough; otherwise, a new array of the same runtime type is
   *    allocated for this purpose
   *  @return
   *    an array containing all of the elements in this queue
   *  @throws ArrayStoreException
   *    if the runtime type of the specified array is not a supertype of the
   *    runtime type of every element in this queue
   *  @throws NullPointerException
   *    if the specified array is null
   */
  override def toArray[T](
      _a: Array[T]
  ): Array[T] = {
    var a = _a
    fullyLock()
    try {
      val size = count.get()
      if (a.length < size)
        a = java.lang.reflect.Array
          .newInstance(a.getClass.getComponentType, size)
          .asInstanceOf[Array[T]]
      var k = 0
      var p = head.next
      while (p != null) {
        val idx = k
        k += 1
        a(idx) = p.item.asInstanceOf[T]
        p = p.next
      }
      if (a.length > k) a(k) = null.asInstanceOf[T]
      a
    } finally fullyUnlock()
  }
  override def toString: String = Helpers.collectionToString(this)

  /** Atomically removes all of the elements from this queue. The queue will be
   *  empty after this call returns.
   */
  override def clear(): Unit = {
    fullyLock()
    try {
      var p: Node[E] = null.asInstanceOf[Node[E]]
      var h = head
      while ({ p = h.next; p != null }) {
        h.next = h
        p.item = null.asInstanceOf[E]
        h = p
      }
      head = last
      // assert head.item == null && head.next == null;
      if (count.getAndSet(0) == capacity) notFull.signal()
    } finally fullyUnlock()
  }

  /** @throws UnsupportedOperationException
   *    {@inheritDoc}
   *  @throws ClassCastException
   *    {@inheritDoc}
   *  @throws NullPointerException
   *    {@inheritDoc}
   *  @throws IllegalArgumentException
   *    {@inheritDoc}
   */
  override def drainTo(c: util.Collection[_ >: E]): Int =
    drainTo(c, Integer.MAX_VALUE)
  override def drainTo(c: util.Collection[_ >: E], maxElements: Int): Int = {
    Objects.requireNonNull(c)
    if (c eq this) throw new IllegalArgumentException
    if (maxElements <= 0) return 0
    var signalNotFull = false
    val takeLock = this.takeLock
    takeLock.lock()
    try {
      val n = Math.min(maxElements, count.get())
      // count.get() provides visibility to first n Nodes
      var h = head
      var i = 0
      try {
        while (i < n) {
          val p = h.next
          c.add(p.item)
          p.item = null.asInstanceOf[E]
          h.next = h
          h = p
          i += 1
        }
        n
      } finally {
        // Restore invariants even if c.add() threw
        if (i > 0) { // assert h.item == null;
          head = h
          signalNotFull = count.getAndAdd(-i) == capacity
        }
      }
    } finally {
      takeLock.unlock()
      if (signalNotFull) this.signalNotFull()
    }
  }

  /** Used for any element traversal that is not entirely under lock. Such
   *  traversals must handle both:
   *    - dequeued nodes (p.next == p)
   *    - (possibly multiple) interior removed nodes (p.item == null)
   */
  private[concurrent] def succ(p: Node[E]) = {
    p.next match {
      case `p`  => head.next
      case next => next
    }
  }

  /** Returns an iterator over the elements in this queue in proper sequence.
   *  The elements will be returned in order from first (head) to last (tail).
   *
   *  <p>The returned iterator is <a
   *  href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
   *
   *  @return
   *    an iterator over the elements in this queue in proper sequence
   */
  override def iterator(): Iterator[E] = new Itr()

  /** Weakly-consistent iterator.
   *
   *  Lazily updated ancestor field provides expected O(1) remove(), but still
   *  O(n) in the worst case, whenever the saved ancestor is concurrently
   *  deleted.
   */
  private class Itr private[concurrent] () extends util.Iterator[E] {
    private var nextNode: Node[E] = _
    private var nextItem: E = _
    private var lastRet: Node[E] = _
    private var ancestor: Node[E] = _ // Helps unlink lastRet on remove()

    fullyLock()
    try if ({ nextNode = head.next; nextNode != null }) nextItem = nextNode.item
    finally fullyUnlock()

    override def hasNext(): Boolean = nextNode != null
    override def next(): E = {
      var p: Node[E] = null
      if ({ p = nextNode; p == null }) throw new NoSuchElementException
      lastRet = p
      val x = nextItem
      fullyLock()
      try {
        var e: E = null.asInstanceOf[E]
        p = p.next
        while (p != null && { e = p.item; e == null }) p = succ(p)
        nextNode = p
        nextItem = e
      } finally fullyUnlock()
      x
    }

    override def forEachRemaining(action: Consumer[_ >: E]): Unit = {
      // A variant of forEachFrom
      Objects.requireNonNull(action)
      var p: Node[E] = nextNode
      if (p == null) return ()
      lastRet = p
      nextNode = null.asInstanceOf[Node[E]]
      val batchSize = 64
      var es: Array[AnyRef] = null
      var n = 0
      var len = 1
      while ({
        fullyLock()
        try {
          if (es == null) {
            p = p.next
            var q = p
            var break = false
            while (q != null && !break) {
              if (q.item != null && { len += 1; len } == batchSize)
                break = true
              else
                q = succ(q)
            }
            es = new Array[AnyRef](len)
            es(0) = nextItem
            nextItem = null.asInstanceOf[E]
            n = 1
          } else n = 0

          while (p != null && n < len) {
            es(n) = p.item
            if (es(n) != null) {
              lastRet = p
              n += 1
            }
            p = succ(p)
          }
        } finally fullyUnlock()
        for (i <- 0 until n) {
          val e = es(i).asInstanceOf[E]
          action.accept(e)
        }
        n > 0 && p != null
      }) ()
    }

    override def remove(): Unit = {
      val p = lastRet
      if (p == null) throw new IllegalStateException
      lastRet = null
      fullyLock()
      try
        if (p.item != null) {
          if (ancestor == null) ancestor = head
          ancestor = findPred(p, ancestor)
          unlink(p, ancestor)
        }
      finally fullyUnlock()
    }
  }

  /** A customized variant of Spliterators.IteratorSpliterator. Keep this class
   *  in sync with (very similar) LBDSpliterator.
   */
  private object LBQSpliterator {
    private[concurrent] val MAX_BATCH = 1 << 25 // max batch array size;

  }
  final private class LBQSpliterator private[concurrent] ()
      extends Spliterator[E] {
    private[concurrent] var current: Node[E] = _
    private[concurrent] var batch = 0 // batch size for splits
    private[concurrent] var exhausted = false // true when no more nodes
    private[concurrent] var est: Long = size() // size estimate

    override def estimateSize: Long = est
    override def trySplit: Spliterator[E] = {
      var h: Node[E] = null.asInstanceOf[Node[E]]
      if (!exhausted &&
          ({ h = current; h != null } || { h = head.next; h != null }) &&
          h.next != null) {
        batch = Math.min(batch + 1, LBQSpliterator.MAX_BATCH)
        val n = batch
        val a = new Array[AnyRef](n)
        var i = 0
        var p: Node[E] = current
        fullyLock()
        try
          if (p != null || { p = head.next; p != null })
            while (p != null && i < n) {
              if ({ a(i) = p.item; a(i) != null }) i += 1
              p = succ(p)
            }
        finally fullyUnlock()
        if ({ current = p; current == null }) {
          est = 0L
          exhausted = true
        } else if ({ est -= i; est < 0L }) est = 0L
        if (i > 0)
          return Spliterators.spliterator(
            a,
            0,
            i,
            Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.CONCURRENT
          )
      }
      null
    }

    override def tryAdvance(action: Consumer[_ >: E]): Boolean = {
      Objects.requireNonNull(action)
      if (!exhausted) {
        var e: E = null.asInstanceOf[E]
        fullyLock()
        try {
          var p: Node[E] = current
          if (p != null || { p = head.next; p != null }) while ({
            e = p.item
            p = succ(p)
            e == null && p != null
          }) ()
          current = p
          if (current == null) exhausted = true
        } finally fullyUnlock()
        if (e != null) {
          action.accept(e)
          return true
        }
      }
      false
    }

    override def forEachRemaining(action: Consumer[_ >: E]): Unit = {
      Objects.requireNonNull(action)
      if (!exhausted) {
        exhausted = true
        val p = current
        current = null
        forEachFrom(action, p)
      }
    }
    override def characteristics: Int =
      Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.CONCURRENT
  }

  /** Returns a {@link Spliterator} over the elements in this queue.
   *
   *  <p>The returned spliterator is <a
   *  href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
   *
   *  <p>The {@code Spliterator} reports {@link Spliterator#CONCURRENT}, {@link
   *  Spliterator#ORDERED}, and {@link Spliterator#NONNULL}.
   *
   *  @implNote
   *    The {@code Spliterator} implements {@code trySplit} to permit limited
   *    parallelism.
   *
   *  @return
   *    a {@code Spliterator} over the elements in this queue
   *  @since 1.8
   */
  // override def spliterator(): Spliterator[E] = new LBQSpliterator

  /** @throws NullPointerException
   *    {@inheritDoc}
   */
  override def forEach(action: Consumer[_ >: E]): Unit = {
    Objects.requireNonNull(action)
    forEachFrom(action, null)
  }

  /** Runs action on each element found during a traversal starting at p. If p
   *  is null, traversal starts at head.
   */
  private[concurrent] def forEachFrom(
      action: Consumer[_ >: E],
      _p: Node[E]
  ): Unit = {
    // Extract batches of elements while holding the lock; then
    // run the action on the elements while not
    var p = _p
    val batchSize = 64 // max number of elements per batch
    var es = null.asInstanceOf[Array[AnyRef]] // container for batch of elements
    var n = 0
    var len = 0
    while ({
      fullyLock()
      try {
        if (es == null) {
          if (p == null) p = head.next
          var q = p
          var break = false
          while (q != null && !break) {
            if (q.item != null && { len += 1; len } == batchSize)
              break = true
            else q = succ(q)
          }
          es = new Array[AnyRef](len)
        }

        n = 0
        while (p != null && n < len) {
          es(n) = p.item
          if (es(n) != null) n += 1
          p = succ(p)
        }
      } finally fullyUnlock()

      for (i <- 0 until n) {
        val e = es(i).asInstanceOf[E]
        action.accept(e)
      }
      n > 0 && p != null
    }) ()
  }

  override def removeIf(filter: Predicate[_ >: E]): Boolean = {
    Objects.requireNonNull(filter)
    bulkRemove(filter)
  }
  override def removeAll(c: util.Collection[_]): Boolean = {
    Objects.requireNonNull(c)
    bulkRemove((e: E) => c.contains(e))
  }
  override def retainAll(c: util.Collection[_]): Boolean = {
    Objects.requireNonNull(c)
    bulkRemove((e: E) => !c.contains(e))
  }

  /** Returns the predecessor of live node p, given a node that was once a live
   *  ancestor of p (or head); allows unlinking of p.
   */
  private[concurrent] def findPred(
      p: Node[E],
      _ancestor: Node[E]
  ) = {
    // assert p.item != null;
    var ancestor = _ancestor
    if (ancestor.item == null) ancestor = head
    // Fails with NPE if precondition not satisfied
    var q = ancestor.next
    while ({ q = ancestor.next; q ne p }) ancestor = q
    ancestor
  }

  /** Implementation of bulk remove methods. */
  private def bulkRemove(
      filter: Predicate[_ >: E]
  ) = {
    var removed = false
    var p = null: Node[E]
    var ancestor = head
    var nodes = null: Array[Node[E]]
    var n = 0
    var len = 0
    while ({ // 1. Extract batch of up to 64 elements while holding the lock.
      fullyLock()
      try {
        if (nodes == null) { // first batch; initialize
          p = head.next
          var q = p
          var break = false
          while (!break && q != null) {
            if (q.item != null && { len += 1; len } == 64)
              break = true
            else
              q = succ(q)
          }
          nodes = new Array[Node[AnyRef]](len).asInstanceOf[Array[Node[E]]]
        }
        n = 0
        while (p != null && n < len) {
          val idx = n
          n += 1
          nodes(idx) = p
          p = succ(p)
        }
      } finally fullyUnlock()
      // 2. Run the filter on the elements while lock is free.
      var deathRow = 0L // "bitset" of size 64
      for (i <- 0 until n) {
        val e = nodes(i).item
        if (e != null && filter.test(e)) deathRow |= 1L << i
      }
      // 3. Remove any filtered elements while holding the lock.
      if (deathRow != 0) {
        fullyLock()
        try
          for (i <- 0 until n) {
            var q = null: Node[E]
            if ((deathRow & (1L << i)) != 0L && {
                  q = nodes(i); q.item != null
                }) {
              ancestor = findPred(q, ancestor)
              unlink(q, ancestor)
              removed = true
            }
            nodes(i) = null
          }
        finally fullyUnlock()
      }
      n > 0 && p != null
    }) ()
    removed
  }

  // No ObjectInputStream in ScalaNative
  // private def writeObject(s: ObjectOutputStream): Unit
  // private def readObject(s: ObjectInputStream): Unit
}
