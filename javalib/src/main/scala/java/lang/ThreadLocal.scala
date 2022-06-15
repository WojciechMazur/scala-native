package java.lang

import java.lang.ref.{Reference, WeakReference}
import java.util.concurrent.atomic.AtomicInteger

// Ported from Harmony

class ThreadLocal[T] {

  import java.lang.ThreadLocal._

  private final val reference: Reference[ThreadLocal[T]] =
    new WeakReference[ThreadLocal[T]](this)

  private final val hash: Int = hashCounter.getAndAdd(0x61c88647 << 1)

  protected def initialValue(): T = null.asInstanceOf[T]

  @SuppressWarnings(Array("unchecked"))
  def get(): T = {
    val vals: Values = values(Thread.currentThread())
    val table: Array[Object] = vals.getTable()
    val index: Int = hash & vals.getMask()

    if (this.reference == table(index)) {
      table(index + 1).asInstanceOf[T]
    } else vals.getAfterMiss(this).asInstanceOf[T]
  }

  def set(value: T): Unit = {
    var vals: Values = values(Thread.currentThread())
    vals.put(this, value.asInstanceOf[Object])
  }

  def remove(): Unit = {
    val currentThread: Thread = Thread.currentThread()
    val vals: Values = values(currentThread)
    if (vals != null) {
      vals.remove(this)
    }
  }

  def values(current: Thread): Values = current.localValues

  protected[lang] def childValue[T](parentValue: T) = {
    throw new UnsupportedOperationException()
  }
}

object ThreadLocal {

  private val hashCounter: AtomicInteger = new AtomicInteger(0)

