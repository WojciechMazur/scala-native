import scala.scalanative.runtime.LLVMIntrinsics._
import scala.scalanative.unsafe._
import scala.scalanative.runtime.libc.{malloc, free}
import scala.scalanative.runtime.RawPtr
import scala.util.boundary

object coro {
   final class CoroutineCtx(
      address: RawPtr,
      handle: RawPtr
  )(using
      // suspensionPoint: boundary.Label[SuspendedFunction],
      cleanupPoint: boundary.Label[CoroutineCleanup]
  ) {
    lazy val fnHandle = SuspendedFunction(handle)
    inline def suspend() =
      `llvm.coro.suspend`(save = null, isFinal = false) match {
        case 0 => println("resumed execution")
        case n => boundary.break(CoroutineCleanup(n, fnHandle))
      }
  }

  type Suspendable[T] = CoroutineCtx ?=> T
  final class SuspendedFunction private[coro] (val handle: RawPtr) {
    inline def resume() = `llvm.coro.resume`(handle)
    inline def destroy() = `llvm.coro.destroy`(handle)
    override def toString(): String =
      s"SuspendedFunction(${scala.scalanative.runtime.fromRawPtr(handle)})"
  }
  final class CoroutineCleanup(val state: Int, val fn: SuspendedFunction)

  inline def suspendable[T](inline body: Suspendable[T]): SuspendedFunction = {
    val token = `llvm.coro.id`(0, null, null, null)
    val addr = malloc(`llvm.coro.size.i32`())
    val handle = `llvm.coro.begin`(token, addr)
    val retAddr = `llvm.coro.free`(token, null)
    boundary[SuspendedFunction] {
      val res = boundary[CoroutineCleanup] {
        val ctx = CoroutineCtx(retAddr, handle)
        body(using ctx)
        CoroutineCleanup(0, ctx.fnHandle)
      }
      if (res.state == 1) free(retAddr)
      `llvm.coro.end`(handle, false)
      res.fn
    }
  }
  transparent inline def ctx(using ctx: CoroutineCtx): CoroutineCtx = ctx
  inline def suspend(using ctx: CoroutineCtx): Unit = ctx.suspend()
}

object Test {
  import coro.*

  def main(args: Array[String]): Unit = {
    println("Hello, World!")
    val hdl1 = f2(42)
    println("resume")
    hdl1.resume()
    println("resume")
    hdl1.resume()
    println("destroy")
    println(hdl1)
    hdl1.destroy()
    println("done")
  }

  def f2(v: Int) = suspendable {
    var state = v
    while (true) {
      println(state)
      state += 1
      println("doSuspend")
      ctx.suspend()
      println("afterSuspend")
      ctx.suspend()
      println("after 2nd suspend")
    }
  }

  // def f(v: Int): CoroHandle = {
  //   val token = `llvm.coro.id`(0, null, null, null)
  //   val addr = malloc(`llvm.coro.size.i32`())
  //   val handle = `llvm.coro.begin`(token, addr)

  //   var state = v
  //   while (true) {
  //     println(state)
  //     state += 1
  //     `llvm.coro.suspend`(null, false) match {
  //       case 0 => ()
  //       case n =>
  //         if (n == 1) free(`llvm.coro.free`(token, handle))
  //         `llvm.coro.end`(handle, false)
  //         return handle
  //     }
  //   }
  //   ???
  // }
}
