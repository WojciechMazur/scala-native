/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent.locks

import java.util.ArrayList
import java.util.Collection
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.RejectedExecutionException
import scala.annotation.tailrec
import scala.util.control.Breaks._
import java.util.concurrent.atomic.{AtomicReference, AtomicInteger}

/**
 * Provides a framework for implementing blocking locks and related
 * synchronizers (semaphores, events, etc) that rely on
 * first-in-first-out (FIFO) wait queues.  This class is designed to
 * be a useful basis for most kinds of synchronizers that rely on a
 * single atomic {@code int} value to represent state. Subclasses
 * must define the protected methods that change this state, and which
 * define what that state means in terms of this object being acquired
 * or released.  Given these, the other methods in this class carry
 * out all queuing and blocking mechanics. Subclasses can maintain
 * other state fields, but only the atomically updated {@code int}
 * value manipulated using methods {@link #getState()}, {@link
 * #setState} and {@link #compareAndSetState} is tracked with respect
 * to synchronization.
 *
 * <p>Subclasses should be defined as non-public internal helper
 * classes that are used to implement the synchronization properties
 * of their enclosing class.  Class
 * {@code AbstractQueuedSynchronizer} does not implement any
 * synchronization interface.  Instead it defines methods such as
 * {@link #acquireInterruptibly} that can be invoked as
 * appropriate by concrete locks and related synchronizers to
 * implement their public methods.
 *
 * <p>This class supports either or both a default <em>exclusive</em>
 * mode and a <em>shared</em> mode. When acquired in exclusive mode,
 * attempted acquires by other threads cannot succeed. Shared mode
 * acquires by multiple threads may (but need not) succeed. This class
 * does not &quot;understand&quot; these differences except in the
 * mechanical sense that when a shared mode acquire succeeds, the next
 * waiting thread (if one exists) must also determine whether it can
 * acquire as well. Threads waiting in the different modes share the
 * same FIFO queue. Usually, implementation subclasses support only
 * one of these modes, but both can come into play for example in a
 * {@link ReadWriteLock}. Subclasses that support only exclusive or
 * only shared modes need not define the methods supporting the unused mode.
 *
 * <p>This class defines a nested {@link ConditionObject} class that
 * can be used as a {@link Condition} implementation by subclasses
 * supporting exclusive mode for which method {@link
 * #isHeldExclusively()} reports whether synchronization is exclusively
 * held with respect to the current thread, method {@link #release}
 * invoked with the current {@link #getState()} value fully releases
 * this object, and {@link #acquire}, given this saved state value,
 * eventually restores this object to its previous acquired state.  No
 * {@code AbstractQueuedSynchronizer} method otherwise creates such a
 * condition, so if this constraint cannot be met, do not use it.  The
 * behavior of {@link ConditionObject} depends of course on the
 * semantics of its synchronizer implementation.
 *
 * <p>This class provides inspection, instrumentation, and monitoring
 * methods for the internal queue, as well as similar methods for
 * condition objects. These can be exported as desired into classes
 * using an {@code AbstractQueuedSynchronizer} for their
 * synchronization mechanics.
 *
 * <p>Serialization of this class stores only the underlying atomic
 * integer maintaining state, so deserialized objects have empty
 * thread queues. Typical subclasses requiring serializability will
 * define a {@code readObject} method that restores this to a known
 * initial state upon deserialization.
 *
 * <h2>Usage</h2>
 *
 * <p>To use this class as the basis of a synchronizer, redefine the
 * following methods, as applicable, by inspecting and/or modifying
 * the synchronization state using {@link #getState()}, {@link
 * #setState} and/or {@link #compareAndSetState}:
 *
 * <ul>
 * <li>{@link #tryAcquire}
 * <li>{@link #tryRelease}
 * <li>{@link #tryAcquireShared}
 * <li>{@link #tryReleaseShared}
 * <li>{@link #isHeldExclusively()}
 * </ul>
 *
 * Each of these methods by default throws {@link
 * UnsupportedOperationException}.  Implementations of these methods
 * must be internally thread-safe, and should in general be short and
 * not block. Defining these methods is the <em>only</em> supported
 * means of using this class. All other methods are declared
 * {@code final} because they cannot be independently varied.
 *
 * <p>You may also find the inherited methods from {@link
 * AbstractOwnableSynchronizer} useful to keep track of the thread
 * owning an exclusive synchronizer.  You are encouraged to use them
 * -- this enables monitoring and diagnostic tools to assist users in
 * determining which threads hold locks.
 *
 * <p>Even though this class is based on an internal FIFO queue, it
 * does not automatically enforce FIFO acquisition policies.  The core
 * of exclusive synchronization takes the form:
 *
 * <pre>
 * <em>Acquire:</em>
 *     while (!tryAcquire(arg)) {
 *        <em>enqueue thread if it is not already queued</em>;
 *        <em>possibly block current thread</em>;
 *     }
 *
 * <em>Release:</em>
 *     if (tryRelease(arg))
 *        <em>unblock the first queued thread</em>;
 * </pre>
 *
 * (Shared mode is similar but may involve cascading signals.)
 *
 * <p id="barging">Because checks in acquire are invoked before
 * enqueuing, a newly acquiring thread may <em>barge</em> ahead of
 * others that are blocked and queued.  However, you can, if desired,
 * define {@code tryAcquire} and/or {@code tryAcquireShared} to
 * disable barging by internally invoking one or more of the inspection
 * methods, thereby providing a <em>fair</em> FIFO acquisition order.
 * In particular, most fair synchronizers can define {@code tryAcquire}
 * to return {@code false} if {@link #hasQueuedPredecessors} (a method
 * specifically designed to be used by fair synchronizers) returns
 * {@code true}.  Other variations are possible.
 *
 * <p>Throughput and scalability are generally highest for the
 * default barging (also known as <em>greedy</em>,
 * <em>renouncement</em>, and <em>convoy-avoidance</em>) strategy.
 * While this is not guaranteed to be fair or starvation-free, earlier
 * queued threads are allowed to recontend before later queued
 * threads, and each recontention has an unbiased chance to succeed
 * against incoming threads.  Also, while acquires do not
 * &quot;spin&quot; in the usual sense, they may perform multiple
 * invocations of {@code tryAcquire} interspersed with other
 * computations before blocking.  This gives most of the benefits of
 * spins when exclusive synchronization is only briefly held, without
 * most of the liabilities when it isn't. If so desired, you can
 * augment this by preceding calls to acquire methods with
 * "fast-path" checks, possibly prechecking {@link #hasContended}
 * and/or {@link #hasQueuedThreads} to only do so if the synchronizer
 * is likely not to be contended.
 *
 * <p>This class provides an efficient and scalable basis for
 * synchronization in part by specializing its range of use to
 * synchronizers that can rely on {@code int} state, acquire, and
 * release parameters, and an internal FIFO wait queue. When this does
 * not suffice, you can build synchronizers from a lower level using
 * {@link java.util.concurrent.atomic atomic} classes, your own custom
 * {@link java.util.Queue} classes, and {@link LockSupport} blocking
 * support.
 *
 * <h2>Usage Examples</h2>
 *
 * <p>Here is a non-reentrant mutual exclusion lock class that uses
 * the value zero to represent the unlocked state, and one to
 * represent the locked state. While a non-reentrant lock
 * does not strictly require recording of the current owner
 * thread, this class does so anyway to make usage easier to monitor.
 * It also supports conditions and exposes some instrumentation methods:
 *
 * <pre> {@code
 * class Mutex implements Lock, java.io.Serializable {
 *
 *   // Our internal helper class
 *   private static class Sync extends AbstractQueuedSynchronizer {
 *     // Acquires the lock if state is zero
 *     public boolean tryAcquire(int acquires) {
 *       assert acquires == 1; // Otherwise unused
 *       if (compareAndSetState(0, 1)) {
 *         setExclusiveOwnerThread(Thread.currentThread());
 *         return true;
 *       }
 *       return false;
 *     }
 *
 *     // Releases the lock by setting state to zero
 *     protected boolean tryRelease(int releases) {
 *       assert releases == 1; // Otherwise unused
 *       if (!isHeldExclusively())
 *         throw new IllegalMonitorStateException();
 *       setExclusiveOwnerThread(null);
 *       setState(0);
 *       return true;
 *     }
 *
 *     // Reports whether in locked state
 *     public boolean isLocked() {
 *       return getState() != 0;
 *     }
 *
 *     public boolean isHeldExclusively() {
 *       // a data race, but safe due to out-of-thin-air guarantees
 *       return getExclusiveOwnerThread() == Thread.currentThread();
 *     }
 *
 *     // Provides a Condition
 *     public Condition newCondition() {
 *       return new ConditionObject();
 *     }
 *
 *     // Deserializes properly
 *     private void readObject(ObjectInputStream s)
 *         throws IOException, ClassNotFoundException {
 *       s.defaultReadObject();
 *       setState(0); // reset to unlocked state
 *     }
 *   }
 *
 *   // The sync object does all the hard work. We just forward to it.
 *   private final Sync sync = new Sync();
 *
 *   public void lock()              { sync.acquire(1); }
 *   public boolean tryLock()        { return sync.tryAcquire(1); }
 *   public void unlock()            { sync.release(1); }
 *   public Condition newCondition() { return sync.newCondition(); }
 *   public boolean isLocked()       { return sync.isLocked(); }
 *   public boolean isHeldByCurrentThread() {
 *     return sync.isHeldExclusively();
 *   }
 *   public boolean hasQueuedThreads() {
 *     return sync.hasQueuedThreads();
 *   }
 *   public void lockInterruptibly() throws InterruptedException {
 *     sync.acquireInterruptibly(1);
 *   }
 *   public boolean tryLock(long timeout, TimeUnit unit)
 *       throws InterruptedException {
 *     return sync.tryAcquireNanos(1, unit.toNanos(timeout));
 *   }
 * }}</pre>
 *
 * <p>Here is a latch class that is like a
 * {@link java.util.concurrent.CountDownLatch CountDownLatch}
 * except that it only requires a single {@code signal} to
 * fire. Because a latch is non-exclusive, it uses the {@code shared}
 * acquire and release methods.
 *
 * <pre> {@code
 * class BooleanLatch {
 *
 *   private static class Sync extends AbstractQueuedSynchronizer {
 *     boolean isSignalled() { return getState() != 0; }
 *
 *     protected int tryAcquireShared(int ignore) {
 *       return isSignalled() ? 1 : -1;
 *     }
 *
 *     protected boolean tryReleaseShared(int ignore) {
 *       setState(1);
 *       return true;
 *     }
 *   }
 *
 *   private final Sync sync = new Sync();
 *   public boolean isSignalled() { return sync.isSignalled(); }
 *   public void signal()         { sync.releaseShared(1); }
 *   public void await() throws InterruptedException {
 *     sync.acquireSharedInterruptibly(1);
 *   }
 * }}</pre>
 *
 * @since 1.5
 * @author Doug Lea
 */
