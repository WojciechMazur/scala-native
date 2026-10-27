package org.scalanative.testsuite.javalib.lang

import org.junit.Assert._
import org.junit._

import scala.scalanative.junit.utils.AssumesHelper

// TEMPORARY: CI repeat harness, not for merge.
object VirtualThreadMonitorRepeatTest {
  @BeforeClass def checkRuntime(): Unit =
    AssumesHelper.assumeSupportsVirtualThreads()
}

class VirtualThreadMonitorRepeatTest {
  @Test def repeatContendedMonitorEnter(): Unit = {
    val minutes = sys.env.get("REPEAT_MINUTES").map(_.toLong).getOrElse(8L)
    val maxIterations =
      sys.env.get("REPEAT_ITERATIONS").map(_.toInt).getOrElse(100000)
    val deadline = System.nanoTime() + minutes * 60L * 1000000000L
    val t = new VirtualThreadMonitorTest
    def run(body: => Unit): Option[String] =
      try { body; None }
      catch { case e: Throwable => Some(e.toString) }

    var iterations = 0
    var consumeFail, offerFail, otherFail = 0
    val messages = scala.collection.mutable.LinkedHashSet.empty[String]
    while (iterations < maxIterations && System.nanoTime() < deadline) {
      // Noise similar to the preceding tests in VirtualThreadMonitorTest.
      run(t.synchronizedBlockExclusivity()).foreach { m =>
        otherFail += 1; messages += m
      }
      run(t.monitorContention()).foreach { m => otherFail += 1; messages += m }
      run(t.contendedMonitorEnterDoesNotConsumeParkingPermit()).foreach { m =>
        consumeFail += 1; messages += m
      }
      run(t.contendedMonitorEnterDoesNotOfferParkingPermit()).foreach { m =>
        offerFail += 1; messages += m
      }
      iterations += 1
    }
    val summary =
      s"REPEAT-RESULT iterations=$iterations consumeFail=$consumeFail offerFail=$offerFail otherFail=$otherFail"
    System.out.println(summary)
    messages.take(10).foreach(m => System.out.println(s"REPEAT-MSG $m"))
    assertEquals(summary, 0, consumeFail + offerFail + otherFail)
  }
}
