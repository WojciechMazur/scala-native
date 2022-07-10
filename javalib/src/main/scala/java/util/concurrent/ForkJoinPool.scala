/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 *
 * Ported to Scala based on rev.
 */

package java.util.concurrent

import java.lang.Thread.UncaughtExceptionHandler
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import java.security.AccessController
import java.security.AccessControlContext
import java.security.Permission
import java.security.Permissions
import java.security.PrivilegedAction
import java.security.ProtectionDomain
import java.util.concurrent.ForkJoinPool.WorkQueue.getAndClearSlot
import java.util.{ArrayList, Collection, Collections, List, concurrent}
import java.util.function.Predicate
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.Condition
import scala.annotation._
import scala.scalanative.annotation._
import scala.util.control.Breaks._
import scala.scalanative.unsafe._

import scala.scalanative.runtime.{fromRawPtr, Intrinsics, ObjectArray}

/*
 * Implementation Overview
 *
 * This class and its nested classes provide the main
 * functionality and control for a set of worker threads:
 * Submissions from non-FJ threads enter into submission queues.
 * Workers take these tasks and typically split them into subtasks
 * that may be stolen by other workers. Work-stealing based on
 * randomized scans generally leads to better throughput than
 * "work dealing" in which producers assign tasks to idle threads,sys
 * in part because threads that have finished other tasks before
 * the signalled thread wakes up (which can be a long time) can
 * take the task instead.  Preference rules give first priority to
 * processing tasks from their own queues (LIFO or FIFO, depending
 * on mode), then to randomized FIFO steals of tasks in other
 * queues.  This framework began as vehicle for supporting
 * tree-structured parallelism using work-stealing.  Over time,
 * its scalability advantages led to extensions and changes to
 * better support more diverse usage contexts.  Because most
 * internal methods and nested classes are interrelated, their
 * main rationale and descriptions are presented here individual
 * methods and nested classes contain only brief comments about
 * details.
 *
 * WorkQueues
 * ==========
 *
 * Most operations occur within work-stealing queues (in nested
 * class WorkQueue).  These are special forms of Deques that
 * support only three of the four possible end-operations -- push,
 * pop, and poll (aka steal), under the further constraints that
 * push and pop are called only from the owning thread (or, as
 * extended here, under a lock), while poll may be called from
 * other threads.  (If you are unfamiliar with them, you probably
 * want to read Herlihy and Shavit's book "The Art of
 * Multiprocessor programming", chapter 16 describing these in
 * more detail before proceeding.)  The main work-stealing queue
 * design is roughly similar to those in the papers "Dynamic
 * Circular Work-Stealing Deque" by Chase and Lev, SPAA 2005
 * (http://research.sun.com/scalable/pubs/index.html) and
 * "Idempotent work stealing" by Michael, Saraswat, and Vechev,
 * PPoPP 2009 (http://portal.acm.org/citation.cfm?id=1504186).
 * The main differences ultimately stem from GC requirements that
 * we null out taken slots as soon as we can, to maintain as small
 * a footprint as possible even in programs generating huge
 * numbers of tasks. To accomplish this, we shift the CAS
 * arbitrating pop vs poll (steal) from being on the indices
 * ("base" and "top") to the slots themselves.
 *
 * Adding tasks then takes the form of a classic array push(task)
 * in a circular buffer:
 *    q.array[q.top++ % length] = task
 *
 * The actual code needs to null-check and size-check the array,
 * uses masking, not mod, for indexing a power-of-two-sized array,
 * enforces memory ordering, supports resizing, and possibly
 * signals waiting workers to start scanning -- see below.
 *
 * The pop operation (always performed by owner) is of the form:
 *   if ((task = getAndSet(q.array, (q.top-1) % length, null)) != null)
 *        decrement top and return task
 * If this fails, the queue is empty.
 *
 * The poll operation by another stealer thread is, basically:
 *   if (CAS nonnull task at q.array[q.base % length] to null)
 *       increment base and return task
 *
 * This may fail due to contention, and may be retried.
 * Implementations must ensure a consistent snapshot of the base
 * index and the task (by looping or trying elsewhere) before
 * trying CAS.  There isn't actually a method of this form,
 * because failure due to inconsistency or contention is handled
 * in different ways in different contexts, normally by first
 * trying other queues. (For the most straightforward example, see
 * method pollScan.) There are further variants for cases
 * requiring inspection of elements before extracting them, so
 * must interleave these with variants of this code.  Also, a more
 * efficient version (nextLocalTask) is used for polls by owners.
 * It avoids some overhead because the queue cannot be growing
 * during call.
 *
 * Memory ordering.  See "Correct and Efficient Work-Stealing for
 * Weak Memory Models" by Le, Pop, Cohen, and Nardelli, PPoPP 2013
 * (http://www.di.ens.fr/~zappa/readings/ppopp13.pdf) for an
 * analysis of memory ordering requirements in work-stealing
 * algorithms similar to the one used here.  Inserting and
 * extracting tasks in array slots via volatile or atomic accesses
 * or explicit fences provides primary synchronization.
 *
 * Operations on deque elements require reads and writes of both
 * indices and slots. When possible, we allow these to occur in
 * any order.  Because the base and top indices (along with other
 * pool or array fields accessed in many methods) only imprecisely
 * guide where to extract from, we let accesses other than the
 * element getAndSet/CAS/setVolatile appear in any order, using
 * plain mode. But we must still preface some methods (mainly
 * those that may be accessed externally) with an acquireFence to
 * avoid unbounded staleness. This is equivalent to acting as if
 * callers use an acquiring read of the reference to the pool or
 * queue when invoking the method, even when they do not. We use
 * explicit acquiring reads (getSlot) rather than plain array
 * access when acquire mode is required but not otherwise ensured
 * by context. To reduce stalls by other stealers, we encourage
 * timely writes to the base index by immediately following
 * updates with a write of a volatile field that must be updated
 * anyway, or an Opaque-mode write if there is no such
 * opportunity.
 *
 * Because indices and slot contents cannot always be consistent,
 * the emptiness check base == top is only quiescently accurate
 * (and so used where this suffices). Otherwise, it may err on the
 * side of possibly making the queue appear nonempty when a push,
 * pop, or poll have not fully committed, or making it appear
 * empty when an update of top or base has not yet been seen.
 * Similarly, the check in push for the queue array being full may
 * trigger when not completely full, causing a resize earlier than
 * required.
 *
 * Mainly because of these potential inconsistencies among slots
 * vs indices, the poll operation, considered individually, is not
 * wait-free. One thief cannot successfully continue until another
 * in-progress one (or, if previously empty, a push) visibly
 * completes.  This can stall threads when required to consume
 * from a given queue (which may spin).  However, in the
 * aggregate, we ensure probabilistic non-blockingness at least
 * until checking quiescence (which is intrinsically blocking):
 * If an attempted steal fails, a scanning thief chooses a
 * different victim target to try next. So, in order for one thief
 * to progress, it suffices for any in-progress poll or new push
 * on any empty queue to complete. The worst cases occur when many
 * threads are looking for tasks being produced by a stalled
 * producer.
 *
 * This approach also enables support of a user mode in which
 * local task processing is in FIFO, not LIFO order, simply by
 * using poll rather than pop.  This can be useful in
 * message-passing frameworks in which tasks are never joined,
 * although with increased contention among task producers and
 * consumers.
 *
 * WorkQueues are also used in a similar way for tasks submitted
 * to the pool. We cannot mix these tasks in the same queues used
 * by workers. Instead, we randomly associate submission queues
 * with submitting threads, using a form of hashing.  The
 * ThreadLocalRandom probe value serves as a hash code for
 * choosing existing queues, and may be randomly repositioned upon
 * contention with other submitters.  In essence, submitters act
 * like workers except that they are restricted to executing local
 * tasks that they submitted (or when known, subtasks thereof).
 * Insertion of tasks in shared mode requires a lock. We use only
 * a simple spinlock (using field "source"), because submitters
 * encountering a busy queue move to a different position to use
 * or create other queues. They block only when registering new
 * queues.
 *
 * Management
 * ==========
 *
 * The main throughput advantages of work-stealing stem from
 * decentralized control -- workers mostly take tasks from
 * themselves or each other, at rates that can exceed a billion
 * per second.  Most non-atomic control is performed by some form
 * of scanning across or within queues.  The pool itself creates,
 * activates (enables scanning for and running tasks),
 * deactivates, blocks, and terminates threads, all with minimal
 * central information.  There are only a few properties that we
 * can globally track or maintain, so we pack them into a small
 * number of variables, often maintaining atomicity without
 * blocking or locking.  Nearly all essentially atomic control
 * state is held in a few volatile variables that are by far most
 * often read (not written) as status and consistency checks. We
 * pack as much information into them as we can.
 *
 * Field "ctl" contains 64 bits holding information needed to
 * atomically decide to add, enqueue (on an event queue), and
 * dequeue and release workers.  To enable this packing, we
 * restrict maximum parallelism to (1<<15)-1 (which is far in
 * excess of normal operating range) to allow ids, counts, and
 * their negations (used for thresholding) to fit into 16bit
 * subfields.
 *
 * Field "mode" holds configuration parameters as well as lifetime
 * status, atomically and monotonically setting SHUTDOWN, STOP,
 * and finally TERMINATED bits. It is updated only via bitwise
 * atomics (getAndBitwiseOr).
 *
 * Array "queues" holds references to WorkQueues.  It is updated
 * (only during worker creation and termination) under the
 * registrationLock, but is otherwise concurrently readable, and
 * accessed directly (although always prefaced by acquireFences or
 * other acquiring reads). To simplify index-based operations, the
 * array size is always a power of two, and all readers must
 * tolerate null slots.  Worker queues are at odd indices. Worker
 * ids masked with SMASK match their index. Shared (submission)
 * queues are at even indices. Grouping them together in this way
 * simplifies and speeds up task scanning.
 *
 * All worker thread creation is on-demand, triggered by task
 * submissions, replacement of terminated workers, and/or
 * compensation for blocked workers. However, all other support
 * code is set up to work with other policies.  To ensure that we
 * do not hold on to worker or task references that would prevent
 * GC, all accesses to workQueues are via indices into the
 * queues array (which is one source of some of the messy code
 * constructions here). In essence, the queues array serves as
 * a weak reference mechanism. Thus for example the stack top
 * subfield of ctl stores indices, not references.
 *
 * Queuing Idle Workers. Unlike HPC work-stealing frameworks, we
 * cannot let workers spin indefinitely scanning for tasks when
 * none can be found immediately, and we cannot start/resume
 * workers unless there appear to be tasks available.  On the
 * other hand, we must quickly prod them into action when new
 * tasks are submitted or generated. These latencies are mainly a
 * function of JVM park/unpark (and underlying OS) performance,
 * which can be slow and variable.  In many usages, ramp-up time
 * is the main limiting factor in overall performance, which is
 * compounded at program start-up by JIT compilation and
 * allocation. On the other hand, throughput degrades when too
 * many threads poll for too few tasks.
 *
 * The "ctl" field atomically maintains total and "released"
 * worker counts, plus the head of the available worker queue
 * (actually stack, represented by the lower 32bit subfield of
 * ctl).  Released workers are those known to be scanning for
 * and/or running tasks. Unreleased ("available") workers are
 * recorded in the ctl stack. These workers are made available for
 * signalling by enqueuing in ctl (see method awaitWork).  The
 * "queue" is a form of Treiber stack. This is ideal for
 * activating threads in most-recently used order, and improves
 * performance and locality, outweighing the disadvantages of
 * being prone to contention and inability to release a worker
 * unless it is topmost on stack. The top stack state holds the
 * value of the "phase" field of the worker: its index and status,
 * plus a version counter that, in addition to the count subfields
 * (also serving as version stamps) provide protection against
 * Treiber stack ABA effects.
 *
 * Creating workers. To create a worker, we pre-increment counts
 * (serving as a reservation), and attempt to construct a
 * ForkJoinWorkerThread via its factory. On starting, the new
 * thread first invokes registerWorker, where it constructs a
 * WorkQueue and is assigned an index in the queues array
 * (expanding the array if necessary).  Upon any exception across
 * these steps, or null return from factory, deregisterWorker
 * adjusts counts and records accordingly.  If a null return, the
 * pool continues running with fewer than the target number
 * workers. If exceptional, the exception is propagated, generally
 * to some external caller.
 *
 * WorkQueue field "phase" is used by both workers and the pool to
 * manage and track whether a worker is UNSIGNALLED (possibly
 * blocked waiting for a signal).  When a worker is enqueued its
 * phase field is set negative. Note that phase field updates lag
 * queue CAS releases seeing a negative phase does not guarantee
 * that the worker is available. When queued, the lower 16 bits of
 * its phase must hold its pool index. So we place the index there
 * upon initialization and never modify these bits.
 *
 * The ctl field also serves as the basis for memory
 * synchronization surrounding activation. This uses a more
 * efficient version of a Dekker-like rule that task producers and
 * consumers sync with each other by both writing/CASing ctl (even
 * if to its current value).  However, rather than CASing ctl to
 * its current value in the common case where no action is
 * required, we reduce write contention by ensuring that
 * signalWork invocations are prefaced with a full-volatile memory
 * access (which is usually needed anyway).
 *
 * Signalling. Signals (in signalWork) cause new or reactivated
 * workers to scan for tasks.  Method signalWork and its callers
 * try to approximate the unattainable goal of having the right
 * number of workers activated for the tasks at hand, but must err
 * on the side of too many workers vs too few to avoid stalls.  If
 * computations are purely tree structured, it suffices for every
 * worker to activate another when it pushes a task into an empty
 * queue, resulting in O(log(#threads)) steps to full activation.
 * If instead, tasks come in serially from only a single producer,
 * each worker taking its first (since the last quiescence) task
 * from a queue should signal another if there are more tasks in
 * that queue. This is equivalent to, but generally faster than,
 * arranging the stealer take two tasks, re-pushing one on its own
 * queue, and signalling (because its queue is empty), also
 * resulting in logarithmic full activation time. Because we don't
 * know about usage patterns (or most commonly, mixtures), we use
 * both approaches.  We approximate the second rule by arranging
 * that workers in scan() do not repeat signals when repeatedly
 * taking tasks from any given queue, by remembering the previous
 * one. There are narrow windows in which both rules may apply,
 * leading to duplicate or unnecessary signals. Despite such
 * limitations, these rules usually avoid slowdowns that otherwise
 * occur when too many workers contend to take too few tasks, or
 * when producers waste most of their time resignalling.  However,
 * contention and overhead effects may still occur during ramp-up,
 * ramp-down, and small computations involving only a few workers.
 *
 * Scanning. Method scan performs top-level scanning for (and
 * execution of) tasks.  Scans by different workers and/or at
 * different times are unlikely to poll queues in the same
 * order. Each scan traverses and tries to poll from each queue in
 * a pseudorandom permutation order by starting at a random index,
 * and using a constant cyclically exhaustive stride restarting
 * upon contention.  (Non-top-level scans for example in
 * helpJoin, use simpler linear probes because they do not
 * systematically contend with top-level scans.)  The pseudorandom
 * generator need not have high-quality statistical properties in
 * the long term. We use Marsaglia XorShifts, seeded with the Weyl
 * sequence from ThreadLocalRandom probes, which are cheap and
 * suffice. Scans do not otherwise explicitly take into account
 * core affinities, loads, cache localities, etc, However, they do
 * exploit temporal locality (which usually approximates these) by
 * preferring to re-poll from the same queue after a successful
 * poll before trying others (see method topLevelExec).  This
 * reduces fairness, which is partially counteracted by using a
 * one-shot form of poll (tryPoll) that may lose to other workers.
 *
 * Deactivation. Method scan returns a sentinel when no tasks are
 * found, leading to deactivation (see awaitWork). The count
 * fields in ctl allow accurate discovery of quiescent states
 * (i.e., when all workers are idle) after deactivation. However,
 * this may also race with new (external) submissions, so a
 * recheck is also needed to determine quiescence. Upon apparently
 * triggering quiescence, awaitWork re-scans and self-signals if
 * it may have missed a signal. In other cases, a missed signal
 * may transiently lower parallelism because deactivation does not
 * necessarily mean that there is no more work, only that that
 * there were no tasks not taken by other workers.  But more
 * signals are generated (see above) to eventually reactivate if
 * needed.
 *
 * Trimming workers. To release resources after periods of lack of
 * use, a worker starting to wait when the pool is quiescent will
 * time out and terminate if the pool has remained quiescent for
 * period given by field keepAlive.
 *
 * Shutdown and Termination. A call to shutdownNow invokes
 * tryTerminate to atomically set a mode bit. The calling thread,
 * as well as every other worker thereafter terminating, helps
 * terminate others by cancelling their unprocessed tasks, and
 * waking them up. Calls to non-abrupt shutdown() preface this by
 * checking isQuiescent before triggering the "STOP" phase of
 * termination. To conform to ExecutorService invoke, invokeAll,
 * and invokeAny specs, we must track pool status while waiting,
 * and interrupt interruptible callers on termination (see
 * ForkJoinTask.joinForPoolInvoke etc).
 *
 * Joining Tasks
 * =============
 *
 * Normally, the first option when joining a task that is not done
 * is to try to unfork it from local queue and run it.  Otherwise,
 * any of several actions may be taken when one worker is waiting
 * to join a task stolen (or always held) by another.  Because we
 * are multiplexing many tasks on to a pool of workers, we can't
 * always just let them block (as in Thread.join).  We also cannot
 * just reassign the joiner's run-time stack with another and
 * replace it later, which would be a form of "continuation", that
 * even if possible is not necessarily a good idea since we may
 * need both an unblocked task and its continuation to progress.
 * Instead we combine two tactics:
 *
 *   Helping: Arranging for the joiner to execute some task that it
 *      could be running if the steal had not occurred.
 *
 *   Compensating: Unless there are already enough live threads,
 *      method tryCompensate() may create or re-activate a spare
 *      thread to compensate for blocked joiners until they unblock.
 *
 * A third form (implemented via tryRemove) amounts to helping a
 * hypothetical compensator: If we can readily tell that a
 * possible action of a compensator is to steal and execute the
 * task being joined, the joining thread can do so directly,
 * without the need for a compensation thread although with a
 * (rare) possibility of reduced parallelism because of a
 * transient gap in the queue array.
 *
 * Other intermediate forms available for specific task types (for
 * example helpAsyncBlocker) often avoid or postpone the need for
 * blocking or compensation.
 *
 * The ManagedBlocker extension API can't use helping so relies
 * only on compensation in method awaitBlocker.
 *
 * The algorithm in helpJoin entails a form of "linear helping".
 * Each worker records (in field "source") the id of the queue
 * from which it last stole a task.  The scan in method helpJoin
 * uses these markers to try to find a worker to help (i.e., steal
 * back a task from and execute it) that could hasten completion
 * of the actively joined task.  Thus, the joiner executes a task
 * that would be on its own local deque if the to-be-joined task
 * had not been stolen. This is a conservative variant of the
 * approach described in Wagner & Calder "Leapfrogging: a portable
 * technique for implementing efficient futures" SIGPLAN Notices,
 * 1993 (http://portal.acm.org/citation.cfm?id=155354). It differs
 * mainly in that we only record queue ids, not full dependency
 * links.  This requires a linear scan of the queues array to
 * locate stealers, but isolates cost to when it is needed, rather
 * than adding to per-task overhead. Also, searches are limited to
 * direct and at most two levels of indirect stealers, after which
 * there are rapidly diminishing returns on increased overhead.
 * Searches can fail to locate stealers when stalls delay
 * recording sources.  Further, even when accurately identified,
 * stealers might not ever produce a task that the joiner can in
 * turn help with. So, compensation is tried upon failure to find
 * tasks to run.
 *
 * Joining CountedCompleters (see helpComplete) differs from (and
 * is generally more efficient than) other cases because task
 * eligibility is determined by checking completion chains rather
 * than tracking stealers.
 *
 * Joining under timeouts (ForkJoinTask timed get) uses a
 * constrained mixture of helping and compensating in part because
 * pools (actually, only the common pool) may not have any
 * available threads: If the pool is saturated (all available
 * workers are busy), the caller tries to remove and otherwise
 * help else it blocks under compensation so that it may time out
 * independently of any tasks.
 *
 * Compensation does not by default aim to keep exactly the target
 * parallelism number of unblocked threads running at any given
 * time. Some previous versions of this class employed immediate
 * compensations for any blocked join. However, in practice, the
 * vast majority of blockages are transient byproducts of GC and
 * other JVM or OS activities that are made worse by replacement
 * when they cause longer-term oversubscription.  Rather than
 * impose arbitrary policies, we allow users to override the
 * default of only adding threads upon apparent starvation.  The
 * compensation mechanism may also be bounded.  Bounds for the
 * commonPool (see COMMON_MAX_SPARES) better enable JVMs to cope
 * with programming errors and abuse before running out of
 * resources to do so.
 *
 * Common Pool
 * ===========
 *
 * The static common pool always exists after static
 * initialization.  Since it (or any other created pool) need
 * never be used, we minimize initial construction overhead and
 * footprint to the setup of about a dozen fields.
 *
 * When external threads submit to the common pool, they can
 * perform subtask processing (see helpComplete and related
 * methods) upon joins.  This caller-helps policy makes it
 * sensible to set common pool parallelism level to one (or more)
 * less than the total number of available cores, or even zero for
 * pure caller-runs.  We do not need to record whether external
 * submissions are to the common pool -- if not, external help
 * methods return quickly. These submitters would otherwise be
 * blocked waiting for completion, so the extra effort (with
 * liberally sprinkled task status checks) in inapplicable cases
 * amounts to an odd form of limited spin-wait before blocking in
 * ForkJoinTask.join.
 *
 * Guarantees for common pool parallelism zero are limited to
 * tasks that are joined by their callers in a tree-structured
 * fashion or use CountedCompleters (as is true for jdk
 * parallelStreams). Support infiltrates several methods,
 * including those that retry helping steps until we are sure that
 * none apply if there are no workers.
 *
 * As a more appropriate default in managed environments, unless
 * overridden by system properties, we use workers of subclass
 * InnocuousForkJoinWorkerThread when there is a SecurityManager
 * present. These workers have no permissions set, do not belong
 * to any user-defined ThreadGroup, and erase all ThreadLocals
 * after executing any top-level task.  The associated mechanics
 * may be JVM-dependent and must access particular Thread class
 * fields to achieve this effect.
 *
 * Interrupt handling
 * ==================
 *
 * The framework is designed to manage task cancellation
 * (ForkJoinTask.cancel) independently from the interrupt status
 * of threads running tasks. (See the public ForkJoinTask
 * documentation for rationale.)  Interrupts are issued only in
 * tryTerminate, when workers should be terminating and tasks
 * should be cancelled anyway. Interrupts are cleared only when
 * necessary to ensure that calls to LockSupport.park do not loop
 * indefinitely (park returns immediately if the current thread is
 * interrupted). If so, interruption is reinstated after blocking
 * if status could be visible during the scope of any task.  For
 * cases in which task bodies are specified or desired to
 * interrupt upon cancellation, ForkJoinTask.cancel can be
 * overridden to do so (as is done for invoke{Any,All}).
 *
 * Memory placement
 * ================
 *
 * Performance can be very sensitive to placement of instances of
 * ForkJoinPool and WorkQueues and their queue arrays. To reduce
 * false-sharing impact, the @Contended annotation isolates the
 * ForkJoinPool.ctl field as well as the most heavily written
 * WorkQueue fields. These mainly reduce cache traffic by scanners.
 * WorkQueue arrays are presized large enough to avoid resizing
 * (which transiently reduces throughput) in most tree-like
 * computations, although not in some streaming usages. Initial
 * sizes are not large enough to avoid secondary contention
 * effects (especially for GC cardmarks) when queues are placed
 * near each other in memory. This is common, but has different
 * impact in different collectors and remains incompletely
 * addressed.
 *
 * Style notes
 * ===========
 *
 * Memory ordering relies mainly on atomic operations (CAS,
 * getAndSet, getAndAdd) along with explicit fences.  This can be
 * awkward and ugly, but also reflects the need to control
 * outcomes across the unusual cases that arise in very racy code
 * with very few invariants. All fields are read into locals
 * before use, and null-checked if they are references, even if
 * they can never be null under current usages.  Array accesses
 * using masked indices include checks (that are always true) that
 * the array length is non-zero to avoid compilers inserting more
 * expensive traps.  This is usually done in a "C"-like style of
 * listing declarations at the heads of methods or blocks, and
 * using inline assignments on first encounter.  Nearly all
 * explicit checks lead to bypass/return, not exception throws,
 * because they may legitimately arise during shutdown.
 *
 * There is a lot of representation-level coupling among classes
 * ForkJoinPool, ForkJoinWorkerThread, and ForkJoinTask.  The
 * fields of WorkQueue maintain data structures managed by
 * ForkJoinPool, so are directly accessed.  There is little point
 * trying to reduce this, since any associated future changes in
 * representations will need to be accompanied by algorithmic
 * changes anyway. Several methods intrinsically sprawl because
 * they must accumulate sets of consistent reads of fields held in
 * local variables. Some others are artificially broken up to
 * reduce producer/consumer imbalances due to dynamic compilation.
 * There are also other coding oddities (including several
 * unnecessary-looking hoisted null checks) that help some methods
 * perform reasonably even when interpreted (not compiled).
 *
 * The order of declarations in this file is (with a few exceptions):
 * (1) Static utility functions
 * (2) Nested (static) classes
 * (3) Static fields
 * (4) Fields, along with constants used when unpacking some of them
 * (5) Internal control methods
 * (6) Callbacks and other support for ForkJoinTask methods
 * (7) Exported methods
 * (8) Static block initializing statics in minimally dependent order
 *
 * Revision notes
 * ==============
 *
 * The main sources of differences of January 2020 ForkJoin
 * classes from previous version are:
 *
 * * ForkJoinTask now uses field "aux" to support blocking joins
 *   and/or record exceptions, replacing reliance on builtin
 *   monitors and side tables.
 * * Scans probe slots (vs compare indices), along with related
 *   changes that reduce performance differences across most
 *   garbage collectors, and reduce contention.
 * * Refactoring for better integration of special task types and
 *   other capabilities that had been incrementally tacked on. Plus
 *   many minor reworkings to improve consistency.
 */

