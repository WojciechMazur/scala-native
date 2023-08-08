import scala.scalanative.runtime.LLVMIntrinsics._
import scala.scalanative.unsafe._
import scala.scalanative.runtime.libc.{malloc, free}

object Test {
  def main(args: Array[String]): Unit = {
    println("Hello, World!")
    val hdl1 = f(42)
    println("resume")
    `llvm.coro.resume`(hdl1)
    println("resume")
    `llvm.coro.resume`(hdl1)
    println("destroy")
    `llvm.coro.destroy`(hdl1)
  }

  def f(v: Int): CoroHandle = {
    val token = `llvm.coro.id`(0, null, null, null)
    val addr = malloc(`llvm.coro.size.i32`())
    val handle = `llvm.coro.begin`(token, addr)

    var state = v
    while (true) {
      println(state)
      state += 1
      `llvm.coro.suspend`(null, false) match {
        case 0 => ()
        case n =>
          if (n == 1) free(`llvm.coro.free`(token, handle))
          `llvm.coro.end`(handle, false)
          return handle
      }
    }
    ???
  }
}