  private[java] class Values private (private var table: Array[Object]) {
    import Values._

    private var size: Int = 0
    private var tombstones: Int = 0
    private var clean: Int = 0

    private def mask: Int = table.length - 1
    private def maximumLoad: Int = table.length / 3

    def this(fromParent: Values) = {
      this(table = Values.inherit(fromParent))
      size = fromParent.size
      tombstones = fromParent.tombstones
      clean = fromParent.clean
    }

    def this() = {
      this(table = new Array[Object](Values.InitialSize))
    }

    def getTable(): Array[Object] = table

    def getMask(): Int = mask

    private def cleanUp(): Unit = {
      // If we rehashed, we needn't clean up (clean up happens as
      // a side effect).
      if (rehash()) return
      // No live entries == nothing to clean.
      if (size == 0) return

      // Clean log(table.length) entries picking up where we left off
      // last time.
      var index: Int = clean
      val table: Array[Object] = this.table
      var counter = table.length

      var continue: scala.Boolean = false

      while (counter > 0) {
        continue = false
        if(index > table.size){
          println(s"Cleanup $index / ${table.size}")
        }
        val k: Object = table(index)

        if (k == Tombstone || k == null) continue = true

        if (!continue) {
          val reference: Reference[ThreadLocal[_]] =
            k.asInstanceOf[Reference[ThreadLocal[_]]]
          if (reference.get() == null) {
            table(index) = Tombstone
            table(index + 1) = null
            tombstones += 1
            size -= 1
          }
        }

        counter >>= 1
        index = next(index)
      }

      clean = index
    }

    private def rehash(): scala.Boolean = {
      if (tombstones + size < maximumLoad) return false

      val newCapacity: Int = (table.length >> 1) match {
        case capacity if (size > capacity) => capacity << 1
        case capacity                      => capacity
      }

      val oldTable: Array[Object] = this.table
      // Allocate new table.
      this.table = new Array[Object](newCapacity)
      this.clean = 0
      // We won't have any tombstones after this.
      this.tombstones = 0

      // If we have no live entries, we can quit here.
      if (size == 0) return true

      // Move over entries.
      var i: Int = oldTable.length - 2
      var continue: scala.Boolean = false
      while (i >= 0) {
        if(i > oldTable.length){
          println(s"rehash - $i / ${oldTable.length}")
        }
        continue = false
        val k: Object = oldTable(i)
        if (k == null || k == Tombstone) continue = true

        if (!continue) {
          val reference: Reference[ThreadLocal[_]] =
            k.asInstanceOf[Reference[ThreadLocal[_]]]

          val key: ThreadLocal[_] = reference.get()
          if (key != null) add(key, oldTable(i + 1))
          else size -= 1
        }
        i -= 2
      }

      true
    }

    /**
     * Adds an entry during rehashing. Compared to put(), this method
     * doesn't have to clean up, check for existing entries, account for
     * tombstones, etc.
     */
    def add(key: ThreadLocal[_], value: Object): Unit = {
      var index: Int = key.hash & mask
      while (true) {
        val k: Object = table(index)
        System.out.println(s"add - $index/${table.length} - $key - $value")
        if (k == null) {
          table(index) = key.reference
          table(index + 1) = value
          return
        }

        index = next(index)
      }
    }

    def put(key: ThreadLocal[_], value: Object): Unit = {
      cleanUp()

      var firstTombstone: Int = -1

      var index: Int = key.hash & mask
      while (true) {
        if(index > table.length || firstTombstone > table.length){
          println(s"Index $index, firstTombstone: ${firstTombstone}, tableLen: ${table.length}")
        }
        val k: Object = table(index)

        if (k == key.reference) {
          table(index + 1) = value
          return
        }

        if (k == null) {
          if (firstTombstone == -1) {
            table(index) = key.reference
            table(index + 1) = value
            size += 1
            return
          }

          table(firstTombstone) = key.reference
          table(firstTombstone + 1) = value
          tombstones -= 1
          size += 1
          return
        }

        if (firstTombstone == -1 && k == Tombstone) firstTombstone = index

        index = next(index)
      }
    }

    def getAfterMiss(key: ThreadLocal[_]): Object = {
      val table: Array[Object] = this.table
      var index: Int = key.hash & mask

      // If the first slot is empty, the search is over
      if (table(index) == null) {
        val value: Object = key.initialValue().asInstanceOf[Object]

        // If the table is still the same and the slot is still empty...
        if ((this.table == table) && table(index) == null) {
          table(index) = key.reference
          table(index + 1) = value
          size += 1

          cleanUp()
          return value
        }

        // The table changed during initialValue().
        put(key, value)
        return value
      }

      // Keep track of first tombstone. That's where we want to go back
      // and add an entry if necessary
      var firstTombstone: Int = -1

      index = next(index)
      while (true) {
        val reference: Object = table(index)
        if (reference == key.reference) return table(index + 1)

        // If no entry was found...
        if (reference == null) {
          val value: Object = key.initialValue().asInstanceOf[Object]

          // If the table is still the same
          if (this.table == table) {
            // If we passed a tombstone and that slot still
            // contains a tombstone
            if (firstTombstone > -1 && table(firstTombstone) == Tombstone) {
              table(firstTombstone) = key.reference
              table(firstTombstone + 1) = value
              tombstones -= 1
              size += 1

              // No need to clean up here. We aren't filling
              // in a null slot
              return value
            }

            // If this slot is still empty...
            if (table(index) == null) {
              table(index) = key.reference
              table(index + 1) = value
              size += 1

              cleanUp()
              return value
            }
          }

          // The table changed during initialValue().
          put(key, value)
          return value
        }

        if (firstTombstone == -1 && reference == Tombstone)
          // Keep track of this tombstone so we can overwrite it.
          firstTombstone = index

        index = next(index)
      }
      // For the compiler
      null: Object
    }

    def remove(key: ThreadLocal[_]): Unit = {
      cleanUp()

      var index: Int = key.hash & mask
      while (true) {
        val reference: Object = table(index)

        if (reference == key.reference) {
          table(index) = Tombstone
          table(index + 1) = null
          tombstones += 1
          size -= 1
          return
        }

        if (reference == null) return

        index = next(index)
      }
    }

    private def next(index: Int) = (index + 2) & mask

  }

  object Values {

    private final val InitialCapacity = 1024
    private final val InitialSize = InitialCapacity << 1

    private case object Tombstone

    private def inherit(fromParent: Values): Array[Object] = {
      val table = fromParent.getTable().clone()
      for {
        i <- (table.length - 2) to 0 by -2
        next = i + 1
        current = table(i) if current != null && current != Tombstone
        // Array can contain only null, Tombstone or Reference
        reference = current
          .asInstanceOf[Reference[InheritableThreadLocal[Object]]]
      } {
        reference.get() match {
          case null =>
            table(i) = Tombstone
            table(next) = null
            fromParent.table(i) = Tombstone
            fromParent.table(next) = null
            fromParent.tombstones += 1
            fromParent.size -= 1

          case key =>
            // Replace value with filtered value
            // We should just let exceptions bubble out and tank
            // the thread creation
            table(next) = key.childValue(fromParent.table(next))
        }
      }
      table
    }
  }

}
