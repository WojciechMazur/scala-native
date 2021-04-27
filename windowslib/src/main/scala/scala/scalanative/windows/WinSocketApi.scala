package scala.scalanative.windows

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

@link("ws2_32")
@extern
object WinSocketApi {
  import WinSocket._

  type Socket           = Ptr[Byte]
  type Group            = DWord
  type WSAProtocolInfoW = Ptr[Byte]
  type WSAPollFd        = CStruct3[Socket, CShort, CShort]
  // This structures contains additional 5 fields with different order in Win_64 and others
  // Should only be treated as read-only and never allocated in ScalaNative.
  type WSAData = CStruct2[Word, Word]

  def WSAStartup(versionRequested: Word, data: Ptr[WSAData]): CInt = extern

  def WSACleanup(): CInt = extern

  def WSASocketW(
      addressFamily: CInt,
      socketType: CInt,
      protocol: CInt,
      protocolInfo: Ptr[WSAProtocolInfoW],
      group: Group,
      flags: DWord
  ): Socket = extern

  def WSAPoll(fds: Ptr[WSAPollFd],
              nfds: CUnsignedLongInt,
              timeout: CInt): CInt =
    extern

  def WSAGetLastError(): CInt = extern

  @name("ioctlsocket")
  def ioctlSocket(socket: Socket, cmd: CInt, argp: Ptr[CInt]): CInt = extern

  @name("closesocket")
  def closeSocket(socket: Socket): CInt = extern

  @name("scalanative_winsocket_fionbio")
  final def FIONBIO: CInt = extern

  @name("scalanative_winsock_wsadata_size")
  final def WSADataSize: CSize = extern

  @name("scalanative_winsock_invalid_socket")
  final def InvalidSocket: Socket = extern

  @name("scalanative_winsock_poll_pollin")
  final def POLLIN: CShort = extern

  @name("scalanative_winsock_poll_pollout")
  final def POLLOUT: CShort = extern
}

object WinSocket {
  import WinSocketApi._

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

  implicit class WSADataOps(ref: Ptr[WSAData]) {
    def version: Word     = ref._1
    def highVersion: Word = ref._2
  }

  implicit class WSAPollFdOps(ref: Ptr[WSAPollFd]) {
    def socket: Socket  = ref._1
    def events: CShort  = ref._2
    def revents: CShort = ref._3

    def socket_=(v: Socket): Unit  = ref._1 = v
    def events_=(v: CShort): Unit  = ref._2 = v
    def revents_=(v: CShort): Unit = ref._3 = v
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