@SerialVersionUID(7373984972572414691L)
object AbstractQueuedSynchronizer { // Node status bits, also used as argument and return values
  private[locks] val WAITING   = 1          // must be 1
  private[locks] val CANCELLED = 0x80000000 // must be negative
  private[locks] val COND      = 2          // in a condition wait

  /** CLH Nodes */
  abstract private[locks] class Node {
    private[locks] val prev   = new AtomicReference[Node]()   // initially attached via casTail
    private[locks] val next   = new AtomicReference[Node]()   // visibly nonnull when signallable
    private[locks] val waiter = new AtomicReference[Thread]() // visibly nonnull when enqueued
    private[locks] val status = new AtomicInteger(0)          // written by owner, atomic bit ops by others

    final private[locks] def getAndUnsetStatus(v: Int): Int = { // for signalling
      status.valueRef.fetchAnd(~v)
    }

    final private[locks] def clearStatus(): Unit = {
      // for reducing unneeded signals
      status.set(0)
    }
  }

  // Concrete classes tagged by type
  final private[locks] class ExclusiveNode
      extends AbstractQueuedSynchronizer.Node {}

  final private[locks] class SharedNode
      extends AbstractQueuedSynchronizer.Node {}

  final private[locks] class ConditionNode
      extends AbstractQueuedSynchronizer.Node
      with ForkJoinPool.ManagedBlocker {

    private[locks] var nextWaiter
        : ConditionNode = null // link to next waiting node

    /**
     * Allows Conditions to be used in ForkJoinPools without
     * risking fixed pool exhaustion. This is usable only for
     * untimed Condition waits, not timed versions.
     */
    override final def isReleasable(): Boolean = {
      status.get() <= 1 || Thread.currentThread().isInterrupted()
    }

    override final def block(): Boolean = {
      while (!isReleasable()) {
        LockSupport.park(this)
      }
      true
    }
  }

  /**
   * Wakes up the successor of given node, if one exists, and unsets its
   * WAITING status to avoid park race. This may fail to wake up an
   * eligible thread when one or more have been cancelled, but
   * cancelAcquire ensures liveness.
   */
  private def signalNext(h: AbstractQueuedSynchronizer.Node): Unit = {
    for {
      h <- Option(h)
      s <- Option(h.next.get()) if s.status.get() != 0
    } {
      s.getAndUnsetStatus(WAITING)
      LockSupport.unpark(s.waiter.get())
    }
  }
}

