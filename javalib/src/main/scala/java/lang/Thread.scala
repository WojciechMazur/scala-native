package java.lang

import java.util
import java.lang.Thread._
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.runtime.{
  Intrinsics,
  NativeThread,
  fromRawPtr,
  toRawPtr
}
import scala.scalanative.posix.sys.types.{pthread_attr_t, pthread_t}
import scala.scalanative.posix.pthread._
import scala.scalanative.posix.sched._
import scala.scalanative.libc.errno

// Ported from Harmony

@extern
object GCExt {

  @name("GC_pthread_create")
  def pthread_create(thread: Ptr[pthread_t],
                     attr: Ptr[pthread_attr_t],
                     startroutine: CFuncPtr1[Ptr[scala.Byte], Ptr[scala.Byte]],
                     args: Ptr[scala.Byte]): CInt = extern
  @name("GC_pthread_join")
  def pthread_join(thread: pthread_t, value_ptr: Ptr[Ptr[Byte]]): CInt = extern
}

class Thread private (group: ThreadGroup, target: Runnable, stackSize: Long)
    extends Runnable {
  private val threadId = getNextThreadId()
  // private var stackSize: scala.Long =  NativeThread.THREAD_DEFAULT_STACK_SIZE.toLong

  private var alive            = false
  private var daemon           = false
  private var interruptedState = false
  private var started          = false

  private var name: String  = s"Thread-$threadId"
  private var priority: Int = Thread.NORM_PRIORITY

  private[lang] var contextClassLoader: ClassLoader = _

  // Uncaught exception handler for this thread
  private var exceptionHandler: Thread.UncaughtExceptionHandler = _

  // ThreadLocal values : local and inheritable
  private[java] var localValues: ThreadLocal.Values       = _
  private[java] var inheritableValues: ThreadLocal.Values = _
  private[java] var threadLocalRandomSeed: Long           = 0
  private[java] var threadLocalRandomProbe: Int           = 0
  private[java] var threadLocalRandomSecondarySeed: Int   = 0

  // The underlying pthread ID
  /*
   * NOTE: This is used to keep track of the pthread linked to this Thread,
   * it might be easier/better to handle this at lower level
   */
  private[this] var underlying: pthread_t = null.asInstanceOf[ULong]

  // constructors
  def this(group: ThreadGroup,
           target: Runnable,
           name: String,
           stacksize: scala.Long,
           inheritThreadLocals: Boolean) = {
    this(
      group = Option(group).getOrElse(Thread.currentThread().getThreadGroup()),
      target = target,
      stackSize =
        if (stacksize > 0) stacksize
        else NativeThread.THREAD_DEFAULT_STACK_SIZE.toLong
    )

    val parent: Thread = Thread.currentThread()

    if (parent != null) {
      this.daemon = parent.daemon
      this.contextClassLoader = parent.getContextClassLoader()
      this.priority = parent.priority
      this.inheritableValues =
        if (parent.inheritableValues != null && inheritThreadLocals) {
          new ThreadLocal.Values(parent.inheritableValues)
        } else new ThreadLocal.Values()
    }

    if (name != null) {
      this.name = name
    }

    checkGCWatermark()
  }

  def this(group: ThreadGroup,
           target: Runnable,
           name: String,
           stacksize: scala.Long) = this(group, target, name, stacksize, true)

  def this() = this(null, null, null, 0)

  def this(target: Runnable) = this(null, target, null, 0)

  def this(target: Runnable, name: String) = this(null, target, name, 0)

  def this(name: String) = this(null, null, name, 0)

  def this(group: ThreadGroup, target: Runnable) =
    this(group, target, null, 0)

  def this(group: ThreadGroup, target: Runnable, name: String) =
    this(group, target, name, 0)

  def this(group: ThreadGroup, name: String) = this(group, null, name, 0)

  // accessors
  def getId(): scala.Long = threadId

  final def getName(): String = name

  final def setName(name: String): Unit = {
    if (name == null) throw new NullPointerException
    this.name = name
  }

  final def getPriority(): Int = priority

  final def setPriority(priority: Int): Unit = {
    if (priority > Thread.MAX_PRIORITY || priority < Thread.MIN_PRIORITY) {
      throw new IllegalArgumentException("Wrong Thread priority value")
    }
    this.priority = priority
    if (started) {
      NativeThread.setPriority(underlying, priority)
    }
  }

  final def getThreadGroup(): ThreadGroup = group

  def getContextClassLoader(): ClassLoader =
    lock.synchronized(contextClassLoader)

  def setContextClassLoader(classLoader: ClassLoader): Unit =
    lock.synchronized(contextClassLoader = classLoader)

  def getUncaughtExceptionHandler(): Thread.UncaughtExceptionHandler = {
    if (exceptionHandler != null)
      return exceptionHandler
    getThreadGroup()
  }

  def setUncaughtExceptionHandler(eh: Thread.UncaughtExceptionHandler): Unit = {
    exceptionHandler = eh
  }

  final def isAlive(): scala.Boolean = lock.synchronized(alive)

  final def isDaemon(): scala.Boolean = daemon

  final def setDaemon(daemon: scala.Boolean): Unit = {
    lock.synchronized {
      if (isAlive)
        throw new IllegalThreadStateException()
      this.daemon = daemon
    }
  }

  def isInterrupted(): scala.Boolean = interruptedState

  def interrupt(): Unit = {
    lock.synchronized {
      if (started) interruptedState = true
    }
  }

  def getStackTrace(): Array[StackTraceElement] =
    new Array[StackTraceElement](0)
  @deprecated
  def countStackFrames(): Int = 0 //deprecated

  @deprecated
  def destroy(): Unit =
    // this method is not implemented
    throw new NoSuchMethodError()

  //synchronized
  final def join(): Unit = {
    while (isAlive) wait()
  }

  // synchronized
  final def join(ml: scala.Long): Unit = {
    var millis: scala.Long = ml
    if (millis == 0)
      join()
    else {
      val end: scala.Long         = System.currentTimeMillis() + millis
      var continue: scala.Boolean = true
      while (isAlive && continue) {
        wait(millis)
        millis = end - System.currentTimeMillis()
        if (millis <= 0)
          continue = false
      }
    }
  }

  //synchronized
  final def join(ml: scala.Long, n: Int): Unit = {
    var nanos: Int         = n
    var millis: scala.Long = ml
    if (millis < 0 || nanos < 0 || nanos > 999999)
      throw new IllegalArgumentException()
    else if (millis == 0 && nanos == 0)
      join()
    else {
      val end: scala.Long         = System.nanoTime() + 1000000 * millis + nanos.toLong
      var rest: scala.Long        = 0L
      var continue: scala.Boolean = true
      while (isAlive && continue) {
        wait(millis, nanos)
        rest = end - System.nanoTime()
        if (rest <= 0)
          continue = false
        if (continue) {
          nanos = (rest % 1000000).toInt
          millis = rest / 1000000
        }
      }
    }
  }

  @deprecated
  final def resume(): Unit = {
    if (started && NativeThread.resume(underlying) != 0)
      throw new RuntimeException(
        "Error while trying to unpark thread " + toString)
  }

  def run(): Unit = {
    println(s"run $this")
    if (target != null) {
      target.run()
    }
  }

  def start(): Unit = synchronized {
    println(s"start $this")
    lock.synchronized {
      if (started) {
        throw new IllegalThreadStateException(
          "This thread was already started!")
      }
      group.add(this)

      val id = stackalloc[pthread_t]
      val arg =
        fromRawPtr[scala.Byte](Intrinsics.castObjectToRawPtr(this))
      val routine = CFuncPtr1.fromScalaFunction { arg: Ptr[scala.Byte] =>
        val thread =
          Intrinsics
            .castRawPtrToObject(toRawPtr(arg))
            .asInstanceOf[Thread]

        thread.started = true
        thread.alive = true
        try {
          thread.run()
        } finally {
          thread.alive = false
        }

        null.asInstanceOf[Ptr[scala.Byte]]
      }

      val status =
        GCExt.pthread_create(thread = id,
                             attr = null.asInstanceOf[Ptr[pthread_attr_t]],
                             startroutine = routine,
                             args = arg)
      if (status != 0)
        throw new Exception(
          "Failed to create new thread, pthread error " + status)

      while (!this.started) {
        try {
          lock.wait()
        } catch {
          case e: InterruptedException =>
            Thread.currentThread().interrupt()
        }
      }

      underlying = !id
      Thread.threadsMap(underlying.toLong) = this
    }
  }

  def getState(): State = {
    Thread.State.RUNNABLE
    /*
    var dead: scala.Boolean = false
    lock.synchronized{
      if(started && !isAlive) dead = true
    }
    if(dead) return TERMINATED

    val state = VMThreadManager.getState(this)

    if(0 != (state & VMThreadManager.TM_THREAD_STATE_TERMINATED)) State.TERMINATED
    else if(0 != (state & VMThreadManager.TM_THREAD_STATE_WAITING_WITH_TIMEOUT)) State.TIMED_WAITING
    else if(0 != (state & VMThreadManager.TM_THREAD_STATE_WAITING)
      || 0 != (state & VMThreadManager.TM_THREAD_STATE_PARKED)) State.WAITING
    else if(0 != (state & VMThreadManager.TM_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER)) State.BLOCKED
    else if(0 != (state & VMThreadManager.TM_THREAD_STATE_RUNNABLE)) State.RUNNABLE

    //TODO track down all situations where a thread is really in RUNNABLE state
    // but TM_THREAD_STATE_RUNNABLE is not set.  In the meantime, leave the following
    // TM_THREAD_STATE_ALIVE test as it is.
    else if(0 != (state & VMThreadManager.TM_THREAD_STATE_ALIVE)) State.RUNNABLE
    else State.NEW
   */
  }

  @deprecated
  final def stop(): Unit = {
    lock.synchronized {
      if (isAlive())
        stop(new ThreadDeath())
    }
  }

  @deprecated
  final def stop(throwable: Throwable): Unit = {
    if (throwable == null)
      throw new NullPointerException("The argument is null!")
    lock.synchronized {
      if (isAlive && started) {
        val status: Int = pthread_cancel(underlying)
        if (status != 0)
          throw new InternalError("Pthread error " + status)
      }
    }
  }

  @deprecated
  final def suspend(): Unit = {
    if (started && NativeThread.suspend(underlying) != 0)
      throw new RuntimeException(
        "Error while trying to park thread " + toString)
  }

  override def toString(): String = {
    val groupName = Option(group).map(_.getName()).getOrElse("")
    "Thread[" + name + "," + priority + "," + groupName + "]"
  }

  override protected[lang] def clone(): Object =
    throw new CloneNotSupportedException("Thread cannot be cloned")

  def checkAccess(): Unit = ()

  private def checkGCWatermark(): Unit = {
    currentGCWatermarkCount += 1
    if (currentGCWatermarkCount % GC_WATERMARK_MAX_COUNT == 0)
      System.gc()
  }

}

