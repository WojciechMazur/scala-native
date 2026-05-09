package scala.scalanative
package build
package cache

/** Validates a module-init dependency graph and returns a load order for dlopen. */
private[scalanative] object ModuleInitOrder {

  def topologicalSort(edges: Seq[(String, String)]): Either[Seq[(String, String)], Seq[String]] = {
    val nodes = (edges.flatMap(e => Seq(e._1, e._2))).distinct
    val succ =
      edges.groupBy(_._1).map { case (k, v) => k -> v.map(_._2).distinct }
    val predCount = scala.collection.mutable.Map.empty[String, Int]
    nodes.foreach(n => predCount(n) = 0)
    edges.foreach { case (_, b) => predCount(b) = predCount(b) + 1 }

    val q = scala.collection.mutable.Queue.empty[String]
    nodes.foreach(n => if (predCount(n) == 0) q.enqueue(n))
    val out = scala.collection.mutable.ArrayBuffer.empty[String]
    while (q.nonEmpty) {
      val n = q.dequeue()
      out += n
      succ.getOrElse(n, Nil).foreach { m =>
        predCount(m) -= 1
        if (predCount(m) == 0) q.enqueue(m)
      }
    }
    if (out.length == nodes.length) Right(out.toSeq)
    else Left(edges) // cycle: return edges for diagnostics
  }
}