@SerialVersionUID(7373984972572414691L)
abstract class AbstractQueuedSynchronizer protected ()
    extends AbstractOwnableSynchronizer
    with Serializable {
  import AbstractQueuedSynchronizer._

  /**
   * Head of the wait queue, lazily initialized.
   */
  private val head = new AtomicReference[Node]()

  /**
   * Tail of the wait queue. After initialization, modified only via casTail.
   */
  private val tail = new AtomicReference[Node]()

  /**
   * The synchronization state.
   */
  protected val state = new AtomicInteger(0)

  /**
   * Returns the current value of synchronization state.
   * This operation has memory semantics of a {@code volatile} read.
   * @return current state value
   */
  final protected def getState(): Int = state.get()

  /**
   * Sets the value of synchronization state.
   * This operation has memory semantics of a {@code volatile} write.
   * @param newState the new state value
   */
  final protected def setState(newState: Int): Unit = state.set(newState)

  final protected def compareAndSetState(expected: Int,
                                         newState: Int): Boolean =
    state.compareAndSet(expected, newState)

  /** tries once to CAS a new dummy node for head */
  private def tryInitializeHead(): Unit = {
    val h = new AbstractQueuedSynchronizer.ExclusiveNode()
    if (head.compareAndSet(null, h)) {
      tail.set(h)
    }
  }

  /**
   * Enqueues the node unless null. (Currently used only for
   * ConditionNodes; other cases are interleaved with acquires.)
   */
  final private[locks] def enqueue(
      node: AbstractQueuedSynchronizer.Node): Unit = {
    @tailrec
    def tryEnqueue(): Unit = {
      val t = tail.get()
      node.prev.setOpaque(t) // avoid unnecessary fence
      t match {
        case null =>
          // initialize
          tryInitializeHead()
          tryEnqueue()

        case t if tail.compareAndSet(t, node) =>
          t.next.set(node)
          if (t.status.get() < 0) { // wake up to clean link
            LockSupport.unpark(node.waiter.get())
          }
        case _ => tryEnqueue()
      }
    }
    if (node != null) tryEnqueue()
  }

  /** Returns true if node is found in traversal from tail */
  final private[locks] def isEnqueued(
      node: AbstractQueuedSynchronizer.Node): Boolean = {
    @tailrec
    def checkLoop(t: AbstractQueuedSynchronizer.Node): Boolean = {
      if (t == null) false
      else if (t eq node) true
      else checkLoop(t.prev.get())
    }
    checkLoop(tail.get())
  }

  /**
   * Main acquire method, invoked by all exported acquire methods.
   *
   * @param node null unless a reacquiring Condition
   * @param arg the acquire argument
   * @param shared true if shared mode else exclusive
   * @param interruptible if abort and return negative on interrupt
   * @param timed if true use timed waits
   * @param time if timed, the System.nanoTime()value to timeout
   * @return positive if acquired, 0 if timed out, negative if interrupted
   */
  final private[locks] def acquire(_node: AbstractQueuedSynchronizer.Node,
                                   arg: Int,
                                   shared: Boolean,
                                   interruptible: Boolean,
                                   timed: Boolean,
                                   time: Long): Int = {
    val current = Thread.currentThread()

    var node: Node         = _node
    var pred: Option[Node] = None
    var interrupted        = false
    var first              = false
    var spins              = 0
    var postSpins          = 0

    @tailrec
    def fetchFirstOrPredecesor(): Unit = {
      if (!first) {
        pred = for {
          n <- Option(node)
          p <- Option(n.prev.get())
        } yield p
        pred match {
          case None => ()
          case Some(pred) =>
            first = head.get() == pred
            if (!first) {
              if (pred.status.get() < 0) {
                cleanQueue()
                fetchFirstOrPredecesor()
              } else if (pred.prev.get() == null) {
                Thread.onSpinWait()
                fetchFirstOrPredecesor()
              } else ()
            }
        }
      }
    }

    def doTryAcquire(): Boolean =
      try {
        if (shared) tryAcquireShared(arg) >= 0
        else tryAcquire(arg)
      } catch {
        case ex: Throwable =>
          cancelAcquire(node, interrupted, false)
          throw ex
      }

    def onFirstAcquired(): Unit = {
      node.prev.set(null)
      head.set(node)
      pred.foreach(_.next.set(null))
      node.waiter.set(null)
      node match {
        case node: SharedNode if shared => signalNext(node)
        case _                          => ()
      }
      if (interrupted) {
        current.interrupt()
      }
    }

    def initNode() = {
      node =
        if (shared) new SharedNode()
        else new ExclusiveNode()
    }

    def onEmptyPred(): Unit = {
      node.waiter.set(current)
      val t = tail.get()
      node.prev.setPlain(t)
      if (t == null) tryInitializeHead()
      else if (!tail.compareAndSet(t, node)) node.prev.setPlain(null)
      else t.next.set(node)
    }

    def await(): Boolean = {
      postSpins = ((postSpins << 1) | 1).min(255)
      spins = postSpins
      if (!timed) LockSupport.park(this)
      else {
        val nanos = time - System.nanoTime()
        if (nanos > 0L) LockSupport.parkNanos(this, nanos)
        else return true
      }

      node.clearStatus()
      interrupted |= Thread.interrupted()
      interrupted && interruptible
    }

    /*
     * Repeatedly:
     *  Check if node now first
     *    if so, ensure head stable, else ensure valid predecessor
     *  if node is first or not yet enqueued, try acquiring
     *  else if node not yet created, create it
     *  else if not yet enqueued, try once to enqueue
     *  else if woken from park, retry (up to postSpins times)
     *  else if WAITING status not set, set and retry
     *  else park and clear WAITING status, and check cancellation
     */
    while (true) {
      fetchFirstOrPredecesor()
      if (first || pred.isEmpty) {
        if (doTryAcquire()) {
          if (first) onFirstAcquired()
          return 1
        }
      }

      if (node == null) initNode()
      else if (pred.isEmpty) onEmptyPred()
      else if (first && spins != 0) {
        spins -= 1
        Thread.onSpinWait()
      } else if (node.status.get() == 0) {
        node.status.set(WAITING)
      } else {
        val shouldExit = await()
        if (shouldExit) return cancelAcquire(node, interrupted, interruptible)
      }
    }
    cancelAcquire(node, interrupted, interruptible)
  }

  /**
   * Possibly repeatedly traverses from tail, unsplicing cancelled
   * nodes until none are found. Unparks nodes that may have been
   * relinked to be next eligible acquirer.
   */
  private def cleanQueue(): Unit = breakable {
    while (true) { // restart point
      var q = tail.get()
      val p = if (q != null) q.prev.get() else null

      var s: Node = null
      var n: Node = null
      while (true) {
        if (q == null || p == null)
          return ()

        val isIncosisient =
          if (s == null) tail.get() != q
          else s.prev.get() != q || s.status.get() < 0
        if (isIncosisient) break()

        if (q.status.get() < 0) { //canceled
          val casNode =
            if (s == null) tail.compareAndSet(q, p)
            else s.prev.compareAndSet(q, p)
          if (casNode && q.prev.get() == p) {
            p.next.compareAndSet(q, s); // OK if fails
            if (p.prev.get() == null)
              signalNext(p);
          }
          break()
        }
        s = q;
        q = q.prev.get();
      }
    }
  }

  /**
   * Cancels an ongoing attempt to acquire.
   *
   * @param node the node (may be null if cancelled before enqueuing)
   * @param interrupted true if thread interrupted
   * @param interruptible if should report interruption vs reset
   */
  private def cancelAcquire(node: AbstractQueuedSynchronizer.Node,
                            interrupted: Boolean,
                            interruptible: Boolean): Int = {
    if (node != null) {
      node.waiter.set(null)
      node.status.set(AbstractQueuedSynchronizer.CANCELLED)
      if (node.prev.get() != null) cleanQueue()
    }

    if (!interrupted) 0
    else {
      if (interruptible) AbstractQueuedSynchronizer.CANCELLED
      else {
        Thread.currentThread().interrupt()
        0
      }
    }
  }

  /**
   * Attempts to acquire in exclusive mode. This method should query
   * if the state of the object permits it to be acquired in the
   * exclusive mode, and if so to acquire it.
   *
   * <p>This method is always invoked by the thread performing
   * acquire.  If this method reports failure, the acquire method
   * may queue the thread, if it is not already queued, until it is
   * signalled by a release from some other thread. This can be used
   * to implement method {@link Lock#tryLock()}.
   *
   * <p>The default
   * implementation throws {@link UnsupportedOperationException}.
   *
   * @param arg the acquire argument. This value is always the one
   *        passed to an acquire method, or is the value saved on entry
   *        to a condition wait.  The value is otherwise uninterpreted
   *        and can represent anything you like.
   * @return {@code true} if successful. Upon success, this object has
   *         been acquired.
   * @throws IllegalMonitorStateException if acquiring would place this
   *         synchronizer in an illegal state. This exception must be
   *         thrown in a consistent fashion for synchronization to work
   *         correctly.
   * @throws UnsupportedOperationException if exclusive mode is not supported
   */
  protected def tryAcquire(arg: Int): Boolean =
    throw new UnsupportedOperationException

  /**
   * Attempts to set the state to reflect a release in exclusive
   * mode.
   *
   * <p>This method is always invoked by the thread performing release.
   *
   * <p>The default implementation throws
   * {@link UnsupportedOperationException}.
   *
   * @param arg the release argument. This value is always the one
   *        passed to a release method, or the current state value upon
   *        entry to a condition wait.  The value is otherwise
   *        uninterpreted and can represent anything you like.
   * @return {@code true} if this object is now in a fully released
   *         state, so that any waiting threads may attempt to acquire;
   *         and {@code false} otherwise.
   * @throws IllegalMonitorStateException if releasing would place this
   *         synchronizer in an illegal state. This exception must be
   *         thrown in a consistent fashion for synchronization to work
   *         correctly.
   * @throws UnsupportedOperationException if exclusive mode is not supported
   */
  protected def tryRelease(arg: Int): Boolean =
    throw new UnsupportedOperationException

  /**
   * Attempts to acquire in shared mode. This method should query if
   * the state of the object permits it to be acquired in the shared
   * mode, and if so to acquire it.
   *
   * <p>This method is always invoked by the thread performing
   * acquire.  If this method reports failure, the acquire method
   * may queue the thread, if it is not already queued, until it is
   * signalled by a release from some other thread.
   *
   * <p>The default implementation throws {@link
   * UnsupportedOperationException}.
   *
   * @param arg the acquire argument. This value is always the one
   *        passed to an acquire method, or is the value saved on entry
   *        to a condition wait.  The value is otherwise uninterpreted
   *        and can represent anything you like.
   * @return a negative value on failure; zero if acquisition in shared
   *         mode succeeded but no subsequent shared-mode acquire can
   *         succeed; and a positive value if acquisition in shared
   *         mode succeeded and subsequent shared-mode acquires might
   *         also succeed, in which case a subsequent waiting thread
   *         must check availability. (Support for three different
   *         return values enables this method to be used in contexts
   *         where acquires only sometimes act exclusively.)  Upon
   *         success, this object has been acquired.
   * @throws IllegalMonitorStateException if acquiring would place this
   *         synchronizer in an illegal state. This exception must be
   *         thrown in a consistent fashion for synchronization to work
   *         correctly.
   * @throws UnsupportedOperationException if shared mode is not supported
   */
  protected def tryAcquireShared(arg: Int): Int =
    throw new UnsupportedOperationException

  /**
   * Attempts to set the state to reflect a release in shared mode.
   *
   * <p>This method is always invoked by the thread performing release.
   *
   * <p>The default implementation throws
   * {@link UnsupportedOperationException}.
   *
   * @param arg the release argument. This value is always the one
   *        passed to a release method, or the current state value upon
   *        entry to a condition wait.  The value is otherwise
   *        uninterpreted and can represent anything you like.
   * @return {@code true} if this release of shared mode may permit a
   *         waiting acquire (shared or exclusive) to succeed; and
   *         {@code false} otherwise
   * @throws IllegalMonitorStateException if releasing would place this
   *         synchronizer in an illegal state. This exception must be
   *         thrown in a consistent fashion for synchronization to work
   *         correctly.
   * @throws UnsupportedOperationException if shared mode is not supported
   */
  protected def tryReleaseShared(arg: Int): Boolean =
    throw new UnsupportedOperationException

  /**
   * Returns {@code true} if synchronization is held exclusively with
   * respect to the current (calling) thread.  This method is invoked
   * upon each call to a {@link ConditionObject} method.
   *
   * <p>The default implementation throws {@link
   * UnsupportedOperationException}. This method is invoked
   * internally only within {@link ConditionObject} methods, so need
   * not be defined if conditions are not used.
   *
   * @return {@code true} if synchronization is held exclusively;
   *         {@code false} otherwise
   * @throws UnsupportedOperationException if conditions are not supported
   */
  protected def isHeldExclusively(): Boolean =
    throw new UnsupportedOperationException

  /**
   * Acquires in exclusive mode, ignoring interrupts.  Implemented
   * by invoking at least once {@link #tryAcquire},
   * returning on success.  Otherwise the thread is queued, possibly
   * repeatedly blocking and unblocking, invoking {@link
   * #tryAcquire} until success.  This method can be used
   * to implement method {@link Lock#lock}.
   *
   * @param arg the acquire argument.  This value is conveyed to
   *        {@link #tryAcquire} but is otherwise uninterpreted and
   *        can represent anything you like.
   */
  final def acquire(arg: Int): Unit = {
    if (!tryAcquire(arg)) acquire(null, arg, false, false, false, 0L)
  }

  /**
   * Acquires in exclusive mode, aborting if interrupted.
   * Implemented by first checking interrupt status, then invoking
   * at least once {@link #tryAcquire}, returning on
   * success.  Otherwise the thread is queued, possibly repeatedly
   * blocking and unblocking, invoking {@link #tryAcquire}
   * until success or the thread is interrupted.  This method can be
   * used to implement method {@link Lock#lockInterruptibly}.
   *
   * @param arg the acquire argument.  This value is conveyed to
   *        {@link #tryAcquire} but is otherwise uninterpreted and
   *        can represent anything you like.
   * @throws InterruptedException if the current thread is interrupted
   */ @throws[InterruptedException]
  final def acquireInterruptibly(arg: Int): Unit = {
    if (Thread.interrupted() || (!tryAcquire(arg) && acquire(null,
                                                             arg,
                                                             false,
                                                             true,
                                                             false,
                                                             0L) < 0))
      throw new InterruptedException
  }

  /**
   * Attempts to acquire in exclusive mode, aborting if interrupted,
   * and failing if the given timeout elapses.  Implemented by first
   * checking interrupt status, then invoking at least once {@link
   * #tryAcquire}, returning on success.  Otherwise, the thread is
   * queued, possibly repeatedly blocking and unblocking, invoking
   * {@link #tryAcquire} until success or the thread is interrupted
   * or the timeout elapses.  This method can be used to implement
   * method {@link Lock#tryLock(long, TimeUnit)}.
   *
   * @param arg the acquire argument.  This value is conveyed to
   *        {@link #tryAcquire} but is otherwise uninterpreted and
   *        can represent anything you like.
   * @param nanosTimeout the maximum number of nanoseconds to wait
   * @return {@code true} if acquired; {@code false} if timed out
   * @throws InterruptedException if the current thread is interrupted
   */ @throws[InterruptedException]
  final def tryAcquireNanos(arg: Int, nanosTimeout: Long): Boolean = {
    if (!Thread.interrupted()) {
      if (tryAcquire(arg)) return true
      if (nanosTimeout <= 0L) return false
      val stat =
        acquire(null, arg, false, true, true, System.nanoTime() + nanosTimeout)
      if (stat > 0) return true
      if (stat == 0) return false
    }
    throw new InterruptedException
  }

  /**
   * Releases in exclusive mode.  Implemented by unblocking one or
   * more threads if {@link #tryRelease} returns true.
   * This method can be used to implement method {@link Lock#unlock}.
   *
   * @param arg the release argument.  This value is conveyed to
   *        {@link #tryRelease} but is otherwise uninterpreted and
   *        can represent anything you like.
   * @return the value returned from {@link #tryRelease}
   */
  final def release(arg: Int): Boolean = {
    if (tryRelease(arg)) {
      AbstractQueuedSynchronizer.signalNext(head.get())
      true
    } else false
  }

  /**
   * Acquires in shared mode, ignoring interrupts.  Implemented by
   * first invoking at least once {@link #tryAcquireShared},
   * returning on success.  Otherwise the thread is queued, possibly
   * repeatedly blocking and unblocking, invoking {@link
   * #tryAcquireShared} until success.
   *
   * @param arg the acquire argument.  This value is conveyed to
   *        {@link #tryAcquireShared} but is otherwise uninterpreted
   *        and can represent anything you like.
   */
  final def acquireShared(arg: Int): Unit = {
    if (tryAcquireShared(arg) < 0) {
      acquire(null, arg, true, false, false, 0L)
    }
  }

  /**
   * Acquires in shared mode, aborting if interrupted.  Implemented
   * by first checking interrupt status, then invoking at least once
   * {@link #tryAcquireShared}, returning on success.  Otherwise the
   * thread is queued, possibly repeatedly blocking and unblocking,
   * invoking {@link #tryAcquireShared} until success or the thread
   * is interrupted.
   * @param arg the acquire argument.
   * This value is conveyed to {@link #tryAcquireShared} but is
   * otherwise uninterpreted and can represent anything
   * you like.
   * @throws InterruptedException if the current thread is interrupted
   */ @throws[InterruptedException]
  final def acquireSharedInterruptibly(arg: Int): Unit = {
    if (Thread.interrupted() || {
          tryAcquireShared(arg) < 0 &&
          acquire(null, arg, true, true, false, 0L) < 0
        }) {
      throw new InterruptedException
    }
  }

  /**
   * Attempts to acquire in shared mode, aborting if interrupted, and
   * failing if the given timeout elapses.  Implemented by first
   * checking interrupt status, then invoking at least once {@link
   * #tryAcquireShared}, returning on success.  Otherwise, the
   * thread is queued, possibly repeatedly blocking and unblocking,
   * invoking {@link #tryAcquireShared} until success or the thread
   * is interrupted or the timeout elapses.
   *
   * @param arg the acquire argument.  This value is conveyed to
   *        {@link #tryAcquireShared} but is otherwise uninterpreted
   *        and can represent anything you like.
   * @param nanosTimeout the maximum number of nanoseconds to wait
   * @return {@code true} if acquired; {@code false} if timed out
   * @throws InterruptedException if the current thread is interrupted
   */ @throws[InterruptedException]
  final def tryAcquireSharedNanos(arg: Int, nanosTimeout: Long): Boolean = {
    if (!Thread.interrupted()) {
      if (tryAcquireShared(arg) >= 0) return true
      if (nanosTimeout <= 0L) return false
      val stat =
        acquire(null, arg, true, true, true, System.nanoTime() + nanosTimeout)
      if (stat > 0) return true
      if (stat == 0) return false
    }
    throw new InterruptedException
  }

  /**
   * Releases in shared mode.  Implemented by unblocking one or more
   * threads if {@link #tryReleaseShared} returns true.
   *
   * @param arg the release argument.  This value is conveyed to
   *        {@link #tryReleaseShared} but is otherwise uninterpreted
   *        and can represent anything you like.
   * @return the value returned from {@link #tryReleaseShared}
   */
  final def releaseShared(arg: Int): Boolean = {
    if (tryReleaseShared(arg)) {
      AbstractQueuedSynchronizer.signalNext(head.get())
      true
    } else false
  }

  /**
   * Queries whether any threads are waiting to acquire. Note that
   * because cancellations due to interrupts and timeouts may occur
   * at any time, a {@code true} return does not guarantee that any
   * other thread will ever acquire.
   *
   * @return {@code true} if there may be other threads waiting to acquire
   */
  final def hasQueuedThreads(): Boolean = {
    val h = head.get()
    @tailrec
    def loop(p: Node): Boolean = {
      if (p != h && p != null) {
        if (p.status.get() >= 0) true
        else loop(p.prev.get())
      } else false
    }
    loop(tail.get())
  }

  /**
   * Queries whether any threads have ever contended to acquire this
   * synchronizer; that is, if an acquire method has ever blocked.
   *
   * <p>In this implementation, this operation returns in
   * constant time.
   *
   * @return {@code true} if there has ever been contention
   */
  final def hasContended(): Boolean = head.get() != null

  /**
   * Returns the first (longest-waiting) thread in the queue, or
   * {@code null} if no threads are currently queued.
   *
   * <p>In this implementation, this operation normally returns in
   * constant time, but may iterate upon contention if other threads are
   * concurrently modifying the queue.
   *
   * @return the first (longest-waiting) thread in the queue, or
   *         {@code null} if no threads are currently queued
   */
  final def getFirstQueuedThread(): Thread = {
    // traverse from tail on stale reads
    @tailrec
    def loop(p: Node, first: Thread): Thread = {
      val q = if (p != null) p.prev.get() else null
      if (q == null) first
      else {
        val newFirst = Option(q.waiter.get()).getOrElse(first)
        loop(q, newFirst)
      }
    }

    val h = head.get()
    if (h == null) null
    else {
      val s     = h.next.get()
      val first = if (s != null) s.waiter.get() else null

      if (s == null || first == null || s.prev.get() == null) {
        loop(tail.get(), first)
      } else null
    }
  }

  /**
   * Returns true if the given thread is currently queued.
   *
   * <p>This implementation traverses the queue to determine
   * presence of the given thread.
   *
   * @param thread the thread
   * @return {@code true} if the given thread is on the queue
   * @throws NullPointerException if the thread is null
   */
  final def isQueued(thread: Thread): Boolean = {
    if (thread == null) throw new NullPointerException
    var p = tail.get()
    while ({ p != null }) {
      if (p.waiter.get() == thread) return true
      p = p.prev.get()
    }
    false
  }

  /**
   * Returns {@code true} if the apparent first queued thread, if one
   * exists, is waiting in exclusive mode.  If this method returns
   * {@code true}, and the current thread is attempting to acquire in
   * shared mode (that is, this method is invoked from {@link
   * #tryAcquireShared}) then it is guaranteed that the current thread
   * is not the first queued thread.  Used only as a heuristic in
   * ReentrantReadWriteLock.
   */
  final private[locks] def apparentlyFirstQueuedIsExclusive() = {
    val isNotShared = for {
      h <- Option(head.get())
      s <- Option(h.next.get())
      _ <- Option(s.waiter.get())
    } yield !s.isInstanceOf[AbstractQueuedSynchronizer.SharedNode]

    isNotShared.getOrElse(false)
  }

  /**
   * Queries whether any threads have been waiting to acquire longer
   * than the current thread.
   *
   * <p>An invocation of this method is equivalent to (but may be
   * more efficient than):
   * <pre> {@code
   * getFirstQueuedThread() != Thread.currentThread()
   *   && hasQueuedThreads()}</pre>
   *
   * <p>Note that because cancellations due to interrupts and
   * timeouts may occur at any time, a {@code true} return does not
   * guarantee that some other thread will acquire before the current
   * thread.  Likewise, it is possible for another thread to win a
   * race to enqueue after this method has returned {@code false},
   * due to the queue being empty.
   *
   * <p>This method is designed to be used by a fair synchronizer to
   * avoid <a href="AbstractQueuedSynchronizer.html#barging">barging</a>.
   * Such a synchronizer's {@link #tryAcquire} method should return
   * {@code false}, and its {@link #tryAcquireShared} method should
   * return a negative value, if this method returns {@code true}
   * (unless this is a reentrant acquire).  For example, the {@code
   * tryAcquire} method for a fair, reentrant, exclusive mode
   * synchronizer might look like this:
   *
   * <pre> {@code
   * protected boolean tryAcquire(int arg) {
   *   if (isHeldExclusively()) {
   *     // A reentrant acquire; increment hold count
   *     return true;
   *   } else if (hasQueuedPredecessors()) {
   *     return false;
   *   } else {
   *     // try to acquire normally
   *   }
   * }}</pre>
   *
   * @return {@code true} if there is a queued thread preceding the
   *         current thread, and {@code false} if the current thread
   *         is at the head of the queue or the queue is empty
   * @since 1.7
   */
  final def hasQueuedPredecessors(): Boolean = {
    val h     = head.get()
    val s     = if (h != null) h.next.get() else null
    val first = if (s != null) s.waiter.get() else null
    val current =
      if (h != null && (s == null || first == null || s.prev.get() == null)) {
        getFirstQueuedThread()
      } else first
    current != null && first != Thread.currentThread()
  }

  /**
   * Returns an estimate of the number of threads waiting to
   * acquire.  The value is only an estimate because the number of
   * threads may change dynamically while this method traverses
   * internal data structures.  This method is designed for use in
   * monitoring system state, not for synchronization control.
   *
   * @return the estimated number of threads waiting to acquire
   */
  final def getQueueLength(): Int = {
    def loop(p: Node, acc: Int): Int = {
      p match {
        case null => acc
        case p =>
          val n =
            if (p.waiter.get() != null) acc + 1
            else acc
          loop(p.prev.get(), n)
      }
    }
    loop(tail.get(), 0)
  }

  private def getThreads(pred: Node => Boolean): Collection[Thread] = {
    val list = new ArrayList[Thread]
    var p    = tail.get()
    while (p != null) {
      if (pred(p)) {
        val t = p.waiter.get()
        if (t != null) list.add(t)
      }
      p = p.prev.get()
    }
    list
  }

  /**
   * Returns a collection containing threads that may be waiting to
   * acquire.  Because the actual set of threads may change
   * dynamically while constructing this result, the returned
   * collection is only a best-effort estimate.  The elements of the
   * returned collection are in no particular order.  This method is
   * designed to facilitate construction of subclasses that provide
   * more extensive monitoring facilities.
   *
   * @return the collection of threads
   */
  final def getQueuedThreads(): Collection[Thread] = getThreads(_ => true)

  /**
   * Returns a collection containing threads that may be waiting to
   * acquire in exclusive mode. This has the same properties
   * as {@link #getQueuedThreads} except that it only returns
   * those threads waiting due to an exclusive acquire.
   *
   * @return the collection of threads
   */
  final def getExclusiveQueuedThreads(): Collection[Thread] = getThreads { p =>
    !p.isInstanceOf[AbstractQueuedSynchronizer.SharedNode]
  }

  /**
   * Returns a collection containing threads that may be waiting to
   * acquire in shared mode. This has the same properties
   * as {@link #getQueuedThreads} except that it only returns
   * those threads waiting due to a shared acquire.
   *
   * @return the collection of threads
   */
  final def getSharedQueuedThreads(): Collection[Thread] = getThreads {
    _.isInstanceOf[AbstractQueuedSynchronizer.SharedNode]
  }

  /**
   * Returns a string identifying this synchronizer, as well as its state.
   * The state, in brackets, includes the String {@code "State ="}
   * followed by the current value of {@link #getState}, and either
   * {@code "nonempty"} or {@code "empty"} depending on whether the
   * queue is empty.
   *
   * @return a string identifying this synchronizer, as well as its state
   */
  override def toString(): String =
    super.toString + "[State = " + getState() + ", " +
      (if (hasQueuedThreads()) "non" else "") + "empty queue]"

  /**
   * Queries whether the given ConditionObject
   * uses this synchronizer as its lock.
   *
   * @param condition the condition
   * @return {@code true} if owned
   * @throws NullPointerException if the condition is null
   */
  final def owns(
      condition: AbstractQueuedSynchronizer#ConditionObject): Boolean =
    condition.isOwnedBy(this)

  /**
   * Queries whether any threads are waiting on the given condition
   * associated with this synchronizer. Note that because timeouts
   * and interrupts may occur at any time, a {@code true} return
   * does not guarantee that a future {@code signal} will awaken
   * any threads.  This method is designed primarily for use in
   * monitoring of the system state.
   *
   * @param condition the condition
   * @return {@code true} if there are any waiting threads
   * @throws IllegalMonitorStateException if exclusive synchronization
   *         is not held
   * @throws IllegalArgumentException if the given condition is
   *         not associated with this synchronizer
   * @throws NullPointerException if the condition is null
   */
  final def hasWaiters(
      condition: AbstractQueuedSynchronizer#ConditionObject): Boolean = {
    if (!owns(condition))
      throw new IllegalArgumentException("Not owner")
    condition.hasWaiters()
  }

  /**
   * Returns an estimate of the number of threads waiting on the
   * given condition associated with this synchronizer. Note that
   * because timeouts and interrupts may occur at any time, the
   * estimate serves only as an upper bound on the actual number of
   * waiters.  This method is designed for use in monitoring system
   * state, not for synchronization control.
   *
   * @param condition the condition
   * @return the estimated number of waiting threads
   * @throws IllegalMonitorStateException if exclusive synchronization
   *         is not held
   * @throws IllegalArgumentException if the given condition is
   *         not associated with this synchronizer
   * @throws NullPointerException if the condition is null
   */
  final def getWaitQueueLength(
      condition: AbstractQueuedSynchronizer#ConditionObject): Int = {
    if (!owns(condition))
      throw new IllegalArgumentException("Not owner")
    condition.getWaitQueueLength()
  }

  /**
   * Returns a collection containing those threads that may be
   * waiting on the given condition associated with this
   * synchronizer.  Because the actual set of threads may change
   * dynamically while constructing this result, the returned
   * collection is only a best-effort estimate. The elements of the
   * returned collection are in no particular order.
   *
   * @param condition the condition
   * @return the collection of threads
   * @throws IllegalMonitorStateException if exclusive synchronization
   *         is not held
   * @throws IllegalArgumentException if the given condition is
   *         not associated with this synchronizer
   * @throws NullPointerException if the condition is null
   */
  final def getWaitingThreads(
      condition: AbstractQueuedSynchronizer#ConditionObject)
      : Collection[Thread] = {
    if (!owns(condition))
      throw new IllegalArgumentException("Not owner")
    condition.getWaitingThreads()
  }

  /**
   * Condition implementation for a {@link AbstractQueuedSynchronizer}
   * serving as the basis of a {@link Lock} implementation.
   *
   * <p>Method documentation for this class describes mechanics,
   * not behavioral specifications from the point of view of Lock
   * and Condition users. Exported versions of this class will in
   * general need to be accompanied by documentation describing
   * condition semantics that rely on those of the associated
   * {@code AbstractQueuedSynchronizer}.
   *
   * <p>This class is Serializable, but all fields are transient,
   * so deserialized conditions have no waiters.
   */
  @SerialVersionUID(1173984872572414699L)
  class ConditionObject() extends Condition with Serializable {

    /** First node of condition queue. */
    private var firstWaiter: ConditionNode = _

    /** Last node of condition queue. */
    private var lastWaiter: ConditionNode = _

    /**
     * Removes and transfers one or all waiters to sync queue.
     */
    private def doSignal(first: AbstractQueuedSynchronizer.ConditionNode,
                         all: Boolean): Unit = {
      @tailrec
      def loop(first: AbstractQueuedSynchronizer.ConditionNode): Unit = {
        if (first == null) ()
        else {
          val next = first.nextWaiter
          firstWaiter = next

          if (next == null) {
            lastWaiter = null
          }

          if ((first.getAndUnsetStatus(AbstractQueuedSynchronizer.COND) & AbstractQueuedSynchronizer.COND) != 0) {
            enqueue(first)
            if (!all) return ()
          }

          loop(next)
        }
      }
    }

    /**
     * Moves the longest-waiting thread, if one exists, from the
     * wait queue for this condition to the wait queue for the
     * owning lock.
     *
     * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
     *         returns {@code false}
     */
    override final def signal(): Unit = {
      val first = firstWaiter
      if (!isHeldExclusively()) throw new IllegalMonitorStateException
      if (first != null) doSignal(first, false)
    }

    /**
     * Moves all threads from the wait queue for this condition to
     * the wait queue for the owning lock.
     *
     * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
     *         returns {@code false}
     */
    override final def signalAll(): Unit = {
      val first = firstWaiter
      if (!isHeldExclusively()) throw new IllegalMonitorStateException
      if (first != null) doSignal(first, true)
    }

    /**
     * Adds node to condition list and releases lock.
     *
     * @param node the node
     * @return savedState to reacquire after wait
     */
    private def enableWait(
        node: AbstractQueuedSynchronizer.ConditionNode): Int = {
      if (isHeldExclusively()) {
        node.waiter.set(Thread.currentThread())
        node.status.setOpaque(
          AbstractQueuedSynchronizer.COND | AbstractQueuedSynchronizer.WAITING)
        val last = lastWaiter

        if (last == null) firstWaiter = node
        else last.nextWaiter = node

        lastWaiter = node
        val savedState = getState()

        if (release(savedState))
          return savedState
      }

      // lock not held or inconsistent
      node.status.set(
        AbstractQueuedSynchronizer.CANCELLED
      )
      throw new IllegalMonitorStateException()
    }

    /**
     * Returns true if a node that was initially placed on a condition
     * queue is now ready to reacquire on sync queue.
     * @param node the node
     * @return true if is reacquiring
     */
    private def canReacquire(node: AbstractQueuedSynchronizer.ConditionNode) = { // check links, not status to avoid enqueue race
      node != null && node.prev.get() != null && isEnqueued(node)
    }

    /**
     * Unlinks the given node and other non-waiting nodes from
     * condition queue unless already unlinked.
     */
    private def unlinkCancelledWaiters(
        node: AbstractQueuedSynchronizer.ConditionNode): Unit = {
      if (node == null || node.nextWaiter != null || (node == lastWaiter)) {
        var w                                               = firstWaiter
        var trail: AbstractQueuedSynchronizer.ConditionNode = null

        while (w != null) {
          val next = w.nextWaiter
          if ((w.status.get() & AbstractQueuedSynchronizer.COND) == 0) {
            w.nextWaiter = null
            if (trail == null) firstWaiter = next
            else trail.nextWaiter = next
            if (next == null) lastWaiter = trail
          } else trail = w
          w = next
        }
      }
    }

    /**
     * Implements uninterruptible condition wait.
     * <ol>
     * <li>Save lock state returned by {@link #getState}.
     * <li>Invoke {@link #release} with saved state as argument,
     *     throwing IllegalMonitorStateException if it fails.
     * <li>Block until signalled.
     * <li>Reacquire by invoking specialized version of
     *     {@link #acquire} with saved state as argument.
     * </ol>
     */
    override final def awaitUninterruptibly(): Unit = {
      val node       = new AbstractQueuedSynchronizer.ConditionNode
      val savedState = enableWait(node)

      var interrupted = false
      var rejected    = false
      while (!canReacquire(node)) {
        if (Thread.interrupted()) interrupted = true
        else if ((node.status.get() & AbstractQueuedSynchronizer.COND) != 0)
          try {
            if (rejected) node.block()
            else ForkJoinPool.managedBlock(node)
          } catch {
            case ex: RejectedExecutionException =>
              rejected = true
            case ie: InterruptedException =>
              interrupted = true
          }
        else Thread.onSpinWait() // awoke while enqueuing
      }
      node.clearStatus()
      acquire(node, savedState, false, false, false, 0L)

      if (interrupted)
        Thread.currentThread().interrupt()
    }

    /**
     * Implements interruptible condition wait.
     * <ol>
     * <li>If current thread is interrupted, throw InterruptedException.
     * <li>Save lock state returned by {@link #getState}.
     * <li>Invoke {@link #release} with saved state as argument,
     *     throwing IllegalMonitorStateException if it fails.
     * <li>Block until signalled or interrupted.
     * <li>Reacquire by invoking specialized version of
     *     {@link #acquire} with saved state as argument.
     * <li>If interrupted while blocked in step 4, throw InterruptedException.
     * </ol>
     */ @throws[InterruptedException]
    override final def await(): Unit = {
      if (Thread.interrupted())
        throw new InterruptedException

      val node       = new AbstractQueuedSynchronizer.ConditionNode
      val savedState = enableWait(node)

      var interrupted = false
      var cancelled   = false
      var rejected    = false
      breakable {
        while (!canReacquire(node)) {
          interrupted |= Thread.interrupted()
          if (interrupted) {
            cancelled =
              (node.getAndUnsetStatus(AbstractQueuedSynchronizer.COND) & AbstractQueuedSynchronizer.COND) != 0
            if (cancelled) {
              break()
              // else interrupted after signal
            } else if ((node.status.get() &
                         AbstractQueuedSynchronizer.COND) != 0) {
              try {
                if (rejected) node.block()
                else ForkJoinPool.managedBlock(node)
              } catch {
                case ex: RejectedExecutionException =>
                  rejected = true
                case ie: InterruptedException =>
                  interrupted = true
              }
            } else Thread.onSpinWait()
          }
        }
      }

      node.clearStatus()
      acquire(node, savedState, false, false, false, 0L)
      if (interrupted) {
        if (cancelled) {
          unlinkCancelledWaiters(node)
          throw new InterruptedException
        }
        Thread.currentThread().interrupt()
      }
    }

    /**
     * Implements timed condition wait.
     * <ol>
     * <li>If current thread is interrupted, throw InterruptedException.
     * <li>Save lock state returned by {@link #getState}.
     * <li>Invoke {@link #release} with saved state as argument,
     *     throwing IllegalMonitorStateException if it fails.
     * <li>Block until signalled, interrupted, or timed out.
     * <li>Reacquire by invoking specialized version of
     *     {@link #acquire} with saved state as argument.
     * <li>If interrupted while blocked in step 4, throw InterruptedException.
     * </ol>
     */
    @throws[InterruptedException]
    override final def awaitNanos(nanosTimeout: Long): Long = {
      if (Thread.interrupted())
        throw new InterruptedException

      val node       = new AbstractQueuedSynchronizer.ConditionNode
      val savedState = enableWait(node)

      var nanos    = nanosTimeout.max(0L)
      val deadline = System.nanoTime() + nanos

      var cancelled   = false
      var interrupted = false
      breakable {
        while (!canReacquire(node)) {
          def interruptedOrDeadline(): Boolean = {
            interrupted |= Thread.interrupted()
            interrupted || {
              nanos = deadline - System.nanoTime()
              nanos <= 0L
            }
          }

          if (interruptedOrDeadline()) {
            cancelled =
              (node.getAndUnsetStatus(AbstractQueuedSynchronizer.COND) &
                AbstractQueuedSynchronizer.COND) != 0
            if (cancelled) break()
            else LockSupport.parkNanos(this, nanos)
          }
        }
      }

      node.clearStatus()
      acquire(node, savedState, false, false, false, 0L)

      if (cancelled) {
        unlinkCancelledWaiters(node)
        if (interrupted)
          throw new InterruptedException
      } else if (interrupted)
        Thread.currentThread().interrupt()

      val remaining = deadline - System.nanoTime() // avoid overflow
      if (remaining <= nanosTimeout) remaining
      else java.lang.Long.MIN_VALUE
    }

    /**
     * Implements absolute timed condition wait.
     * <ol>
     * <li>If current thread is interrupted, throw InterruptedException.
     * <li>Save lock state returned by {@link #getState}.
     * <li>Invoke {@link #release} with saved state as argument,
     *     throwing IllegalMonitorStateException if it fails.
     * <li>Block until signalled, interrupted, or timed out.
     * <li>Reacquire by invoking specialized version of
     *     {@link #acquire} with saved state as argument.
     * <li>If interrupted while blocked in step 4, throw InterruptedException.
     * <li>If timed out while blocked in step 4, return false, else true.
     * </ol>
     */
    @throws[InterruptedException]
    final def awaitUntil(deadline: Date): Boolean = {
      val abstime = deadline.getTime()
      if (Thread.interrupted())
        throw new InterruptedException

      val node       = new AbstractQueuedSynchronizer.ConditionNode
      val savedState = enableWait(node)

      var cancelled   = false
      var interrupted = false
      breakable {
        while (!canReacquire(node)) {
          interrupted |= Thread.interrupted()
          if (interrupted || System.currentTimeMillis() >= abstime) {
            cancelled =
              (node.getAndUnsetStatus(AbstractQueuedSynchronizer.COND) & AbstractQueuedSynchronizer.COND) != 0
            if (cancelled) break()
            else LockSupport.parkUntil(this, abstime)
          }
        }
      }

      node.clearStatus()
      acquire(node, savedState, false, false, false, 0L)
      if (cancelled) {
        unlinkCancelledWaiters(node)
        if (interrupted) throw new InterruptedException
      } else if (interrupted)
        Thread.currentThread().interrupt()

      !cancelled
    }

    /**
     * Implements timed condition wait.
     * <ol>
     * <li>If current thread is interrupted, throw InterruptedException.
     * <li>Save lock state returned by {@link #getState}.
     * <li>Invoke {@link #release} with saved state as argument,
     *     throwing IllegalMonitorStateException if it fails.
     * <li>Block until signalled, interrupted, or timed out.
     * <li>Reacquire by invoking specialized version of
     *     {@link #acquire} with saved state as argument.
     * <li>If interrupted while blocked in step 4, throw InterruptedException.
     * <li>If timed out while blocked in step 4, return false, else true.
     * </ol>
     */
    @throws[InterruptedException]
    final def await(time: Long, unit: TimeUnit): Boolean = {
      val nanosTimeout = unit.toNanos(time)
      if (Thread.interrupted())
        throw new InterruptedException
      val node       = new AbstractQueuedSynchronizer.ConditionNode
      val savedState = enableWait(node)
      var nanos      = nanosTimeout.max(0L)
      val deadline   = System.nanoTime() + nanos

      var cancelled   = false
      var interrupted = false
      breakable {
        while (!canReacquire(node)) {
          def interruptedOrDeadline(): Boolean = {
            interrupted |= Thread.interrupted()
            interrupted || {
              nanos = deadline - System.nanoTime()
              nanos <= 0L
            }
          }

          if (interruptedOrDeadline()) {
            cancelled =
              (node.getAndUnsetStatus(AbstractQueuedSynchronizer.COND) & AbstractQueuedSynchronizer.COND) != 0
            if (cancelled)
              break()
            else LockSupport.parkNanos(this, nanos)
          }
        }
      }

      node.clearStatus()
      acquire(node, savedState, false, false, false, 0L)
      if (cancelled) {
        unlinkCancelledWaiters(node)
        if (interrupted) throw new InterruptedException
      } else if (interrupted) Thread.currentThread().interrupt()
      !cancelled
    }

    /**
     * Returns true if this condition was created by the given
     * synchronization object.
     *
     * @return {@code true} if owned
     */
    final private[locks] def isOwnedBy(sync: AbstractQueuedSynchronizer) = {
      sync == AbstractQueuedSynchronizer.this
    }

    /**
     * Queries whether any threads are waiting on this condition.
     * Implements {@link AbstractQueuedSynchronizer#hasWaiters(ConditionObject)}.
     *
     * @return {@code true} if there are any waiting threads
     * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
     *         returns {@code false}
     */
    final private[locks] def hasWaiters(): Boolean = {
      if (!isHeldExclusively())
        throw new IllegalMonitorStateException

      var w = firstWaiter
      while (w != null) {
        if ((w.status.get() & AbstractQueuedSynchronizer.COND) != 0)
          return true

        w = w.nextWaiter
      }
      false
    }

    /**
     * Returns an estimate of the number of threads waiting on
     * this condition.
     * Implements {@link AbstractQueuedSynchronizer#getWaitQueueLength(ConditionObject)}.
     *
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
     *         returns {@code false}
     */
    final private[locks] def getWaitQueueLength(): Int = {
      if (!isHeldExclusively())
        throw new IllegalMonitorStateException

      var n = 0
      var w = firstWaiter
      while (w != null) {
        if ((w.status.get() & AbstractQueuedSynchronizer.COND) != 0)
          n += 1

        w = w.nextWaiter
      }
      n
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on this Condition.
     * Implements {@link AbstractQueuedSynchronizer#getWaitingThreads(ConditionObject)}.
     *
     * @return the collection of threads
     * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
     *         returns {@code false}
     */
    final private[locks] def getWaitingThreads(): Collection[Thread] = {
      if (!isHeldExclusively())
        throw new IllegalMonitorStateException
      val list = new ArrayList[Thread]
      var w    = firstWaiter
      while (w != null) {
        if ((w.status.get() & AbstractQueuedSynchronizer.COND) != 0) {
          val t = w.waiter.get()
          if (t != null) list.add(t)
        }

        w = w.nextWaiter
      }
      list
    }
  }
}
