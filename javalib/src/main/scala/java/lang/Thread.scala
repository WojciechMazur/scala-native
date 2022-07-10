package java.lang

import java.util
import java.lang.Thread._
import java.util.concurrent.atomic.{AtomicReference, AtomicLong}
import java.util.concurrent.locks.LockSupport
import java.lang.impl._

import scala.annotation.tailrec

import scala.scalanative.unsafe._
import scala.scalanative.runtime.NativeThread
import scala.scalanative.meta.LinktimeInfo.{isWindows, isMultithreadingEnabled}
import scala.annotation.switch

// Ported from Harmony

class Thread private[lang] (
    group: ThreadGroup,
    target: Runnable,
    stackSize: Long,
    private[java] val inheritableValues: ThreadLocal.Values
) extends Runnable {
  private val threadId = getNextThreadId()

  @volatile private var daemon = false
  @volatile private var interruptedState = false

  @volatile private var name: String = s"Thread-$threadId"
  @volatile private var priority: Int = Thread.NORM_PRIORITY

  @volatile private[lang] var contextClassLoader: ClassLoader = _

  // Uncaught exception handler for this thread
  @volatile private var exceptionHandler: Thread.UncaughtExceptionHandler = _

  // ThreadLocal values : local and inheritable
  private[java] lazy val localValues: ThreadLocal.Values =
    new ThreadLocal.Values()
  private[java] var threadLocalRandomSeed: Long = 0
  private[java] var threadLocalRandomProbe: Int = 0
  private[java] var threadLocalRandomSecondarySeed: Int = 0

  private[java] val parkBlocker: AtomicReference[Object] =
    new AtomicReference[Object]()

  @volatile private[java] var nativeThread: NativeThread = _

  // constructors
  def this(
      group: ThreadGroup,
      target: Runnable,
      name: String,
      stacksize: scala.Long,
      inheritThreadLocals: Boolean
  ) = {
    this(
      group = Option(group).getOrElse(Thread.currentThread().getThreadGroup()),
      target = target,
      stackSize =
        if (stacksize > 0) stacksize
        else NativeThread.DefaultStackSize.toLong,
      inheritableValues =
        if (inheritThreadLocals)
          new ThreadLocal.Values(Thread.currentThread().inheritableValues)
        else new ThreadLocal.Values()
    )

    val parent: Thread = Thread.currentThread()

    if (parent != null) {
      this.daemon = parent.daemon
      this.contextClassLoader = parent.getContextClassLoader()
      this.priority = parent.priority

    }

    if (name != null) {
      this.name = name
    }

    checkGCWatermark()
  }

  def this(
      group: ThreadGroup,
      target: Runnable,
      name: String,
      stacksize: scala.Long
  ) = this(group, target, name, stacksize, true)

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
    if (isStarted) {
      nativeThread.setPriority(priority)
    }
  }

  final def getThreadGroup(): ThreadGroup = group

  def getContextClassLoader(): ClassLoader = contextClassLoader

  def setContextClassLoader(classLoader: ClassLoader): Unit = {
    contextClassLoader = classLoader
  }

  def getUncaughtExceptionHandler(): Thread.UncaughtExceptionHandler = {
    if (exceptionHandler != null)
      return exceptionHandler
    getThreadGroup()
  }

  def setUncaughtExceptionHandler(eh: Thread.UncaughtExceptionHandler): Unit = {
    exceptionHandler = eh
  }

  final def isAlive(): scala.Boolean =
    nativeThread != null && {
      (nativeThread.state.state: @switch) match {
        case NativeThread.State.New        => false
        case NativeThread.State.Terminated => false
        case _                             => true
      }
    }
  private def isStarted =
    nativeThread != null &&
      nativeThread.state.state != NativeThread.State.New

  final def isDaemon(): scala.Boolean = daemon

  final def setDaemon(daemon: scala.Boolean): Unit = {
    if (isAlive())
      throw new IllegalThreadStateException()
    this.daemon = daemon
  }

  def isInterrupted(): scala.Boolean = interruptedState

  def interrupt(): Unit = {
    lock.synchronized {
      if (isStarted) interruptedState = true
    }
    if (nativeThread.state.is(NativeThread.State.Parked)) {
      LockSupport.unpark(this)
    }
  }

  def getStackTrace(): Array[StackTraceElement] =
    new Array[StackTraceElement](0)

  @deprecated("Deprecated for removal", "1.2")
  def countStackFrames(): Int = 0

  @deprecated("Deprecated for removal", "1.7")
  def destroy(): Unit =
    // this method is not implemented
    throw new NoSuchMethodError()

  final def join(): Unit = synchronized {
    while (isAlive()) wait()
  }

  final def join(ml: scala.Long): Unit = {
    var millis: scala.Long = ml
    if (millis < 0)
      throw new IllegalArgumentException("timeout value is negative")
    if (millis == 0) join()
    else {
      val callingThread = Thread.currentThread()
      synchronized {
        val end: scala.Long = System.currentTimeMillis() + millis
        var continue: scala.Boolean = true
        while (isAlive() && millis > 0) {
          wait(millis)
          millis = end - System.currentTimeMillis()
        }
      }
    }
  }

  final def join(ml: scala.Long, n: Int): Unit = {
    var nanos: Int = n
    var millis: scala.Long = ml
    if (millis < 0 || nanos < 0 || nanos > 999999)
      throw new IllegalArgumentException()
    if (millis == 0 && nanos == 0) join()
    else {
      val callingThread = Thread.currentThread()
      synchronized {
        val end = System.nanoTime() + 1000000 * millis + nanos.toLong
        var rest = 0L
        var continue = true
        while (isAlive() && { rest = end - System.nanoTime(); rest > 0 }) {
          wait(millis, nanos)
          nanos = (rest % 1000000).toInt
          millis = rest / 1000000
        }
      }
    }
  }

  @deprecated("Deprecated for removal", "1.7")
  final def resume(): Unit = {
    if (isStarted) nativeThread.resume()
  }

  def run(): Unit = {
    if (target != null) {
      target.run()
    }
  }

  def start(): Unit = synchronized {
    if (isStarted) {
      throw new IllegalThreadStateException(
        "This thread was already started!"
      )
    }
    group.add(this)

    nativeThread = if (isMultithreadingEnabled) {
      if (isWindows) new impl.WindowsThread(this, isMainThread = false)
      else new impl.PosixThread(this, isMainThread = false)
    } else
      throw new UnsupportedOperationException(
        "Cannot start threads with disabled multithreading support"
      )
    while (nativeThread.state == NativeThread.State.New) {
      Thread.onSpinWait()
    }
  }

  def getState(): State = nativeThread.state.toThreadState()

  @deprecated("Deprecated for removal", "1.7")
  final def stop(): Unit = stop(new ThreadDeath())

  @deprecated("Deprecated for removal", "1.7")
  final def stop(throwable: Throwable): Unit = {
    if (throwable == null)
      throw new NullPointerException("The argument is null!")
    if (isStarted && isAlive()) {
      nativeThread.stop()
    }
  }

  @deprecated("Deprecated for removal", "1.7")
  final def suspend(): Unit = {
    if (isStarted) {
      nativeThread.suspend()
    }
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

  // Called from NativeThreads on termination
  private[lang] def onTermination(): Unit = {
    this.getThreadGroup().remove(this)
  }

}

