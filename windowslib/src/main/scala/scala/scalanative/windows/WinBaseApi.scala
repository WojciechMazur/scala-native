package scala.scalanative.windows

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import HandleApi.Handle

@link("Advapi32")
@extern()
object WinBaseApi {
  import WinBase._
  import SecurityBase._

  type CallbackContext     = Ptr[Byte]
  type WaitOrTimerCallback = CFuncPtr2[CallbackContext, Boolean, Unit]
  type LocalHandle         = Ptr[_]

  def CreateHardLinkW(linkFileName: CWString,
                      existingFileName: CWString,
                      securityAttributes: SecurityAttributes): Boolean = extern

  def CreateSymbolicLinkW(symlinkFileName: CWString,
                          targetFileName: CWString,
                          flags: DWord): Boolean = extern
  def FormatMessageA(flags: DWord,
                     source: Ptr[Byte],
                     messageId: DWord,
                     languageId: DWord,
                     buffer: Ptr[CWString],
                     size: DWord,
                     arguments: CVarArgList): DWord = extern

  def FormatMessageW(flags: DWord,
                     source: Ptr[Byte],
                     messageId: DWord,
                     languageId: DWord,
                     buffer: Ptr[CWString],
                     size: DWord,
                     arguments: CVarArgList): DWord = extern
  def GetCurrentDirectoryA(bufferLength: DWord, buffer: CString): DWord = extern
  def GetCurrentDirectoryW(bufferLength: DWord, buffer: CWString): DWord =
    extern
  def GetVolumePathNameW(filename: CWString,
                         volumePathName: CWString,
                         bufferLength: DWord): Boolean = extern

  def GetFileSecurityW(filename: CWString,
                       requestedInformation: SecurityInformation,
                       securityDescriptor: Ptr[SecurityDescriptor],
                       length: DWord,
                       lengthNeeded: Ptr[DWord]): Boolean =
    extern

  def LocalFree(ref: LocalHandle): LocalHandle = extern
  def LookupAccountSidA(systemName: Ptr[CString],
                        sid: SIDPtr,
                        name: CString,
                        nameSize: Ptr[DWord],
                        referencedDomainName: CString,
                        referencedDomainNameSize: Ptr[DWord],
                        use: Ptr[SidNameUse]): Boolean = extern
  def LookupAccountSidW(systemName: Ptr[CWString],
                        sid: SIDPtr,
                        name: CWString,
                        nameSize: Ptr[DWord],
                        referencedDomainName: CWString,
                        referencedDomainNameSize: Ptr[DWord],
                        use: Ptr[SidNameUse]): Boolean = extern
  def MoveFileExA(existingFileName: CString,
                  newFileName: CString,
                  flags: DWord): Boolean = extern

  def MoveFileExW(existingFileName: CWString,
                  newFileName: CWString,
                  flags: DWord): Boolean = extern

  def RegisterWaitForSingleObject(retHandle: Ptr[Handle],
                                  ref: Handle,
                                  callbackFn: WaitOrTimerCallback,
                                  context: Ptr[Byte],
                                  miliseconds: DWord,
                                  flags: DWord): Boolean = extern

  def UnregisterWait(handle: Handle): Boolean = extern

  @name("scalanative_win32_default_language")
  final def DefaultLangugageId(): DWord = extern
}

object WinBase {
  object RegisterWaitObjectFlags {
    final val ExecuteDefault            = 0x00000000.toUInt
    final val ExecuteIOThread           = 0x00000001.toUInt
    final val ExecuteInPersistantThread = 0x00000080.toUInt
    final val ExecuteInWaitThread       = 0x00000004.toUInt
    final val ExecuteLongFunction       = 0x00000010.toUInt
    final val ExecuteOnlyOnce           = 0x00000008.toUInt
    final val TransferImpersonation     = 0x00000100.toUInt
  }

