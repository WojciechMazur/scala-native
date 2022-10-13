package scala.scalanative
package runtime

import scalanative.unsafe._

// Minimal bindings for the subset of libc used by the nativelib.
// This is done purely to avoid circular dependency between clib
// and nativelib. The actual bindings should go to clib namespace.
@extern
object libc {
  def malloc(size: CSize): RawPtr = extern
  def realloc(ptr: RawPtr, size: CSize): RawPtr = extern
  def free(ptr: RawPtr): Unit = extern
  def strlen(str: CString): CSize = extern
  def wcslen(str: CWideString): CSize = extern
  def strcpy(dest: CString, src: CString): CString = extern
  def strcat(dest: CString, src: CString): CString = extern
  def memcpy(dst: RawPtr, src: RawPtr, count: CSize): RawPtr = extern
  def memcmp(lhs: RawPtr, rhs: RawPtr, count: CSize): CInt = extern
  def memset(dest: RawPtr, ch: CInt, count: CSize): RawPtr = extern
  def memmove(dest: RawPtr, src: RawPtr, count: CSize): RawPtr = extern
  def remove(fname: CString): CInt = extern

  // Glue layer defined in libc
  @name("scalanative_atomic_compare_exchange_strong_byte")
  def atomic_compare_exchange_strong(
      ptr: RawPtr,
      expected: RawPtr,
      desired: Byte
  ): CBool = extern
  @name("scalanative_atomic_compare_exchange_strong_long")
  def atomic_compare_exchange_strong(
      ptr: RawPtr,
      expected: RawPtr,
      desired: CLong
  ): CBool = extern
  @name("scalanative_atomic_compare_exchange_strong_intptr")
  def atomic_compare_exchange_strong(
      ptr: RawPtr,
      expected: RawPtr,
      desired: AnyRef
  ): CBool = extern
  @name("scalanative_atomic_store_explicit_intptr")
  def atomic_store_explicit(
      ptr: RawPtr,
      v: AnyRef,
      memoryOrder: memory_order
  ): Unit = extern
  @name("scalanative_atomic_store_explicit_long")
  def atomic_store_explicit(
      ptr: RawPtr,
      v: Long,
      memoryOrder: memory_order
  ): Unit = extern
  @name("scalanative_atomic_thread_fence")
  final def atomic_thread_fence(order: memory_order): Unit = extern

  type memory_order = Int
  @extern object memory_order {
    @name("scalanative_atomic_memory_order_relaxed")
    final def memory_order_relaxed: memory_order = extern
    @name("scalanative_atomic_memory_order_consume")
    final def memory_order_consume: memory_order = extern
    @name("scalanative_atomic_memory_order_acquire")
    final def memory_order_acquire: memory_order = extern
    @name("scalanative_atomic_memory_order_release")
    final def memory_order_release: memory_order = extern
    @name("scalanative_atomic_memory_order_acq_rel")
    final def memory_order_acq_rel: memory_order = extern
    @name("scalanative_atomic_memory_order_seq_cst")
    final def memory_order_seq_cst: memory_order = extern
  }

  // posix
  def usleep(usec: Int): Int = extern

  type pthread_cond_t = RawPtr
  type pthread_condattr_t = RawPtr
  type pthread_mutex_t = RawPtr
  type pthread_mutexattr_t = RawPtr
  type time_t = CLong
  type timespec = CStruct2[time_t, CLong]
  def pthread_cond_broadcast(cond: Ptr[pthread_cond_t]): CInt = extern
  def pthread_cond_destroy(cond: Ptr[pthread_cond_t]): CInt = extern
  def pthread_cond_init(
      cond: Ptr[pthread_cond_t],
      attr: Ptr[pthread_condattr_t]
  ): CInt = extern
  def pthread_cond_signal(cond: Ptr[pthread_cond_t]): CInt = extern
  def pthread_cond_timedwait(
      cond: Ptr[pthread_cond_t],
      mutex: Ptr[pthread_mutex_t],
      timespec: Ptr[timespec]
  ): CInt = extern
  def pthread_cond_wait(
      cond: Ptr[pthread_cond_t],
      mutex: Ptr[pthread_mutex_t]
  ): CInt = extern
  def pthread_condattr_destroy(attr: Ptr[pthread_condattr_t]): CInt = extern
  def pthread_condattr_init(attr: Ptr[pthread_condattr_t]): CInt = extern
  def pthread_mutex_destroy(mutex: Ptr[pthread_mutex_t]): CInt = extern
  def pthread_mutex_init(
      mutex: Ptr[pthread_mutex_t],
      attr: Ptr[pthread_mutexattr_t]
  ): CInt = extern

  def pthread_mutex_lock(mutex: Ptr[pthread_mutex_t]): CInt = extern
  def pthread_mutex_trylock(mutex: Ptr[pthread_mutex_t]): CInt = extern
  def pthread_mutex_unlock(mutex: Ptr[pthread_mutex_t]): CInt = extern

  def pthread_mutexattr_destroy(attr: Ptr[pthread_mutexattr_t]): CInt = extern
  def pthread_mutexattr_init(attr: Ptr[pthread_mutexattr_t]): CInt = extern

  @name("scalanative_pthread_cond_t_size")
  def pthread_cond_t_size: CSize = extern
  @name("scalanative_pthread_condattr_t_size")
  def pthread_condattr_t_size: CSize = extern
  @name("scalanative_pthread_mutex_t_size")
  def pthread_mutex_t_size: CSize = extern
  @name("scalanative_pthread_mutexattr_t_size")
  def pthread_mutexattr_t_size: CSize = extern
}
