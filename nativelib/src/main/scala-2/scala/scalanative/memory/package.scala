package scala.scalanative
import scala.annotation.Annotation

package object memory{
  type Nullable[T] = T

  // Stub for Scala 3 captured capabilities
  private[memory] class capabiltyCompat extends Annotation
}