  object MoveFileFlags {
    final val MOVEFILE_REPLACE_EXISTING      = 0x1.toUInt
    final val MOVEFILE_COPY_ALLOWED          = 0x2.toUInt
    final val MOVEFILE_DELAY_UNTIL_REBOOT    = 0x4.toUInt
    final val MOVEFILE_WRITE_THROUGH         = 0x8.toUInt
    final val MOVEFILE_CREATE_HARDLINK       = 0x10.toUInt
    final val MOVEFILE_FAIL_IF_NOT_TRACKABLE = 0x20.toUInt
  }

  type SecurityInformation = DWord
  @extern
  object SecurityInformation {
    @name("scalanative_win32_security_information_attribute")
    def Attribute(): DWord = extern
    @name("scalanative_win32_security_information_backup")
    def Backup(): DWord = extern
    @name("scalanative_win32_security_information_owner")
    def Owner(): DWord = extern
    @name("scalanative_win32_security_information_group")
    def Group(): DWord = extern
    @name("scalanative_win32_security_information_label")
    def Label(): DWord = extern
    @name("scalanative_win32_security_information_scope")
    def Scope(): DWord = extern

    @name("scalanative_win32_security_information_dacl")
    def DACL(): DWord = extern
    @name("scalanative_win32_security_information_protected_dacl")
    def ProtectedDACL(): DWord = extern
    @name("scalanative_win32_security_information_unprotected_dacl")
    def UnprotectedDACL(): DWord = extern

    @name("scalanative_win32_security_information_sacl")
    def SACL(): DWord = extern
    @name("scalanative_win32_security_information_protected_sacl")
    def ProtectedSACL(): DWord = extern
    @name("scalanative_win32_security_information_unprotected_sacl")
    def UnprotectedSACL(): DWord = extern
  }

  object SymbolicLinkFlags {
    final val File                    = 0.toUInt
    final val Directory               = 0x01.toUInt
    final val AllowUnprivilegedCreate = 0x02.toUInt
  }

  type SidNameUse = CInt
  @extern
  object SidNameUse {
    @name("scalanative_win32_winnt_sid_name_use_sidtypeuser")
    def SidTypeUser: SidNameUse = extern
    @name("scalanative_win32_winnt_sid_name_use_sidtypegroup")
    def SidTypeGroup: SidNameUse = extern
    @name("scalanative_win32_winnt_sid_name_use_sidtypedomain")
    def SidTypeDomain: SidNameUse = extern
    @name("scalanative_win32_winnt_sid_name_use_sidtypealias")
    def SidTypeAlias: SidNameUse = extern
    @name("scalanative_win32_winnt_sid_name_use_sidtypewellknowngroup")
    def SidTypeWellKnownGroup: SidNameUse = extern
    @name("scalanative_win32_winnt_sid_name_use_sidtypedeletedaccount")
    def SidTypeDeletedAccount: SidNameUse = extern
    @name("scalanative_win32_winnt_sid_name_use_sidtypeinvalid")
    def SidTypeInvalid: SidNameUse = extern
    @name("scalanative_win32_winnt_sid_name_use_sidtypeunknown")
    def SidTypeUnknown: SidNameUse = extern
    @name("scalanative_win32_winnt_sid_name_use_sidtypecomputer")
    def SidTypeComputer: SidNameUse = extern
    @name("scalanative_win32_winnt_sid_name_use_sidtypelabel")
    def SidTypeLabel: SidNameUse = extern
    @name("scalanative_win32_winnt_sid_name_use_sidtypelogonsession")
    def SidTypeLogonSession: SidNameUse = extern
  }

  object FormatMessageFlags {
    final val FORMAT_MESSAGE_ALLOCATE_BUFFER: DWord = 0x00000100.toUInt
    final val FORMAT_MESSAGE_IGNORE_INSERTS: DWord  = 0x00000200.toUInt
    final val FORMAT_MESSAGE_FROM_STRING: DWord     = 0x00000400.toUInt
    final val FORMAT_MESSAGE_FROM_HMODULE: DWord    = 0x00000800.toUInt
    final val FORMAT_MESSAGE_FROM_SYSTEM: DWord     = 0x00001000.toUInt
    final val FORMAT_MESSAGE_ARGUMENT_ARRAY: DWord  = 0x00002000.toUInt
  }

}
