import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.posix.ucontext._
import scala.scalanative.libc.stdlib._
import scala.scalanative.libc.string.memset
import scala.scalanative.runtime.Intrinsics.{
  castObjectToRawPtr,
  castRawPtrToObject
}
import scala.scalanative.runtime.{fromRawPtr, toRawPtr}
object Coroutine {

  @extern private object externs {
    @name("scalantive_coroutine_setResumeContext")
    def setResumeContext(
        ctx: Ptr[ucontext_t],
        stackSize: CSize,
        stackPointer: Ptr[_]
    ): Unit =
      extern
    def makecontext(
        ucp: Ptr[ucontext_t],
        func: CFuncPtr,
        argc: Int,
        handle: Handle
    ): Unit = extern
  }

  val coroutineEntryPoint: CFuncPtr1[Handle, Unit] =
    CFuncPtr1.fromScalaFunction { handle =>
      println("in coro entrypoint")
      println(handle)
      try handle.function(handle)
      catch {
        case ex: Throwable => 
          println(s"unhandled ex: $ex")
          ex.printStackTrace()
      } finally {
        handle.isDone = true
        handle.suspend()
      }

    }

  type Function = Coroutine.Handle => Unit
  final class Handle(
      private[Coroutine] val function: Coroutine.Function,
      context: Ptr[ucontext_t],
      callerContext: Ptr[ucontext_t],
      stack: Ptr[Byte]
  ) {
    var isDone: Boolean = false

    def resume(): Unit = {
      if (!isDone) swapcontext(callerContext, context)
    }

    def suspend(): Unit = {
      swapcontext(context, callerContext)
    }

    def destroy(): Unit = {
      free(stack)
      free(callerContext)
      free(context)
    }
  }

  def apply(run: Function): Handle = {
    // Make sure to creae big enough stack
    // Initial 8kB is not enough when trying to get stack trace
    // In the future we should implement growable stack 
    val stackSize = (8192 * 10).toUInt // 80kB
    val stack = calloc(1.toUInt, stackSize)

    val context, callerContext =
      malloc(sizeof_ucontext_t).asInstanceOf[Ptr[ucontext_t]]
    getcontext(context)
    externs.setResumeContext(context, stackSize, stack)

    val handle = new Handle(
      function = run,
      context = context,
      callerContext = callerContext,
      stack = stack
    )
    Zone { implicit z =>
      externs.makecontext(
        context,
        coroutineEntryPoint,
        1,
        handle
      )
    }
    handle
  }

}

object Test {
  def main(args: Array[String]): Unit = {
    println(Thread.currentThread())
    // Additioanl thread is used due to problem with thread_local memory in GC
    // It looks like main thread TLS is not propagated upon context switch
    // However, in pthread based hreads there is no such issues
    val t = new Thread(() => {
      println("Hello, World!")
      val c = Coroutine { (handle: Coroutine.Handle) =>
        def inner(): Unit = {
          val x = 42
          println("in inner")
          println(Thread.currentThread())
          handle.suspend()
          def innerInner() = {
            println("inner inner")
            // new RuntimeException().printStackTrace()
            println("throwing")
            throw new RuntimeException("fail", null, true, false) {}
            handle.suspend()
          }
          innerInner()
        }
        inner()

        println("I'm in coroutine")
        handle.suspend()
        println("I've waken up")
        handle.suspend()
        println("I'm finishing now")
      }

      while (!c.isDone) {
        println(s"resume $c")
        c.resume()
      }

    })

    t.start()
    t.join()
  }
}
