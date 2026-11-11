package scala.scalanative
package build
package units

import java.nio.file.Path

import scala.collection.mutable

import scala.scalanative.build.store.Digest

/** A unit of separate compilation: one classpath entry, or entries that refer
 *  to each other and so can only be compiled together.
 *
 *  @param entries
 *    classpath entries of the unit with their libraries, in classpath order
 *  @param dependencies
 *    indices, in the partition, of the units this one refers to
 */
private[scalanative] final case class BuildUnit(
    entries: Seq[(Path, LibraryIndex)],
    dependencies: Seq[Int]
) {

  /** Changes whenever any class of the unit changes. */
  lazy val digest: Digest =
    Digest.of(entries.map(_._2.digest.toString).mkString("\n"))

  /** Changes only when the ABI of the unit changes, see `LibraryIndex`. */
  lazy val abiDigest: Digest =
    Digest.of(entries.map(_._2.abiDigest.toString).mkString("\n"))
}

private[scalanative] object UnitPartitioner {

  /** Partitions the classpath into units: the strongly connected components of
   *  the graph of references between entries, dependencies first. A class
   *  defined by several entries belongs to the first one, as when linking.
   *  References to classes defined nowhere are ignored.
   */
  def apply(classpath: Seq[(Path, LibraryIndex)]): Seq[BuildUnit] = {
    val owner = mutable.HashMap.empty[nir.Global.Top, Int]
    classpath.zipWithIndex.foreach {
      case ((_, library), entry) =>
        library.classes.foreach { cls =>
          if (!owner.contains(cls.name)) owner(cls.name) = entry
        }
    }
    val edges: IndexedSeq[Seq[Int]] = classpath.indices.map { entry =>
      val (_, library) = classpath(entry)
      library.classes.iterator
        .filter(cls => owner.get(cls.name).contains(entry))
        .flatMap(_.references.iterator.flatMap(owner.get))
        .filter(_ != entry)
        .toSet
        .toSeq
        .sorted
    }

    // Tarjan's algorithm emits components after all components they reach
    val components = stronglyConnectedComponents(classpath.size, edges)
    val componentOf = new Array[Int](classpath.size)
    components.zipWithIndex.foreach {
      case (members, component) =>
        members.foreach(componentOf(_) = component)
    }
    components.zipWithIndex.map {
      case (members, component) =>
        val dependencies = members
          .flatMap(edges(_))
          .map(componentOf(_))
          .filter(_ != component)
          .distinct
          .sorted
        BuildUnit(members.sorted.map(classpath(_)), dependencies)
    }
  }

  private def stronglyConnectedComponents(
      size: Int,
      edges: IndexedSeq[Seq[Int]]
  ): Seq[Seq[Int]] = {
    val index = Array.fill(size)(-1)
    val lowLink = new Array[Int](size)
    val onStack = new Array[Boolean](size)
    val stack = mutable.ArrayBuffer.empty[Int]
    val components = mutable.ArrayBuffer.empty[Seq[Int]]
    var next = 0

    // Iterative, classpaths can be long chains
    def visit(root: Int): Unit = {
      val work = mutable.ArrayBuffer[(Int, Int)](root -> 0)
      index(root) = next; lowLink(root) = next; next += 1
      stack += root; onStack(root) = true
      while (work.nonEmpty) {
        val (node, edge) = work.remove(work.size - 1)
        if (edge < edges(node).size) {
          work += node -> (edge + 1)
          val target = edges(node)(edge)
          if (index(target) == -1) {
            index(target) = next; lowLink(target) = next; next += 1
            stack += target; onStack(target) = true
            work += target -> 0
          } else if (onStack(target))
            lowLink(node) = math.min(lowLink(node), index(target))
        } else {
          if (lowLink(node) == index(node)) {
            val members = mutable.ArrayBuffer.empty[Int]
            var member = -1
            while (member != node) {
              member = stack.remove(stack.size - 1)
              onStack(member) = false
              members += member
            }
            components += members.toSeq
          }
          if (work.nonEmpty) {
            val (parent, _) = work.last
            lowLink(parent) = math.min(lowLink(parent), lowLink(node))
          }
        }
      }
    }

    (0 until size).foreach(node => if (index(node) == -1) visit(node))
    components.toSeq
  }
}
