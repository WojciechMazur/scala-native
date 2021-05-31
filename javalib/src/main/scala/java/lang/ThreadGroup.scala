package java.lang

import java.util
import java.lang.Thread.UncaughtExceptionHandler
import java.util.ScalaOps._
import scala.collection.mutable.Set
import scala.annotation.nowarn
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

// Ported from Harmony

class ThreadGroup(name: String, parent: ThreadGroup, isTop: Boolean)
    extends UncaughtExceptionHandler {
  if (parent == null && !isTop) {
    throw new NullPointerException("The parent thread group specified is null!")
  }

  // This group's max priority
  private var maxPriority: Int = Thread.MAX_PRIORITY

  // Indicated if this thread group was marked as daemon
  private var daemon: scala.Boolean = false

  // Indicates if this thread group was already destroyed
  private var destroyed: scala.Boolean = false

  private val childrenGroups  = ListBuffer.empty[ThreadGroup]
  private val childrenThreads = ListBuffer.empty[Thread]

  def this(parent: ThreadGroup, name: String) = {
    this(name, parent, false)
    this.daemon = parent.daemon
    this.maxPriority = parent.maxPriority
    parent.add(this)
  }

  def this(name: String) = this(
    Thread.currentThread().getThreadGroup(),
    name
  )

  def getMaxPriority(): Int = maxPriority

  def setMaxPriority(priority: Int): Unit = {
    if (priority <= this.maxPriority) {
      val newMax = priority.max(Thread.MIN_PRIORITY)
      val parentPriority =
        if (parent == null) newMax
        else parent.getMaxPriority()
      this.maxPriority = newMax.min(parentPriority)

      childrenGroups.synchronized {
        childrenGroups.foreach(_.setMaxPriority(newMax))
      }
    }
  }

  def getName(): String = name

  def getParent(): ThreadGroup = {
    parent
  }

  def isDaemon(): scala.Boolean = daemon

  def setDaemon(daemon: scala.Boolean): Unit = {
    this.daemon = daemon
  }

  def isDestroyed(): scala.Boolean = destroyed

  def activeCount(): Int = {
    childrenGroups.synchronized {
      childrenGroups.foldLeft(childrenThreads.size) { _ + _.activeCount() }
    }
  }

  def activeGroupCount(): Int = {
    childrenGroups.synchronized {
      childrenGroups.foldLeft(childrenGroups.size) { _ + _.activeGroupCount() }
    }
  }

  @deprecated
  def allowchildrenThreadSuspension(b: scala.Boolean): scala.Boolean = true

  def destroy(): Unit = {
    if (destroyed) {
      throw new IllegalThreadStateException(
        "The thread group " + name + " is already destroyed!")
    }

    childrenThreads.synchronized {
      childrenGroups.synchronized {
        childrenGroups.foreach(_.destroy())
        if (parent != null) {
          parent.remove(this)
        }
        this.destroyed = true
      }
    }
  }

  def enumerate(threads: Array[Thread]): Int = {
    enumerate(threads, true)
  }

  def enumerate(threads: Array[Thread], recurse: scala.Boolean): Int = {
    enumerateImpl(threads.asInstanceOf[Array[Any]],
                  recurse,
                  0,
                  enumeratingThreads = true)
  }

  def enumerate(groups: Array[ThreadGroup]): Int = {
    enumerate(groups, true)
  }

  def enumerate(groups: Array[ThreadGroup], recurse: scala.Boolean): Int = {
    enumerateImpl(groups.asInstanceOf[Array[Any]],
                  recurse,
                  0,
                  enumeratingThreads = false)
  }

  private def enumerateImpl(enumeration: Array[Any],
                            recurse: scala.Boolean,
                            enumerationIndex: Int,
                            enumeratingThreads: scala.Boolean): Int = {
    val collection =
      if (enumeratingThreads) childrenThreads
      else childrenGroups

    var idx = enumerationIndex
    collection.synchronized {
      collection.foreach { elem =>
        val shouldTap = elem match {
          case thread: Thread => thread.isAlive()
          case _              => true
        }

        if (shouldTap) {
          if (idx >= enumeration.length)
            return idx

          idx += 1
          enumeration(idx) = elem
        }
      }
    }

    if (recurse) {
      childrenGroups.synchronized {
        childrenGroups.foreach { group =>
          if (idx >= enumeration.length)
            return idx

          idx =
            group.enumerateImpl(enumeration, recurse, idx, enumeratingThreads)

        }
      }
    }
    idx
  }

  def interrupt(): Unit = {
    childrenThreads.synchronized {
      childrenThreads.foreach(_.interrupt())
    }

    childrenGroups.synchronized {
      childrenGroups.foreach(_.interrupt())
    }
  }

  def list(): Unit = {
    println()
    list("")
  }

  private def list(indent: String): Unit = {
    val indentLevel = "  "
    print(indent)
    println(this.toString())

    childrenThreads.synchronized {
      childrenThreads.foreach { thread =>
        print(indent)
        println(thread.toString())
      }
    }

    val nextIndent = indent + indentLevel
    childrenGroups.synchronized {
      childrenGroups.foreach(_.list(nextIndent))
    }
  }

  def parentOf(group: ThreadGroup): scala.Boolean = {
    if (group == null) false
    else if (this == group) true
    else parentOf(group.getParent())
  }

  @nowarn("msg=method resume in class Thread is deprecated")
  @deprecated
  def resume(): Unit = {
    childrenThreads.synchronized {
      childrenThreads.foreach(_.resume())
    }
    childrenGroups.synchronized {
      childrenGroups.foreach(_.resume())
    }
  }

  @nowarn("msg=method stop in class Thread is deprecated")
  @deprecated
  def stop(): Unit = {
    childrenThreads.synchronized {
      childrenThreads.foreach(_.stop())
    }
    childrenGroups.synchronized {
      childrenGroups.foreach(_.stop())
    }
  }

  @nowarn("msg=method suspend in class Thread is deprecated")
  @deprecated
  def suspend(): Unit = {
    childrenThreads.synchronized {
      childrenThreads.foreach(_.suspend())
    }
    childrenGroups.synchronized {
      childrenGroups.foreach(_.suspend())
    }
  }

  override def toString: String = {
    getClass().getName() + "[name=" + name + ",maxpri=" + maxPriority + "]"
  }

  def uncaughtException(thread: Thread, throwable: Throwable): Unit = {
    if (parent != null) {
      parent.uncaughtException(thread, throwable)
    } else {
      val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
      if (defaultHandler != null) {
        defaultHandler.uncaughtException(thread, throwable)
      } else {
        throwable match {
          case _: ThreadDeath => ()
          case _ =>
            System.err.println(
              "Uncaught exception in " + thread.getName() + ":")
            throwable.printStackTrace()
        }
      }
    }
  }

  @throws[IllegalThreadStateException]
  def add(thread: Thread): Unit = {
    childrenThreads.synchronized {
      if (destroyed) {
        throw new IllegalThreadStateException(
          "The thread group is already destroyed!")
      }
      childrenThreads += thread
    }
  }

  @throws[IllegalThreadStateException]
  def add(group: ThreadGroup): Unit = {
    childrenGroups.synchronized {
      if (destroyed) {
        throw new IllegalThreadStateException(
          "The thread group is already destroyed!")
      }
      childrenGroups += group
    }
  }

  def remove(thread: Thread): Unit = {
    childrenThreads.synchronized {
      childrenThreads -= thread
    }
    destroyIfEmptyDeamon()
  }

  private def remove(group: ThreadGroup): Unit = {
    childrenGroups.synchronized {
      childrenGroups -= group
    }
    destroyIfEmptyDeamon()
  }

  private def destroyIfEmptyDeamon(): Unit = {
    if (daemon && !destroyed) {
      childrenThreads.synchronized {
        if (childrenThreads.isEmpty) {
          childrenGroups.synchronized {
            if (childrenGroups.isEmpty) {
              destroy()
            }
          }
        }
      }
    }
  }

  def checkAccess(): Unit = ()

}

object ThreadGroup {
  val System = new ThreadGroup("system", null, isTop = true)
}
