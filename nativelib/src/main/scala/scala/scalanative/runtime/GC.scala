package scala.scalanative
package runtime

import scalanative.unsafe._
import scala.scalanative.annotation.alwaysinline
import scala.scalanative.meta.LinktimeInfo

/** The Boehm GC conservative garbage collector
 *
 *  @see
 *    [[http://hboehm.info/gc/gcinterface.html C Interface]]
 */
@extern
object GC {
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
   *  functions respectively for POSIX and Windows.
   */
  private type pthread_t = CUnsignedLongInt
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

  @name("scalanative_CreateThread")
  def CreateThread(
      threadAttributes: Ptr[SecurityAttributes],
      stackSize: CSize,
      startRoutine: ThreadStartRoutine,
      routineArg: PtrAny,
      creationFlags: DWord,
      threadId: Ptr[DWord]
  ): Handle = extern

  type MutatorThreadState = CInt
  object MutatorThreadState {

    /** Thread executes Scala Native code using GC following cooperative mode -
     *  it periodically polls for synchronization events.
     */
    @alwaysinline final def Managed = 0

    /** Thread executes foreign code (syscalls, C functions) and is not able to
     *  modify the state of the GC. Upon synchronization event garbage collector
     *  would ignore this thread. Upon returning from foreign execution thread
     *  would stop until synchronization event would finish.
     */
    @alwaysinline final def Unmanaged = 1
  }
  @name("scalanative_setMutatorThreadState")
  def setMutatorThreadState(newState: MutatorThreadState): Unit = extern

}