object Thread {
  private val MainThread = {
    val mainGroup = new ThreadGroup(ThreadGroup.System, "main")
    val t = new Thread(group = mainGroup,
                       target = null,
                       stackSize =
                         NativeThread.THREAD_DEFAULT_STACK_SIZE.toLong)
    t.setName("main")
    t
  }

  final val MAX_PRIORITY: Int  = 10
  final val MIN_PRIORITY: Int  = 1
  final val NORM_PRIORITY: Int = 5

  sealed class State(name: String, ordinal: Int)
      extends Enum[State](name, ordinal)
  object State {
    final val NEW: State           = new State("NEW", 0)
    final val RUNNABLE: State      = new State("RUNNABLE", 1)
    final val BLOCKED: State       = new State("BLOCKED", 2)
    final val WAITING: State       = new State("WAITING", 3)
    final val TIMED_WAITING: State = new State("TIMED_WAITING", 4)
    final val TERMINATED: State    = new State("TERMINATED", 5)

    private[this] val cachedValues =
      Array(NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED)
    def values(): Array[State] = cachedValues.clone()
    def valueOf(name: String): State = {
      cachedValues.find(_.name() == name).getOrElse {
        throw new IllegalArgumentException("No enum const Thread.State." + name)
      }
    }

  }

  def onSpinWait(): Unit = {}