/** An {@link ExecutorService} for running {@link ForkJoinTask}s. A {@code
 *  ForkJoinPool} provides the entry point for submissions from non-{@code
 *  ForkJoinTask} clients, as well as management and monitoring operations.
 *
 *  <p>A {@code ForkJoinPool} differs from other kinds of {@link
 *  ExecutorService} mainly by virtue of employing <em>work-stealing</em>: all
 *  threads in the pool attempt to find and execute tasks submitted to the pool
 *  and/or created by other active tasks (eventually blocking waiting for work
 *  if none exist). This enables efficient processing when most tasks spawn
 *  other subtasks (as do most {@code ForkJoinTask}s), as well as when many
 *  small tasks are submitted to the pool from external clients. Especially when
 *  setting <em>asyncMode</em> to true in constructors, {@code ForkJoinPool}s
 *  may also be appropriate for use with event-style tasks that are never
 *  joined. All worker threads are initialized with {@link Thread#isDaemon} set
 *  {@code true}.
 *
 *  <p>A static {@link #commonPool()} is available and appropriate for most
 *  applications. The common pool is used by any ForkJoinTask that is not
 *  explicitly submitted to a specified pool. Using the common pool normally
 *  reduces resource usage (its threads are slowly reclaimed during periods of
 *  non-use, and reinstated upon subsequent use).
 *
 *  <p>For applications that require separate or custom pools, a {@code
 *  ForkJoinPool} may be constructed with a given target parallelism level by
 *  default, equal to the number of available processors. The pool attempts to
 *  maintain enough active (or available) threads by dynamically adding,
 *  suspending, or resuming internal worker threads, even if some tasks are
 *  stalled waiting to join others. However, no such adjustments are guaranteed
 *  in the face of blocked I/O or other unmanaged synchronization. The nested
 *  {@link ManagedBlocker} interface enables extension of the kinds of
 *  synchronization accommodated. The default policies may be overridden using a
 *  constructor with parameters corresponding to those documented in class
 *  {@link ThreadPoolExecutor}.
 *
 *  <p>In addition to execution and lifecycle control methods, this class
 *  provides status check methods (for example {@link #getStealCount}) that are
 *  intended to aid in developing, tuning, and monitoring fork/join
 *  applications. Also, method {@link #toString} returns indications of pool
 *  state in a convenient form for informal monitoring.
 *
 *  <p>As is the case with other ExecutorServices, there are three main task
 *  execution methods summarized in the following table. These are designed to
 *  be used primarily by clients not already engaged in fork/join computations
 *  in the current pool. The main forms of these methods accept instances of
 *  {@code ForkJoinTask}, but overloaded forms also allow mixed execution of
 *  plain {@code Runnable}- or {@code Callable}- based activities as well.
 *  However, tasks that are already executing in a pool should normally instead
 *  use the within-computation forms listed in the table unless using async
 *  event-style tasks that are not usually joined, in which case there is little
 *  difference among choice of methods.
 *
 *  <table class="plain"> <caption>Summary of task execution methods</caption>
 *  <tr> <td></td> <th scope="col"> Call from non-fork/join clients</th> <th
 *  scope="col"> Call from within fork/join computations</th> </tr> <tr> <th
 *  scope="row" style="text-align:left"> Arrange async execution</th> <td>
 *  {@link #execute(ForkJoinTask)}</td> <td> {@link ForkJoinTask#fork}</td>
 *  </tr> <tr> <th scope="row" style="text-align:left"> Await and obtain
 *  result</th> <td> {@link #invoke(ForkJoinTask)}</td> <td> {@link
 *  ForkJoinTask#invoke}</td> </tr> <tr> <th scope="row"
 *  style="text-align:left"> Arrange exec and obtain Future</th> <td> {@link
 *  #submit(ForkJoinTask)}</td> <td> {@link ForkJoinTask#fork} (ForkJoinTasks
 *  <em>are</em> Futures)</td> </tr> </table>
 *
 *  <p>The parameters used to construct the common pool may be controlled by
 *  setting the following {@linkplain System#getProperty system properties}:
 *  <ul> <li>{@systemProperty
 *  java.util.concurrent.ForkJoinPool.common.parallelism}
 *    - the parallelism level, a non-negative integer <li>{@systemProperty
 *      java.util.concurrent.ForkJoinPool.common.threadFactory}
 *    - the class name of a {@link ForkJoinWorkerThreadFactory}. The {@linkplain
 *      ClassLoader#getSystemClassLoader() system class loader} is used
 *      to.get()this class. <li>{@systemProperty
 *      java.util.concurrent.ForkJoinPool.common.exceptionHandler}
 *    - the class name of a {@link UncaughtExceptionHandler}. The {@linkplain
 *      ClassLoader#getSystemClassLoader() system class loader} is used
 *      to.get()this class. <li>{@systemProperty
 *      java.util.concurrent.ForkJoinPool.common.maximumSpares}
 *    - the maximum number of allowed extra threads to maintain target
 *      parallelism (default 256). </ul> If no thread factory is supplied via a
 *      system property, then the common pool uses a factory that uses the
 *      system class loader as the {@linkplain Thread#getContextClassLoader()
 *      thread context class loader}. In addition, if a {@link SecurityManager}
 *      is present, then the common pool uses a factory supplying threads that
 *      have no {@link Permissions} enabled.
 *
 *  Upon any error in establishing these settings, default parameters are used.
 *  It is possible to disable or limit the use of threads in the common pool by
 *  setting the parallelism property to zero, and/or using a factory that may
 *  return {@code null}. However doing so may cause unjoined tasks to never be
 *  executed.
 *
 *  <p><b>Implementation notes:</b> This implementation restricts the maximum
 *  number of running threads to 32767. Attempts to create pools with greater
 *  than the maximum number result in {@code IllegalArgumentException}.
 *
 *  <p>This implementation rejects submitted tasks (that is, by throwing {@link
 *  RejectedExecutionException}) only when the pool is shut down or internal
 *  resources have been exhausted.
 *
 *  @since 1.7
 *  @author
 *    Doug Lea
 */
