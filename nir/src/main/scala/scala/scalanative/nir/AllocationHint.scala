package scala.scalanative.nir

sealed trait AllocationHint {
  def allocator: Option[Val.Local] = this match {
    case AllocationHint.GC            => None
    case AllocationHint.Stack         => None
    case AllocationHint.UnsafeZone(v) => Some(v)
    case AllocationHint.SafeZone(v)   => Some(v)
  }

  def mapAllocator(mapping: Val.Local => Val.Local): AllocationHint =
    this match {
      case AllocationHint.GC                   => this
      case AllocationHint.Stack                => this
      case hint @ AllocationHint.UnsafeZone(v) => hint.copy(mapping(v))
      case hint @ AllocationHint.SafeZone(v)   => hint.copy(mapping(v))
    }
}

object AllocationHint {
  def default: AllocationHint = GC
  object Default {
    def unapply(v: AllocationHint): Boolean = v eq default
  }

  case object GC extends AllocationHint
  case object Stack extends AllocationHint
  case class UnsafeZone(zone: Val.Local) extends AllocationHint
  case class SafeZone(zone: Val.Local) extends AllocationHint
}
