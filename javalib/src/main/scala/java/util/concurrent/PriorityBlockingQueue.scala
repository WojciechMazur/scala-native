/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import java.util
import java.util._
import java.util.concurrent.locks._
import java.util.function._
import scala.annotation.tailrec
import scala.scalanative.runtime.{fromRawPtr, Intrinsics}
import scala.scalanative.libc.atomic.CAtomicInt

/** An unbounded {@linkplain BlockingQueue blocking queue} that uses the same
 *  ordering rules as class {@link PriorityQueue} and supplies blocking
 *  retrieval operations. While this queue is logically unbounded, attempted
 *  additions may fail due to resource exhaustion (causing {@code
 *  OutOfMemoryError}). This class does not permit {@code null} elements. A
 *  priority queue relying on {@linkplain Comparable natural ordering} also does
 *  not permit insertion of non-comparable objects (doing so results in {@code
 *  ClassCastException}).
 *
 *  <p>This class and its iterator implement all of the <em>optional</em>
 *  methods of the {@link Collection} and {@link Iterator} interfaces. The
 *  Iterator provided in method {@link #iterator()} and the Spliterator provided
 *  in method {@link #spliterator()} are <em>not</em> guaranteed to traverse the
 *  elements of the PriorityBlockingQueue in any particular order. If you need
 *  ordered traversal, consider using {@code Arrays.sort(pq.toArray())}. Also,
 *  method {@code drainTo} can be used to <em>remove</em> some or all elements
 *  in priority order and place them in another collection.
 *
 *  <p>Operations on this class make no guarantees about the ordering of
 *  elements with equal priority. If you need to enforce an ordering, you can
 *  define custom classes or comparators that use a secondary key to break ties
 *  in primary priority values. For example, here is a class that applies
 *  first-in-first-out tie-breaking to comparable elements. To use it, you would
 *  insert a {@code new FIFOEntry(anEntry)} instead of a plain entry object.
 *
 *  <pre> {@code class FIFOEntry<E extends Comparable<? super E>> implements
 *  Comparable<FIFOEntry<E>> { static final AtomicLong seq = new AtomicLong(0);
 *  final long seqNum; final E entry; public FIFOEntry(E entry) { seqNum =
 *  seq.getAndIncrement(); this.entry = entry; } public E getEntry() { return
 *  entry; } public int compareTo(FIFOEntry<E> other) { int res =
 *  entry.compareTo(other.entry); if (res == 0 && other.entry != this.entry) res
 *  \= (seqNum < other.seqNum ? -1 : 1); return res; } }}</pre>
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
@SerialVersionUID(5595510919245408276L)
object PriorityBlockingQueue {

  /** Default array capacity.
   */
  private final val DEFAULT_INITIAL_CAPACITY = 11

  /** Ensures that queue[0] exists, helping peek() and poll(). */
  private def ensureNonEmpty[E](es: Array[E]) =
    if (es.length > 0) es
    else new Array[AnyRef](1).asInstanceOf[Array[E]]

  /** Inserts item x at position k, maintaining heap invariant by promoting x up
   *  the tree until it is greater than or equal to its parent, or is the root.
   *
   *  To simplify and speed up coercions and comparisons, the Comparable and
   *  Comparator versions are separated into different methods that are
   *  otherwise identical. (Similarly for siftDown.)
   *
   *  @param k
   *    the position to fill
   *  @param x
   *    the item to insert
   *  @param es
   *    the heap array
   */
  private def siftUpComparable[T](_k: Int, x: T, es: Array[T]): Unit = {
    var k = _k
    val key = x.asInstanceOf[Comparable[_ >: T]]
    @tailrec def loop(): Unit = {
      if (k > 0) {
        val parent = (k - 1) >>> 1
        val e = es(parent)
        if (key.compareTo(e.asInstanceOf[T]) >= 0) () // break
        else {
          es(k) = e
          k = parent
          loop()
        }
      }
    }
    loop()
    es(k) = key.asInstanceOf[T]
  }

  private def siftUpUsingComparator[T](
      _k: Int,
      x: T,
      es: Array[T],
      cmp: Comparator[_ >: T]
  ): Unit = {
    var k = _k
    @tailrec def loop(): Unit = {
      if (k > 0) {
        val parent = (k - 1) >>> 1
        val e = es(parent)
        if (cmp.compare(x, e.asInstanceOf[T]) >= 0) () // break
        else {
          es(k) = e
          k = parent
          loop()
        }
      }
    }
    loop()
    es(k) = x.asInstanceOf[T]
  }

  /** Inserts item x at position k, maintaining heap invariant by demoting x
   *  down the tree repeatedly until it is less than or equal to its children or
   *  is a leaf.
   *
   *  @param k
   *    the position to fill
   *  @param x
   *    the item to insert
   *  @param es
   *    the heap array
   *  @param n
   *    heap curSize
   */
  private def siftDownComparable[T](
      _k: Int,
      x: T,
      es: Array[T],
      n: Int
  ): Unit = { // assert n > 0;
    val key = x.asInstanceOf[Comparable[_ >: T]]
    val half = n >>> 1 // loop while a non-leaf
    var k = _k
    @tailrec def loop(): Unit = {
      if (k < half) {
        var child = (k << 1) + 1
        var c = es(child)
        val right = child + 1
        if (right < n && c
              .asInstanceOf[Comparable[_ >: T]]
              .compareTo(es(right).asInstanceOf[T]) > 0) {
          child = right
          c = es(child)
        }
        if (key.compareTo(c.asInstanceOf[T]) <= 0) () // break
        else {
          es(k) = c
          k = child
          loop()
        }
      }
    }
    loop()
    es(k) = key.asInstanceOf[T]
  }

  private def siftDownUsingComparator[T](
      _k: Int,
      x: T,
      es: Array[T],
      n: Int,
      cmp: Comparator[_ >: T]
  ): Unit = {
    val half = n >>> 1
    var k = _k
    @tailrec def loop(): Unit = {
      if (k < half) {
        var child = (k << 1) + 1
        var c = es(child)
        val right = child + 1
        if (right < n && cmp.compare(
              c.asInstanceOf[T],
              es(right).asInstanceOf[T]
            ) > 0) {
          child = right
          c = es(child)
        }
        if (cmp.compare(x, c.asInstanceOf[T]) <= 0) () // break
        else {
          es(k) = c
          k = child
          loop()
        }
      }
    }
    loop()
    es(k) = x.asInstanceOf[T]
  }

  private def nBits(n: Int) = new Array[Long](((n - 1) >> 6) + 1)
  private def setBit(bits: Array[Long], i: Int): Unit = {
    bits(i >> 6) |= 1L << i
  }
  private def isClear(bits: Array[Long], i: Int) =
    (bits(i >> 6) & (1L << i)) == 0
}