class ForkJoinPool(
    parallelism: Int,
    factory: ForkJoinPool.ForkJoinWorkerThreadFactory,
    private[concurrent] val ueh: UncaughtExceptionHandler,
    saturate: Predicate[ForkJoinPool],
    keepAlive: Long,
    workerNamePrefix: String,
    queueSize: Int,
    initialMode: Int,
    initialCtl: Long,
    bounds: Long // min, max threads packed as shorts
) extends AbstractExecutorService {
  import ForkJoinPool._

  if (factory == null)
    throw new NullPointerException()

  @volatile private[concurrent] var mode: Int = initialMode
  @volatile private[concurrent] var ctl: Long = initialCtl
  @volatile private[concurrent] var stealCount: Long = 0
  @volatile private[concurrent] var threadIds: Int = 0

  private val modePtr =
    fromRawPtr[Int](Intrinsics.classFieldRawPtr(this, "mode"))
  private val modeAtomic = new CAtomicInt(modePtr)
  private val ctlAtomic = new CAtomicLong(
    fromRawPtr(Intrinsics.classFieldRawPtr(this, "ctl"))
  )
  private val stealCountAtomic = new CAtomicLong(
    fromRawPtr(Intrinsics.classFieldRawPtr(this, "stealCount"))
  )
  private val threadIdsAtomic = new CAtomicInt(
    fromRawPtr(Intrinsics.classFieldRawPtr(this, "threadIds"))
  )

  private[concurrent] val registrationLock = new ReentrantLock()

  private var queues = new Array[WorkQueue](queueSize) // main registry

  private[concurrent] var scanRover: Int = 0 // advances across pollScan calls
  private[concurrent] var termination: Condition = _ // lazily constructed

  // Constructors

  def this(
      parallelism: Int,
      factory: ForkJoinPool.ForkJoinWorkerThreadFactory,
      handler: UncaughtExceptionHandler,
      asyncMode: Boolean,
      corePoolSize: Int,
      maximumPoolSize: Int,
      minimumRunnable: Int,
      saturate: Predicate[ForkJoinPool],
      keepAliveTime: Long,
      unit: TimeUnit
  ) = {
    this(
      parallelism = {
        if (parallelism <= 0 || parallelism > ForkJoinPool.MAX_CAP)
          throw new IllegalArgumentException()
        parallelism
      },
      factory = factory,
      ueh = handler,
      saturate = saturate,
      keepAlive = {
        if (unit == null || keepAliveTime <= 0L)
          throw new IllegalArgumentException()
        Math.max(unit.toMillis(keepAliveTime), ForkJoinPool.TIMEOUT_SLOP)
      },
      workerNamePrefix = {
        val pid = ForkJoinPool.poolIds.getAndIncrement()
        "ForkJoinPool-" + pid + "-worker-"
      },
      queueSize = {
        1 << (33 - Integer.numberOfLeadingZeros(parallelism - 1))
      },
      initialMode = {
        parallelism | (if (asyncMode) ForkJoinPool.FIFO
                       else 0) // // parallelism, runstate, queue mode
      },
      initialCtl = {
        val ncp =
          -(Math
            .min(Math.max(corePoolSize, parallelism), ForkJoinPool.MAX_CAP))
            .toLong
        val tcSeed = (ncp << ForkJoinPool.TC_SHIFT) & ForkJoinPool.TC_MASK
        val rcSeed = (ncp << ForkJoinPool.RC_SHIFT) & ForkJoinPool.RC_MASK
        tcSeed | rcSeed
      },
      bounds = {
        if (parallelism > maximumPoolSize)
          throw new IllegalArgumentException()
        val maxSpares =
          Math.min(maximumPoolSize, ForkJoinPool.MAX_CAP) - parallelism
        val minAvail =
          Math.min(Math.max(minimumRunnable, 0), ForkJoinPool.MAX_CAP)
        ((minAvail - parallelism) & ForkJoinPool.SMASK) | (maxSpares << ForkJoinPool.SWIDTH)
      }
    )
  }

  /** Creates a {@code ForkJoinPool} with the given parameters (using defaults
   *  for others -- see {@link #ForkJoinPool(int, ForkJoinWorkerThreadFactory,
   *  UncaughtExceptionHandler, boolean, int, int, int, Predicate, long,
   *  TimeUnit)}).
   *
   *  @param parallelism
   *    the parallelism level. For default value, use {@link
   *    java.lang.Runtime#availableProcessors}.
   *  @param factory
   *    the factory for creating new threads. For default value, use {@link
   *    #defaultForkJoinWorkerThreadFactory}.
   *  @param handler
   *    the handler for internal worker threads that terminate due to
   *    unrecoverable errors encountered while executing tasks. For default
   *    value, use {@code null}.
   *  @param asyncMode
   *    if true, establishes local first-in-first-out scheduling mode for forked
   *    tasks that are never joined. This mode may be more appropriate than
   *    default locally stack-based mode in applications in which worker threads
   *    only process event-style asynchronous tasks. For default value, use
   *    {@code false}.
   *  @throws IllegalArgumentException
   *    if parallelism less than or equal to zero, or greater than
   *    implementation limit
   *  @throws NullPointerException
   *    if the factory is null
   *  @throws SecurityException
   *    if a security manager exists and the caller is not permitted to modify
   *    threads because it does not hold {@link
   *    java.lang.RuntimePermission}{@code ("modifyThread")}
   */
  def this(
      parallelism: Int,
      factory: ForkJoinPool.ForkJoinWorkerThreadFactory,
      handler: UncaughtExceptionHandler,
      asyncMode: Boolean
  ) = {
    this(
      parallelism,
      factory,
      handler,
      asyncMode,
      0,
      ForkJoinPool.MAX_CAP,
      1,
      null,
      ForkJoinPool.DEFAULT_KEEPALIVE,
      TimeUnit.MILLISECONDS
    )
  }

  /** Creates a {@code ForkJoinPool} with the indicated parallelism level, using
   *  defaults for all other parameters (see {@link #ForkJoinPool(int,
   *  ForkJoinWorkerThreadFactory, UncaughtExceptionHandler, boolean, int, int,
   *  int, Predicate, long, TimeUnit)}).
   *
   *  @param parallelism
   *    the parallelism level
   *  @throws IllegalArgumentException
   *    if parallelism less than or equal to zero, or greater than
   *    implementation limit
   *  @throws SecurityException
   *    if a security manager exists and the caller is not permitted to modify
   *    threads because it does not hold {@link
   *    java.lang.RuntimePermission}{@code ("modifyThread")}
   */
  def this(parallelism: Int) = {
    this(
      parallelism,
      ForkJoinPool.defaultForkJoinWorkerThreadFactory,
      null,
      false
    )
  }

  /** Creates a {@code ForkJoinPool} with parallelism equal to {@link
   *  java.lang.Runtime#availableProcessors}, using defaults for all other
   *  parameters (see {@link #ForkJoinPool(int, ForkJoinWorkerThreadFactory,
   *  UncaughtExceptionHandler, boolean, int, int, int, Predicate, long,
   *  TimeUnit)}).
   *
   *  @throws SecurityException
   *    if a security manager exists and the caller is not permitted to modify
   *    threads because it does not hold {@link
   *    java.lang.RuntimePermission}{@code ("modifyThread")}
   */
  def this() = {
    this(
      Math.min(ForkJoinPool.MAX_CAP, Runtime.getRuntime().availableProcessors())
    )
  }

  @alwaysinline
  private def compareAndSetCtl(expected: Long, value: Long): Boolean =
    ctlAtomic.compareExchangeStrong(expected, value)._1
  @alwaysinline
  private def compareAndExchangeCtl(expected: Long, value: Long): Long =
    ctlAtomic.compareExchangeStrong(expected, value)._2
  @alwaysinline
  private def getAndAddCtl(v: Long): Long =
    ctlAtomic.fetchAdd(v)
  @alwaysinline
  private def getAndBitwiseOrMode(v: Int): Int =
    modeAtomic.fetchOr(v)
  @alwaysinline
  private def getAndAddThreadIds(v: Int): Int =
    threadIdsAtomic.fetchAdd(v)

  // Creating, registering and deregistering workers

  /** Tries to construct and start one worker. Assumes that total count has
   *  already been incremented as a reservation. Invokes deregisterWorker on any
   *  failure.
   *
   *  @return
   *    true if successful
   */
  private def createWorker(): Boolean = {
    var ex: Throwable = null
    var wt: ForkJoinWorkerThread = null

    try {
      if (factory != null) {
        wt = factory.newThread(this)
        if (wt != null) {
          wt.start()
          return true
        }
      }
    } catch {
      case rex: Throwable =>
        ex = rex
    }
    deregisterWorker(wt, ex)
    false
  }

  /** Provides a name for ForkJoinWorkerThread constructor.
   */
  private[concurrent] final def nextWorkerThreadName(): String = {
    val tid = getAndAddThreadIds(1) + 1
    val prefix = Option(workerNamePrefix)
      .getOrElse("ForkJoinPool.commonPool-worker-") // commonPool has no prefix
    prefix + tid
  }

  /** Finishes initializing and records owned queue.
   *
   *  @param w
   *    caller's WorkQueue
   */
  private[concurrent] final def registerWorker(w: WorkQueue): Unit = {
    val lock = registrationLock
    val seed = {
      ThreadLocalRandom.localInit()
      ThreadLocalRandom.getProbe()
    }
    if (w != null && lock != null) {
      val modebits = (mode & FIFO) | w.config
      w.array = new Array[ForkJoinTask[_]](INITIAL_QUEUE_CAPACITY)
      w.stackPred = seed // stash for runWorker
      if ((modebits & INNOCUOUS) != 0) {
        w.initializeInnocuousWorker()
      }
      lock.lock()
      try {
        // find queue index
        val qs = queues
        if (qs != null && qs.nonEmpty) {
          val n = qs.length
          val m = n - 1

          @tailrec()
          def loopCalcId(id: Int, k: Int): Int = {
            val newId = id & m
            if (qs(newId) != null && k > 0) loopCalcId(newId - 2, k - 2)
            else if (k == 0) n | 1 // resize below
            else newId
          }

          val id = loopCalcId(id = (seed << 1) | 1, k = n)
          w.config = id | modebits
          w.phase = w.config // now publishable

          if (id < n)
            qs(id) = w
          else { // expand array
            val an = n << 1
            val am = an - 1
            val as = new Array[WorkQueue](an)
            as(id & am) = w
            for (j <- qs.indices.tail.by(2)) {
              as(j) = qs(j)
            }
            for {
              j <- qs.indices.by(2)
              q = qs(j) if q != null
            } {
              as(q.config & am) = q
            }
            VarHandle.releaseFence() // fill before publish
            queues = as
          }
        }
      } finally {
        lock.unlock()
      }
    }
  }

  /** Final callback from terminating worker, as well as upon failure to
   *  construct or start a worker. Removes record of worker from array, and
   *  adjusts counts. If pool is shutting down, tries to complete termination.
   *
   *  @param wt
   *    the worker thread, or null if construction failed
   *  @param ex
   *    the exception causing failure, or null if none
   */
  private[concurrent] final def deregisterWorker(
      wt: ForkJoinWorkerThread,
      ex: Throwable
  ): Unit = {
    val lock: ReentrantLock = registrationLock
    val w: WorkQueue = if (wt != null) wt.workQueue else null
    var cfg: Int = 0
    if (w != null && lock != null) {
      cfg = w.config
      val ns: Long = w.nsteals & 0xffffffffL

      lock.lock() // remove index from array
      val qs = queues
      val n: Int = qs.length
      val i: Int = cfg & (n - 1)
      if (qs != null && n > 0 && qs(i) == w) {
        qs(i) = null
      }
      stealCount += ns // accumulate steals
      lock.unlock()

      var c = ctl
      if ((cfg & QUIET) == 0) { // unless self-signalled, decrement counts
        while ({
          val c0 = c
          val newC = (RC_MASK & (c - RC_UNIT)) |
            (TC_MASK & (c - TC_UNIT)) |
            (SP_MASK & c)
          c = compareAndExchangeCtl(c, newC)
          c0 != c
        }) ()
      } else if (c == 0) { // was dropped on timeout
        cfg = 0 // suppress signal if last
      }
      @tailrec
      def cancelTasks(): Unit = {
        w.pop() match {
          case null => ()
          case t =>
            ForkJoinTask.cancelIgnoringExceptions(t)
            cancelTasks()
        }
      }
    }

    if (!tryTerminate(false, false) && w != null && (cfg & SRC) != 0)
      signalWork() // possibly replace worker
    if (ex != null)
      ForkJoinTask.rethrow(ex)
  }

  /*
   * Tries to create or release a worker if too few are running.
   */
  private[concurrent] final def signalWork(): Unit = {
    @tailrec
    def loop(c: Long): Unit = {
      ((c.toInt & ~UNSIGNALLED): @switch) match {
        case 0 => // no idle workers
          if ((c & ADD_WORKER) == 0L) () // enough total workers
          else {
            compareAndExchangeCtl(
              c,
              (RC_MASK & (c + RC_UNIT)) | (TC_MASK & (c + TC_UNIT))
            ) match {
              case `c` => createWorker()
              case newC =>
                if (newC < 0) loop(newC)
                else ()
            }
          }

        case sp =>
          val i = sp & SMASK
          val qs = queues
          def unstartedOrTerminated = qs == null
          def terminated = qs.length <= i
          def terminating = qs(i) == null

          if (unstartedOrTerminated || terminated || terminating) {} else {
            val v = qs(i)
            val vt = v.owner
            val nc = (v.stackPred & SP_MASK) | (UC_MASK & (c + RC_UNIT))
            compareAndExchangeCtl(c, nc) match {
              case `c` =>
                // release idle worker
                v.phase = sp
                vt.foreach(LockSupport.unpark)
              case newC =>
                if (newC < 0) loop(newC)
                else ()
            }
          }
      }
    }
    loop(c = ctl)
  }

  /** Top-level runloop for workers, called by ForkJoinWorkerThread.run. See
   *  above for explanation.
   *
   *  @param w
   *    caller's WorkQueue (may be null on failed initialization)
   */
  private[concurrent] final def runWorker(w: WorkQueue): Unit = {
    if (mode >= 0 && w != null) { // skip on failed init
      w.config |= SRC // mark as valid source
      var r = w.stackPred // use seed from registerWorker
      var src = 0

      @inline def tryScan(): Boolean = {
        src = scan(w, src, r)
        src >= 0
      }

      @inline def tryAwaitWork(): Boolean = {
        src = awaitWork(w)
        src == 0
      }

      while ({
        r ^= r << 13
        r ^= r >>> 17
        r ^= r << 5 // xorshift
        tryScan() || tryAwaitWork()
      }) ()
    }
  }

  /** Scans for and if found executes top-level tasks: Tries to poll each queue
   *  starting at a random index with random stride, returning source id or
   *  retry indicator if contended or inconsistent.
   *
   *  @param w
   *    caller's WorkQueue
   *  @param prevSrc
   *    the previous queue stolen from in current phase, or 0
   *  @param r
   *    random seed
   *  @return
   *    id of queue if taken, negative if none found, prevSrc for retry
   */
  private def scan(w: WorkQueue, prevSrc: Int, r0: Int): Int = {
    val qs = queues
    val n = if (w == null || qs == null) 0 else qs.length
    val step = r0 >>> 16 | 1
    var r = r0

    for {
      _ <- 0 until n
      j = r & (n - 1)
      _ = r += step
      q = qs(j) if q != null
      a = q.array if a != null
      cap = a.length if cap > 0

      b = q.base
      nextBase = b + 1
      k = (cap - 1) & b
      nextIndex = (cap - 1) & nextBase
      src = j | SRC
    } {
      val t = WorkQueue.getSlot(a, k)
      if (q.base != b) { // inconsistent
        return prevSrc
      } else if (t != null && WorkQueue.casSlotToNull(a, k, t)) {
        val next = a(nextIndex)
        q.base = nextBase
        w.source = src
        if (src != prevSrc && next != null) {
          signalWork() // propagate
        }
        w.topLevelExec(t, q)
        return src
      } else if (a(nextIndex) != null) { // revisit
        return prevSrc
      } else ()
    }
    // possibly resized
    if (queues != qs) prevSrc
    else -1
  }

  /** Advances worker phase, pushes onto ctl stack, and awaits signal or reports
   *  termination.
   *
   *  @return
   *    negative if terminated, else 0
   */
  private def awaitWork(w: WorkQueue): Int = {
    if (w == null)
      return -1 // already terminated

    val phase = (w.phase + SS_SEQ) & ~UNSIGNALLED
    w.phase |= UNSIGNALLED // advance phase

    @tailrec
    def tryExchangeAndUpdateCtl(prevCtl: Long): (Long, Long) = {
      w.stackPred = prevCtl.toInt
      val newCtl = ((prevCtl - RC_UNIT) & UC_MASK) | (phase & SP_MASK)
      compareAndExchangeCtl(prevCtl, newCtl) match {
        case `prevCtl` => (prevCtl, newCtl)
        case witnessed => tryExchangeAndUpdateCtl(witnessed)
      }
    }
    val (prevCtl, c) = tryExchangeAndUpdateCtl(ctl)

    Thread.interrupted() // clear status

    var deadline = 0L
    def setDeadline(v: Long): Unit = {
      deadline = v match {
        case 0L => 1L
        case _  => v
      }
    }

    val ac = (c >> RC_SHIFT).toInt
    val md = mode
    if (md < 0) { // pool is terminating
      return -1
    } else if ((md & SMASK) + ac <= 0) {
      var checkTermination = (md & SHUTDOWN) != 0
      val qs = queues // check for racing submission
      val n = if (qs != null) qs.length else 0
      setDeadline(System.currentTimeMillis() + keepAlive)
      breakable {
        for (i <- 0 until n by 2) {
          if (ctl != c) { // already signalled
            checkTermination = false
            break()
          } else {
            for {
              q <- Option(qs(i))
              a <- Option(q.array)
              cap = a.length if cap > 0
              b = q.base
              if b != q.top ||
                a((cap - 1) & b) != null ||
                q.source != 0
            } {
              if (compareAndSetCtl(c, prevCtl)) {
                w.phase = phase // self-signal
              }
              checkTermination = false
              break()
            }
          }
        }
      }
      if (checkTermination && tryTerminate(false, false)) {
        return -1 // trigger quiescent termination
      }
    } else ()

    var alt = false
    while (true) {
      val currentCtl = ctl
      if (w.phase >= 0) return 0
      else if (mode < 0) return -1
      else if (currentCtl == prevCtl) Thread.onSpinWait() // signal in progress
      else if ({ alt = !alt; !alt }) Thread.interrupted() // check between parks
      else if (deadline == 0L) LockSupport.park(this)
      else if (deadline - System.currentTimeMillis() > TIMEOUT_SLOP)
        LockSupport.parkUntil(this, deadline)
      else {
        val newCtl = ((UC_MASK & (currentCtl - TC_UNIT)) | (prevCtl & SP_MASK))
        if ((currentCtl.toInt & SMASK) == (w.config & SMASK) &&
            compareAndSetCtl(currentCtl, newCtl)) {
          w.config |= QUIET // sentinel for deregisterWorker
          return -1 // drop on timeout
        } else setDeadline(deadline + keepAlive)
      }
    }
    0
  }

  // Utilities used by ForkJoinTask

  /** Returns true if all workers are busy, possibly creating one if allowed
   */
  private[concurrent] final def isSaturated(): Boolean = {
    val maxTotal = (bounds >>> SWIDTH).toInt
    @tailrec
    def loop(): Boolean = {
      val c = ctl
      if ((c & ~UNSIGNALLED).toInt != 0) false
      else if ((c >>> TC_SHIFT).toShort >= maxTotal) true
      else {
        val nc = ((c + TC_UNIT) & TC_MASK) | (c & ~TC_MASK)
        if (compareAndSetCtl(c, nc)) !createWorker()
        else loop()
      }
    }
    loop()
  }

  /** Returns true if can start terminating if enabled, or already terminated
   */
  private[concurrent] final def canStop(): Boolean = {
    var md = mode
    def isStoped() = {
      // recheck mode on false return
      md = mode
      (md & STOP) != 0
    }

    @tailrec
    def loop(oldSum: Long): Boolean = {
      val qs = queues
      if (qs == null || isStoped())
        return true

      val c = ctl
      if (((md & SMASK) + (c >> RC_SHIFT).toInt) > 0)
        return isStoped()

      import scala.util.control.Breaks._
      var hasBreaked = false
      var checkSum = c
      // non-local return would break tail-rec
      breakable {
        for {
          i <- 1 until qs.length by 2
          q = qs(i) if q != null
          a = q.array if a != null
          cap = a.length if cap > 0
          s = q.top
        } {
          if (s != q.base ||
              a((cap - 1) & s) != null ||
              q.source != 0) {
            hasBreaked = true
            break()
          } else checkSum += (i.toLong << 32) ^ s
        }
      }

      if (hasBreaked) isStoped()
      else if (oldSum == checkSum && queues == qs) true
      else loop(checkSum)
    }
    loop(0L)
  }

  /** Tries to decrement counts (somet@HotSpotIntrinsicCandidate mes implicitly)
   *  and possibly arrange for a compensating worker in preparation for
   *  blocking. May fail due to interference, in which case -1 is returned so
   *  caller may retry. A zero return value indicates that the caller doesn't
   *  need to re-adjust counts when later unblocked.
   *
   *  @param c
   *    incoming ctl value
   *  @return
   *    UNCOMPENSATE: block then adjust, 0: block, -1 : retry
   */
  private def tryCompensate(c: Long): Int = {
    val md = mode
    val b = bounds
    // counts are signed centered at parallelism level == 0
    val minActive = (b & SMASK).toShort
    val maxTotal = b >>> SWIDTH
    val active = (c >> RC_SHIFT).toInt
    val total = (c >>> TC_SHIFT).toShort
    val sp = c.toInt & ~UNSIGNALLED

    if ((md & SMASK) == 0) return 0 // cannot compensate if parallelism zero
    else if (total >= 0) {
      if (sp != 0) { // activate idle worker
        val qs = queues
        val n = if (qs != null) qs.length else 0
        val v: WorkQueue = if (n > 0) qs(sp & (n - 1)) else null
        if (v != null) {
          val nc: Long = (v.stackPred.toLong & SP_MASK) | (UC_MASK & c)
          if (compareAndSetCtl(c, nc)) {
            v.phase = sp
            v.owner.foreach(LockSupport.unpark)
            return UNCOMPENSATE
          }
        }
        return -1 // retry
      } else if (active > minActive) { // reduce parallelism
        val nc = (RC_MASK & (c - RC_UNIT)) | (~RC_MASK & c)
        return if (compareAndSetCtl(c, nc)) UNCOMPENSATE
        else -1
      }
    }
    if (total < maxTotal) { // expand pool
      val nc = ((c + TC_UNIT) & TC_MASK) | (c & ~TC_MASK)
      return if (!compareAndSetCtl(c, nc)) -1
      else {
        if (!createWorker()) 0
        else UNCOMPENSATE
      }
    } else if (!compareAndSetCtl(c, c))
      return -1;
    else if (saturate != null && saturate.test(this))
      return 0
    else
      throw new RejectedExecutionException(
        "Thread limit exceeded replacing blocked worker"
      )
  }

  /** Readjusts RC count called from ForkJoinTask after blocking.
   */
  private[concurrent] final def uncompensate(): Unit = getAndAddCtl(RC_UNIT)

  /** Helps if possible until the given task is done. Scans other queues for a
   *  task produced by one of w's stealers returning compensated blocking
   *  sentinel if none are found.
   *
   *  @param task
   *    the task
   *  @param w
   *    caller's WorkQueue
   *  @param canHelp
   *    if false, compensate only
   *  @return
   *    task status on exit, or UNCOMPENSATE for compensated blocking
   */
  private[concurrent] final def helpJoin(
      task: ForkJoinTask[_],
      w: WorkQueue
  ): Int = {
    var s = 0
    if (task != null && w != null) {
      val wSrc = w.source
      val wid = w.config & SMASK
      var scan = true
      var c = 0L
      while (true) {
        s = task.status
        if (s < 0) return s
        else if ({ scan = !scan; scan }) { // previous scan was empty
          if (mode < 0)
            ForkJoinTask.cancelIgnoringExceptions(task)
          else {
            def ctlIsConsistient() = {
              val prevC = c
              c = ctl
              prevC == c
            }
            def componsated() = {
              s = tryCompensate(c)
              s >= 0
            }
            if (ctlIsConsistient() && componsated()) {
              return s // block
            }
          }
        } else {
          val qs = queues
          val n = if (qs != null) qs.length else 0
          val m = n - 1
          var r = wid
          breakable {
            for (_ <- n until 0 by -2) {
              r += 2
              val j = r & m
              val q = qs(j)
              val a = if (q != null) q.array else null
              val cap = if (a != null) a.length else 0
              if (cap > 0) {
                val b = q.base
                val nextBase = b + 1
                val k = (cap - 1) & b
                val sq = q.source & SMASK
                val t = WorkQueue.getSlot(a, k)
                val eligible =
                  sq == wid || {
                    val x = qs(sq & m)
                    x != null && { // indirect
                      val sx = x.source & SMASK
                      sx == wid || {
                        val y = qs(sx & m)
                        y != null && // 2-indirect
                          (y.source & SMASK) == wid
                      }
                    }
                  }

                s = task.status
                if (s <= 0) return s
                else if ((q.source & SMASK) != sq || q.base != b) {
                  scan = true // inconsistent
                } else if (t == null) {
                  val shouldScan = a(nextBase & (cap - 1)) != null ||
                    q.top != b // lagging
                  scan |= shouldScan
                } else if (eligible) {
                  if (WorkQueue.casSlotToNull(a, k, t)) {
                    q.base = nextBase
                    w.source = j | SRC
                    t.doExec()
                    w.source = wSrc
                  }
                  scan = true
                  break()
                }
              }
            }
          }
        }
      }
    }
    s
  }

  /** Extra helpJoin steps for CountedCompleters. Scans for and runs subtasks of
   *  the given root task, returning if none are found.
   *
   *  @param task
   *    root of CountedCompleter computation
   *  @param w
   *    caller's WorkQueue
   *  @param owned
   *    true if owned by a ForkJoinWorkerThread
   *  @return
   *    task status on exit
   */
  private[concurrent] final def helpComplete(
      task: ForkJoinTask[_],
      w: WorkQueue,
      owned: Boolean
  ): Int = {
    var s = 0
    if (task != null && w != null) {
      var r = w.config
      var scan = true
      var locals = true
      var c = 0L
      while (true) {
        if (locals) { // try locals before scanning
          s = w.helpComplete(task, owned, 0)
          if (s < 0) return s
          else locals = false
        } else if ({ s = task.status; s < 0 }) return s
        else if ({ scan = !scan; scan }) {
          val prevCtl = c
          c = ctl
          if (prevCtl == c) return s
        } else { // scan for subtasks
          val qs = queues
          val n = if (qs != null) qs.length else 0
          for {
            _ <- n until 0 by -1
            j = r & (n - 1)
            _ = r += 1
            q = qs(j) if q != null
            a = q.array if a != null
            cap = a.length if cap > 0
            b = q.base
            nextBase = b + 1
            k = (cap - 1) & b
            t = WorkQueue.getSlot(a, k)
          } {
            @tailrec
            def checkEligable(current: CountedCompleter[_]): Boolean = {
              current match {
                case `task` => true
                case null   => false
                case _      => checkEligable(current.completer)
              }
            }
            val isEligable = t match {
              case completer: CountedCompleter[_] => checkEligable(completer)
              case _                              => false
            }
            s = task.status
            if (s < 0) return s
            else if (q.base != b)
              scan = true // inconsistent
            else if (t == null) {
              val shouldScan = a(nextBase & (cap - 1)) != null ||
                q.top != b
              scan |= shouldScan
            } else if (isEligable) {
              if (WorkQueue.casSlotToNull(a, k, t)) {
                q.setBaseOpaque(nextBase)
                t.doExec()
                locals = true
              }
              scan = true
              break()
            }
          }
        }
      }
    }
    return s
  }

  /** Scans for and returns a polled task, if available. Used only for untracked
   *  polls. Begins scan at an index (scanRover) advanced on each call, to avoid
   *  systematic unfairness.
   *
   *  @param submissionsOnly
   *    if true, only scan submission queues
   */
  private def pollScan(submissionsOnly: Boolean): ForkJoinTask[_] = {
    VarHandle.acquireFence()
    scanRover += 0x61c88647 // Weyl increment raciness OK
    val r =
      if (submissionsOnly) scanRover & ~1 // even indices only
      else scanRover
    val step = if (submissionsOnly) 2 else 1

    @tailrec
    def loop(): ForkJoinTask[_] = {
      val qs = queues
      var found: Option[ForkJoinTask[_]] = None
      var rescan = false

      if (qs == null || qs.isEmpty) null
      else
        breakable {
          val n = queues.length
          for {
            idx <- 0.until(n).by(step)
            j = (n - 1) & (r + idx)
            q = queues(j) if q != null
            a = q.array if a != null
            cap = a.length if cap > 0
            b = q.base
            nextBase = b + 1
            k = (cap - 1) & b
            t = WorkQueue.getSlot(a, k)
          } {
            if (q.base != b)
              rescan = true
            else if (t == null)
              rescan |= (q.top != b || a(nextBase & (cap - 1)) != null)
            else if (!WorkQueue.casSlotToNull(a, k, t))
              rescan = true
            else {
              !q.basePtr = nextBase
              // Compiler seemed to have problems to return value directly from this place
              // instead store result outside and break loop
              found = Some(t)
              break()
            }
          }
        }

      found match {
        case Some(value) => value
        case None =>
          if (!rescan && queues == qs) null
          else loop()
      }
    }

    loop()
  }

  /** Runs tasks until {@code isQuiescent()}. Rather than blocking when tasks
   *  cannot be found, rescans until all others cannot find tasks either.
   *
   *  @param nanos
   *    max wait time (Long.MAX_VALUE if effectively untimed)
   *  @param interruptible
   *    true if return on interrupt
   *  @return
   *    positive if quiescent, negative if interrupted, else 0
   */
  private[concurrent] final def helpQuiescePool(
      w: WorkQueue,
      nanos: Long,
      interruptible: Boolean
  ): Int = {
    if (w == null) return 0
    val startTime = System.nanoTime()
    var parkTime = 0L
    val prevSrc = w.source
    var wsrc = prevSrc
    val cfg = w.config
    var r = cfg + 1
    var active = true
    var locals = true
    while (true) {
      var busy = false
      var scan = false
      if (locals) { // run local tasks before (re)polling
        locals = false
        var u = null: ForkJoinTask[_]
        while ({
          u = w.nextLocalTask(cfg)
          u != null
        }) { u.doExec() }
      }
      val qs = queues
      val n = if (qs == null) 0 else qs.length
      import scala.util.control.Breaks._
      breakable {
        for (i <- n until 0 by -1) {
          // int j, b, cap WorkQueue q Array[ForkJoinTask[_]]()  a
          val j = (n - 1 & r)
          val q = qs(j)
          val a = if (q != null) q.array else null
          val cap = if (a != null) a.length else 0
          if (q != w && cap > 0) {
            val b = q.base
            val k = (cap - 1) & b
            val nextBase = b + 1
            val src = j | SRC
            val t = WorkQueue.getSlot(a, k)
            if (q.base != b) {
              busy = true
              scan = true
            } else if (t != null) {
              busy = true
              scan = true
              if (!active) {
                // increment before taking
                active = true
                getAndAddCtl(RC_UNIT)
              }
              if (WorkQueue.casSlotToNull(a, k, t)) {
                q.base = nextBase
                w.source = src
                t.doExec()
                w.source = prevSrc
                wsrc = prevSrc
                locals = true
              }
              break()
            } else if (!busy) {
              if (q.top != b || a(nextBase & (cap - 1)) != null) {
                busy = true
                scan = true
              } else if (q.source != QUIET && q.phase >= 0)
                busy = true
            }
          }
          r += 1
        }
      }
      VarHandle.acquireFence()
      if (!scan && queues == qs) {
        if (!busy) {
          w.source = prevSrc
          if (!active)
            getAndAddCtl(RC_UNIT)
          return 1
        }
        if (wsrc != QUIET) {
          w.source = QUIET
          wsrc = QUIET
        }
        if (active) { // decrement
          active = false
          parkTime = 0L
          getAndAddCtl(RC_MASK & -RC_UNIT)
        } else if (parkTime == 0L) {
          parkTime = 1L << 10 // initially about 1 usec
          Thread.`yield`()
        } else {
          val interrupted = interruptible && Thread.interrupted()
          if (interrupted || System.nanoTime() - startTime > nanos) {
            getAndAddCtl(RC_UNIT)
            return if (interrupted) -1 else 0
          } else {
            LockSupport.parkNanos(this, parkTime)
            if (parkTime < (nanos >>> 8) && parkTime < (1L << 20))
              parkTime <<= 1 // max sleep approx 1 sec or 1% nanos
          }
        }
      }
    }
    throw new IllegalStateException("unaccessible")
  }

  /** Helps quiesce from external caller until done, interrupted, or timeout
   *
   *  @param nanos
   *    max wait time (Long.MAX_VALUE if effectively untimed)
   *  @param interruptible
   *    true if return on interrupt
   *  @return
   *    positive if quiescent, negative if interrupted, else 0
   */
  private[concurrent] final def externalHelpQuiescePool(
      nanos: Long,
      interruptible: Boolean
  ): Int = {
    val startTime = System.nanoTime()
    var parkTime = 0L
    while (true) {
      val t = pollScan(false)
      if (t != null) {
        t.doExec()
        parkTime = 0L
      } else if (canStop())
        return 1
      else if (parkTime == 0L) {
        parkTime = 1L << 10
        Thread.`yield`()
      } else if ((System.nanoTime() - startTime) > nanos)
        return 0
      else if (interruptible && Thread.interrupted())
        return -1
      else {
        LockSupport.parkNanos(this, parkTime)
        if (parkTime < (nanos >>> 8) && parkTime < (1L << 20))
          parkTime <<= 1
      }
    }
    throw new IllegalStateException("Unaccessible")
  }

  /** Gets and removes a local or stolen task for the given worker.
   *
   *  @return
   *    a task, if available
   */
  private[concurrent] final def nextTaskFor(w: WorkQueue): ForkJoinTask[_] =
    if (w == null) pollScan(false)
    else
      w.nextLocalTask(w.config) match {
        case null => pollScan(false)
        case t    => t
      }

  // External operations

  /** Finds and locks a WorkQueue for an external submitter, or returns null if
   *  shutdown or terminating.
   */
  private[concurrent] final def submissionQueue(): WorkQueue = {

    @tailrec
    def loop(r: Int): WorkQueue = {
      val qs = queues
      val n = if (qs != null) qs.length else 0
      if ((mode & SHUTDOWN) != 0 || n <= 0) {
        return null
      }

      val id = r << 1
      val i = (n - 1) & id
      qs(i) match {
        case null =>
          Option(registrationLock)
            .foreach { lock =>
              val w = new WorkQueue(id | SRC)
              lock.lock() // install under lock
              if (qs(i) == null)
                qs(i) = w // else lost race discard
              lock.unlock()
            }
          loop(r)
        case q if !q.tryLock() => // move and restart
          loop(ThreadLocalRandom.advanceProbe(r))
        case q => q
      }
    }

    val r = ThreadLocalRandom.getProbe() match {
      case 0 => // initialize caller's probe
        ThreadLocalRandom.localInit()
        ThreadLocalRandom.getProbe()
      case probe => probe
    }
    loop(r) // even indices only
  }

  /** Adds the given task to an external submission queue, or throws exception
   *  if shutdown or terminating.
   *
   *  @param task
   *    the task. Caller must ensure non-null.
   */
  private[concurrent] final def externalPush(task: ForkJoinTask[_]): Unit = {
    val q = submissionQueue()
    if (q == null)
      throw new RejectedExecutionException() // shutdown or disabled

    if (q.lockedPush(task)) {
      signalWork()
    }
  }

  /** Pushes a possibly-external submission.
   */
  private def externalSubmit[T](task: ForkJoinTask[T]): ForkJoinTask[T] = {
    if (task == null)
      throw new NullPointerException()

    Thread.currentThread() match {
      case worker: ForkJoinWorkerThread
          if worker.workQueue != null &&
            worker.pool == this =>
        worker.workQueue.push(task, this)
      case _ => externalPush(task)
    }
    task
  }

  /** Returns queue for an external thread, if one exists
   */
  private[concurrent] final def externalQueue(): WorkQueue = {
    val r = ThreadLocalRandom.getProbe()
    val qs = queues
    val n = if (qs != null) qs.length else 0
    if (n > 0 && r != 0) {
      val idx = (n - 1) & (r << 1)
      qs(idx)
    } else null
  }

  // Termination

  /** Possibly initiates and/or completes termination.
   *
   *  @param now
   *    if true, unconditionally terminate, else only if no work and no active
   *    workers
   *  @param enable
   *    if true, terminate when next possible
   *  @return
   *    true if terminating or terminated
   */
  private def tryTerminate(now: Boolean, enable: Boolean): Boolean = {
    // try to set SHUTDOWN, then STOP, then help terminate
    var md: Int = mode
    if ((md & SHUTDOWN) == 0) {
      if (!enable) return false
      md = getAndBitwiseOrMode(SHUTDOWN)
    }
    if ((md & STOP) == 0) {
      if (!now && !canStop()) return false
      md = getAndBitwiseOrMode(STOP)
    }
    if ((md & TERMINATED) == 0) {
      // help cancel tasks
      while ({
        val t = pollScan(false)
        t != null && {
          ForkJoinTask.cancelIgnoringExceptions(t)
          true
        }
      }) ()

      // unblock other workers
      val qs = queues
      val n = if (qs != null) qs.length else 0
      if (n > 0) {
        for {
          j <- 1 until n by 2
          q = qs(j) if q != null
          thread <- q.owner if !thread.isInterrupted()
        } {
          try thread.interrupt()
          catch {
            case ignore: Throwable => ()
          }
        }
      }

      // signal when no workers
      if ((md & SMASK) + (ctl >>> TC_SHIFT).toShort <= 0 &&
          (getAndBitwiseOrMode(TERMINATED) & TERMINATED) == 0) {
        val lock = registrationLock
        if (lock != null) {
          lock.lock()
          val cond = termination
          if (cond != null) cond.signalAll()
          lock.unlock()
        }
      }
    }
    true
  }

  // Exported methods

  // Execution methods

  /** Performs the given task, returning its result upon completion. If the
   *  computation encounters an unchecked Exception or Error, it is rethrown as
   *  the outcome of this invocation. Rethrown exceptions behave in the same way
   *  as regular exceptions, but, when possible, contain stack traces (as
   *  displayed for example using {@code ex.printStackTrace()}) of both the
   *  current thread as well as the thread actually encountering the exception
   *  minimally only the latter.
   *
   *  @param task
   *    the task
   *  @param <T>
   *    the type of the task's result
   *  @return
   *    the task's result
   *  @throws NullPointerException
   *    if the task is null
   *  @throws RejectedExecutionException
   *    if the task cannot be scheduled for execution
   */
  def invoke[T](task: ForkJoinTask[T]): T = {
    externalSubmit(task)
    task.join()
  }

  /** Arranges for (asynchronous) execution of the given task.
   *
   *  @param task
   *    the task
   *  @throws NullPointerException
   *    if the task is null
   *  @throws RejectedExecutionException
   *    if the task cannot be scheduled for execution
   */
  def execute(task: ForkJoinTask[_]): Unit = {
    externalSubmit(task)
  }

  // AbstractExecutorService methods

  /** @throws NullPointerException
   *    if the task is null
   *  @throws RejectedExecutionException
   *    if the task cannot be scheduled for execution
   */
  override def execute(task: Runnable): Unit = {
    // Scala3 compiler has problems with type intererenfe when passed to externalSubmit directlly
    val taskToUse: ForkJoinTask[_] = task match {
      case task: ForkJoinTask[_] => task
      case _                     => new ForkJoinTask.RunnableExecuteAction(task)
    }
    externalSubmit(taskToUse)
  }

  /** Submits a ForkJoinTask for execution.
   *
   *  @param task
   *    the task to submit
   *  @param <T>
   *    the type of the task's result
   *  @return
   *    the task
   *  @throws NullPointerException
   *    if the task is null
   *  @throws RejectedExecutionException
   *    if the task cannot be scheduled for execution
   */
  def submit[T](task: ForkJoinTask[T]): ForkJoinTask[T] = {
    externalSubmit(task)
  }

  /** @throws NullPointerException
   *    if the task is null
   *  @throws RejectedExecutionException
   *    if the task cannot be scheduled for execution
   */
  override def submit[T](task: Callable[T]): ForkJoinTask[T] = {
    externalSubmit(new ForkJoinTask.AdaptedCallable[T](task))
  }

  /** @throws NullPointerException
   *    if the task is null
   *  @throws RejectedExecutionException
   *    if the task cannot be scheduled for execution
   */
  override def submit[T](task: Runnable, result: T): ForkJoinTask[T] = {
    externalSubmit(new ForkJoinTask.AdaptedRunnable[T](task, result))
  }

  /** @throws NullPointerException
   *    if the task is null
   *  @throws RejectedExecutionException
   *    if the task cannot be scheduled for execution
   */
  override def submit(task: Runnable): ForkJoinTask[_] = {
    val taskToUse = task match {
      case task: ForkJoinTask[_] => task
      case _ => new ForkJoinTask.AdaptedRunnableAction(task): ForkJoinTask[_]
    }
    externalSubmit(taskToUse)
  }

  /** @throws NullPointerException
   *    {@inheritDoc}
   *  @throws RejectedExecutionException
   *    {@inheritDoc}
   */
  override def invokeAll[T](
      tasks: Collection[_ <: Callable[T]]
  ): List[Future[T]] = {
    val futures = new ArrayList[Future[T]](tasks.size())
    try {
      tasks.forEach { t =>
        val f = new ForkJoinTask.AdaptedInterruptibleCallable[T](t)
        futures.add(f)
        externalSubmit(f)
      }
      for (i <- futures.size() - 1 to 0 by -1) {
        futures.get(i).asInstanceOf[ForkJoinTask[_]].quietlyJoin()
      }
      futures
    } catch {
      case t: Throwable =>
        futures.forEach(ForkJoinTask.cancelIgnoringExceptions(_))
        throw t
    }
  }

  @throws[InterruptedException]
  override def invokeAll[T](
      tasks: Collection[_ <: Callable[T]],
      timeout: Long,
      unit: TimeUnit
  ): List[Future[T]] = {
    val nanos = unit.toNanos(timeout)
    val futures = new ArrayList[Future[T]](tasks.size())
    try {
      tasks.forEach { t =>
        val f = new ForkJoinTask.AdaptedInterruptibleCallable[T](t)
        futures.add(f)
        externalSubmit(f)
      }
      val startTime = System.nanoTime()
      var ns = nanos
      def timedOut = ns < 0L
      for (i <- futures.size() - 1 to 0 by -1) {
        val f = futures.get(i)
        if (!f.isDone()) {
          if (timedOut)
            ForkJoinTask.cancelIgnoringExceptions(f)
          else {
            try f.get(ns, TimeUnit.NANOSECONDS)
            catch {
              case _: CancellationException | _: TimeoutException |
                  _: ExecutionException =>
                ()
            }
            ns = nanos - (System.nanoTime() - startTime)
          }
        }
      }
      futures
    } catch {
      case t: Throwable =>
        futures.forEach(ForkJoinTask.cancelIgnoringExceptions(_))
        throw t
    }
  }

  @throws[InterruptedException]
  @throws[ExecutionException]
  override def invokeAny[T](tasks: Collection[_ <: Callable[T]]): T = {
    if (tasks.isEmpty()) throw new IllegalArgumentException()
    val n = tasks.size()
    val root = new InvokeAnyRoot[T](n, this)
    val fs = new ArrayList[InvokeAnyTask[T]](n)
    breakable {
      tasks.forEach {
        case null => throw new NullPointerException()
        case c =>
          val f = new InvokeAnyTask[T](root, c)
          fs.add(f)
          if (isSaturated()) f.doExec()
          else externalSubmit(f)
          if (root.isDone()) break()
      }
    }
    try root.get()
    finally fs.forEach(ForkJoinTask.cancelIgnoringExceptions(_))
  }

  @throws[InterruptedException]
  @throws[ExecutionException]
  @throws[TimeoutException]
  override def invokeAny[T](
      tasks: Collection[_ <: Callable[T]],
      timeout: Long,
      unit: TimeUnit
  ): T = {
    val nanos = unit.toNanos(timeout)
    if (tasks.isEmpty()) throw new IllegalArgumentException()
    val n = tasks.size()
    val root = new InvokeAnyRoot[T](n, this)
    val fs = new ArrayList[InvokeAnyTask[T]](n)
    breakable {
      tasks.forEach {
        case null => throw new NullPointerException()
        case c =>
          val f = new InvokeAnyTask(root, c)
          fs.add(f)
          if (isSaturated()) f.doExec()
          else externalSubmit(f)
          if (root.isDone()) break()
      }
    }
    try root.get(nanos, TimeUnit.NANOSECONDS)
    finally fs.forEach(ForkJoinTask.cancelIgnoringExceptions(_))
  }

  /** Returns the factory used for constructing new workers.
   *
   *  @return
   *    the factory used for constructing new workers
   */
  def getFactory(): ForkJoinWorkerThreadFactory = factory

  /** Returns the handler for internal worker threads that terminate due to
   *  unrecoverable errors encountered while executing tasks.
   *
   *  @return
   *    the handler, or {@code null} if none
   */
  def getUncaughtExceptionHandler(): UncaughtExceptionHandler = ueh

  /** Returns the targeted parallelism level of this pool.
   *
   *  @return
   *    the targeted parallelism level of this pool
   */
  def getParallelism(): Int = {
    (!modePtr & SMASK).max(1)
  }

  /** Returns the number of worker threads that have started but not yet
   *  terminated. The result returned by this method may differ from {@link
   *  #getParallelism} when threads are created to maintain parallelism when
   *  others are cooperatively blocked.
   *
   *  @return
   *    the number of worker threads
   */
  def getPoolSize(): Int = {
    ((mode & SMASK) + (ctl >>> TC_SHIFT).toShort)
  }

  /** Returns {@code true} if this pool uses local first-in-first-out scheduling
   *  mode for forked tasks that are never joined.
   *
   *  @return
   *    {@code true} if this pool uses async mode
   */
  def getAsyncMode(): Boolean = {
    (mode & FIFO) != 0
  }

  /** Returns an estimate of the number of worker threads that are not blocked
   *  waiting to join tasks or for other managed synchronization. This method
   *  may overestimate the number of running threads.
   *
   *  @return
   *    the number of worker threads
   */
  @stub()
  def getRunningThreadCount(): Int = {
    ???
    // VarHandle.acquireFence()
    // WorkQueue[] qs WorkQueue q
    // int rc = 0
    // if ((qs = queues) != null) {
    //     for (int i = 1 i < qs.length i += 2) {
    //         if ((q = qs[i]) != null && q.isApparentlyUnblocked())
    //             ++rc
    //     }
    // }
    // return rc
  }

  /** Returns an estimate of the number of threads that are currently stealing
   *  or executing tasks. This method may overestimate the number of active
   *  threads.
   *
   *  @return
   *    the number of active threads
   */
  def getActiveThreadCount(): Int = {
    val r = (mode & SMASK) + (ctl >> RC_SHIFT).toInt
    r.max(0) // suppress momentarily negative values
  }

  /** Returns {@code true} if all worker threads are currently idle. An idle
   *  worker is one that cannot obtain a task to execute because none are
   *  available to steal from other threads, and there are no pending
   *  submissions to the pool. This method is conservative it might not return
   *  {@code true} immediately upon idleness of all threads, but will eventually
   *  become true if threads remain inactive.
   *
   *  @return
   *    {@code true} if all threads are currently idle
   */
  def isQuiescent(): Boolean = canStop()

  /** Returns an estimate of the total number of completed tasks that were
   *  executed by a thread other than their submitter. The reported value
   *  underestimates the actual total number of steals when the pool is not
   *  quiescent. This value may be useful for monitoring and tuning fork/join
   *  programs: in general, steal counts should be high enough to keep threads
   *  busy, but low enough to avoid overhead and contention across threads.
   *
   *  @return
   *    the number of steals
   */
  def getStealCount(): Long = {
    var count = stealCount
    val qs = queues
    if (queues != null) {
      for {
        i <- 1 until qs.length by 2
        q = qs(i) if q != null
      } count += q.nsteals & 0xffffffffL
    }
    count
  }

  /** Returns an estimate of the total number of tasks currently held in queues
   *  by worker threads (but not including tasks submitted to the pool that have
   *  not begun executing). This value is only an approximation, obtained by
   *  iterating across all threads in the pool. This method may be useful for
   *  tuning task granularities.
   *
   *  @return
   *    the number of queued tasks
   */
  def getQueuedTaskCount(): Long = {
    VarHandle.acquireFence()
    var count = 0
    val qs = queues
    if (qs != null) {
      for {
        i <- 1 until qs.length by 2
        q = qs(i) if q != null
      } count += q.queueSize()
    }
    count
  }

  /** Returns an estimate of the number of tasks submitted to this pool that
   *  have not yet begun executing. This method may take time proportional to
   *  the number of submissions.
   *
   *  @return
   *    the number of queued submissions
   */
  def getQueuedSubmissionCount(): Int = {
    VarHandle.acquireFence()
    var count = 0
    val qs = queues
    if (qs != null) {
      for {
        i <- 0 until qs.length by 2
        q = qs(i) if q != null
      } count += q.queueSize()
    }
    count
  }

  /** Returns {@code true} if there are any tasks submitted to this pool that
   *  have not yet begun executing.
   *
   *  @return
   *    {@code true} if there are any queued submissions
   */
  def hasQueuedSubmissions(): Boolean = {

    VarHandle.acquireFence()
    val qs = queues
    if (qs != null) {
      for {
        i <- 0 until qs.length by 2
        q = qs(i) if q != null
      } if (!q.isEmpty()) return true
    }
    false
  }

  /** Removes and returns the next unexecuted submission if one is available.
   *  This method may be useful in extensions to this class that re-assign work
   *  in systems with multiple pools.
   *
   *  @return
   *    the next submission, or {@code null} if none
   */
  protected[concurrent] def pollSubmission(): ForkJoinTask[_] = {
    pollScan(true)
  }

  /** Removes all available unexecuted submitted and forked tasks from
   *  scheduling queues and adds them to the given collection, without altering
   *  their execution status. These may include artificially generated or
   *  wrapped tasks. This method is designed to be invoked only when the pool is
   *  known to be quiescent. Invocations at other times may not remove all
   *  tasks. A failure encountered while attempting to add elements to
   *  collection {@code c} may result in elements being in neither, either or
   *  both collections when the associated exception is thrown. The behavior of
   *  this operation is undefined if the specified collection is modified while
   *  the operation is in progress.
   *
   *  @param c
   *    the collection to transfer elements into
   *  @return
   *    the number of elements transferred
   */
  protected def drainTasksTo(c: Collection[_ >: ForkJoinTask[_]]): Int = {
    var count = 0
    while ({
      val t = pollScan(false)
      t match {
        case null => false
        case t =>
          c.add(t)
          true
      }
    }) {
      count += 1
    }
    count
  }

  /** Returns a string identifying this pool, as well as its state, including
   *  indications of run state, parallelism level, and worker and task counts.
   *
   *  @return
   *    a string identifying this pool, as well as its state
   */
  override def toString(): String = {
    // Use a single pass through queues to collect counts
    val md: Int = mode // read volatile fields first
    val c: Long = ctl
    var st: Long = stealCount
    var ss, qt: Long = 0L
    var rc = 0
    if (queues != null) {
      queues.indices.foreach { i =>
        val q = queues(i)
        if (q != null) {
          val size = q.queueSize()
          if ((i & 1) == 0)
            ss += size
          else {
            qt += size
            st += q.nsteals.toLong & 0xffffffffL
            if (q.isApparentlyUnblocked()) {
              rc += 1
            }
          }
        }
      }
    }

    val pc = md & SMASK
    val tc = pc + (c >>> TC_SHIFT).toShort
    val ac = (pc + (c >> RC_SHIFT).toInt) match {
      case n if n < 0 => 0 // ignore transient negative
      case n          => n
    }

    @alwaysinline
    def modeSetTo(mode: Int): Boolean = (md & mode) != 0
    val level =
      if (modeSetTo(TERMINATED)) "Terminated"
      else if (modeSetTo(STOP)) "Terminating"
      else if (modeSetTo(SHUTDOWN)) "Shutting down"
      else "Running"

    return super.toString() +
      "[" + level +
      ", parallelism = " + pc +
      ", size = " + tc +
      ", active = " + ac +
      ", running = " + rc +
      ", steals = " + st +
      ", tasks = " + qt +
      ", submissions = " + ss +
      "]"
  }

  /** Possibly initiates an orderly shutdown in which previously submitted tasks
   *  are executed, but no new tasks will be accepted. Invocation has no effect
   *  on execution state if this is the {@link #commonPool()}, and no additional
   *  effect if already shut down. Tasks that are in the process of being
   *  submitted concurrently during the course of this method may or may not be
   *  rejected.
   *
   *  @throws SecurityException
   *    if a security manager exists and the caller is not permitted to modify
   *    threads because it does not hold {@link
   *    java.lang.RuntimePermission}{@code ("modifyThread")}
   */
  def shutdown(): Unit = {
    if (this != common) tryTerminate(false, true)
  }

  /** Possibly attempts to cancel and/or stop all tasks, and reject all
   *  subsequently submitted tasks. Invocation has no effect on execution state
   *  if this is the {@link #commonPool()}, and no additional effect if already
   *  shut down. Otherwise, tasks that are in the process of being submitted or
   *  executed concurrently during the course of this method may or may not be
   *  rejected. This method cancels both existing and unexecuted tasks, in order
   *  to permit termination in the presence of task dependencies. So the method
   *  always returns an empty list (unlike the case for some other Executors).
   *
   *  @return
   *    an empty list
   *  @throws SecurityException
   *    if a security manager exists and the caller is not permitted to modify
   *    threads because it does not hold {@link
   *    java.lang.RuntimePermission}{@code ("modifyThread")}
   */
  def shutdownNow(): List[Runnable] = {
    if (this != common)
      tryTerminate(true, true)
    Collections.emptyList()
  }

  /** Returns {@code true} if all tasks have completed following shut down.
   *
   *  @return
   *    {@code true} if all tasks have completed following shut down
   */
  def isTerminated(): Boolean = {
    (mode & TERMINATED) != 0
  }

  /** Returns {@code true} if the process of termination has commenced but not
   *  yet completed. This method may be useful for debugging. A return of {@code
   *  true} reported a sufficient period after shutdown may indicate that
   *  submitted tasks have ignored or suppressed interruption, or are waiting
   *  for I/O, causing this executor not to properly terminate. (See the
   *  advisory notes for class {@link ForkJoinTask} stating that tasks should
   *  not normally entail blocking operations. But if they do, they must abort
   *  them on interrupt.)
   *
   *  @return
   *    {@code true} if terminating but not yet terminated
   */
  def isTerminating(): Boolean = {
    (mode & (STOP | TERMINATED)) == STOP
  }

  /** Returns {@code true} if this pool has been shut down.
   *
   *  @return
   *    {@code true} if this pool has been shut down
   */
  def isShutdown(): Boolean = {
    (mode & SHUTDOWN) != 0
  }

  /** Blocks until all tasks have completed execution after a shutdown request,
   *  or the timeout occurs, or the current thread is interrupted, whichever
   *  happens first. Because the {@link #commonPool()} never terminates until
   *  program shutdown, when applied to the common pool, this method is
   *  equivalent to {@link #awaitQuiescence(long, TimeUnit)} but always returns
   *  {@code false}.
   *
   *  @param timeout
   *    the maximum time to wait
   *  @param unit
   *    the time unit of the timeout argument
   *  @return
   *    {@code true} if this executor terminated and {@code false} if the
   *    timeout elapsed before termination
   *  @throws InterruptedException
   *    if interrupted while waiting
   */
  @throws[InterruptedException]
  def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = {
    var nanos = unit.toNanos(timeout)
    var terminated = false
    if (this == common) {
      val q = Thread.currentThread() match {
        case worker: ForkJoinWorkerThread if worker.pool == this =>
          helpQuiescePool(worker.workQueue, nanos, true)
        case _ =>
          externalHelpQuiescePool(nanos, true)
      }
      if (q < 0) throw new InterruptedException()
    } else {
      def isTerminated() = (mode & TERMINATED) != 0
      terminated = isTerminated()
      val lock = registrationLock
      if (!terminated && lock != null) {
        lock.lock()
        try {
          val cond = termination match {
            case null =>
              val cond = lock.newCondition()
              termination = cond
              cond
            case cond => cond
          }
          while ({
            terminated = isTerminated()
            !terminated && nanos > 0L
          }) nanos = cond.awaitNanos(nanos)
        } finally lock.unlock()
      }
    }
    terminated
  }

  /** If called by a ForkJoinTask operating in this pool, equivalent in effect
   *  to {@link ForkJoinTask#helpQuiesce}. Otherwise, waits and/or attempts to
   *  assist performing tasks until this pool {@link #isQuiescent} or the
   *  indicated timeout elapses.
   *
   *  @param timeout
   *    the maximum time to wait
   *  @param unit
   *    the time unit of the timeout argument
   *  @return
   *    {@code true} if quiescent {@code false} if the timeout elapsed.
   */
  @stub()
  def awaitQuiescence(timeout: Long, unit: TimeUnit): Boolean = {
    ???
    // Thread t ForkJoinWorkerThread wt int q
    // long nanos = unit.toNanos(timeout)
    // if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread &&
    //     (wt = (ForkJoinWorkerThread)t).pool == this)
    //     q = helpQuiescePool(wt.workQueue, nanos, false)
    // else
    //     q = externalHelpQuiescePool(nanos, false)
    // return (q > 0)
  }

  /** ManagedBlock for ForkJoinWorkerThreads */
  @throws[InterruptedException]
  private def compensatedBlock(blocker: ManagedBlocker): Unit = {
    if (blocker == null)
      throw new NullPointerException()

    @tailrec
    def loop(): Unit = {
      val c = ctl
      if (blocker.isReleasable()) return ()

      val comp = tryCompensate(c)
      if (comp >= 0) {
        val post = if (comp == 0) 0L else RC_UNIT
        val done =
          try blocker.block()
          finally getAndAddCtl(post)
        if (done) return ()
        else loop()
      }
    }

    loop()
  }

  // AbstractExecutorService.newTaskFor overrides rely on
  // undocumented fact that ForkJoinTask.adapt returns ForkJoinTasks
  // that also implement RunnableFuture.

  @stub()
  override protected[concurrent] def newTaskFor[T](
      runnable: Runnable,
      value: T
  ): RunnableFuture[T] = {
    ???
    // return new ForkJoinTask.AdaptedRunnable<T>(runnable, value)
  }

  @stub()
  override protected[concurrent] def newTaskFor[T](
      callable: Callable[T]
  ): RunnableFuture[T] = {
    ???
    // return new ForkJoinTask.AdaptedCallable[T](callable)
  }
}