  private val lock: Object = new Object()

  // Default uncaught exception handler
  private var defaultExceptionHandler: UncaughtExceptionHandler = _

  def getDefaultUncaughtExceptionHandler(): UncaughtExceptionHandler =
    defaultExceptionHandler

  def setDefaultUncaughtHandler(eh: UncaughtExceptionHandler): Unit =
    defaultExceptionHandler = eh

  // Counter used to generate thread's ID
  private var threadOrdinalNum: scala.Long = 0L

  private def getNextThreadId(): scala.Long = synchronized {
    threadOrdinalNum += 1
    threadOrdinalNum
  }

  // Number of threads that was created w/o garbage collection //TODO
  private var currentGCWatermarkCount: Int = 0

  // Max number of threads to be created w/o GC, required collect dead Thread references
  private final val GC_WATERMARK_MAX_COUNT: Int = 700

  def activeCount(): Int = currentThread().getThreadGroup().activeCount()

  import scala.collection.mutable
  private val threadsMap = new mutable.LongMap[Thread]()

  def currentThread(): Thread = lock.synchronized {
    threadsMap.getOrElse(pthread_self().toLong, MainThread)
  }

  def dumpStack(): Unit = {
    System.err.println("Stack trace")
    new Throwable()
      .getStackTrace()
      .foreach { elem => System.err.println(s"  $elem") }
  }

