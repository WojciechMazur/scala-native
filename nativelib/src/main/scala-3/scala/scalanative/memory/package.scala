package scala.scalanative

package object memory {
  type Nullable[T] = T | Null

  private[memory] type capabiltyCompat = scala.annotation.capability
}
