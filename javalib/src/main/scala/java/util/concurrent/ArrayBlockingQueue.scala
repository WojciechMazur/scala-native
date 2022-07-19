/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package java.util.concurrent

import java.lang.ref.WeakReference
import java.util
import java.util._
import java.util.concurrent.locks._
import java.util.function._

/** A bounded {@linkplain BlockingQueue blocking queue} backed by an array. This
 *  queue orders elements FIFO (first-in-first-out). The <em>head</em> of the
 *  queue is that element that has been on the queue the longest time. The
 *  <em>tail</em> of the queue is that element that has been on the queue the
 *  shortest time. New elements are inserted at the tail of the queue, and the
 *  queue retrieval operations obtain elements at the head of the queue.
 *
 *  <p>This is a classic &quot;bounded buffer&quot;, in which a fixed-sized
 *  array holds elements inserted by producers and extracted by consumers. Once
 *  created, the capacity cannot be changed. Attempts to {@code put} an element
 *  into a full queue will result in the operation blocking; attempts to {@code
 *  take} an element from an empty queue will similarly block.
 *
 *  <p>This class supports an optional fairness policy for ordering waiting
 *  producer and consumer threads. By default, this ordering is not guaranteed.
 *  However, a queue constructed with fairness set to {@code true} grants
 *  threads access in FIFO order. Fairness generally decreases throughput but
 *  reduces variability and avoids starvation.
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
@SerialVersionUID(-817911632652898426L)
object ArrayBlockingQueue {

  /** Increments i, mod modulus. Precondition and postcondition: 0 <= i <
   *  modulus.
   */
  private[concurrent] def inc(i: Int, modulus: Int) = {
    val j = i + 1
    if (j >= modulus) 0
    else j
  }

  /** Decrements i, mod modulus. Precondition and postcondition: 0 <= i <
   *  modulus.
   */
  private[concurrent] def dec(i: Int, modulus: Int) = {
    val j = i - 1
    if (j < 0) modulus - 1
    else j
  }

  /** Returns element at array index i. This is a slight abuse of generics,
   *  accepted by javac.
   */
  private[concurrent] def itemAt[E](items: Array[AnyRef], i: Int) =
    items(i).asInstanceOf[E]

  /** Nulls out slots starting at array index i, upto index end. Condition i ==
   *  end means "full" - the entire array is cleared.
   */
  private def circularClear(items: Array[AnyRef], i: Int, end: Int): Unit = {
    // assert 0 <= i && i < items.length;
    // assert 0 <= end && end < items.length;
    val to = if (i < end) end else items.length
    for (i <- i until to) items(i) = null
    if (to != end) {
      for (i <- 0 until end) items(i) = null
    }
  }

  private def nBits(n: Int) = new Array[Long](((n - 1) >> 6) + 1)
  private def setBit(bits: Array[Long], i: Int): Unit = {
    bits(i >> 6) |= 1L << i
  }
  private def isClear(bits: Array[Long], i: Int) =
    (bits(i >> 6) & (1L << i)) == 0
}

