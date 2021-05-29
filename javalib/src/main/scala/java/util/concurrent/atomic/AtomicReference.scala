package java.util.concurrent.atomic

import java.util.function.UnaryOperator
import scala.scalanative.runtime.CAtomicRef
import scala.language.implicitConversions

class AtomicReference[T <: AnyRef](private[this] var value: T)
    extends Serializable {

  def this() = this(null.asInstanceOf[T])

  private[this] val inner = CAtomicRef[T](value)

  final def get(): T = inner.load()

  final def set(newValue: T): Unit = inner.store(newValue)

  final def lazySet(newValue: T): Unit = inner.store(newValue)

  final def compareAndSet(expect: T, update: T): Boolean = {
    inner
      .compareAndSwapStrong(expect, update)
      ._1
  }

  final def weakCompareAndSet(expect: T, update: T): Boolean = {
    inner
      .compareAndSwapWeak(expect, update)
      ._1
  }

  final def getAndSet(newValue: T): T = {
    val old = inner.load()
    inner.store(newValue)
    old
  }

  final def getAndUpdate(updateFunction: UnaryOperator[T]): T = {
    val old = value
    value = updateFunction(old)
    old
  }

  final def updateAndGet(updateFunction: UnaryOperator[T]): T = {
    value = updateFunction(value)
    value
  }

  override def toString(): String =
    String.valueOf(value)
}

object AtomicReference {

  private final val serialVersionUID: Long = -1848883965231344442L

}