object ForkJoinPool {

  /** Sequence number for creating worker names
   */
  private val poolIds: AtomicInteger = new AtomicInteger(0)

  // Constants shared across ForkJoinPool and WorkQueue

  // Bounds
  final val SWIDTH = 16 // width of shortTry(
  final val SMASK = 0xffff // short bits == max index
  final val MAX_CAP = 0x7fff // max #workers - 1

  // Masks and units for WorkQueue.phase and ctl sp subfield
  final val UNSIGNALLED = 1 << 31 // must be negative
  final val SS_SEQ = 1 << 16 // version count

  // Mode bits and sentinels, some also used in WorkQueue fields
  final val FIFO = 1 << 16 // fifo queue or access mode
  final val SRC = 1 << 17 // set for valid queue ids
  final val INNOCUOUS = 1 << 18 // set for Innocuous workers
  final val QUIET = 1 << 19 // quiescing phase or source
  final val SHUTDOWN = 1 << 24
  final val TERMINATED = 1 << 25
  final val STOP = 1 << 31 // must be negative
  final val UNCOMPENSATE = 1 << 16 // tryCompensate return

  /** Initial capacity of work-stealing queue array. Must be a power of two, at
   *  least 2. See above.
   */
  final val INITIAL_QUEUE_CAPACITY = 1 << 8

