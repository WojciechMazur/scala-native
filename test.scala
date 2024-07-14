//> using scala 3.nightly

import scala.language.experimental.namedTuples

object Test:

  type X = (a: String, b: Int)
  val bar = (a = "hello", b = 1)
  type Y = bar.type
  summon[bar.type =:= (a: String, b: Int)]

  // summon[NamedTuple.Names[X] <:< ("a", "b")]
  // summon[NamedTuple.Names[bar.type] <:< ("a", "b")]

  // summon[NamedTuple.DropNames[bar] <:< (String, Int)]