@SuppressWarnings(Array("unchecked"))
@SerialVersionUID(5595510919245408276L)
class PriorityBlockingQueue[E <: AnyRef] private (
  /** Priority queue represented as a balanced binary heap: the two children of
   *  queue[n] are queue[2*n+1] and queue[2*(n+1)]. The priority queue is
   *  ordered by comparator, or by the elements' natural ordering, if comparator
   *  is null: For each node n in the heap and each descendant d of n, n <= d.
   *  The element with the lowest value is in queue[0], assuming the queue is
   *  nonempty.
   */
    private var queue: Array[E],
    /** The comparator, or null if priority queue uses elements' natural ordering.   */
    elemComparator: Comparator[_ >: E]
) extends util.AbstractQueue[E]
    with BlockingQueue[E]
    with Serializable {
  import PriorityBlockingQueue._


  /** The number of elements in the priority queue.
   */
  private var curSize = 0

  /** Lock used for all public operations.
   */
  final private val lock = new ReentrantLock

  /** Condition for blocking when empty.
   */
  final private val notEmpty: Condition = lock.newCondition()

  /** Spinlock for allocation, acquired via CAS.
   */
  private var allocationSpinLock = 0

  private val atomicAllocationSpinLock = new CAtomicInt(
    fromRawPtr(Intrinsics.classFieldRawPtr(this, "allocationSpinLock"))
  )

  /** A plain PriorityQueue used only for serialization, to maintain
   *  compatibility with previous versions of this class. Non-null only during
   *  serialization/deserialization.
   */
  private var q = null

  def this(initialCapacity: Int, comparator: Comparator[_ >: E]) = {
    this(
      queue = {
        if (initialCapacity < 1)
          throw new IllegalArgumentException()
        new Array[AnyRef](initialCapacity.max(1)).asInstanceOf[Array[E]]
      },
      comparator
    )
  }
  def this(initialCapacity: Int) = this(initialCapacity, null)
  def this() = this(PriorityBlockingQueue.DEFAULT_INITIAL_CAPACITY, null)
  def this(c: util.Collection[_ <: E]) = {
    this(
      elemComparator = c match {
        case s: SortedSet[E] @unchecked =>
          s.comparator()
        case p: PriorityBlockingQueue[E] @unchecked => p.comparator()
        case _                                      => null
      },
      queue = {
        var screen = true // true if must screen for nulls
        val hasComparator = c match {
          case s: SortedSet[_] =>
            s.comparator() != null
          case p: PriorityBlockingQueue[_] =>
            screen = false
            p.comparator() != null
          case _ => false
        }
        var es = c.toArray()
        val n = es.length
        if (c.getClass() != classOf[java.util.ArrayList[_]])
          es = Arrays.copyOf(es, n)
        if (screen && (n == 1 || hasComparator)) {
          if (es.contains(null)) throw new NullPointerException()
        }
        PriorityBlockingQueue.ensureNonEmpty(es.asInstanceOf[Array[E]])
      }
    )

    this.curSize = this.queue.length
    val heapify = c match {
      case s: SortedSet[_] => false
      case p: PriorityBlockingQueue[_] =>
        p.getClass() != classOf[PriorityBlockingQueue[_]]
      case _ => true
    }
    if (heapify) this.heapify()
  }

  /** Tries to grow array to accommodate at least one more element (but normally
   *  expand by about 50%), giving up (allowing retry) on contention (which we
   *  expect to be rare). Call only while holding lock.
   *
   *  @param array
   *    the heap array
   *  @param oldCap
   *    the length of the array
   */
  private def tryGrow(array: Array[E], oldCap: Int): Unit = {
    lock.unlock() // must release and then re-acquire main lock

    var newArray: Array[E] = null
    if (allocationSpinLock == 0 && atomicAllocationSpinLock
          .compareExchangeStrong(0, 1)) {
      try {
        val growth =
          if (oldCap < 64) oldCap + 2 // grow faster if small}
          else oldCap >> 1
        val newCap = oldCap + growth
        if (newCap < 0)
          throw new OutOfMemoryError(
            s"Required array length $oldCap + $growth is too large"
          )
        if (queue eq array)
          newArray = new Array[AnyRef](newCap).asInstanceOf[Array[E]]
      } finally allocationSpinLock = 0
    }
    if (newArray == null) { // back off if another thread is allocating
      Thread.`yield`()
    }
    lock.lock()
    if (newArray != null && (queue eq array)) {
      queue = newArray.asInstanceOf[Array[E]]
      System.arraycopy(array, 0, newArray, 0, oldCap)
    }
  }

  /** Mechanics for poll(). Call only while holding lock.
   */
  private def dequeue() = {
    // assert lock.isHeldByCurrentThread();
    val es = queue
    val result = es(0).asInstanceOf[E]
    if (result != null) {
      curSize -= 1
      val n = curSize
      val x = es(n).asInstanceOf[E]
      es(n) = null.asInstanceOf[E]
      if (n > 0) {
        val cmp = comparator()
        if (cmp == null)
          PriorityBlockingQueue.siftDownComparable(0, x, es, n)
        else
          PriorityBlockingQueue.siftDownUsingComparator(0, x, es, n, cmp)
      }
    }
    result
  }

  /** Establishes the heap invariant (described above) in the entire tree,
   *  assuming nothing about the order of the elements prior to the call. This
   *  classic algorithm due to Floyd (1964) is known to be O(curSize).
   */
  private def heapify(): Unit = {
    val es = queue
    val n = curSize
    var i = (n >>> 1) - 1
    val cmp = comparator()
    if (cmp == null) while (i >= 0) {
      PriorityBlockingQueue.siftDownComparable(i, es(i).asInstanceOf[E], es, n)
      i -= 1
    }
    else
      while (i >= 0) {
        PriorityBlockingQueue.siftDownUsingComparator(
          i,
          es(i).asInstanceOf[E],
          es,
          n,
          cmp
        )
        i -= 1
      }
  }

  /** Inserts the specified element into this priority queue.
   *
   *  @param e
   *    the element to add
   *  @return
   *    {@code true} (as specified by {@link Collection#add})
   *  @throws ClassCastException
   *    if the specified element cannot be compared with elements currently in
   *    the priority queue according to the priority queue's ordering
   *  @throws NullPointerException
   *    if the specified element is null
   */
  override def add(e: E) = offer(e)

  /** Inserts the specified element into this priority queue. As the queue is
   *  unbounded, this method will never return {@code false}.
   *
   *  @param e
   *    the element to add
   *  @return
   *    {@code true} (as specified by {@link Queue#offer})
   *  @throws ClassCastException
   *    if the specified element cannot be compared with elements currently in
   *    the priority queue according to the priority queue's ordering
   *  @throws NullPointerException
   *    if the specified element is null
   */
  override def offer(e: E) = {
    if (e == null) throw new NullPointerException
    val lock = this.lock
    lock.lock()
    var n: Int = 0
    var cap: Int = 0
    var es = queue
    while ({
      n = curSize
      es = queue
      cap = es.length
      n >= cap
    }) tryGrow(es, cap)
    try {
      val cmp = comparator()
      if (cmp == null)
        PriorityBlockingQueue.siftUpComparable(n, e, es)
      else 
        PriorityBlockingQueue.siftUpUsingComparator(n, e, es, cmp)
      curSize = n + 1
      notEmpty.signal()
    } finally lock.unlock()
    true
  }

  /** Inserts the specified element into this priority queue. As the queue is
   *  unbounded, this method will never block.
   *
   *  @param e
   *    the element to add
   *  @throws ClassCastException
   *    if the specified element cannot be compared with elements currently in
   *    the priority queue according to the priority queue's ordering
   *  @throws NullPointerException
   *    if the specified element is null
   */
  override def put(e: E): Unit = {
    offer(e) // never need to block

  }

  /** Inserts the specified element into this priority queue. As the queue is
   *  unbounded, this method will never block or return {@code false}.
   *
   *  @param e
   *    the element to add
   *  @param timeout
   *    This parameter is ignored as the method never blocks
   *  @param unit
   *    This parameter is ignored as the method never blocks
   *  @return
   *    {@code true} (as specified by {@link
   *    BlockingQueue#offer(Object,long,TimeUnit) BlockingQueue.offer})
   *  @throws ClassCastException
   *    if the specified element cannot be compared with elements currently in
   *    the priority queue according to the priority queue's ordering
   *  @throws NullPointerException
   *    if the specified element is null
   */
  override def offer(e: E, timeout: Long, unit: TimeUnit) = offer(e)
  override def poll() = {
    val lock = this.lock
    lock.lock()
    try dequeue()
    finally lock.unlock()
  }

  @throws[InterruptedException]
  override def take() = {
    val lock = this.lock
    lock.lockInterruptibly()
    var result: E = null.asInstanceOf[E]
    try while ({ result = dequeue(); result == null }) notEmpty.await()
    finally lock.unlock()
    result
  }
  @throws[InterruptedException]
  override def poll(timeout: Long, unit: TimeUnit) = {
    var nanos = unit.toNanos(timeout)
    val lock = this.lock
    lock.lockInterruptibly()
    var result: E = null.asInstanceOf[E]
    try
      while ({ result = dequeue(); result == null && nanos > 0 })
        nanos = notEmpty.awaitNanos(nanos)
    finally lock.unlock()
    result
  }

  override def peek() = {
    val lock = this.lock
    lock.lock()
    try queue(0).asInstanceOf[E]
    finally lock.unlock()
  }

  /** Returns the comparator used to order the elements in this queue, or {@code
   *  null} if this queue uses the {@linkplain Comparable natural ordering} of
   *  its elements.
   *
   *  @return
   *    the comparator used to order the elements in this queue, or {@code null}
   *    if this queue uses the natural ordering of its elements
   */
  def comparator(): Comparator[_ >: E] = this.elemComparator

  override def size(): Int = {
    val lock = this.lock
    lock.lock()
    try curSize
    finally lock.unlock()
  }

  /** Always returns {@code Integer.MAX_VALUE} because a {@code
   *  PriorityBlockingQueue} is not capacity constrained.
   *  @return
   *    {@code Integer.MAX_VALUE} always
   */
  override def remainingCapacity() = Integer.MAX_VALUE
  private def indexOf(o: Any): Int = {
    if (o != null) {
      val es = queue
      var i = 0
      val n = curSize
      while ({ i < n }) {
        if (o == es(i)) return i
        i += 1
      }
    }
    -1
  }

  /** Removes the ith element from queue.
   */
  private def removeAt(i: Int): Unit = {
    val es = queue
    val n = curSize - 1
    if (n == i) { // removed last element
      es(i) = null.asInstanceOf[E]
    } else {
      val moved = es(n)
      es(n) = null.asInstanceOf[E]
      val cmp = comparator()
      if (cmp == null)
        PriorityBlockingQueue.siftDownComparable(i, moved, es, n)
      else PriorityBlockingQueue.siftDownUsingComparator(i, moved, es, n, cmp)
      if (es(i) eq moved)
        if (cmp == null) PriorityBlockingQueue.siftUpComparable(i, moved, es)
        else PriorityBlockingQueue.siftUpUsingComparator(i, moved, es, cmp)
    }
    curSize = n
  }

  /** Removes a single instance of the specified element from this queue, if it
   *  is present. More formally, removes an element {@code e} such that {@code
   *  o.equals(e)}, if this queue contains one or more such elements. Returns
   *  {@code true} if and only if this queue contained the specified element (or
   *  equivalently, if this queue changed as a result of the call).
   *
   *  @param o
   *    element to be removed from this queue, if present
   *  @return
   *    {@code true} if this queue changed as a result of the call
   */
  override def remove(o: Any): Boolean = {
    val lock = this.lock
    lock.lock()
    try {
      val i = indexOf(o)
      if (i == -1) return false
      removeAt(i)
      true
    } finally lock.unlock()
  }

  /** Identity-based version for use in Itr.remove.
   *
   *  @param o
   *    element to be removed from this queue, if present
   */
  private[concurrent] def removeEq(o: AnyRef): Unit = {
    val lock = this.lock
    lock.lock()
    try {
      val es = queue
      var i = 0
      val n = curSize
      var break = false
      while (i < n && !break) {
        if (o eq es(i)) {
          removeAt(i)
          break = true
        }
        i += 1
      }
    } finally lock.unlock()
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
  override def contains(o: Any) = {
    val lock = this.lock
    lock.lock()
    try indexOf(o) != -1
    finally lock.unlock()
  }
  override def toString = Helpers.collectionToString(this)

  /** @throws UnsupportedOperationException
   *    {@inheritDoc}
   *  @throws ClassCastException
   *    {@inheritDoc}
   *  @throws NullPointerException
   *    {@inheritDoc}
   *  @throws IllegalArgumentException
   *    {@inheritDoc}
   */
  override def drainTo(c: util.Collection[_ >: E]) =
    drainTo(c, Integer.MAX_VALUE)
  override def drainTo(c: util.Collection[_ >: E], maxElements: Int): Int = {
    Objects.requireNonNull(c)
    if (c eq this) throw new IllegalArgumentException
    if (maxElements <= 0) return 0
    val lock = this.lock
    lock.lock()
    try {
      val n = Math.min(curSize, maxElements)
      for (i <- 0 until n) {
        c.add(queue(0).asInstanceOf[E]) // In this order, in case add() throws.
        dequeue()
      }
      n
    } finally lock.unlock()
  }

  /** Atomically removes all of the elements from this queue. The queue will be
   *  empty after this call returns.
   */
  override def clear(): Unit = {
    val lock = this.lock
    lock.lock()
    try {
      val es = queue
      var i = 0
      val n = curSize
      while ({ i < n }) {
        es(i) = null.asInstanceOf[E]
        i += 1
      }
      curSize = 0
    } finally lock.unlock()
  }

  /** Returns an array containing all of the elements in this queue. The
   *  returned array elements are in no particular order.
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
  override def toArray() = {
    val lock = this.lock
    lock.lock()
    try util.Arrays.copyOf(queue.asInstanceOf[Array[AnyRef]], curSize)
    finally lock.unlock()
  }

  /** Returns an array containing all of the elements in this queue; the runtime
   *  type of the returned array is that of the specified array. The returned
   *  array elements are in no particular order. If the queue fits in the
   *  specified array, it is returned therein. Otherwise, a new array is
   *  allocated with the runtime type of the specified array and the curSize of
   *  this queue.
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
  override def toArray[T <: AnyRef](a: Array[T]): Array[T] = {
    val lock = this.lock
    lock.lock()
    try {
      val n = curSize
      if (a.length < n) { // Make a new array of a's runtime type, but my contents:
        util.Arrays
          .copyOf(queue, curSize)
          .asInstanceOf[Array[T]]
      } else {
        System.arraycopy(queue, 0, a, 0, n)
        if (a.length > n) a(n) = null.asInstanceOf[T]
        a
      }
    } finally lock.unlock()
  }

  /** Returns an iterator over the elements in this queue. The iterator does not
   *  return the elements in any particular order.
   *
   *  <p>The returned iterator is <a
   *  href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
   *
   *  @return
   *    an iterator over the elements in this queue
   */
  override def iterator() = new Itr(toArray())

  /** Snapshot iterator that works off copy of underlying q array.
   */
  final private[concurrent] class Itr private[concurrent] (
      val array: Array[AnyRef] // Array of all elements
  ) extends util.Iterator[E] {
    private[concurrent] var cursor = 0 // index of next element to return

    private[concurrent] var lastRet =
      -1 // index of last element, or -1 if no such

    override def hasNext(): Boolean = cursor < array.length
    override def next(): E = {
      if (cursor >= array.length) throw new NoSuchElementException
      lastRet = cursor
      cursor += 1
      array(lastRet).asInstanceOf[E]
    }
    override def remove(): Unit = {
      if (lastRet < 0) throw new IllegalStateException
      removeEq(array(lastRet))
      lastRet = -1
    }
    override def forEachRemaining(action: Consumer[_ >: E]): Unit = {
      Objects.requireNonNull(action)
      val es = array
      var i = cursor
      if (i < es.length) {
        lastRet = -1
        cursor = es.length

        while (i < es.length) {
          action.accept(es(i).asInstanceOf[E])
          i += 1
        }
        lastRet = es.length - 1
      }
    }
  }

  /** Immutable snapshot spliterator that binds to elements "late".
   */
  final private[concurrent] class PBQSpliterator private[concurrent] (
      array: Array[AnyRef],
      var index: Int,
      var fence: Int
  ) extends Spliterator[E] {
    private[concurrent] def this(array: Array[AnyRef]) =
      this(array, 0, array.length)
    private[concurrent] def this() = this(toArray())

    override def trySplit(): PBQSpliterator = {
      val hi = fence
      val lo = index
      val mid = (lo + hi) >>> 1
      if (lo >= mid) null
      else {
        index = mid
        new PBQSpliterator(array, lo, index)
      }
    }
    override def forEachRemaining(action: Consumer[_ >: E]): Unit = {
      Objects.requireNonNull(action)
      val hi = fence
      val lo = index
      val es = array
      index = hi // ensure exhaustion

      for (i <- lo until hi) { action.accept(es(i).asInstanceOf[E]) }
    }

    override def tryAdvance(action: Consumer[_ >: E]): Boolean = {
      Objects.requireNonNull(action)
      if (fence > index && index >= 0) {
        val idx = index
        index += 1
        action.accept(array(idx).asInstanceOf[E])
        return true
      }
      false
    }
    override def estimateSize: Long = fence - index
    override def characteristics: Int =
      Spliterator.NONNULL | Spliterator.SIZED | Spliterator.SUBSIZED
  }

  /** Returns a {@link Spliterator} over the elements in this queue. The
   *  spliterator does not traverse elements in any particular order (the {@link
   *  Spliterator#ORDERED ORDERED} characteristic is not reported).
   *
   *  <p>The returned spliterator is <a
   *  href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
   *
   *  <p>The {@code Spliterator} reports {@link Spliterator#SIZED} and {@link
   *  Spliterator#NONNULL}.
   *
   *  @implNote
   *    The {@code Spliterator} additionally reports {@link
   *    Spliterator#SUBSIZED}.
   *
   *  @return
   *    a {@code Spliterator} over the elements in this queue
   *  @since 1.8
   */
  // override def spliterator() = new PBQSpliterator

  /** @throws NullPointerException
   *    {@inheritDoc}
   */
  override def removeIf(filter: Predicate[_ >: E]) = {
    Objects.requireNonNull(filter)
    bulkRemove(filter)
  }
  override def removeAll(c: util.Collection[_]) = {
    Objects.requireNonNull(c)
    bulkRemove((e: E) => c.contains(e))
  }
  override def retainAll(c: util.Collection[_]) = {
    Objects.requireNonNull(c)
    bulkRemove((e: E) => !c.contains(e))
  }

  /** Implementation of bulk remove methods. */
  private def bulkRemove(filter: Predicate[_ >: E]): Boolean = {
    val lock = this.lock
    lock.lock()
    try {
      val es = queue
      val end = curSize
      var i = 0
      // Optimize for initial run of survivors
      i = 0
      while ({ i < end && !filter.test(es(i).asInstanceOf[E]) }) i += 1
      if (i >= end) return false
      // Tolerate predicates that reentrantly access the
      // collection for read, so traverse once to find elements
      // to delete, a second pass to physically expunge.
      val beg = i
      val deathRow = PriorityBlockingQueue.nBits(end - beg)
      deathRow(0) = 1L // set bit 0

      i = beg + 1
      while (i < end) {
        if (filter.test(es(i).asInstanceOf[E]))
          PriorityBlockingQueue.setBit(deathRow, i - beg)
        i += 1
      }
      var w = beg
      i = beg
      while (i < end) {
        if (PriorityBlockingQueue.isClear(deathRow, i - beg)) es({
          w += 1; w - 1
        }) = es(i)
        i += 1
      }
      curSize = w
      i = curSize
      while (i < end) {
        es(i) = null.asInstanceOf[E]
        i += 1
      }
      heapify()
      true
    } finally lock.unlock()
  }
  override def forEach(action: Consumer[_ >: E]): Unit = {
    Objects.requireNonNull(action)
    val lock = this.lock
    lock.lock()
    try {
      val es = queue
      var i = 0
      val n = curSize
      while ({ i < n }) {
        action.accept(es(i).asInstanceOf[E])
        i += 1
      }
    } finally lock.unlock()
  }
}