@SerialVersionUID(-817911632652898426L)
class ArrayBlockingQueue[E <: AnyRef](val capacity: Int, val fair: Boolean)
    extends util.AbstractQueue[E]
    with BlockingQueue[E]
    with Serializable {

  /** The queued items */
  private[concurrent] final var items: Array[AnyRef] = null

  /** items index for next take, poll, peek or remove */
  private[concurrent] var takeIndex = 0

  /** items index for next put, offer, or add */
  private[concurrent] var putIndex = 0

  /** Number of elements in the queue */
  private[concurrent] var count = 0

  /** Main lock guarding all access */
  final private[concurrent] var lock: ReentrantLock = null

  /** Condition for waiting takes */
  final private var notEmpty: Condition = null

  /** Condition for waiting puts */
  final private var notFull: Condition = null

  /** Shared state for currently active iterators, or null if there are known
   *  not to be any. Allows queue operations to update iterator state.
   */
  private[concurrent] var itrs: Itrs = null

  locally {
    if (capacity <= 0) throw new IllegalArgumentException
    this.items = new Array[AnyRef](capacity)
    lock = new ReentrantLock(fair)
    notEmpty = lock.newCondition()
    notFull = lock.newCondition()
  }

  /** Returns item at index i.
   */
  final private[concurrent] def itemAt(i: Int) = items(i).asInstanceOf[E]

  /** Inserts element at current put position, advances, and signals. Call only
   *  when holding lock.
   */
  private def enqueue(e: E): Unit = {
    // assert lock.isHeldByCurrentThread();
    // assert lock.getHoldCount() == 1;
    // assert items[putIndex] == null;
    val items = this.items
    items(putIndex) = e
    if ({ putIndex += 1; putIndex } == items.length) putIndex = 0
    count += 1
    notEmpty.signal()
    // checkInvariants();
  }

  /** Extracts element at current take position, advances, and signals. Call
   *  only when holding lock.
   */
  private def dequeue = {
    // assert items[takeIndex] != null;
    val items = this.items
    val e = items(takeIndex).asInstanceOf[E]
    items(takeIndex) = null
    takeIndex += 1
    if (takeIndex == items.length) takeIndex = 0
    count -= 1
    if (itrs != null) itrs.elementDequeued()
    notFull.signal()
    e
  }

  /** Deletes item at array index removeIndex. Utility for remove(Object) and
   *  iterator.remove. Call only when holding lock.
   */
  private[concurrent] def removeAt(removeIndex: Int): Unit = {
    // assert items[removeIndex] != null;
    // assert removeIndex >= 0 && removeIndex < items.length;
    val items = this.items
    if (removeIndex == takeIndex) { // removing front item; just advance
      items(takeIndex) = null
      takeIndex += 1
      if (takeIndex == items.length) takeIndex = 0
      count -= 1
      if (itrs != null) itrs.elementDequeued()
    } else { // an "interior" remove
      // slide over all others up through putIndex.
      var i = removeIndex
      val putIndex = this.putIndex
      import scala.util.control.Breaks._
      breakable {
        while (true) {
          val pred = i
          i += 1
          if (i == items.length) i = 0
          if (i == putIndex) {
            items(pred) = null
            this.putIndex = pred
            break()
          }
          items(pred) = items(i)
        }
      }
      count -= 1
      if (itrs != null) itrs.removedAt(removeIndex)
    }
    notFull.signal()
  }

  /** Creates an {@code ArrayBlockingQueue} with the given (fixed) capacity and
   *  default access policy.
   *
   *  @param capacity
   *    the capacity of this queue
   *  @throws IllegalArgumentException
   *    if {@code capacity < 1}
   */
  def this(capacity: Int) = this(capacity, false)

  /** Creates an {@code ArrayBlockingQueue} with the given (fixed) capacity, the
   *  specified access policy and initially containing the elements of the given
   *  collection, added in traversal order of the collection's iterator.
   *
   *  @param capacity
   *    the capacity of this queue
   *  @param fair
   *    if {@code true} then queue accesses for threads blocked on insertion or
   *    removal, are processed in FIFO order; if {@code false} the access order
   *    is unspecified.
   *  @param c
   *    the collection of elements to initially contain
   *  @throws IllegalArgumentException
   *    if {@code capacity} is less than {@code c.size()}, or less than 1.
   *  @throws NullPointerException
   *    if the specified collection or any of its elements are null
   */
  def this(capacity: Int, fair: Boolean, c: util.Collection[_ <: E]) = {
    this(capacity, fair)
    val lock = this.lock
    lock.lock() // Lock only for visibility, not mutual exclusion

    try {
      val items = this.items
      var i = 0
      try
        c.forEach { e =>
          items(i) = Objects.requireNonNull(e)
          i += 1
        }
      catch {
        case ex: ArrayIndexOutOfBoundsException =>
          throw new IllegalArgumentException
      }
      count = i
      putIndex =
        if (i == capacity) 0
        else i
    } finally lock.unlock()
  }

  /** Inserts the specified element at the tail of this queue if it is possible
   *  to do so immediately without exceeding the queue's capacity, returning
   *  {@code true} upon success and throwing an {@code IllegalStateException} if
   *  this queue is full.
   *
   *  @param e
   *    the element to add
   *  @return
   *    {@code true} (as specified by {@link Collection#add})
   *  @throws IllegalStateException
   *    if this queue is full
   *  @throws NullPointerException
   *    if the specified element is null
   */
  override def add(e: E): Boolean = super.add(e)

  /** Inserts the specified element at the tail of this queue if it is possible
   *  to do so immediately without exceeding the queue's capacity, returning
   *  {@code true} upon success and {@code false} if this queue is full. This
   *  method is generally preferable to method {@link #add}, which can fail to
   *  insert an element only by throwing an exception.
   *
   *  @throws NullPointerException
   *    if the specified element is null
   */
  override def offer(e: E): Boolean = {
    Objects.requireNonNull(e)
    val lock = this.lock
    lock.lock()
    try
      if (count == items.length) false
      else {
        enqueue(e)
        true
      }
    finally lock.unlock()
  }

  /** Inserts the specified element at the tail of this queue, waiting for space
   *  to become available if the queue is full.
   *
   *  @throws InterruptedException
   *    {@inheritDoc}
   *  @throws NullPointerException
   *    {@inheritDoc}
   */
  @throws[InterruptedException]
  override def put(e: E): Unit = {
    Objects.requireNonNull(e)
    val lock = this.lock
    lock.lockInterruptibly()
    try {
      while (count == items.length) notFull.await()
      enqueue(e)
    } finally lock.unlock()
  }

  /** Inserts the specified element at the tail of this queue, waiting up to the
   *  specified wait time for space to become available if the queue is full.
   *
   *  @throws InterruptedException
   *    {@inheritDoc}
   *  @throws NullPointerException
   *    {@inheritDoc}
   */
  @throws[InterruptedException]
  override def offer(e: E, timeout: Long, unit: TimeUnit): Boolean = {
    Objects.requireNonNull(e)
    var nanos = unit.toNanos(timeout)
    val lock = this.lock
    lock.lockInterruptibly()
    try {
      while (count == items.length) {
        if (nanos <= 0L) return false
        nanos = notFull.awaitNanos(nanos)
      }
      enqueue(e)
      true
    } finally lock.unlock()
  }

  override def poll(): E = {
    val lock = this.lock
    lock.lock()
    try
      if (count == 0) null.asInstanceOf[E]
      else dequeue
    finally lock.unlock()
  }
  @throws[InterruptedException]
  override def take(): E = {
    val lock = this.lock
    lock.lockInterruptibly()
    try {
      while ({ count == 0 }) notEmpty.await()
      dequeue
    } finally lock.unlock()
  }
  @throws[InterruptedException]
  override def poll(timeout: Long, unit: TimeUnit): E = {
    var nanos = unit.toNanos(timeout)
    val lock = this.lock
    lock.lockInterruptibly()
    try {
      while ({ count == 0 }) {
        if (nanos <= 0L) return null.asInstanceOf[E]
        nanos = notEmpty.awaitNanos(nanos)
      }
      dequeue
    } finally lock.unlock()
  }

  override def peek(): E = {
    val lock = this.lock
    lock.lock()
    try itemAt(takeIndex) // null when queue is empty

    finally lock.unlock()
  }

  /** Returns the number of elements in this queue.
   *
   *  @return
   *    the number of elements in this queue
   */
  override def size(): Int = {
    val lock = this.lock
    lock.lock()
    try count
    finally lock.unlock()
  }

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
  override def remainingCapacity(): Int = {
    val lock = this.lock
    lock.lock()
    try items.length - count
    finally lock.unlock()
  }

  /** Removes a single instance of the specified element from this queue, if it
   *  is present. More formally, removes an element {@code e} such that {@code
   *  o.equals(e)}, if this queue contains one or more such elements. Returns
   *  {@code true} if this queue contained the specified element (or
   *  equivalently, if this queue changed as a result of the call).
   *
   *  <p>Removal of interior elements in circular array based queues is an
   *  intrinsically slow and disruptive operation, so should be undertaken only
   *  in exceptional circumstances, ideally only when the queue is known not to
   *  be accessible by other threads.
   *
   *  @param o
   *    element to be removed from this queue, if present
   *  @return
   *    {@code true} if this queue changed as a result of the call
   */
  override def remove(o: Any): Boolean = {
    if (o == null) return false
    val lock = this.lock
    lock.lock()
    try {
      if (count > 0) {
        val items = this.items
        var i = takeIndex
        val end = putIndex
        var to =
          if (i < end) end
          else items.length
        while (true) {
          while ({ i < to }) {
            if (o == items(i)) {
              removeAt(i)
              return true
            }
            i += 1
          }
          if (to == end) return false // todo: break is not supported

          i = 0
          to = end
        }
      }
      false
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
  override def contains(o: Any): Boolean = {
    if (o == null) return false
    val lock = this.lock
    lock.lock()
    try {
      if (count > 0) {
        val items = this.items
        var i = takeIndex
        val end = putIndex
        var to =
          if (i < end) end
          else items.length
        while ({ true }) {
          while ({ i < to }) {
            if (o == items(i)) return true
            i += 1
          }
          if (to == end) return false

          i = 0
          to = end
        }
      }
      false
    } finally lock.unlock()
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
    val lock = this.lock
    lock.lock()
    try {
      val items = this.items
      val end = takeIndex + count
      val a = util.Arrays.copyOfRange(items, takeIndex, end)
      if (end != putIndex)
        System.arraycopy(items, 0, a, items.length - takeIndex, putIndex)
      a
    } finally lock.unlock()
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
  override def toArray[T <: AnyRef](_a: Array[T]): Array[T] = {
    var a: Array[T] = null
    val lock = this.lock
    lock.lock()
    try {
      val items = this.items
      val count = this.count
      val firstLeg = Math.min(items.length - takeIndex, count)
      if (a.length < count)
        a = util.Arrays
          .copyOfRange(items, takeIndex, takeIndex + count)
          .asInstanceOf[Array[T]]
      else {
        System.arraycopy(items, takeIndex, a, 0, firstLeg)
        if (a.length > count) a(count) = null.asInstanceOf[T]
      }
      if (firstLeg < count) System.arraycopy(items, 0, a, firstLeg, putIndex)
      a
    } finally lock.unlock()
  }

  override def toString(): String = Helpers.collectionToString(this)

  /** Atomically removes all of the elements from this queue. The queue will be
   *  empty after this call returns.
   */
  override def clear(): Unit = {
    val lock = this.lock
    lock.lock()
    try {
      var k = count
      if (k > 0) {
        ArrayBlockingQueue.circularClear(items, takeIndex, putIndex)
        takeIndex = putIndex
        count = 0
        if (itrs != null) itrs.queueIsEmpty()
        while (k > 0 && lock.hasWaiters(notFull)) {
          notFull.signal()
          k -= 1
        }
      }
    } finally lock.unlock()
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
    val items = this.items
    val lock = this.lock
    lock.lock()
    try {
      val n = Math.min(maxElements, count)
      var take = takeIndex
      var i = 0
      try {
        while (i < n) {
          val e = items(take).asInstanceOf[E]
          c.add(e)
          items(take) = null
          if ({ take += 1; take } == items.length) take = 0
          i += 1
        }
        n
      } finally {
        // Restore invariants even if c.add() threw
        if (i > 0) {
          count -= i
          takeIndex = take
          if (itrs != null)
            if (count == 0) itrs.queueIsEmpty()
            else if (i > take) itrs.takeIndexWrapped()

          while ({ i > 0 && lock.hasWaiters(notFull) }) {
            notFull.signal()
            i -= 1
          }
        }
      }
    } finally lock.unlock()
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
  override def iterator(): Iterator[E] = new Itr

  /** Shared data between iterators and their queue, allowing queue
   *  modifications to update iterators when elements are removed.
   *
   *  This adds a lot of complexity for the sake of correctly handling some
   *  uncommon operations, but the combination of circular-arrays and supporting
   *  interior removes (i.e., those not at head) would cause iterators to
   *  sometimes lose their places and/or (re)report elements they shouldn't. To
   *  avoid this, when a queue has one or more iterators, it keeps iterator
   *  state consistent by:
   *
   *  (1) keeping track of the number of "cycles", that is, the number of times
   *  takeIndex has wrapped around to 0. (2) notifying all iterators via the
   *  callback removedAt whenever an interior element is removed (and thus other
   *  elements may be shifted).
   *
   *  These suffice to eliminate iterator inconsistencies, but unfortunately add
   *  the secondary responsibility of maintaining the list of iterators. We
   *  track all active iterators in a simple linked list (accessed only when the
   *  queue's lock is held) of weak references to The list is cleaned up using 3
   *  different mechanisms:
   *
   *  (1) Whenever a new iterator is created, do some O(1) checking for stale
   *  list elements.
   *
   *  (2) Whenever takeIndex wraps around to 0, check for iterators that have
   *  been unused for more than one wrap-around cycle.
   *
   *  (3) Whenever the queue becomes empty, all iterators are notified and this
   *  entire data structure is discarded.
   *
   *  So in addition to the removedAt callback that is necessary for
   *  correctness, iterators have the shutdown and takeIndexWrapped callbacks
   *  that help remove stale iterators from the list.
   *
   *  Whenever a list element is examined, it is expunged if either the GC has
   *  determined that the iterator is discarded, or if the iterator reports that
   *  it is "detached" (does not need any further state updates). Overhead is
   *  maximal when takeIndex never advances, iterators are discarded before they
   *  are exhausted, and all removals are interior removes, in which case all
   *  stale iterators are discovered by the GC. But even in this case we don't
   *  increase the amortized complexity.
   *
   *  Care must be taken to keep list sweeping methods from reentrantly invoking
   *  another such method, causing subtle corruption bugs.
   */
  private[concurrent] object Itrs {
    private val SHORT_SWEEP_PROBES = 4
    private val LONG_SWEEP_PROBES = 16
  }
  private[concurrent] class Itrs private[concurrent] (val initial: Itr) {
    register(initial)

    /** Node in a linked list of weak iterator references.
     */
    private[concurrent] class Node private[concurrent] (
        val iterator: Itr,
        var next: Node
    ) extends WeakReference[Itr](iterator) {}

    /** Incremented whenever takeIndex wraps around to 0 */
    private[concurrent] var cycles = 0

    /** Linked list of weak iterator references */
    private var head: Node = null

    /** Used to expunge stale iterators */
    private var sweeper: Node = null

    /** Sweeps itrs, looking for and expunging stale iterators. If at least one
     *  was found, tries harder to find more. Called only from iterating thread.
     *
     *  @param tryHarder
     *    whether to start in try-harder mode, because there is known to be at
     *    least one iterator to collect
     */
    private[concurrent] def doSomeSweeping(tryHarder: Boolean): Unit = {
      // assert head != null;
      val probes =
        if (tryHarder) Itrs.LONG_SWEEP_PROBES
        else Itrs.SHORT_SWEEP_PROBES
      var o: Node = null
      var p: Node = null
      val sweeper = this.sweeper
      var passedGo = false // to limit search to one full sweep
      if (sweeper == null) {
        o = null
        p = head
        passedGo = true
      } else {
        o = sweeper
        p = o.next
        passedGo = false
      }

      @annotation.tailrec
      def loop(probes: Int): Unit = {
        if (probes > 0) {
          if (p == null && passedGo) ()
          else {
            if (p == null) {
              o = null
              p = head
              passedGo = true
            }
            val it = p.get()
            val next = p.next
            val nextProbes =
              if (it == null || it.isDetached) {
                // found a discarded/exhausted iterator
                // unlink p
                p.clear()
                p.next = null
                if (o == null) {
                  head = next
                  if (next == null) {
                    // We've run out of iterators to track; retire
                    itrs = null
                    return ()
                  }
                } else o.next = next
                Itrs.LONG_SWEEP_PROBES // "try harder"
              } else {
                o = p
                probes - 1
              }
            p = next
            loop(nextProbes)
          }
        }
      }

      loop(probes)
      this.sweeper =
        if (p == null) null
        else o
    }

    /** Adds a new iterator to the linked list of tracked iterators.
     */
    private[concurrent] def register(itr: Itr): Unit = {
      head = new Node(itr, head)
    }

    /** Called whenever takeIndex wraps around to 0.
     *
     *  Notifies all iterators, and expunges any that are now stale.
     */
    private[concurrent] def takeIndexWrapped(): Unit = {
      cycles += 1
      var o: Node = null
      var p: Node = head
      while (p != null) {
        val it = p.get()
        val next = p.next
        if (it == null || it.takeIndexWrapped) { // assert it == null || it.isDetached();
          p.clear()
          p.next = null
          if (o == null) head = next
          else o.next = next
        } else o = p
        p = next
      }
      if (head == null) { // no more iterators to track
        itrs = null
      }
    }

    /** Called whenever an interior remove (not at takeIndex) occurred.
     *
     *  Notifies all iterators, and expunges any that are now stale.
     */
    private[concurrent] def removedAt(removedIndex: Int): Unit = {
      var o: Node = null
      var p: Node = head
      while (p != null) {
        val it = p.get()
        val next = p.next
        if (it == null || it.removedAt(removedIndex)) {
          p.clear()
          p.next = null
          if (o == null) head = next
          else o.next = next
        } else o = p
        p = next
      }
      if (head == null) {
        itrs = null
      }
    }

    /** Called whenever the queue becomes empty.
     *
     *  Notifies all active iterators that the queue is empty, clears all weak
     *  refs, and unlinks the itrs datastructure.
     */
    private[concurrent] def queueIsEmpty(): Unit = {
      var p = head
      while (p != null) {
        val it = p.get()
        if (it != null) {
          p.clear()
          it.shutdown()
        }

        p = p.next
      }
      head = null
      itrs = null
    }

    /** Called whenever an element has been dequeued (at takeIndex).
     */
    private[concurrent] def elementDequeued(): Unit = {
      if (count == 0) queueIsEmpty()
      else if (takeIndex == 0) takeIndexWrapped()
    }
  }

  /** Iterator for ArrayBlockingQueue.
   *
   *  To maintain weak consistency with respect to puts and takes, we read ahead
   *  one slot, so as to not report hasNext true but then not have an element to
   *  return.
   *
   *  We switch into "detached" mode (allowing prompt unlinking from itrs
   *  without help from the GC) when all indices are negative, or when hasNext
   *  returns false for the first time. This allows the iterator to track
   *  concurrent updates completely accurately, except for the corner case of
   *  the user calling Iterator.remove() after hasNext() returned false. Even in
   *  this case, we ensure that we don't remove the wrong element by keeping
   *  track of the expected element to remove, in lastItem. Yes, we may fail to
   *  remove lastItem from the queue if it moved due to an interleaved interior
   *  remove while in detached mode.
   *
   *  Method forEachRemaining, added in Java 8, is treated similarly to hasNext
   *  returning false, in that we switch to detached mode, but we regard it as
   *  an even stronger request to "close" this iteration, and don't bother
   *  supporting subsequent remove().
   */
  private object Itr {

    /** Special index value indicating "not available" or "undefined" */
    private val NONE = -1

    /** Special index value indicating "removed elsewhere", that is, removed by
     *  some operation other than a call to this.remove().
     */
    private val REMOVED = -2

    /** Special value for prevTakeIndex indicating "detached mode" */
    private val DETACHED = -3
  }

  private[concurrent] class Itr private[concurrent] ()
      extends util.Iterator[E] {
    import Itr._

    /** Index to look for new nextItem; NONE at end */
    private var cursor = 0

    /** Element to be returned by next call to next(); null if none */
    private var nextItem: E = _

    /** Index of nextItem; NONE if none, REMOVED if removed elsewhere */
    private var nextIndex = 0

    /** Last element returned; null if none or not detached. */
    private var lastItem: E = _

    /** Index of lastItem, NONE if none, REMOVED if removed elsewhere */
    private var lastRet = 0

    /** Previous value of takeIndex, or DETACHED when detached */
    private var prevTakeIndex = 0

    /** Previous value of iters.cycles */
    private var prevCycles = 0

    locally {
      lastRet = NONE
      val lock = ArrayBlockingQueue.this.lock
      lock.lock()
      try
        if (count == 0) {
          // assert itrs == null;
          cursor = NONE
          nextIndex = NONE
          prevTakeIndex = DETACHED
        } else {
          val takeIndex = ArrayBlockingQueue.this.takeIndex
          prevTakeIndex = takeIndex
          nextIndex = takeIndex
          nextItem = itemAt(nextIndex)
          cursor = incCursor(takeIndex)
          if (itrs == null)
            itrs = new Itrs(this)
          else {
            itrs.register(this) // in this order
            itrs.doSomeSweeping(false)
          }
          prevCycles = itrs.cycles
          // assert takeIndex >= 0;
          // assert prevTakeIndex == takeIndex;
          // assert nextIndex >= 0;
          // assert nextItem != null;
        }
      finally lock.unlock()
    }

    private[concurrent] def isDetached = prevTakeIndex < 0
    private def incCursor(index: Int) = {
      var idx = index + 1
      if (idx == items.length) idx = 0
      if (idx == putIndex) idx = NONE
      idx
    }

    /** Returns true if index is invalidated by the given number of dequeues,
     *  starting from prevTakeIndex.
     */
    private def invalidated(
        index: Int,
        prevTakeIndex: Int,
        dequeues: Long,
        length: Int
    ): Boolean = {
      if (index < 0) return false
      var distance = index - prevTakeIndex
      if (distance < 0) distance += length
      dequeues > distance
    }

    /** Adjusts indices to incorporate all dequeues since the last operation on
     *  this iterator. Call only from iterating thread.
     */
    private def incorporateDequeues(): Unit = {
      // assert lock.isHeldByCurrentThread();
      // assert itrs != null;
      // assert !isDetached();
      // assert count > 0;
      val cycles = itrs.cycles
      val takeIndex = ArrayBlockingQueue.this.takeIndex
      val prevCycles = this.prevCycles
      val prevTakeIndex = this.prevTakeIndex
      if (cycles != prevCycles || takeIndex != prevTakeIndex) {
        val len = items.length
        // how far takeIndex has advanced since the previous
        // operation of this iterator
        val dequeues =
          (cycles - prevCycles).toLong * len + (takeIndex - prevTakeIndex)
        // Check indices for invalidation
        if (invalidated(lastRet, prevTakeIndex, dequeues, len))
          lastRet = REMOVED
        if (invalidated(nextIndex, prevTakeIndex, dequeues, len))
          nextIndex = REMOVED
        if (invalidated(cursor, prevTakeIndex, dequeues, len))
          cursor = takeIndex
        if (cursor < 0 && nextIndex < 0 && lastRet < 0) detach()
        else {
          this.prevCycles = cycles
          this.prevTakeIndex = takeIndex
        }
      }
    }

    /** Called when itrs should stop tracking this iterator, either because
     *  there are no more indices to update (cursor < 0 && nextIndex < 0 &&
     *  lastRet < 0) or as a special exception, when lastRet >= 0, because
     *  hasNext() is about to return false for the first time. Call only from
     *  iterating thread.
     */
    private def detach(): Unit = {
      // Switch to detached mode
      // assert cursor == NONE;
      // assert nextIndex < 0;
      // assert lastRet < 0 || nextItem == null;
      // assert lastRet < 0 ^ lastItem != null;
      if (prevTakeIndex >= 0) {
        prevTakeIndex = DETACHED
        // try to unlink from itrs (but not too hard)
        itrs.doSomeSweeping(true)
      }
    }

    /** For performance reasons, we would like not to acquire a lock in hasNext
     *  in the common case. To allow for this, we only access fields (i.e.
     *  nextItem) that are not modified by update operations triggered by queue
     *  modifications.
     */
    override def hasNext(): Boolean = {
      if (nextItem != null) true
      else {
        noNext()
        false
      }
    }

    private def noNext(): Unit = {
      val lock = ArrayBlockingQueue.this.lock
      lock.lock()
      try // assert nextIndex == NONE;
        if (!isDetached) { // assert lastRet >= 0;
          incorporateDequeues() // might update lastRet

          if (lastRet >= 0) {
            lastItem = itemAt(lastRet)
            // assert lastItem != null;
            detach()
          }
        }
      finally lock.unlock()
    }

    override def next(): E = {
      val e = nextItem
      if (e == null) throw new NoSuchElementException
      val lock = ArrayBlockingQueue.this.lock
      lock.lock()
      try {
        if (!isDetached) incorporateDequeues()
        // assert nextIndex != NONE;
        // assert lastItem == null;
        lastRet = nextIndex
        val cursor = this.cursor
        if (cursor >= 0) {
          nextIndex = cursor
          nextItem = itemAt(nextIndex)
          this.cursor = incCursor(cursor)
        } else {
          nextIndex = NONE
          nextItem = null.asInstanceOf[E]
          if (lastRet == REMOVED) detach()
        }
      } finally lock.unlock()
      e
    }
    override def forEachRemaining(action: Consumer[_ >: E]): Unit = {
      Objects.requireNonNull(action)
      val lock = ArrayBlockingQueue.this.lock
      lock.lock()
      try {
        val e = nextItem
        if (e == null) return
        if (!isDetached) incorporateDequeues()
        action.accept(e)
        if (isDetached || cursor < 0) return
        val items = ArrayBlockingQueue.this.items
        var i = cursor
        val end = putIndex
        var to =
          if (i < end) end
          else items.length
        @annotation.tailrec
        def loop(): Unit = {
          while (i < to) {
            action.accept(ArrayBlockingQueue.itemAt(items, i))
            i += 1
          }
          if (to != end) {
            i = 0
            to = end
            loop()
          }
        }
        loop()
      } finally {
        // Calling forEachRemaining is a strong hint that this
        // iteration is surely over; supporting remove() after
        // forEachRemaining() is more trouble than it's worth
        lastRet = NONE
        nextIndex = lastRet
        cursor = lastRet
        lastItem = null.asInstanceOf[E]
        nextItem = lastItem
        detach()
        lock.unlock()
      }
    }
    override def remove(): Unit = {
      val lock = ArrayBlockingQueue.this.lock
      lock.lock()
      try {
        if (!isDetached)
          incorporateDequeues() // might update lastRet or detach
        val lastRet = this.lastRet
        this.lastRet = NONE
        if (lastRet >= 0) {
          if (!isDetached) removeAt(lastRet)
          else {
            val lastItem = this.lastItem
            this.lastItem = null.asInstanceOf[E]
            if (itemAt(lastRet) eq lastItem)
              removeAt(lastRet)
          }
        } else if (lastRet == NONE) throw new IllegalStateException
        // else lastRet == REMOVED and the last returned element was
        // previously asynchronously removed via an operation other
        // than this.remove(), so nothing to do.
        if (cursor < 0 && nextIndex < 0)
          detach()
      } finally lock.unlock()
    }

    /** Called to notify the iterator that the queue is empty, or that it has
     *  fallen hopelessly behind, so that it should abandon any further
     *  iteration, except possibly to return one more element from next(), as
     *  promised by returning true from hasNext().
     */
    private[concurrent] def shutdown(): Unit = {
      cursor = NONE
      if (nextIndex >= 0) nextIndex = REMOVED
      if (lastRet >= 0) {
        lastRet = REMOVED
        lastItem = null.asInstanceOf[E]
      }
      prevTakeIndex = DETACHED
      // Don't set nextItem to null because we must continue to be
      // able to return it on next().
      //
      // Caller will unlink from itrs when convenient.
    }
    private def distance(index: Int, prevTakeIndex: Int, length: Int) = {
      var distance = index - prevTakeIndex
      if (distance < 0) distance += length
      distance
    }

    /** Called whenever an interior remove (not at takeIndex) occurred.
     *
     *  @return
     *    true if this iterator should be unlinked from itrs
     */
    private[concurrent] def removedAt(removedIndex: Int): Boolean = {
      if (isDetached) return true
      val takeIndex = ArrayBlockingQueue.this.takeIndex
      val prevTakeIndex = this.prevTakeIndex
      val len = items.length
      // distance from prevTakeIndex to removedIndex
      val removedDistance =
        len * (itrs.cycles - this.prevCycles + {
          if (removedIndex < takeIndex) 1 else 0
        }) + (removedIndex - prevTakeIndex)
      // assert itrs.cycles - this.prevCycles >= 0;
      // assert itrs.cycles - this.prevCycles <= 1;
      // assert removedDistance > 0;
      // assert removedIndex != takeIndex;
      var cursor = this.cursor
      if (cursor >= 0) {
        val x = distance(cursor, prevTakeIndex, len)
        if (x == removedDistance)
          if (cursor == putIndex) {
            cursor = NONE
            this.cursor = NONE
          } else if (x > removedDistance) { // assert cursor != prevTakeIndex;
            this.cursor = ArrayBlockingQueue.dec(cursor, len)
            cursor = this.cursor
          }
      }
      var lastRet = this.lastRet
      if (lastRet >= 0) {
        val x = distance(lastRet, prevTakeIndex, len)
        if (x == removedDistance) {
          lastRet = REMOVED
          this.lastRet = lastRet
        } else if (x > removedDistance) {
          lastRet = ArrayBlockingQueue.dec(lastRet, len)
          this.lastRet = lastRet

        }
      }
      var nextIndex = this.nextIndex
      if (nextIndex >= 0) {
        val x = distance(nextIndex, prevTakeIndex, len)
        if (x == removedDistance) {
          nextIndex = REMOVED
          this.nextIndex = nextIndex
        } else if (x > removedDistance) {
          nextIndex = ArrayBlockingQueue.dec(nextIndex, len)
          this.nextIndex = nextIndex
        }
      }
      if (cursor < 0 && nextIndex < 0 && lastRet < 0) {
        this.prevTakeIndex = DETACHED
        true
      } else false
    }

    /** Called whenever takeIndex wraps around to zero.
     *
     *  @return
     *    true if this iterator should be unlinked from itrs
     */
    private[concurrent] def takeIndexWrapped: Boolean = {
      if (isDetached) return true
      if (itrs.cycles - prevCycles > 1) { // All the elements that existed at the time of the last
        // operation are gone, so abandon further iteration.
        shutdown()
        return true
      }
      false
    }
    //         /** Uncomment for debugging. */
    //         public String toString() {
    //             return ("cursor=" + cursor + " " +
    //                     "nextIndex=" + nextIndex + " " +
    //                     "lastRet=" + lastRet + " " +
    //                     "nextItem=" + nextItem + " " +
    //                     "lastItem=" + lastItem + " " +
    //                     "prevCycles=" + prevCycles + " " +
    //                     "prevTakeIndex=" + prevTakeIndex + " " +
    //                     "size()=" + size() + " " +
    //                     "remainingCapacity()=" + remainingCapacity());
    //         }
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
  def spliterator(): Spliterator[E] = Spliterators.spliterator(
    this,
    Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.CONCURRENT
  )

  /** @throws NullPointerException
   *    {@inheritDoc}
   */
  override def forEach(action: Consumer[_ >: E]): Unit = {
    Objects.requireNonNull(action)
    val lock = this.lock
    lock.lock()
    try
      if (count > 0) {
        val items = this.items
        var i = takeIndex
        val end = putIndex
        var to =
          if (i < end) end
          else items.length
        @annotation.tailrec
        def loop(): Unit = {
          while (i < to) {
            action.accept(ArrayBlockingQueue.itemAt(items, i))
            i += 1
          }
          if (to == end) ()
          else {
            i = 0
            to = end
            loop()
          }
        }
        loop()
      }
    finally lock.unlock()
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

  /** Implementation of bulk remove methods. */
  private def bulkRemove(filter: Predicate[_ >: E]): Boolean = {
    val lock = this.lock
    lock.lock()
    try
      if (itrs == null) { // check for active iterators
        if (count > 0) {
          val items = this.items
          // Optimize for initial run of survivors
          val start = takeIndex
          val end = putIndex
          val to =
            if (start < end) end
            else items.length

          def findInRange(range: Range) = range
            .find(i => filter.test(ArrayBlockingQueue.itemAt(items, i)))
            .map(bulkRemoveModified(filter, _))
            .getOrElse(false)

          return findInRange(start until to) ||
            (to != end && findInRange(0 until end))
        }
        return false
      }
    finally lock.unlock()
    // Active iterators are too hairy!
    // Punting (for now) to the slow n^2 algorithm ...
    super.removeIf(filter)
  }

  /** Returns circular distance from i to j, disambiguating i == j to
   *  items.length; never returns 0.
   */
  private def distanceNonEmpty(i: Int, _j: Int) = {
    var j = i - _j
    if (j <= 0) j += items.length
    j
  }

  /** Helper for bulkRemove, in case of at least one deletion. Tolerate
   *  predicates that reentrantly access the collection for read (but not
   *  write), so traverse once to find elements to delete, a second pass to
   *  physically expunge.
   *
   *  @param beg
   *    valid index of first element to be deleted
   */
  private def bulkRemoveModified(filter: Predicate[_ >: E], beg: Int) = {
    val es = items
    val capacity = items.length
    val end = putIndex
    val deathRow = ArrayBlockingQueue.nBits(distanceNonEmpty(beg, putIndex))
    deathRow(0) = 1L // set bit 0

    var i = beg + 1
    var to =
      if (i <= end) end
      else es.length
    var k = beg
    @annotation.tailrec
    def loop(): Unit = {
      while (i < to) {
        if (filter.test(ArrayBlockingQueue.itemAt(es, i)))
          ArrayBlockingQueue.setBit(deathRow, i - k)
        i += 1
      }
      if (to == end) ()
      else {
        i = 0
        to = end
        k -= capacity
        loop()
      }
    }
    loop()
    // a two-finger traversal, with hare i reading, tortoise w writing
    var w = beg
    i = beg + 1
    to =
      if (i <= end) end
      else es.length
    k = beg
    import scala.util.control.Breaks._
    breakable {
      while (true) { // w rejoins i on second leg
        // In this loop, i and w are on the same leg, with i > w
        while (i < to) {
          if (ArrayBlockingQueue.isClear(deathRow, i - k)) {
            es(w) = es(i)
            w += 1
          }
          i += 1
        }
        if (to == end) break()
        // In this loop, w is on the first leg, i on the second
        i = 0
        to = end
        k -= capacity
        while (i < to && w < capacity) {
          if (ArrayBlockingQueue.isClear(deathRow, i - k)) {
            es(w) = es(i)
            w += 1
          }
          i += 1
        }
        if (i >= to) {
          if (w == capacity) w = 0 // "corner" case
          break()

        }

        w = 0
      }
    }
    count -= distanceNonEmpty(w, end)
    putIndex = w
    ArrayBlockingQueue.circularClear(es, putIndex, end)
    true
  }

  /** debugging */
  private[concurrent] def checkInvariants(): Unit = { // meta-assertions
    if (!invariantsSatisfied) {
      val detail = String.format(
        "takeIndex=%d putIndex=%d count=%d capacity=%d items=%s",
        takeIndex,
        putIndex,
        count,
        items.length,
        util.Arrays.toString(items)
      )
      System.err.println(detail)
      throw new AssertionError(detail)
    }
  }
  private def invariantsSatisfied = {
    // Unlike ArrayDeque, we have a count field but no spare slot.
    // We prefer ArrayDeque's strategy (and the names of its fields!),
    // but our field layout is baked into the serial form, and so is
    // too annoying to change.
    // putIndex == takeIndex must be disambiguated by checking count.
    val capacity = items.length
    capacity > 0 &&
      (items.getClass == classOf[Array[AnyRef]]) &&
      (takeIndex | putIndex | count) >= 0 &&
      takeIndex < capacity &&
      putIndex < capacity &&
      count <= capacity &&
      (putIndex - takeIndex - count) % capacity == 0 &&
      (count == 0 || items(takeIndex) != null) &&
      (count == capacity || items(putIndex) == null) &&
      (count == 0 || items(ArrayBlockingQueue.dec(putIndex, capacity)) != null)
  }

  // /** Reconstitutes this queue from a stream (that is, deserializes it).
  //  *
  //  *  @param s
  //  *    the stream
  //  *  @throws ClassNotFoundException
  //  *    if the class of a serialized object could not be found
  //  *  @throws java.io.InvalidObjectException
  //  *    if invariants are violated
  //  *  @throws java.io.IOException
  //  *    if an I/O error occurs
  //  */
  // @throws[java.io.IOException]
  // @throws[ClassNotFoundException]
  // private def readObject(s: ObjectInputStream): Unit = { // Read in items array and various fields
  //   s.defaultReadObject()
  //   if (!invariantsSatisfied)
  //     throw new InvalidObjectException("invariants violated")
  // }
}
