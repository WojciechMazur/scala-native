# Language semantics

In general, the semantics of the Scala Native language are the same as
Scala on the JVM. However, a few differences exist, which we mention
here.

## Interop extensions

Annotations and types defined `scala.scalanative.unsafe` may modify
semantics of the language for sake of interoperability with C libraries,
read more about those in [interop](./interop.md)
section.

(multithreading)=
## Multithreading

Scala Native supports parallel multi-threaded programming and assumes
multi-threaded execution by default. Upon the absence of system threads
in the linked program, Scala Native can automatically switch to
single-threaded mode, allowing to get rid of redundant synchronization,
as the state is never shared between threads.

Scala Native tries to follow the Java Memory Model, but by default uses
more relaxed semantics in some areas. Due to the majority of immutable
shared states in most Scala programs, Scala Native does not follow Java
final fields semantics. Safe publication of final fields (`val`s in
Scala) can be enabled by annotating fields or the whole class with
`@scala.scalanative.annotation.safePublish`, this
behaviour can be also enabled on whole project scope by providing a
Scala compiler plugin options
`-Pscalanative:forceStrictFinalFields`. Semantics of final
fields can be also overridden at linktime using
`NativeConfig.semanticsConfig` - it can be configured to
override default relaxed memory model, allowing to replace it with
strict JMM semantics or disable synchronization entirely.

Scala Native ensures that all class field operations would be executed
atomically, but does not impose any synchronization or happens-before
guarantee.

 When executing extern functions Garbage Collector needs to be notified about the internal state of the calling thread. This notification is required to correctly track reachable objects and skip waiting for threads executing unmanaged code - these may block (e.g. waiting on socket connection) for long time leading to deadlocks during GC.
 By default only calls to methods annotated with `@scala.scalanative.unsafe.blocking` would notify the GC - it allows to reduce overhead of extern method calls, but might lead to deadlocks or longer GC pauses when waiting for unannotated blocking function call.
 This behaviour can be changed by enabling `NativeConfig.strictExternCallSemantics`. Under this mode every invocation of foreign function would notify the GC about the thread state which guarantees no deadlocks introduced by waiting for threads executing foreign code, but might reduce overall performance.


### Virtual threads and continuations

Virtual threads (`Thread.ofVirtual()`) and the delimited continuations
in `scala.scalanative.runtime.Continuations` require Scala 3,
multithreading, and a 64-bit x86_64 or aarch64 target. They are
supported on Linux, macOS, FreeBSD, OpenBSD, NetBSD and Windows. Use
`LinktimeInfo.isVirtualThreadsSupported` and
`LinktimeInfo.isContinuationsSupported` to check support at link time.

On Windows each `boundary` runs on its own OS fiber, which differs from
the other platforms in a few ways:

- A continuation can be resumed only once. Resuming it again throws
  `IllegalStateException`.
- A suspended continuation that is no longer referenced is collected by
  the GC, together with the objects referenced only from its stack. Its
  fibers are released at a later suspend. Without a GC (`GC.none`) the
  fibers are never released.
- Each suspend copies the suspended stacks into a heap object owned by the
  continuation, so the GC can trace them.
- The Boehm GC is not supported.
- Each fiber reserves 1 MB of stack address space by default. Set the
  `SCALANATIVE_CONTINUATION_STACK_SIZE` environment variable to change
  it, for example `SCALANATIVE_CONTINUATION_STACK_SIZE=4m`.

## Finalization

Finalize method from `java.lang.Object` is never called in Scala Native.

## Undefined behavior

Generally, Scala Native follows most of the special error conditions
similarly to JVM:

1.  Arrays throw `IndexOutOfBoundsException` on out-of-bounds access.
2.  Casts throw `ClassCastException` on incorrect casts.
3.  Accessing a field or method on `null`, throwing `null` exception,
    throws `NullPointerException`.
4.  Integer division by zero throws `ArithmeticException`.

There is one exception:

1.  Stack overflows are undefined behavior and would typically segfault
    on supported architectures instead of throwing `StackOverflowError`.

The Immix and Commix garbage collectors throw `OutOfMemoryError` when the
managed heap is exhausted. None GC does the same when it cannot map another
heap chunk. They reserve one 32 KiB block to construct the first error and
capture its stack trace. Until a later GC replenishes the reserve with an
empty block, concurrent failures throw a shared, preallocated
`OutOfMemoryError` without a stack trace.

Continue to [interop](./interop.md).