  // static fields

  /** The default value for COMMON_MAX_SPARES. Overridable using the
   *  "java.util.concurrent.ForkJoinPool.common.maximumSpares" system property.
   *  The default value is far in excess of normal requirements, but also far
   *  short of MAX_CAP and typical OS thread limits, so allows JVMs to catch
   *  misuse/abuse before running out of resources needed to do so.
   */
  private final val DEFAULT_COMMON_MAX_SPARES = 256

  /** Limit on spare thread construction in tryCompensate.
   */
  private final val COMMON_MAX_SPARES: Int = {
    Option(
      System.getProperty(
        "java.util.concurrent.ForkJoinPool.common.maximumSpares"
      )
    )
      .flatMap { stringValue =>
        util.Try(java.lang.Integer.parseInt(stringValue)).toOption
      }
      .getOrElse(DEFAULT_COMMON_MAX_SPARES)
  }

  // static configuration constants

  /** Default idle timeout value (in milliseconds) for the thread triggering
   *  quiescence to park waiting for new work
   */
  private final val DEFAULT_KEEPALIVE = 60000L

  /** Undershoot tolerance for idle timeouts
   */
  private final val TIMEOUT_SLOP = 20L

  /*
   * Bits and masks for field ctl, packed with 4 16 bit subfields:
   * RC: Number of released (unqueued) workers minus target parallelism
   * TC: Number of total workers minus target parallelism
   * SS: version count and status of top waiting thread
   * ID: poolIndex of top of Treiber stack of waiters
   *
   * When convenient, we can extract the lower 32 stack top bits
   * (including version bits) as sp=(int)ctl.  The offsets of counts
   * by the target parallelism and the positionings of fields makes
   * it possible to perform the most common checks via sign tests of
   * fields: When ac is negative, there are not enough unqueued
   * workers, when tc is negative, there are not enough total
   * workers.  When sp is non-zero, there are waiting workers.  To
   * deal with possibly negative fields, we use casts in and out of
   * "short" and/or signed shifts to maintain signedness.
   *
   * Because it occupies uppermost bits, we can add one release
   * count using getAndAdd of RC_UNIT, rather than CAS, when
   * returning from a blocked join.  Other updates entail multiple
   * subfields and masking, requiring CAS.
   *
   * The limits packed in field "bounds" are also offset by the
   * parallelism level to make them comparable to the ctl rc and tc
   * fields.
   */

