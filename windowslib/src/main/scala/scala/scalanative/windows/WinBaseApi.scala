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

  @name("CreateHardLinkA")
  def createHardLinkA(linkFileName: CString,
                      existingFileName: CString,
                      securityAttributes: SecurityAttributes): Boolean = extern

  @name("CreateSymbolicLinkA")
  def createSymbolicLinkA(symlinkFileName: CString,
                          targetFileName: CString,
                          flags: DWord): Boolean = extern

  @name("GetCurrentDirectoryA")
  def getCurrentDirectoryA(bufferLength: DWord, buffer: CString): DWord = extern

  @name("GetVolumePathNameA")
  def getVolumePathNameA(filename: CString,
                         volumePathName: CString,
                         bufferLength: DWord): Boolean = extern

  @name("GetFileSecurityA")
  def getFileSecurityA(filename: CString,
                       requestedInformation: SecurityInformation,
                       securityDescriptor: Ptr[SecurityDescriptor],
                       length: DWord,
                       lengthNeeded: Ptr[DWord]): Boolean =
    extern

  @name("LocalFree")
  def localFree(ref: LocalHandle): LocalHandle = extern

  @name("LookupAccountSidA")
  def lookupAccountSidA(systemName: Ptr[CString],
                        sid: SIDPtr,
                        name: CString,
                        nameSize: Ptr[DWord],
                        referencedDomainName: CString,
                        referencedDomainNameSize: Ptr[DWord],
                        use: Ptr[SidNameUse]): Boolean = extern

  @name("RegisterWaitForSingleObject")
  def registerWaitForSingleObject(retHandle: Ptr[Handle],
                                  ref: Handle,
                                  callbackFn: WaitOrTimerCallback,
                                  context: Ptr[Byte],
                                  miliseconds: DWord,
                                  flags: DWord): Boolean = extern

  @name("UnregisterWait")
  def unregisterWait(handle: Handle): Boolean = extern
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

}
