package scala.scalanative.windows

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import HandleApi.Handle

@extern()
object SecurityBaseApi {
  import SecurityBase._
  import winnt.TokenInformationClass

  @name("AccessCheck")
  def accessCheck(securityDescriptor: Ptr[SecurityDescriptor],
                  clientToken: Handle,
                  desiredAccess: DWord,
                  genericMapping: Ptr[GenericMapping],
                  privilegeSet: PrivilegeSetPtr,
                  privilegeSetLength: Ptr[DWord],
                  grantedAccess: Ptr[DWord],
                  accessStatus: Ptr[Boolean]): DWord =
    extern

  @name("DuplicateToken")
  def duplicateToken(existingToken: Handle,
                     impersonationLevel: CInt,
                     duplicateTokenHandle: Ptr[Handle]): Boolean = extern

  @name("FreeSid")
  def freeSid(sid: SIDPtr): Ptr[Byte] = extern

  @name("GetTokenInformation")
  def getTokenInformation(handle: Handle,
                          informationClass: TokenInformationClass.Type,
                          information: Ptr[Byte],
                          informationLength: DWord,
                          returnLength: Ptr[DWord]): Boolean = extern

  @name("MapGenericMask")
  def mapGenericMask(accessMask: Ptr[DWord],
                     genericMapping: Ptr[GenericMapping]): Unit = extern

  // utils
  @name("scalanative_win32_winnt_empty_priviliges_size")
  def emptyPriviligesSize(): CSize = extern
}

object SecurityBase {
  type AccessMask                = DWord
  type SecurityDescriptorControl = Word

  type SecurityDescriptor = CStruct7[Byte,
                                     Byte,
                                     SecurityDescriptorControl,
                                     SIDPtr,
                                     SIDPtr,
                                     ACLPtr,
                                     ACLPtr]
  implicit class SecurityDescriptorOps(ref: Ptr[SecurityDescriptor]) {
    def revision: Byte                     = ref._1
    def sbz1: Byte                         = ref._2
    def control: SecurityDescriptorControl = ref._3
    def owner: SIDPtr                      = ref._4
    def group: SIDPtr                      = ref._5
    def sAcl: ACLPtr                       = ref._6
    def dAcl: ACLPtr                       = ref._7

    def revision_=(v: Byte): Unit                     = ref._1 = v
    def sbz1_=(v: Byte): Unit                         = ref._2 = v
    def control_=(v: SecurityDescriptorControl): Unit = ref._3 = v
    def owner_=(v: SIDPtr): Unit                      = ref._4 = v
    def group_=(v: SIDPtr): Unit                      = ref._5 = v
    def sAcl_=(v: ACLPtr): Unit                       = ref._6 = v
    def dAcl_=(v: ACLPtr): Unit                       = ref._7 = v
  }

  type GenericMapping = CStruct4[AccessMask, AccessMask, AccessMask, AccessMask]
  implicit class GenericMappingOps(ref: Ptr[GenericMapping]) {
    def genericRead: AccessMask    = ref._1
    def genericWrite: AccessMask   = ref._2
    def genericExecute: AccessMask = ref._3
    def genericAll: AccessMask     = ref._4

    def genericRead_=(v: AccessMask): Unit    = ref._1 = v
    def genericWrite_=(v: AccessMask): Unit   = ref._2 = v
    def genericExecute_=(v: AccessMask): Unit = ref._3 = v
    def genericAll_=(v: AccessMask): Unit     = ref._4 = v
  }

  // Internal Windows structures, might have variable size and should not be modifed by the user
  type SIDPtr          = Ptr[Byte]
  type ACLPtr          = Ptr[Byte]
  type PrivilegeSetPtr = Ptr[Byte]

  @extern
  object AccessToken {

    /** Required to change the default owner, primary group, or DACL of an access token. */
    @name("scalanative_win32_access_token_adjust_default")
    def AdjustDefault(): DWord = extern

    /** Required to adjust the attributes of the groups in an access token. */
    @name("scalanative_win32_access_token_adjust_groups")
    def AdjustGroup(): DWord = extern

    /** Required to enable or disable the privileges in an access token. */
    @name("scalanative_win32_access_token_adjust_privileges")
    def AdjustPrivileges(): DWord = extern

    /** Required to adjust the session ID of an access token. The SE_TCB_NAME privilege is required. */
    @name("scalanative_win32_access_token_adjust_sessionid")
    def AdjustSessionId(): DWord = extern

    /** Required to attach a primary token to a process. The SE_ASSIGNPRIMARYTOKEN_NAME privilege is also required to accomplish this task. */
    @name("scalanative_win32_access_token_assign_primary")
    def AssignPrimary(): DWord = extern

    /** Required to duplicate an access token. */
    @name("scalanative_win32_access_token_duplicate")
    def Duplicate(): DWord = extern

    /** Combines STANDARD_RIGHTS_EXECUTE and TOKEN_IMPERSONATE. */
    @name("scalanative_win32_access_token_execute")
    def Execute(): DWord = extern

    /** Required to attach an impersonation access token to a process. */
    @name("scalanative_win32_access_token_impersonate")
    def Impersonate(): DWord = extern

    /** Required to query an access token. */
    @name("scalanative_win32_access_token_query")
    def Query(): DWord = extern

    /** Required to query the source of an access token. */
    @name("scalanative_win32_access_token_query_source")
    def QuerySource(): DWord = extern

    /** Combines STANDARD_RIGHTS_READ and TOKEN_QUERY. */
    @name("scalanative_win32_access_token_read")
    def Read(): DWord = extern

    /** Combines STANDARD_RIGHTS_WRITE, TOKEN_ADJUST_PRIVILEGES, TOKEN_ADJUST_GROUPS, and TOKEN_ADJUST_DEFAULT. */
    @name("scalanative_win32_access_token_write")
    def Write(): DWord = extern

    /** Combines all possible access rights for a token. */
    @name("scalanative_win32_access_token_all_access")
    def AllAccess(): DWord = extern
  }

  // Enum in Windows API
  @extern
  object SecurityImpersonationLevel {

    /** The server process cannot obtain identification information about the client,
     *  and it cannot impersonate the client. It is defined with no value given,
     * and thus, by ANSI C rules, defaults to a value of zero. */
    @name("scalanative_win32_security_impersonation_anonymous")
    def Anonymous(): CInt = extern

    /** The server process can obtain information about the client, such as security
     *  identifiers and privileges, but it cannot impersonate the client.
     *  This is useful for servers that export their own objects, for example,
     *  database products that export tables and views.
     *  Using the retrieved client-security information,
     *  the server can make access-validation decisions without being able to use other
     *  services that are using the client's security context. */
    @name("scalanative_win32_security_impersonation_identification")
    def Identification(): CInt = extern

    /** The server process can impersonate the client's security context on its local system.
     *  The server cannot impersonate the client on remote systems. */
    @name("scalanative_win32_security_impersonation_impersonation")
    def Impersonation(): CInt = extern

    /** The server process can impersonate the client's security context on remote systems. */
    @name("scalanative_win32_security_impersonation_delegation")
    def Delegation(): CInt = extern
  }
}
