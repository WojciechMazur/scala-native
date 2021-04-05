package scala.scalanative

import scalanative.unsafe._
import scalanative.unsigned._

package object windows {
  type Word      = UShort
  type DWord     = UInt
  type DWordLong = ULong

  type SecurityAttributes = CStruct3[DWord, Ptr[Byte], Boolean]
  implicit class SecurityAttributesOps(ref: Ptr[SecurityAttributes])(
      implicit tag: Tag[SecurityAttributes]) {
    def length: DWord                 = ref._1
    def securityDescriptor: Ptr[Byte] = ref._2
    def inheritHandle: Boolean        = ref._3

    def length_=(v: DWord): Unit                 = ref._1 = v
    def securityDescriptor_=(v: Ptr[Byte]): Unit = ref._2 = v
    def inheritHandle_=(v: Boolean): Unit        = ref._3 = v
  }

  @extern
  object Constants {
    @name("scalanative_win32_infinite")
    def Infinite(): DWord = extern
  }

}