object Thread {
  // type State = ThreadState
  // lazy val State = ThreadState
  sealed case class State(name: String, ordinal: Int)
  // extends Enum[State](name, ordinal)

  object State {
    final val NEW: State = new State("NEW", 0)
    final val RUNNABLE: State = new State("RUNNABLE", 1)
    final val BLOCKED: State = new State("BLOCKED", 2)
    final val WAITING: State = new State("WAITING", 3)
    final val TIMED_WAITING: State = new State("TIMED_WAITING", 4)
    final val TERMINATED: State = new State("TERMINATED", 5)

    private[this] val cachedValues =
      Array(NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED)
    def values(): Array[State] = cachedValues.clone()
    def valueOf(name: String): State = {
      cachedValues.find(_.name == name).getOrElse {
        throw new IllegalArgumentException("No enum const Thread.State." + name)
      }
    }
  }

  object MainThread
      extends Thread(
        group = new ThreadGroup(ThreadGroup.System, "main"),
        target = null: Runnable,
        stackSize = NativeThread.DefaultStackSize.toLong,
        inheritableValues = new ThreadLocal.Values()
      ) {
    import scala.scalanative.meta.LinktimeInfo.isWindows
    import scala.scalanative.unsigned._
    setName("main")
    trait MainThreadOverrides {
      self: NativeThread =>
      override def setPriority(priority: CInt): Unit = ()
      override def stop(): Unit = sys.exit()
      override def suspend(): Unit = LockSupport.park()
      override def resume(): Unit = LockSupport.unpark(this.thread)
      state = NativeThread.State.Running
    }
    nativeThread = {
      if (isWindows)
        new impl.WindowsThread(this, isMainThread = true)
          with MainThreadOverrides
      else
        new impl.PosixThread(this, isMainThread = true) with MainThreadOverrides
    }
    NativeThread.TLS.currentThread = this.nativeThread
  }

