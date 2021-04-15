package scala.scalanative.windows.winnt

import scalanative.unsafe._
import scalanative.windows.DWord

@link("Advapi32")
@extern
object TokenInformationClass {
  type Type = DWord

  @name("scalanative_win32_winnt_token_info_class_user")
  def User: Type = extern
  @name("scalanative_win32_winnt_token_info_class_groups")
  def Groups: Type = extern
  @name("scalanative_win32_winnt_token_info_class_privileges")
  def Privileges: Type = extern
  @name("scalanative_win32_winnt_token_info_class_owner")
  def Owner: Type = extern
  @name("scalanative_win32_winnt_token_info_class_primarygroup")
  def PrimaryGroup: Type = extern
  @name("scalanative_win32_winnt_token_info_class_defaultdacl")
  def DefaultDacl: Type = extern
  @name("scalanative_win32_winnt_token_info_class_source")
  def Source: Type = extern
  @name("scalanative_win32_winnt_token_info_class_type")
  def Type: Type = extern
  @name("scalanative_win32_winnt_token_info_class_impersonationlevel")
  def ImpersonationLevel: Type = extern
  @name("scalanative_win32_winnt_token_info_class_statistics")
  def Statistics: Type = extern
  @name("scalanative_win32_winnt_token_info_class_restrictedsids")
  def RestrictedSids: Type = extern
  @name("scalanative_win32_winnt_token_info_class_sessionid")
  def SessionId: Type = extern
  @name("scalanative_win32_winnt_token_info_class_groupsandprivileges")
  def GroupsAndPrivileges: Type = extern
  @name("scalanative_win32_winnt_token_info_class_sessionreference")
  def SessionReference: Type = extern
  @name("scalanative_win32_winnt_token_info_class_sandboxinert")
  def SandBoxInert: Type = extern
  @name("scalanative_win32_winnt_token_info_class_auditpolicy")
  def AuditPolicy: Type = extern
  @name("scalanative_win32_winnt_token_info_class_origin")
  def Origin: Type = extern
  @name("scalanative_win32_winnt_token_info_class_elevationtype")
  def ElevationType: Type = extern
  @name("scalanative_win32_winnt_token_info_class_linkedtoken")
  def LinkedToken: Type = extern
  @name("scalanative_win32_winnt_token_info_class_elevation")
  def Elevation: Type = extern
  @name("scalanative_win32_winnt_token_info_class_hasrestrictions")
  def HasRestrictions: Type = extern
  @name("scalanative_win32_winnt_token_info_class_accessinformation")
  def AccessInformation: Type = extern
  @name("scalanative_win32_winnt_token_info_class_virtualizationallowed")
  def VirtualizationAllowed: Type = extern
  @name("scalanative_win32_winnt_token_info_class_virtualizationenabled")
  def VirtualizationEnabled: Type = extern
  @name("scalanative_win32_winnt_token_info_class_integritylevel")
  def IntegrityLevel: Type = extern
  @name("scalanative_win32_winnt_token_info_class_uiaccess")
  def UIAccess: Type = extern
  @name("scalanative_win32_winnt_token_info_class_mandatorypolicy")
  def MandatoryPolicy: Type = extern
  @name("scalanative_win32_winnt_token_info_class_logonsid")
  def LogonSid: Type = extern
  @name("scalanative_win32_winnt_token_info_class_isappcontainer")
  def IsAppContainer: Type = extern
  @name("scalanative_win32_winnt_token_info_class_capabilities")
  def Capabilities: Type = extern
  @name("scalanative_win32_winnt_token_info_class_appcontainersid")
  def AppContainerSid: Type = extern
  @name("scalanative_win32_winnt_token_info_class_appcontainernumber")
  def AppContainerNumber: Type = extern
  @name("scalanative_win32_winnt_token_info_class_userclaimattributes")
  def UserClaimAttributes: Type = extern
  @name("scalanative_win32_winnt_token_info_class_deviceclaimattributes")
  def DeviceClaimAttributes: Type = extern
  @name(
    "scalanative_win32_winnt_token_info_class_restricteduserclaimattributes")
  def RestrictedUserClaimAttributes: Type = extern
  @name(
    "scalanative_win32_winnt_token_info_class_restricteddeviceclaimattributes")
  def RestrictedDeviceClaimAttributes: Type = extern
  @name("scalanative_win32_winnt_token_info_class_devicegroups")
  def DeviceGroups: Type = extern
  @name("scalanative_win32_winnt_token_info_class_restricteddevicegroups")
  def RestrictedDeviceGroups: Type = extern
  @name("scalanative_win32_winnt_token_info_class_securityattributes")
  def SecurityAttributes: Type = extern
  @name("scalanative_win32_winnt_token_info_class_isrestricted")
  def IsRestricted: Type = extern
  @name("scalanative_win32_winnt_token_info_class_processtrustlevel")
  def ProcessTrustLevel: Type = extern
  @name("scalanative_win32_winnt_token_info_class_privatenamespace")
  def PrivateNameSpace: Type = extern
  @name("scalanative_win32_winnt_token_info_class_singletonattributes")
  def SingletonAttributes: Type = extern
  @name("scalanative_win32_winnt_token_info_class_bnoisolation")
  def BnoIsolation: Type = extern
  @name("scalanative_win32_winnt_token_info_class_childprocessflags")
  def ChildProcessFlags: Type = extern
  @name("scalanative_win32_winnt_token_info_class_islessprivilegedappcontainer")
  def IsLessPrivilegedAppContainer: Type = extern
  @name("scalanative_win32_winnt_token_info_class_issandboxed")
  def IsSandboxed: Type = extern
  @name("scalanative_win32_winnt_token_info_class_originatingprocesstrustlevel")
  def OriginatingProcessTrustLevel: Type = extern
  @name("scalanative_win32_winnt_token_info_class_infoclass_max")
  def MaxValue: Type = extern
}