  // Lower and upper word masks
  private final val SP_MASK = 0xffffffffL
  private final val UC_MASK = ~SP_MASK

  // Release counts
  private final val RC_SHIFT = 48
  private final val RC_UNIT = 0x0001L << RC_SHIFT
  private final val RC_MASK = 0xffffL << RC_SHIFT

  // Total counts
  private final val TC_SHIFT = 32
  private final val TC_UNIT = 0x0001L << TC_SHIFT
  private final val TC_MASK = 0xffffL << TC_SHIFT
  private final val ADD_WORKER = 0x0001L << (TC_SHIFT + 15) // sign

  /** Common (static) pool. Non-null for public use unless a static construction
   *  exception, but internal usages null-check on use to paranoically avoid
   *  potential initialization circularities as well as to simplify generated
   *  code.
   */
  private[concurrent] lazy val common: ForkJoinPool = {
    val parallelism = Option(
      System.getProperty("java.util.concurrent.ForkJoinPool.common.parallelism")
    ).flatMap { stringValue =>
      scala.util.Try {
        java.lang.Integer.parseInt(stringValue)
      }.toOption
    }.getOrElse(Runtime.getRuntime().availableProcessors() - 1)

    val p = Math.min(Math.max(parallelism, 0), MAX_CAP)
    val (size, bounds, ctl) = if (p > 0) {
      val size = 1 << (33 - Integer.numberOfLeadingZeros(p - 1))
      val bounds = ((1 - p) & SMASK) | (COMMON_MAX_SPARES << SWIDTH)
      val ctl = {
        val np = -p.toLong
        val tcSeed = (np << TC_SHIFT) & TC_MASK
        val rcSeed = (np << RC_SHIFT) & RC_MASK
        tcSeed | rcSeed
      }
      (size, bounds, ctl)
    } else (1, 0, 0L) // zero min, max, spare counts, 1 slot

    // this.mode = p
    new ForkJoinPool(
      parallelism,
      factory = new DefaultCommonPoolForkJoinWorkerThreadFactory(),
      ueh = null,
      null,
      ForkJoinPool.DEFAULT_KEEPALIVE,
      workerNamePrefix = null,
      queueSize = size,
      initialMode = p,
      initialCtl = ctl,
      bounds = bounds
    )
  }

