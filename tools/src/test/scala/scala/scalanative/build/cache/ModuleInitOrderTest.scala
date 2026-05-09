package scala.scalanative
package build
package cache

import org.junit.Assert.*
import org.junit.Test

class ModuleInitOrderTest {

  @Test def topologicalSortLinear(): Unit = {
    val edges = Seq(("a", "b"), ("b", "c"))
    ModuleInitOrder.topologicalSort(edges) match {
      case Right(order) =>
        assertTrue(order.indexOf("a") < order.indexOf("b"))
        assertTrue(order.indexOf("b") < order.indexOf("c"))
      case Left(_) => fail("expected acyclic graph")
    }
  }

  @Test def cycleDetected(): Unit = {
    val edges = Seq(("a", "b"), ("b", "c"), ("c", "a"))
    ModuleInitOrder.topologicalSort(edges) match {
      case Right(_) => fail("expected cycle")
      case Left(c)  => assertFalse(c.isEmpty)
    }
  }
}
