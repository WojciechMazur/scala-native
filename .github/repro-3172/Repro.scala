//> using scala 2.13.12
//> using platform native

import scala.annotation.tailrec

// Minimized from https://github.com/JD557/minart/pull/322 (scala-native#3172).
// A left-nested chain of flatMaps re-associated by a tail-recursive `run`
// allocates many short-lived closures while live references are kept only in
// callee-saved registers (once LLVM optimizes the code, e.g. with LTO in debug mode).
sealed trait St[S, A] {
  @tailrec final def run(s: S): (S, A) = this match {
    case Pt(f) => f(s)
    case FM(st, k) =>
      st match {
        case Pt(f) =>
          val (s2, a) = f(s)
          k(a).run(s2)
        case FM(st2, k2) =>
          st2.flatMap((x: Any) => k2(x).flatMap(k)).run(s)
      }
  }
  final def flatMap[B](f: A => St[S, B]): St[S, B] = FM(this, f)
}
final case class Pt[S, A](f: S => (S, A)) extends St[S, A]
final case class FM[S, A, B](st: St[S, A], k: A => St[S, B]) extends St[S, B]

object Main {
  def main(args: Array[String]): Unit = {
    val n = args.headOption.fold(300000)(_.toInt)
    val rounds = if (args.length > 1) args(1).toInt else 5
    for (r <- 0 until rounds) {
      var acc: St[List[Array[Byte]], Unit] = Pt(s => (s, ()))
      var i = 0
      while (i < n) {
        val b = i.toByte
        acc = acc.flatMap(_ => Pt(s => (Array.fill(24)(b) :: s, ())))
        i += 1
      }
      val (out, _) = acc.run(Nil)
      var j = n - 1
      var l = out
      while (l.nonEmpty) {
        assert(l.head(0) == j.toByte && l.head(23) == j.toByte, s"bad at $j")
        l = l.tail
        j -= 1
      }
      assert(j == -1, s"length mismatch $j")
      println(s"round $r OK")
    }
  }
}
