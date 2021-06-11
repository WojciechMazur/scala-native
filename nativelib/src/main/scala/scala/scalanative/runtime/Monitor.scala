package scala.scalanative.runtime

import scalanative.annotation.alwaysinline
import scala.scalanative.unsafe._
import monitor._

class Monitor(basicMonitor: BasicMonitor) {

  @alwaysinline def _notify(): Unit    = basicMonitor._notify()
  @alwaysinline def _notifyAll(): Unit = basicMonitor._notifyAll()
  @alwaysinline def _wait(): Unit      = basicMonitor._wait()

  @alwaysinline
  def _wait(timeout: scala.Long): Unit =
    basicMonitor._wait(timeout)

  @alwaysinline
  def _wait(timeout: scala.Long, nanos: Int): Unit =
    basicMonitor._wait(timeout, nanos)

  @alwaysinline def enter(): Unit = basicMonitor.enter()
  @alwaysinline def exit(): Unit  = basicMonitor.exit()

}
