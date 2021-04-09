package scala.scalanative.windows

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

@link("ws2_32")
@extern
object WinSocketApi {
  import WinSocket._

  def WSAStartup(versionRequested: Word, data: Ptr[WSAData]): CInt = extern

  def WSACleanup(): CInt = extern

  // Posix pollFd has the same structure as Windows WSAPoolFd
  type WSAPollFd =
    CStruct3[CInt,   // Posix style file descriptor for compat in java.net
             CShort, // requested events
             CShort] // returned events

  @name("WSAPoll")
  def poll(fds: Ptr[WSAPollFd], nfds: CUnsignedLongInt, timeout: CInt): CInt =
    extern

  @name("ioctlsocket")
  def ioctlSocket(socket: Socket, cmd: CInt, argp: Ptr[CInt]): CInt = extern

  @name("closesocket")
  def closeSocket(socket: Socket): CInt = extern

  @name("scalanative_winsocket_fionbio")
  final def FIONBIO: CInt = extern

  @name("scalanative_winsock_wsadata_size")
  final def WSADataSize: CSize = extern
}

object WinSocket {
  type Socket = Ptr[Byte]
  private final val WinSocketVersion: (Byte, Byte) = (2, 2)

  final def withSocketsEnabled[T](body: => T): T = {
    init()
    try body
    finally WinSocketApi.WSACleanup()
  }

  final def init(): Unit = {
    val requiredVersion = (wordFromBytes _).tupled(WinSocketVersion)
    val winSocketData = stackalloc[Byte](WinSocketApi.WSADataSize)
      .asInstanceOf[Ptr[WSAData]]

    val initError = WinSocketApi.WSAStartup(requiredVersion, winSocketData)
    if (initError != 0) {
      throw new RuntimeException(
        s"Failed to initialize socket support, error code $initError ")
    }
    val receivedVersion = wordToBytes(winSocketData.version)
    if (WinSocketVersion != receivedVersion) {
      WinSocketApi.WSACleanup()
      throw new RuntimeException(
        s"Could not find a usable version of WinSock.dll, expected $WinSocketVersion, got $receivedVersion")
    }
  }

  // This structures contains additional 5 fields with different order in Win_64 and others
  type WSAData = CStruct2[Word, Word]
  implicit class WSADataOps(ref: Ptr[WSAData]) {
    def version: Word     = ref._1
    def highVersion: Word = ref._2
  }

  private def wordToBytes(word: Word): (Byte, Byte) = {
    val lowByte  = (word & 0xFF.toUShort).toByte
    val highByte = (word >> 8 & 0xFF.toUShort).toByte
    (lowByte, highByte)
  }

  private def wordFromBytes(low: Byte, high: Byte): Word = {
    ((low & 0xff) | ((high & 0xff) << 8)).toUShort
  }

}