  import scala.collection.mutable
  private var defaultExceptionHandler: UncaughtExceptionHandler = _

  private val lock: Object = new Object()
  private var threadOrdinalNum = 0L

  final val MAX_PRIORITY: Int = 10
  final val MIN_PRIORITY: Int = 1
  final val NORM_PRIORITY: Int = 5

  def onSpinWait(): Unit = NativeThread.Intrinsics.yieldProcessor()

  def getDefaultUncaughtExceptionHandler(): UncaughtExceptionHandler =
    defaultExceptionHandler

  def setDefaultUncaughtHandler(eh: UncaughtExceptionHandler): Unit =
    defaultExceptionHandler = eh

  // Counter used to generate thread's ID

  private def getNextThreadId(): scala.Long = synchronized {
    threadOrdinalNum += 1
    threadOrdinalNum
  }

  // Number of threads that was created w/o garbage collection //TODO
  private var currentGCWatermarkCount: Int = 0

  // Max number of threads to be created w/o GC, required collect dead Thread references
  private final val GC_WATERMARK_MAX_COUNT: Int = 700

  def activeCount(): Int = currentThread().getThreadGroup().activeCount()

  def currentThread(): Thread = {
    val nativeThread = NativeThread.TLS.currentThread
    if (nativeThread != null) nativeThread.thread
    else MainThread
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

  def `yield`(): Unit =
    if (isWindows) WindowsThread.yieldThread()
    else PosixThread.yieldThread()

  def getAllStackTraces(): java.util.Map[Thread, Array[StackTraceElement]] = {
    var parent: ThreadGroup =
      new ThreadGroup(currentThread().getThreadGroup(), "Temporary")
    var newParent: ThreadGroup = parent.getParent()
    parent.destroy()
    while (newParent != null) {
      parent = newParent
      newParent = parent.getParent()
    }
    var threadsCount: Int = parent.activeCount() + 1
    var count: Int = 0
    var liveThreads: Array[Thread] = Array.empty
    var break: scala.Boolean = false
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
      val ste: Array[StackTraceElement] = liveThreads(i).getStackTrace()
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
    if (millis < 0) {
      throw new IllegalArgumentException("millis must be >= 0")
    }
    if (nanos < 0 || nanos > 999999) {
      throw new IllegalArgumentException("nanos value out of range")
    }

    val nativeThread = Thread.currentThread().nativeThread
    nativeThread.state = NativeThread.State.Waiting
    try {
      if (isWindows) WindowsThread.sleep(millis, nanos)
      else PosixThread.sleep(millis, nanos)
    } finally nativeThread.state = NativeThread.State.Running

  }

  def sleep(millis: scala.Long): Unit = sleep(millis, 0)

  trait UncaughtExceptionHandler {
    def uncaughtException(t: Thread, e: Throwable): Unit
  }

}