  /** Common pool parallelism. To allow simpler use and management when common
   *  pool threads are disabled, we allow the underlying common.parallelism
   *  field to be zero, but in that case still report parallelism as 1 to
   *  reflect resulting caller-runs mechanics.
   */
  private[concurrent] final val COMMON_PARALLELISM: Int =
    Math.max(common.mode & SMASK, 1)

  /** Creates a new ForkJoinWorkerThread. This factory is used unless overridden
   *  in ForkJoinPool constructors.
   */
  final lazy val defaultForkJoinWorkerThreadFactory
      : ForkJoinWorkerThreadFactory = {
    new DefaultForkJoinWorkerThreadFactory()
  }

  // Nested classes

  /** Factory for creating new {@link ForkJoinWorkerThread}s. A {@code
   *  ForkJoinWorkerThreadFactory} must be defined and used for {@code
   *  ForkJoinWorkerThread} subclasses that extend base functionality or
   *  initialize threads with different contexts.
   */
  trait ForkJoinWorkerThreadFactory {

    /** Returns a new worker thread operating in the given pool. Returning null
     *  or throwing an exception may result in tasks never being executed. If
     *  this method throws an exception, it is relayed to the caller of the
     *  method (for example {@code execute}) causing attempted thread creation.
     *  If this method returns null or throws an exception, it is not retried
     *  until the next attempted creation (for example another call to {@code
     *  execute}).
     *
     *  @param pool
     *    the pool this thread works in
     *  @return
     *    the new worker thread, or {@code null} if the request to create a
     *    thread is rejected
     *  @throws NullPointerException
     *    if the pool is null
     */
    def newThread(pool: ForkJoinPool): ForkJoinWorkerThread
  }

  /** Default ForkJoinWorkerThreadFactory implementation creates a new
   *  ForkJoinWorkerThread using the system class loader as the thread context
   *  class loader.
   */
  final class DefaultForkJoinWorkerThreadFactory
      extends ForkJoinWorkerThreadFactory {

    final def newThread(pool: ForkJoinPool): ForkJoinWorkerThread = {
      new ForkJoinWorkerThread(null, pool, true, false)
    }
  }

  /** Factory for CommonPool unless overridden by System property. Creates
   *  InnocuousForkJoinWorkerThreads if a security manager is present at time of
   *  invocation. Support requires that we break quite a lot of encapsulation
   *  (some via helper methods in ThreadLocalRandom) to access and set Thread
   *  fields.
   */
  final class DefaultCommonPoolForkJoinWorkerThreadFactory
      extends ForkJoinWorkerThreadFactory {

    final def newThread(pool: ForkJoinPool): ForkJoinWorkerThread = {
      // if (System.getSecurityManager() == null)
      new ForkJoinWorkerThread(null, pool, true, true)
      // else
      // new ForkJoinWorkerThread.InnocuousForkJoinWorkerThread(pool)
    }
  }

  /** Returns common pool queue for an external thread that has possibly ever
   *  submitted a common pool task (nonzero probe), or null if none.
   */
  private[concurrent] def commonQueue(): WorkQueue = {
    val r = ThreadLocalRandom.getProbe()
    val p = common
    val qs = if (common != null) p.queues else null
    val n = if (qs != null) qs.length else 0
    val idx = (n - 1) & (r << 1)
    if (n > 0 && r != 0) qs(idx)
    else null
  }

  /** If the given executor is a ForkJoinPool, poll and execute
   *  AsynchronousCompletionTasks from worker's queue until none are available
   *  or blocker is released.
   */
  @stub()
  private[concurrent] def helpAsyncBlocker(
      e: Executor,
      blocker: ManagedBlocker
  ): Unit = {
    ???
    // WorkQueue w = null Thread t ForkJoinWorkerThread wt
    // if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) {
    //     if ((wt = (ForkJoinWorkerThread)t).pool == e)
    //         w = wt.workQueue
    // }
    // else if (e instanceof ForkJoinPool)
    //     w = ((ForkJoinPool)e).externalQueue()
    // if (w != null)
    //     w.helpAsyncBlocker(blocker)
  }

  /** Returns a cheap heuristic guide for task partitioning when programmers,
   *  frameworks, tools, or languages have little or no idea about task
   *  granularity. In essence, by offering this method, we ask users only about
   *  tradeoffs in overhead vs expected throughput and its variance, rather than
   *  how finely to partition tasks.
   *
   *  In a steady state strict (tree-structured) computation, each thread makes
   *  available for stealing enough tasks for other threads to remain active.
   *  Inductively, if all threads play by the same rules, each thread should
   *  make available only a constant number of tasks.
   *
   *  The minimum useful constant is just 1. But using a value of 1 would
   *  require immediate replenishment upon each steal to maintain enough tasks,
   *  which is infeasible. Further, partitionings/granularities of offered tasks
   *  should minimize steal rates, which in general means that threads nearer
   *  the top of computation tree should generate more than those nearer the
   *  bottom. In perfect steady state, each thread is at approximately the same
   *  level of computation tree. However, producing extra tasks amortizes the
   *  uncertainty of progress and diffusion assumptions.
   *
   *  So, users will want to use values larger (but not much larger) than 1 to
   *  both smooth over transient shortages and hedge against uneven progress as
   *  traded off against the cost of extra task overhead. We leave the user to
   *  pick a threshold value to compare with the results of this call to guide
   *  decisions, but recommend values such as 3.
   *
   *  When all threads are active, it is on average OK to estimate surplus
   *  strictly locally. In steady-state, if one thread is maintaining say 2
   *  surplus tasks, then so are others. So we can just use estimated queue
   *  length. However, this strategy alone leads to serious mis-estimates in
   *  some non-steady-state conditions (ramp-up, ramp-down, other stalls). We
   *  can detect many of these by further considering the number of "idle"
   *  threads, that are known to have zero queued tasks, so compensate by a
   *  factor of (#idle/#active) threads.
   */
  private[concurrent] def getSurplusQueuedTaskCount(): Int = {
    // Thread t ForkJoinWorkerThread wt ForkJoinPool pool WorkQueue q
    Thread.currentThread() match {
      case wt: ForkJoinWorkerThread =>
        val pool = wt.pool
        val q = wt.workQueue
        if (pool != null && q != null) {
          var p = pool.mode & SMASK
          def pShiftRight() = {
            p >>>= 1
            p
          }
          val a = p + (pool.ctl >> RC_SHIFT).toInt
          val n = q.top - q.base
          if (a > pShiftRight()) 0
          else if (a > pShiftRight()) 1
          else if (a > pShiftRight()) 2
          else if (a > pShiftRight()) 4
          else 8
        } else 0

      case _ => 0
    }
  }

  // helper method for commonPool constructor
  @throws[ReflectiveOperationException]
  @stub()
  private def newInstanceFromSystemProperty(property: String): Object = {
    ???
    // String className = System.getProperty(property)
    // return (className == null)
    //     ? null
    //     : ClassLoader.getSystemClassLoader().loadClass(className)
    //     .getConstructor().newInstance()
  }

  /** Returns the common pool instance. This pool is statically constructed its
   *  run state is unaffected by attempts to {@link #shutdown} or {@link
   *  #shutdownNow}. However this pool and any ongoing processing are
   *  automatically terminated upon program {@link System#exit}. Any program
   *  that relies on asynchronous task processing to complete before program
   *  termination should invoke {@code commonPool().}{@link #awaitQuiescence
   *  awaitQuiescence}, before exit.
   *
   *  @return
   *    the common pool instance
   *  @since 1.8
   */
  def commonPool(): ForkJoinPool = {
    assert(common != null, "Common pool not initialized")
    common
  }

  // Task to hold results from InvokeAnyTasks
  @SerialVersionUID(2838392045355241008L)
  private[concurrent] final class InvokeAnyRoot[E](
      n: Int,
      pool: ForkJoinPool
  ) extends ForkJoinTask[E] {
    @volatile var result: E = _
    final val count = new AtomicInteger(n) // in case all throw
    final def tryComplete(c: Callable[E]): Unit = { // called by InvokeAnyTasks
      var ex: Throwable = null
      var failed = false
      if (c == null || Thread.interrupted() || (pool != null && pool.mode < 0))
        failed = true
      else if (isDone()) ()
      else
        try complete(c.call())
        catch {
          case tx: Throwable =>
            ex = tx
            failed = true
        }

      if ((pool != null && pool.mode < 0) ||
          (failed && count.getAndDecrement() <= 1))
        trySetThrown(if (ex != null) ex else new CancellationException())
    }
    final def exec(): Boolean = false // never forked
    final def getRawResult(): E = result
    final def setRawResult(v: E): Unit = { result = v }
  }

  // Variant of AdaptedInterruptibleCallable with results in InvokeAnyRoot
  @SerialVersionUID(2838392045355241008L)
  private[concurrent] final class InvokeAnyTask[E](
      root: InvokeAnyRoot[E],
      callable: Callable[E]
  ) extends ForkJoinTask[E] {
    @volatile var runner: Thread = _
    final def exec(): Boolean = {
      Thread.interrupted()
      runner = Thread.currentThread()
      root.tryComplete(callable)
      runner = null
      Thread.interrupted()
      true
    }
    final override def cancel(mayInterruptIfRunning: Boolean): Boolean = {
      val stat = super.cancel(false)
      if (mayInterruptIfRunning) runner match {
        case null => ()
        case t =>
          try t.interrupt()
          catch { case ignore: Throwable => () }
      }
      stat
    }
    final def setRawResult(v: E): Unit = () // unused
    final def getRawResult(): E = null.asInstanceOf[E]
  }

  /** Returns the targeted parallelism level of the common pool.
   *
   *  @return
   *    the targeted parallelism level of the common pool
   *  @since 1.8
   */
  def getCommonPoolParallelism(): Int = COMMON_PARALLELISM

  object WorkQueue {
    // Support for atomic operations
    import scala.scalanative.unsafe.atomic.memory_order._
    @alwaysinline
    private def arraySlotAtomicAccess[T <: AnyRef](
        a: Array[T],
        idx: Int
    ): CAtomicRef[T] = {
      val nativeArray = a.asInstanceOf[ObjectArray]
      val elemRef = nativeArray.at(idx).asInstanceOf[Ptr[T]]
      new CAtomicRef[T](elemRef)
    }

    final def getSlot(a: Array[ForkJoinTask[_]], i: Int): ForkJoinTask[_] = {
      arraySlotAtomicAccess(a, i).load(memory_order_acquire)
    }

    @alwaysinline
    final def getAndClearSlot(
        a: Array[ForkJoinTask[_]],
        i: Int
    ): ForkJoinTask[_] = {
      arraySlotAtomicAccess(a, i).exchange(null: ForkJoinTask[_])
    }

    final def setSlotVolatile(
        a: Array[ForkJoinTask[_]],
        i: Int,
        v: ForkJoinTask[_]
    ): Unit = {
      arraySlotAtomicAccess(a, i).store(v)
    }

    final def casSlotToNull(
        a: Array[ForkJoinTask[_]],
        i: Int,
        c: ForkJoinTask[_]
    ): Boolean = {
      arraySlotAtomicAccess(a, i)
        .compareExchangeStrong(c, null: ForkJoinTask[_])
        ._1
    }
  }

