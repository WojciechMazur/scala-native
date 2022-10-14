package scala.scalanative
package runtime

import scalanative.unsafe._

/** The Boehm GC conservative garbage collector
 *
 *  @see
 *    [[http://hboehm.info/gc/gcinterface.html C Interface]]
 */
@extern
object GC {
  @deprecated("Marked for removal, use alloc(Class[_], CSize) instead", "0.4.1")
  @name("scalanative_alloc")
  def alloc(rawty: RawPtr, size: CSize): RawPtr = extern

  @deprecated(
    "Marked for removal, use alloc_atomic(Class[_], CSize) instead",
    "0.4.1"
  )
  @name("scalanative_alloc_atomic")
  def alloc_atomic(rawty: RawPtr, size: CSize): RawPtr = extern

  @name("scalanative_alloc")
  def alloc(cls: Class[_], size: CSize): RawPtr = extern
  @name("scalanative_alloc")
  private[runtime] def alloc(cls: Class[_], size: RawSize): RawPtr = extern

  @name("scalanative_alloc_atomic")
  def alloc_atomic(cls: Class[_], size: CSize): RawPtr = extern
  @name("scalanative_alloc_atomic")
  private[runtime] def alloc_atomic(cls: Class[_], size: RawSize): RawPtr =
    extern

  @name("scalanative_alloc_small")
  def alloc_small(cls: Class[_], size: CSize): RawPtr = extern
  @name("scalanative_alloc_small")
  private[runtime] def alloc_small(cls: Class[_], size: RawSize): RawPtr =
    extern

  @name("scalanative_alloc_large")
  def alloc_large(cls: Class[_], size: CSize): RawPtr = extern
  @name("scalanative_alloc_large")
  private[runtime] def alloc_large(cls: Class[_], size: RawSize): RawPtr =
    extern

  @name("scalanative_collect")
  def collect(): Unit = extern
  @name("scalanative_init")
  def init(): Unit = extern
  @name("scalanative_register_weak_reference_handler")
  def registerWeakReferenceHandler(handler: Ptr[Byte]): Unit = extern

  @extern
  object MutatorThread {
    type State = Int
    @name("scalanative_gc_switch_mutator_thread_state")
    def switchState(newState: State): State = extern

    object State {
      final val Running = 0
      final val WaitingForGC = 1
      final val InSafeZone = 2
    }
  }

}
