package scala.scalanative
package runtime

import scalanative.unsafe._
import scala.scalanative.annotation.alwaysinline

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

  /*  Multihreading awareness for GC Every implementation of GC supported in
   *  ScalaNative needs to register a given thread The main thread is
   *  automatically registered. Every additional thread needs to explicitlly
   *  notify GC about it's creation and termination. For that purpuse we follow
   *  the Boehm GC convention for overloading the pthread_create/CreateThread
   *  functions respectively for POSIX and Windows. Respectivelly after the last
   *  object allocation and before termination the thread needs to call
   *  pthread_exit/ExitThread to unregister itself from the GC.
   */
  private type pthread_t = CUnsignedLongLong
  private type pthread_attr_t = CUnsignedLongLong
  private type Handle = Ptr[Byte]
  private type DWord = CUnsignedInt
  private type SecurityAttributes = CStruct3[DWord, Ptr[Byte], Boolean]
  type PtrAny = Ptr[Byte]
  type ThreadRoutineArg = PtrAny
  type ThreadStartRoutine = CFuncPtr1[PtrAny, PtrAny]

  @name("scalanative_pthread_create")
  def pthread_create(
      thread: Ptr[pthread_t],
      attr: Ptr[pthread_attr_t],
      startroutine: ThreadStartRoutine,
      args: PtrAny
  ): CInt = extern
  @name("scalanative_pthread_exit")
  def pthread_exit(retVal: Ptr[Byte]): Unit = extern

  @name("scalanative_CreateThread")
  def CreateThread(
      threadAttributes: Ptr[SecurityAttributes],
      stackSize: CSize,
      startRoutine: ThreadStartRoutine,
      routineArg: PtrAny,
      creationFlags: DWord,
      threadId: Ptr[DWord]
  ): Handle = extern

  @name("scalanative_ExitThread")
  def ExitThread(exitCode: DWord): Unit = extern

  @extern
  object MutatorThread {
    object Ext {
      @alwaysinline def withGCSafeZone[T](fn: => T) = {
        val prev = switchState(State.InSafeZone)
        try fn
        finally switchState(State.Running)
      }
    }
    type State = Int
    @name("scalanative_switch_mutator_thread_state")
    def switchState(newState: State): State = extern

    object State {
      final val Running = 0
      final val WaitingForGC = 1
      final val InSafeZone = 2
    }
  }

}