  def enumerate(list: Array[Thread]): Int = {
    currentThread().getThreadGroup().enumerate(list)
  }

  def holdsLock(obj: Object): scala.Boolean = ???

  def `yield`(): Unit = {
    sched_yield()
  }

  def getAllStackTraces(): java.util.Map[Thread, Array[StackTraceElement]] = {
    var parent: ThreadGroup =
      new ThreadGroup(currentThread().getThreadGroup, "Temporary")
    var newParent: ThreadGroup = parent.getParent
    parent.destroy()
    while (newParent != null) {
      parent = newParent
      newParent = parent.getParent
    }
    var threadsCount: Int          = parent.activeCount() + 1
    var count: Int                 = 0
    var liveThreads: Array[Thread] = Array.empty
    var break: scala.Boolean       = false
    while (!break) {
      liveThreads = new Array[Thread](threadsCount)
      count = parent.enumerate(liveThreads)
      if (count == threadsCount) {
        threadsCount *= 2
      } else
        break = true
    }

    val map: java.util.Map[Thread, Array[StackTraceElement]] =
      new util.HashMap[Thread, Array[StackTraceElement]](count + 1)
    var i: Int = 0
    while (i < count) {
      val ste: Array[StackTraceElement] = liveThreads(i).getStackTrace
      if (ste.length != 0)
        map.put(liveThreads(i), ste)
      i += 1
    }

    map
  }

  def interrupted(): scala.Boolean = {
    val ret = currentThread().isInterrupted()
    currentThread().interruptedState = false
    ret
  }

  def sleep(millis: scala.Long, nanos: scala.Int): Unit = {
    println(s"Sleep ${Thread.currentThread()}")
    import scala.scalanative.posix.errno.EINTR
    import scala.scalanative.unsafe._
    import scala.scalanative.posix.unistd

    def checkErrno() =
      if (errno.errno == EINTR) {
        throw new InterruptedException("Sleep was interrupted")
      }

    if (millis < 0) {
      throw new IllegalArgumentException("millis must be >= 0")
    }
    if (nanos < 0 || nanos > 999999) {
      throw new IllegalArgumentException("nanos value out of range")
    }

    val secs  = millis / 1000
    val usecs = (millis % 1000) * 1000 + nanos / 1000
    if (secs > 0 && unistd.sleep(secs.toUInt) != 0.toUInt) checkErrno()
    if (usecs > 0 && unistd.usleep(usecs.toUInt) != 0) checkErrno()
  }

  def sleep(millis: scala.Long): Unit = sleep(millis, 0)

  trait UncaughtExceptionHandler {
    def uncaughtException(t: Thread, e: Throwable)
  }

}
