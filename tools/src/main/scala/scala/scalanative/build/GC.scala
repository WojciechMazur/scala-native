package scala.scalanative.build

/** Garbage Collector. The Application is going to be automatically linked with
 *  corresponding libraries that implement one of the given collectors:
 *
 *  * None GC. Never frees allocated memory.
 *
 *  * Boehm GC. Conservative mark-and-sweep garbage collector.
 *
 *  * Immix GC. Mostly-precise mark-region garbage collector.
 *
 *  * Commix GC. Mostly-precise mark-region garbage collector running
 *  concurrently.
 *
 *  * Experimental GC. Stub so implementers can experiment with a new GC without
 *  having to change the build system.
 *
 *  Additional GCs might be added to the list in the future.
 *
 *  @param dir
 *    name of the gc
 *  @param links
 *    linking dependencies of the gc
 */
sealed abstract class GC private (val name: String) {
  def links(config: NativeConfig): Seq[String] = Nil
  @deprecated(
    "Use config-based links to resolve dynamically linked libraries instead",
    since = "0.5.6"
  ) def links: Seq[String] = links(NativeConfig.empty)

  /** The name of the [[GC]] object
   *
   *  @return
   *    the [[GC]] name
   */
  override def toString: String = name
}

/** Utility to create a [[GC]] object */
object GC {
  private[scalanative] case object None extends GC("none")
  private[scalanative] case object Boehm extends GC("boehm") {
    override def links(config: NativeConfig): Seq[String] = Seq("gc")
  }
  private[scalanative] case object Immix extends GC("immix") {
    override def links(config: NativeConfig): Seq[String] =
      if (config.multithreadingSupport && !config.targetsWindows) Seq("pthread")
      else Seq.empty
  }
  private[scalanative] case object Commix extends GC("commix") {
    override def links(config: NativeConfig): Seq[String] =
      if (!config.targetsWindows) Seq("pthread")
      else Seq.empty
  }
  private[scalanative] case object Experimental extends GC("experimental")

  /** Non-freeing garbage collector. */
  def none: GC = None

  /** Conservative garbage collector based on libgc. */
  def boehm: GC = Boehm

  /** Mostly-precise mark-region garbage collector. */
  def immix: GC = Immix

  /** Mostly-precise mark-region garbage collector running concurrently. */
  def commix: GC = Commix

  /** The default garbage collector. */
  def default: GC = Immix

  /** Placeholder for a user defined experimental garbage collector. */
  def experimental: GC = Experimental

  /** Get a garbage collector with given name. */
  def apply(gc: String) = gc match {
    case "none" =>
      none
    case "boehm" =>
      boehm
    case "immix" =>
      immix
    case "commix" =>
      commix
    case "experimental" =>
      experimental
    case value =>
      throw new IllegalArgumentException(
        "GC can be either \"none\", \"boehm\", \"immix\", \"commix\" or \"experimental\", not: " + value
      )
  }
}
