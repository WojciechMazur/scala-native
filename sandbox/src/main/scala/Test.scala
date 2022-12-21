//> using lib "org.typelevel::cats-effect::3.4.2"
import cats.effect.{IO, IOApp}
import cats.effect.std.Random

import scala.concurrent.duration._

object Hello extends IOApp.Simple {
  println("Hello world")

  // EH is currently not available when using WASI-SDK, can work with Enscripten
  // try throw new RuntimeException()
  // catch {case ex: Throwable => println(ex)}
 
  def sleepPrint(word: String, name: String, rand: Random[IO]) =
    for {
      delay <- rand.betweenInt(200, 700)
      _ <- IO.println(s"$word sleep for $delay ms")
      _ <- IO.sleep(delay.millis)
      _ <- IO.println(s"$word, $name")
    } yield ()

  override val run: IO[Unit] =
    for {
      _ <- IO.println("Hello Cats Effects!")
      rand <- Random.scalaUtilRandom[IO]

      // try uncommenting first one locally! Scastie doesn't like System.in
      // name <- IO.print("Enter your name: ") >> IO.readLine
      name <- IO.pure("Daniel")

      _ <- IO.println("Spawn cancellables")
      english <- sleepPrint("Hello", name, rand).foreverM.start
      french <- sleepPrint("Bonjour", name, rand).foreverM.start
      spanish <- sleepPrint("Hola", name, rand).foreverM.start

      _ <- IO.println("sleep")
      _ <- IO.sleep(1.seconds)

      _ <- IO.println("cancel")
      _ <- english.cancel >> french.cancel >> spanish.cancel
    } yield ()
}