  /** Queues supporting work-stealing as well as external task submission. See
   *  above for descriptions and algorithms.
   */
  final class WorkQueue private (
      private[concurrent] val owner: Option[ForkJoinWorkerThread]
  ) {
    // versioned, negative if inactive
    @volatile private[concurrent] var phase: Int = 0
    // source queue id, lock, or sentinel
    @volatile private[concurrent] var source: Int = 0
    private val sourceAtomic = new CAtomicInt(
      fromRawPtr(Intrinsics.classFieldRawPtr(this, "source"))
    )

    // index, mode, ORed with SRC after init
    private[concurrent] var config: Int = 0

    // the queued tasks power of 2 size
    private[concurrent] var array: Array[ForkJoinTask[_]] = _

    // pool stack (ctl) predecessor link
    private[concurrent] var stackPred: Int = 0
    // index of next slot for poll
    @volatile private[concurrent] var base: Int = 0
    private[ForkJoinPool] val basePtr =
      fromRawPtr[Int](Intrinsics.classFieldRawPtr(this, "base"))

    private[concurrent] var top: Int = 0 // index of next slot for push
    private[concurrent] var nsteals: Int = 0 // steals from other queues

    /** Constructor used by ForkJoinWorkerThreads. Most fields are initialized
     *  upon thread start, in pool.registerWorker.
     */
    def this(owner: ForkJoinWorkerThread, isInnocuous: Boolean) = {
      this(Some(owner))
      this.config = if (isInnocuous) INNOCUOUS else 0
    }

    /** Constructor used for external queues.
     */
    def this(config: Int) = {
      this(owner = None)
      this.array = new Array[ForkJoinTask[_]](INITIAL_QUEUE_CAPACITY)
      this.config = config
      this.phase = -1
    }

    @alwaysinline
    final def tryLock(): Boolean = {
      sourceAtomic.compareExchangeStrong(0, 1)._1
    }

    @alwaysinline
    final def setBaseOpaque(b: Int): Unit = !basePtr = b

    /** Returns an exportable index (used by ForkJoinWorkerThread).
     */
    final def getPoolIndex(): Int = {
      (config & 0xffff) >>> 1 // ignore odd/even tag bit
    }

    /** Returns the approximate number of tasks in the queue.
     */
    final def queueSize(): Int = {
      VarHandle.acquireFence() // ensure fresh reads by external callers
      val n = top - base
      n.max(0) // ignore transient negative
    }

    /** Provides a more conservative estimate of whether this queue has any
     *  tasks than does queueSize.
     */
    final def isEmpty(): Boolean = {
      !((source != 0 && owner.isEmpty) ||
        top - base > 0)
    }

    /** Pushes a task. Call only by owner in unshared queues.
     *
     *  @param task
     *    the task. Caller must ensure non-null.
     *  @param pool
     *    (no-op if null)
     *  @throws RejectedExecutionException
     *    if array cannot be resized
     */
    final def push(task: ForkJoinTask[_], pool: ForkJoinPool): Unit = {
      val a = array
      val s = top
      top += 1
      val d = s - base
      val cap = if (a != null) a.length else 0
      // skip insert if disabled
      if (pool != null && cap > 0) {
        val m = cap - 1
        WorkQueue.setSlotVolatile(a, m & s, task)
        val shouldGrowArray = d == m
        if (shouldGrowArray)
          growArray()
        if (shouldGrowArray || a(m & (s - 1)) == null)
          pool.signalWork() // signal if was empty or resized
      }
    }

    /** Pushes task to a shared queue with lock already held, and unlocks.
     *
     *  @return
     *    true if caller should signal work
     */
    final def lockedPush(task: ForkJoinTask[_]): Boolean = {
      val a = array
      val s = top
      top += 1
      val d = s - base
      val cap = if (a != null) a.length else 0
      if (cap > 0) {
        val m = cap - 1
        val shouldGrowArray = d == m
        WorkQueue.setSlotVolatile(a, m & s, task)
        // a(m & s) = task
        if (shouldGrowArray) growArray()
        source = 0 // unlock
        if (shouldGrowArray || a(m & (s - 1)) == null)
          return true
      }
      false
    }

    /** Doubles the capacity of array. Called by owner or with lock held after
     *  pre-incrementing top, which is reverted on allocation failure.
     */
    final def growArray(): Unit = {
      val oldArray = array
      val oldCap = if (oldArray != null) oldArray.length else 0
      val newCap = oldCap << 1
      val s = top - 1
      if (oldCap > 0 && newCap > 0) { // skip if disabled
        val newArray: Array[ForkJoinTask[_]] =
          try {
            new Array[ForkJoinTask[_]](newCap)
          } catch {
            case ex: Throwable =>
              top = s
              if (owner.isEmpty) {
                source = 0 // unlock
              }
              throw new RejectedExecutionException("Queue capacity exceeded")
          }

        val newMask = newCap - 1
        val oldMask = oldCap - 1

        @tailrec
        def loop(k: Int, s: Int): Unit = {
          if (k > 0) {
            // poll old, push to new
            getAndClearSlot(oldArray, s & oldMask) match {
              case null => () // break, others already taken
              case task =>
                newArray(s & newMask) = task
                loop(k = k - 1, s = s - 1)
            }
          }
        }

        loop(oldCap, s)

        VarHandle.releaseFence() // fill before publish
        array = newArray
      }
    }

    // Variants of pop

    /** Pops and returns task, or null if empty. Called only by owner.
     */
    @stub()
    private[concurrent] def pop(): ForkJoinTask[_] = {
      ???
      // ForkJoinTask[_] t = null
      // int s = top, cap Array[ForkJoinTask[_]]()  a
      // if ((a = array) != null && (cap = a.length) > 0 && base != s-- &&
      //     (t = getAndClearSlot(a, (cap - 1) & s)) != null)
      //     top = s
      // return t
    }

    /** Pops the given task for owner only if it is at the current top.
     */
    final def tryUnpush(task: ForkJoinTask[_]): Boolean = {
      val s = top
      val newS = s - 1
      val a = array
      val cap = if (a != null) a.length else 0
      if (cap > 0 &&
          base != s &&
          WorkQueue.casSlotToNull(a, (cap - 1) & newS, task)) {
        top = newS
        true
      } else false
    }

    /** Locking version of tryUnpush.
     */
    final def externalTryUnpush(task: ForkJoinTask[_]): Boolean = {
      while (true) {
        val s = top
        val a = array
        val cap = if (a != null) a.length else 0
        val k = (cap - 1) & (s - 1)
        if (cap <= 0 || a(k) != task) return false
        else if (tryLock()) {
          if (top == s && array == a) {
            if (WorkQueue.casSlotToNull(a, k, task))
              top = s - 1
            source = 0
            return true
          }
          source = 0 // release lock for retry
        }
        Thread.`yield`() // trylock failure
      }
      false
    }

    /** Deep form of tryUnpush: Traverses from top and removes task if present,
     *  shifting others to fill gap.
     */
    final def tryRemove(task: ForkJoinTask[_], owned: Boolean): Boolean = {
      val p = top
      val a = array
      val cap = if (a != null) a.length else 0
      var taken = false

      if (task != null && cap > 0) {
        val m = cap - 1
        val s = p - 1
        val d = p - base

        @tailrec
        def loop(i: Int, d: Int): Unit = {
          val k = i & m
          a(k) match {
            case `task` =>
              if (owned || tryLock()) {
                if ((owned || (array == a && top == p)) &&
                    WorkQueue.casSlotToNull(a, k, task)) {
                  for (j <- i.until(s)) {
                    a(j & m) = getAndClearSlot(a, (j + 1) & m)
                  }
                  top = s
                  taken = true
                }
                if (!owned) source = 0
              }

            case _ =>
              if (d > 0) loop(i - 1, d - 1)
          }
        }

        loop(i = s, d = d)
      }

      taken
    }

    // variants of poll

    /** Tries once to poll next task in FIFO order, failing on inconsistency or
     *  contention.
     */
    final def tryPoll(): ForkJoinTask[_] = {
      val a = array
      val cap = if (a != null) a.length else 0

      val b = base
      val k = (cap - 1) & b
      if (cap > 0) {
        val task = WorkQueue.getSlot(a, k)
        if (base == b &&
            task != null &&
            WorkQueue.casSlotToNull(a, k, task)) {
          setBaseOpaque(b + 1)
          return task
        }
      }
      null
    }

    /** Takes next task, if one exists, in order specified by mode.
     */
    final def nextLocalTask(cfg: Int): ForkJoinTask[_] = {
      val a = array
      val cap = if (a != null) a.length else 0
      val mask = cap - 1
      var currentTop = top

      @tailrec
      def loop(): ForkJoinTask[_] = {
        var currentBase = base

        val d = currentTop - currentBase
        if (d <= 0) null
        else {
          def tryTopSlot(): Option[ForkJoinTask[_]] = {
            currentTop -= 1
            Option(getAndClearSlot(a, currentTop & mask))
              .map { task =>
                top = currentTop
                task
              }
          }

          def tryBaseSlot(): Option[ForkJoinTask[_]] = {
            val b = currentBase
            currentBase += 1
            Option(getAndClearSlot(a, b & mask))
              .map { task =>
                setBaseOpaque(currentBase)
                task
              }
          }

          if (d == 1 || (cfg & FIFO) == 0)
            tryTopSlot().orNull
          else
            tryBaseSlot() match {
              case Some(value) => value
              case None        => loop()
            }
        }
      }

      if (cap > 0) loop()
      else null
    }

    /** Takes next task, if one exists, using configured mode.
     */
    final def nextLocalTask(): ForkJoinTask[_] = {
      nextLocalTask(config)
    }

    /** Returns next task, if one exists, in order specified by mode.
     */
    final def peek(): ForkJoinTask[_] = {
      VarHandle.acquireFence()
      // int cap Array[ForkJoinTask[_]]()  a
      val a = array
      val cap = if (a != null) a.length else 0
      if (cap > 0) {
        val mask = if ((config & FIFO) != 0) base else top - 1
        a((cap - 1) & mask)
      } else null: ForkJoinTask[_]
    }

    // specialized execution methods

    /** Runs the given (stolen) task if nonnull, as well as remaining local
     *  tasks and/or others available from the given queue.
     */
    final def topLevelExec(task: ForkJoinTask[_], q: WorkQueue): Unit = {
      val cfg = config
      var currentTask = task
      var nStolen = 1
      while (currentTask != null) {
        currentTask.doExec()
        currentTask = nextLocalTask(cfg)
        currentTask match {
          case null if q != null =>
            currentTask = q.tryPoll()
            if (currentTask != null) {
              nStolen += 1
            }
          case _ => ()
        }
      }
      nsteals += nStolen
      source = 0
      if ((cfg & INNOCUOUS) != 0) {
        ThreadLocalRandom.eraseThreadLocals(Thread.currentThread())
      }
    }

    /** Tries to pop and run tasks within the target's computation until done,
     *  not found, or limit exceeded.
     *
     *  @param task
     *    root of CountedCompleter computation
     *  @param owned
     *    true if owned by a ForkJoinWorkerThread
     *  @param limit
     *    max runs, or zero for no limit
     *  @return
     *    task status on exit
     */
    final def helpComplete(
        task: ForkJoinTask[_],
        owned: Boolean,
        limit: Int
    ): Int = {
      def loop(currentLimit: Int): Int = {
        val status = task.status
        val p = top
        val s = p - 1
        val a = array
        val cap = if (a != null) a.length else 0
        val k = (cap - 1) & s
        val t = if (cap > 0) a(k) else null

        var taken = false
        @inline
        def updateAndCheckTaked() = {
          taken = WorkQueue.casSlotToNull(a, k, t)
          taken
        }

        @tailrec
        def doTryComplete(current: CountedCompleter[_]): Unit = {
          current match {
            case `task` =>
              if (owned) {
                if (updateAndCheckTaked()) top = s
              } else if (tryLock()) {
                if (top == p && array == a && updateAndCheckTaked()) {
                  top = s
                }
                source = 0
              }
              if (taken) t.doExec()
              else if (!owned) Thread.`yield`() // tryLock failure
              return

            case _ =>
              val next = current.completer
              if (next == null) ()
              else doTryComplete(next)
          }
        }

        t match {
          case completer: CountedCompleter[_] if status >= 0 =>
            doTryComplete(completer)
          case task => ()
        }
        if (taken && limit != 0 && currentLimit - 1 == 0) status
        else loop(currentLimit = currentLimit - 1)
      }

      if (task == null) 0
      else loop(limit)
    }

    /** Tries to poll and run AsynchronousComple // misc
     *
     *  /** AccessControlContext for innocuous workers, created on 1st use. */
     *  private static AccessControlContext INNOCUOUS_ACC
     *
     *  @param blocker
     *    the blocker
     */
    @stub()
    final def helpAsyncBlocker(blocker: ManagedBlocker): Unit = {
      ???
      // int cap, b, d, k Array[ForkJoinTask[_]]()  a ForkJoinTask[_] t
      // while (blocker != null && (d = top - (b = base)) > 0 &&
      //        (a = array) != null && (cap = a.length) > 0 &&
      //        (((t = getSlot(a, k = (cap - 1) & b)) == null && d > 1) ||
      //         t instanceof
      //         CompletableFuture.AsynchronousCompletionTask) &&
      //        !blocker.isReleasable()) {
      //     if (t != null && base == b++ && casSlotToNull(a, k, t)) {
      //         setBaseOpaque(b)
      //         t.doExec()
      //     }
      // }
    }

    // misc

    /** Initializes (upon registration) InnocuousForkJoinWorkerThreads.
     */
    final def initializeInnocuousWorker(): Unit = {
      // AccessControlContext acc // racy construction OK
      // if ((acc = INNOCUOUS_ACC) == null)
      //     INNOCUOUS_ACC = acc = new AccessControlContext(
      //         new ProtectionDomain[] { new ProtectionDomain(null, null) })
      val t = Thread.currentThread()
      // ThreadLocalRandom.setInheritedAccessControlContext(t, acc)
      ThreadLocalRandom.eraseThreadLocals(t)
    }

    /** Returns true if owned by a worker thread and not known to be blocked.
     */
    final def isApparentlyUnblocked(): Boolean = {
      owner
        .map(_.getState())
        .exists { s =>
          s != Thread.State.BLOCKED &&
          s != Thread.State.WAITING &&
          s != Thread.State.TIMED_WAITING
        }
    }

  }

  /** Interface for extending managed parallelism for tasks running in {@link
   *  ForkJoinPool}s.
   *
   *  <p>A {@code ManagedBlocker} provides two methods. Method {@link
   *  #isReleasable} must return {@code true} if blocking is not necessary.
   *  Method {@link #block} blocks the current thread if necessary (perhaps
   *  internally invoking {@code isReleasable} before actually blocking). These
   *  actions are performed by any thread invoking {@link
   *  ForkJoinPool#managedBlock(ManagedBlocker)}. The unusual methods in this
   *  API accommodate synchronizers that may, but don't usually, block for long
   *  periods. Similarly, they allow more efficient internal handling of cases
   *  in which additional workers may be, but usually are not, needed to ensure
   *  sufficient parallelism. Toward this end, implementations of method {@code
   *  isReleasable} must be amenable to repeated invocation. Neither method is
   *  invoked after a prior invocation of {@code isReleasable} or {@code block}
   *  returns {@code true}.
   *
   *  <p>For example, here is a ManagedBlocker based on a ReentrantLock: <pre>
   *  {@code class ManagedLocker implements ManagedBlocker { final ReentrantLock
   *  lock boolean hasLock = false ManagedLocker(ReentrantLock lock) { this.lock
   *  =lock } public boolean block() { if (!hasLock) lock.lock() return true }
public boolean isReleasable() { return hasLock || (hasLock=
   *  lock.tryLock()) } }}</pre>
   *
   *  <p>Here is a class that possibly blocks waiting for an item on a given
   *  queue: <pre> {@code class QueueTaker<E> implements ManagedBlocker { final
   *  BlockingQueue<E> queue volatile E item = null QueueTaker(BlockingQueue<E>
   *  q) { this.queue = q } public boolean block() throws InterruptedException {
   *  if (item == null) item = queue.take() return true } public boolean
   *  isReleasable() { return item != null || (item = queue.poll()) != null }
   *  public E getItem() { // call after pool.managedBlock completes return item
   *  } }}</pre>
   */
  trait ManagedBlocker {

    /** Possibly blocks the current thread, for example waiting for a lock or
     *  condition.
     *
     *  @return
     *    {@code true} if no additional blocking is necessary (i.e., if
     *    isReleasable would return true)
     *  @throws InterruptedException
     *    if interrupted while waiting (the method is not required to do so, but
     *    is allowed to)
     */
    @throws[InterruptedException]
    def block(): Boolean

    /** Returns {@code true} if blocking is unnecessary.
     *  @return
     *    {@code true} if blocking is unnecessary
     */
    @stub()
    def isReleasable(): Boolean
  }

  /** Runs the given possibly blocking task. When {@linkplain
   *  ForkJoinTask#inForkJoinPool() running in a ForkJoinPool}, this method
   *  possibly arranges for a spare thread to be activated if necessary to
   *  ensure sufficient parallelism while the current thread is blocked in
   *  {@link ManagedBlocker#block blocker.block()}.
   *
   *  <p>This method repeatedly calls {@code blocker.isReleasable()} and {@code
   *  blocker.block()} until either method returns {@code true}. Every call to
   *  {@code blocker.block()} is preceded by a call to {@code
   *  blocker.isReleasable()} that returned {@code false}.
   *
   *  <p>If not running in a ForkJoinPool, this method is behaviorally
   *  equivalent to <pre> {@code while (!blocker.isReleasable()) if
   *  (blocker.block()) break}</pre>
   *
   *  If running in a ForkJoinPool, the pool may first be expanded to ensure
   *  sufficient parallelism available during the call to {@code
   *  blocker.block()}.
   *
   *  @param blocker
   *    the blocker task
   *  @throws InterruptedException
   *    if {@code blocker.block()} did so
   */
  @throws[InterruptedException]
  def managedBlock(blocker: ManagedBlocker): Unit = {
    Thread.currentThread() match {
      case thread: ForkJoinWorkerThread if thread.pool != null =>
        thread.pool.compensatedBlock(blocker)
      case _ => unmanagedBlock(blocker)
    }
  }

  /** ManagedBlock for external threads */
  @throws[InterruptedException]
  private def unmanagedBlock(blocker: ManagedBlocker): Unit = {
    if (blocker == null)
      throw new NullPointerException()

    while (!blocker.isReleasable() && !blocker.block()) {
      ()
    }
  }

}
