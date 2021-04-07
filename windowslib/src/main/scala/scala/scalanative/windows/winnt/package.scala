package scala.scalanative.windows

import scala.scalanative.unsafe._
import scala.scalanative.windows.SecurityBase._

package object winnt {
  type SidAndAttributes = CStruct2[SIDPtr, DWord]
  implicit class SidAndAttributesOps(ref: Ptr[SidAndAttributes]) {
    def sid: SIDPtr       = ref._1
    def attributes: DWord = ref._2

    def size_=(v: SIDPtr)      = ref._1 = v
    def attributes_=(v: DWord) = ref._2 = v
  }


}
