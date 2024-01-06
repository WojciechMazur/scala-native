package scala.scalanative.annotation

import scala.scalanative.unsafe.Zone
import scala.annotation.meta

@meta.field
sealed abstract class allocationHint extends scala.annotation.StaticAnnotation

object allocationHint {
  final class stack extends allocationHint
  final class gc extends allocationHint
  final class zone(implicit zone: Zone) extends allocationHint
}
