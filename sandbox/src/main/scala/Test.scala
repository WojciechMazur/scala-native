//> using lib "org.typelevel::cats-effect::3.4.2"
import cats.effect.{IO, IOApp}
// import cats.effect.std.Random
// import scala.concurrent.duration._
import java.net._
import java.io._

object Test {
  def main(args: Array[String]): Unit = {

    println("Hello world")
    System.getenv().forEach{(k, v) => println(k -> v)}
    println("all")
    return

    // int preopen_fd = getenv("DEBUGGER_FD") ? 4 : 3;
    import scala.scalanative.posix.sys.socket._
    import scala.scalanative.posix.errno._
    import scala.scalanative.unsafe._
    val preOpenFd = 3
    val addrOut = stackalloc[sockaddr]()
    val addrLen = stackalloc[socklen_t]()

    // libc's accept4 is mapped to WASI's sock_accept with some additional parameter/return mapping at https://github.com/WebAssembly/wasi-libc/blob/63e4489d01ad0262d995c6d9a5f1a1bab719c917/libc-bottom-half/sources/accept.c#L10
    // struct sockaddr addr_out_ignored;
    // socklen_t addr_len_out_ignored;
    var fd = -1
    while(fd == -1){
      fd = accept4(preOpenFd, addrOut, addrLen, SOCK_NONBLOCK)
      // int new_connection_fd = accept4(preopen_fd, &addr_out_ignored, &addr_len_out_ignored, SOCK_NONBLOCK);
      println(errno)
      println(fd)
    } 

    // val clientSocket = new Socket(
    //   InetAddress.getByAddress("localhost", Array[Byte](127, 0, 0, 1)),
    //   8080
    // )
    // val inStream = new DataInputStream(clientSocket.getInputStream())
    // val outStream = new DataOutputStream(clientSocket.getOutputStream())
    // println(inStream.readUTF())
    // println(System.currentTimeMillis())
    // Thread.sleep(100)
    // println(System.currentTimeMillis())

    // // EH is currently not available when using WASI-SDK, can work with Enscripten
    // try throw new RuntimeException()
    // catch {case ex: Throwable => println(ex)}

    // println("done")
  }
}

// object Hello extends IOApp.Simple {
//   println("Hello world")
//   println(System.currentTimeMillis())
//   Thread.sleep(100)
//   println(System.currentTimeMillis())

//   // EH is currently not available when using WASI-SDK, can work with Enscripten
//   try throw new RuntimeException()
//   catch {case ex: Throwable => println(ex)}

//   println("foo")

//   def sleepPrint(word: String, name: String, rand: Random[IO]) =
//     for {
//       delay <- rand.betweenInt(200, 700)
//       _ <- IO.println(s"$word sleep for $delay ms")
//       _ <- IO.sleep(delay.millis)
//       _ <- IO.println(s"$word, $name")
//     } yield ()

//   override val run: IO[Unit] =
//     for {
//       _ <- IO.println("Hello Cats Effects!")
//       rand <- Random.scalaUtilRandom[IO]

//       // try uncommenting first one locally! Scastie doesn't like System.in
//       // name <- IO.print("Enter your name: ") >> IO.readLine
//       name <- IO.pure("Daniel")

//       _ <- IO.println("Spawn cancellables")
//       english <- sleepPrint("Hello", name, rand).foreverM.start
//       french <- sleepPrint("Bonjour", name, rand).foreverM.start
//       spanish <- sleepPrint("Hola", name, rand).foreverM.start

//       _ <- IO.println("sleep")
//       _ <- IO.sleep(1.seconds)

//       _ <- IO.println("cancel")
//       _ <- english.cancel >> french.cancel >> spanish.cancel
//     } yield ()
// }
