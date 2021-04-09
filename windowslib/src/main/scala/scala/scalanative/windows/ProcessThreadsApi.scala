package scala.scalanative.windows

import scala.scalanative.unsafe._
import scalanative.unsigned._
import HandleApi.Handle

@link("advapi32")
@link("kernel32")
@extern()
object ProcessThreadsApi {
  import ProcessThreads._

  @name("CreateProcessA")
  def createProcessA(applicationName: CString,
                     commandLine: CString,
                     processAttributres: Ptr[SecurityAttributes],
                     threadAttributes: Ptr[SecurityAttributes],
                     inheritHandle: Boolean,
                     creationFlags: DWord,
                     environment: Ptr[Byte],
                     currentDirectory: CString,
                     startupInfo: Ptr[StartupInfo],
                     processInformation: Ptr[ProcessInformation]): Boolean =
    extern

  @name("CreateProcessW")
  def createProcessW(applicationName: CWString,
                     commandLine: CWString,
                     processAttributres: Ptr[SecurityAttributes],
                     threadAttributes: Ptr[SecurityAttributes],
                     inheritHandle: Boolean,
                     creationFlags: DWord,
                     environment: Ptr[Byte],
                     currentDirectory: CWString,
                     startupInfo: Ptr[StartupInfo],
                     processInformation: Ptr[ProcessInformation]): Boolean =
    extern

  @name("ExitProcess")
  def exitProcess(exitCode: UInt): Unit = extern

  @name("ExitThread")
  def exitThread(exitCode: DWord): Unit = extern

  @name("FlushProcessWriteBuffers")
  def flushProcessWriteBuffers(): Unit = extern

  @name("GetCurrentProcess")
  def getCurrentProcess(): Handle = extern

  @name("GetCurrentProcessToken")
  def getCurrentProcessToken(): Handle = extern

  @name("GetCurrentThread")
  def getCurrentThread(): Handle = extern

  @name("GetExitCodeProcess")
  def getExitCodeProcess(handle: Handle, exitCodePtr: Ptr[DWord]): Boolean =
    extern

  @name("GetExitCodeThread")
  def getExitCodeThread(handle: Handle, exitCodePtr: Ptr[DWord]): Boolean =
    extern

  @name("GetProcessId")
  def getProcessId(handle: Handle): DWord = extern

  @name("OpenThreadToken")
  def openThreadToken(thread: Handle,
                      desiredAccess: DWord,
                      openAsSelf: Boolean,
                      tokenHandle: Ptr[Handle]): Boolean = extern
  @name("OpenProcessToken")
  def openProcessToken(process: Handle,
                       desiredAccess: DWord,
                       tokenHandle: Ptr[Handle]): Boolean = extern

  @name("TerminateProcess")
  def terminateProcess(handle: Handle, exitCode: UInt): Boolean = extern

  @name("TerminateThread")
  def terminateThread(handle: Handle, exitCode: DWord): Boolean = extern

}

object ProcessThreads {
  object ExitCodes {
    final val Successful  = 0.toUInt
    final val StillActive = 259.toUInt
  }

  type StartupInfo = CStruct18[DWord,
                               CString,
                               CString,
                               CString,
                               DWord,
                               DWord,
                               DWord,
                               DWord,
                               DWord,
                               DWord,
                               DWord,
                               DWord,
                               Word,
                               Word,
                               Ptr[Byte],
                               Handle,
                               Handle,
                               Handle]
  implicit class StartupInfoOps(ref: Ptr[StartupInfo])(
      implicit tag: Tag[StartupInfo]) {
    def cb: DWord              = ref._1
    def reserved: CString      = ref._2
    def desktop: CString       = ref._3
    def title: CString         = ref._4
    def x: DWord               = ref._5
    def y: DWord               = ref._6
    def xSize: DWord           = ref._7
    def ySize: DWord           = ref._8
    def xCountChars: DWord     = ref._9
    def yCountChars: DWord     = ref._10
    def fillAtribute: DWord    = ref._11
    def flags: DWord           = ref._12
    def showWindows: Word      = ref._13
    def cbReserved2: Word      = ref._14
    def lpReserved2: Ptr[Byte] = ref._15
    def stdInput: Handle       = ref._16
    def stdOutput: Handle      = ref._17
    def stdError: Handle       = ref._18

    def cb_=(v: DWord): Unit              = ref._1 = v
    def reserved_=(v: CString): Unit      = ref._2 = v
    def desktop_=(v: CString): Unit       = ref._3 = v
    def title_=(v: CString): Unit         = ref._4 = v
    def x_=(v: DWord): Unit               = ref._5 = v
    def y_=(v: DWord): Unit               = ref._6 = v
    def xSize_=(v: DWord): Unit           = ref._7 = v
    def ySize_=(v: DWord): Unit           = ref._8 = v
    def xCountChars_=(v: DWord): Unit     = ref._9 = v
    def yCountChars_=(v: DWord): Unit     = ref._10 = v
    def fillAtribute_=(v: DWord): Unit    = ref._11 = v
    def flags_=(v: DWord): Unit           = ref._12 = v
    def showWindows_=(v: Word): Unit      = ref._13 = v
    def cbReserved2_=(v: Word): Unit      = ref._14 = v
    def lpReserved2_=(v: Ptr[Byte]): Unit = ref._15 = v
    def stdInput_=(v: Handle): Unit       = ref._16 = v
    def stdOutput_=(v: Handle): Unit      = ref._17 = v
    def stdError_=(v: Handle): Unit       = ref._18 = v
  }

  object StartupInfoFlags {
    final val ForceOnFeedback  = 0x00000040.toUInt
    final val ForceOffFeedback = 0x00000080.toUInt
    final val PreventPinning   = 0x00002000.toUInt
    final val RunFullScreen    = 0x00000020.toUInt
    final val TitleIsAppId     = 0x00001000.toUInt
    final val TitleIsLinkName  = 0x00000800.toUInt
    final val UntrustedSource  = 0x00008000.toUInt
    final val UseCountChars    = 0x00000008.toUInt
    final val UseFileAttribute = 0x00000010.toUInt
    final val UseHotkey        = 0x00000200.toUInt
    final val UsePosition      = 0x00000004.toUInt
    final val UseHowWindow     = 0x00000001.toUInt
    final val UseSize          = 0x00000002.toUInt
    final val UseStdHandles    = 0x00000100.toUInt
  }

  type ProcessInformation = CStruct4[Handle, Handle, DWord, DWord]
  implicit class ProcessInformationOps(ref: Ptr[ProcessInformation])(
      implicit tag: Tag[ProcessInformation]) {
    def process: Handle  = ref._1
    def thread: Handle   = ref._2
    def processId: DWord = ref._3
    def threadId: DWord  = ref._4

    def process_=(v: Handle): Unit  = ref._1 = v
    def thread_=(v: Handle): Unit   = ref._2 = v
    def processId_=(v: DWord): Unit = ref._3 = v
    def threadId_=(v: DWord): Unit  = ref._4 = v
  }
}
