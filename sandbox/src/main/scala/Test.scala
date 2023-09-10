import scala.scalanative.runtime.LLVMIntrinsics._
import scala.scalanative.unsafe._
import scala.scalanative.runtime.libc.{malloc, free}
import scala.scalanative.runtime.RawPtr
import scala.util.boundary
import scala.scalanative.runtime.struct
import scala.scalanative.runtime.{fromRawPtr, toRawPtr}
import scala.scalanative.runtime.Intrinsics
import scala.annotation.switch
import scala.runtime.LazyVals.Names.get

object coro {
  final class CoroutineCtx[T: Tag](
      address: RawPtr,
      handle: RawPtr,
      promise: RawPtr
  )(using
      // suspensionPoint: boundary.Label[SuspendedFunction],
      cleanupPoint: boundary.Label[CoroutineCleanup[T]]
  ) {
    lazy val fnHandle = SuspendedFunction[T](handle)
    inline def suspend(): Unit = suspend(isFinal = false)
    private[coro] inline def suspend(inline isFinal: Boolean): Unit =
      (`llvm.coro.suspend`(save = null, isFinal = isFinal): @switch) match {
        case 0 =>
          fnHandle.yielded = false
        case -1 =>
          // TODO: this branch should not be reachable
          // could be fixed by not using boundary/break which introduces try-catch block
          // and usage of invoke llvm.coro.suspend instead of call
          boundary.break(CoroutineCleanup(-1, fnHandle))
        case 1 =>
          fnHandle.yielded = false
          boundary.break(CoroutineCleanup(1, fnHandle))
      }
    inline def suspend(value: T): Unit = suspend(value, isFinal = false)
    private[coro] inline def suspend(value: T, isFinal: Boolean): Unit = {
      val promise = fromRawPtr[T](this.promise)
      !promise = value
      fnHandle.yielded = true
      suspend(isFinal)
    }
  }

  type Suspendable[T] = CoroutineCtx[T] ?=> T
  final class SuspendedFunction[T: Tag] private[coro] (
      val handle: RawPtr
  ) {
    var yielded: Boolean = false
    inline def resume() = `llvm.coro.resume`(handle)
    inline def destroy() = `llvm.coro.destroy`(handle)
    inline def isDone = `llvm.coro.done`(handle)
    inline def get: Option[T] = Option.when(yielded) {
      val promisePtr =
        `llvm.coro.promise`(handle, alignmentOf[T], from = false)
      val promise: Ptr[T] = fromRawPtr(promisePtr)
      !promise
    }
    override def toString(): String =
      s"SuspendedFunction(${scala.scalanative.runtime.fromRawPtr(handle)})"
  }
  final class CoroutineCleanup[T](val state: Int, val fn: SuspendedFunction[T])

  inline def suspendable[T: Tag](
      inline body: Suspendable[T]
  ): SuspendedFunction[T] = {
    // init
    val promise = Intrinsics.stackalloc[T]()
    val token = `llvm.coro.id`(0, promise, null, null)
    val addr = malloc(`llvm.coro.size.i32`())
    val handle = `llvm.coro.begin`(token, addr)
    val retAddr = `llvm.coro.free`(token, null)

    val res = boundary[CoroutineCleanup[T]] {
      val ctx = CoroutineCtx[T](retAddr, handle, promise)
      val value = body(using ctx)
      ctx.suspend(value, isFinal = true)
      CoroutineCleanup(1, ctx.fnHandle)
    }
    // cleanup
    if (res.state == 1) free(retAddr)
    `llvm.coro.end`(handle, unwind = res.state == -1)
    res.fn
  }
  transparent inline def ctx[T](using ctx: CoroutineCtx[T]): CoroutineCtx[T] =
    ctx
  inline def suspend(using ctx: CoroutineCtx[_]): Unit = ctx.suspend()
  inline def suspend[T](value: T)(using ctx: CoroutineCtx[T]): Unit =
    ctx.suspend(value)
}

  import coro.*

  @main def run(): Unit = {
    val generator = simpleGenerator(from = 0)
    def lookup() = println(
      s"isDone=${generator.isDone}, promise value=${generator.get}"
    )
    while (!generator.isDone) {
      lookup()
      generator.resume()
    }
    lookup()
    generator.destroy()
    lookup()
  }

  def simpleGenerator(from: Int) = suspendable[Int] {
    var state = from
    while (state < 3) {
      println(state)
      state += 1
      println("doSuspend")
      suspend(state)

      println("afterSuspend")
      suspend(-state)
      
      println("after 2nd suspend")
    }
    -1
  }
}
