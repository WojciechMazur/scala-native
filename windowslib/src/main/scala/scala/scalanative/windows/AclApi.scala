package scala.scalanative.windows

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

@extern()
object AclApi {
  import Acl._
  import SecurityBaseApi._
  import SecurityBase._
  import WinBase._
  import accctrl._

  @name("SetEntriesInAclA")
  def setEntriesInAclA(countOfExplicitEntries: ULong,
                       listOfExplicitEntries: Ptr[ExplicitAccess],
                       oldAcl: ACLPtr,
                       newAcl: Ptr[ACLPtr]): DWord = extern

  @name("GetNamedSecurityInfoA")
  def getNamedSecurityInfoA(
      objectName: CString,
      objectType: SecurityObjectType,
      securityInfo: SecurityInformation,
      sidOwner: Ptr[SIDPtr],
      sidGroup: Ptr[SIDPtr],
      dacl: Ptr[ACLPtr],
      sacl: Ptr[ACLPtr],
      securityDescriptor: Ptr[Ptr[SecurityDescriptor]]): DWord = extern

  @name("SetNamedSecurityInfoA")
  def setNamedSecurityInfoA(
      objectName: CString,
      objectType: SecurityObjectType,
      securityInfo: SecurityInformation,
      sidOwner: SIDPtr,
      sidGroup: SIDPtr,
      dacl: ACLPtr,
      sacl: ACLPtr
  ): DWord = extern
}

object Acl {
  type SecurityObjectType = CInt
  @extern
  object SecurityObjectType {
    @name("scalanative_win32_se_object_type_unknown_object_type")
    def UnknownObjectType: CInt = extern
    @name("scalanative_win32_se_object_type_file_object")
    def FileObject: CInt = extern
    @name("scalanative_win32_se_object_type_service")
    def Service: CInt = extern
    @name("scalanative_win32_se_object_type_printer")
    def Printer: CInt = extern
    @name("scalanative_win32_se_object_type_registry_key")
    def RegistryKey: CInt = extern
    @name("scalanative_win32_se_object_type_lmshare")
    def LMShare: CInt = extern
    @name("scalanative_win32_se_object_type_kernel_object")
    def KernelObject: CInt = extern
    @name("scalanative_win32_se_object_type_window_object")
    def WindowObject: CInt = extern
    @name("scalanative_win32_se_object_type_ds_object")
    def DSObject: CInt = extern
    @name("scalanative_win32_se_object_type_ds_object_all")
    def DSObjectAll: CInt = extern
    @name("scalanative_win32_se_object_type_provider_defined_object")
    def ProviderDefinedObject: CInt = extern
    @name("scalanative_win32_se_object_type_wmiguid_object")
    def WmiguidObject: CInt = extern
    @name("scalanative_win32_se_object_type_registry_wow64_32key")
    def RegistryWow64_32key: CInt = extern
    @name("scalanative_win32_se_object_type_registry_wow64_64key")
    def RegistryWow64_64key: CInt = extern
  }
}
