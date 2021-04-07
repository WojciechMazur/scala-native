package scala.scalanative.windows.accctrl

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.windows._

object `package` {
  import Acl._
  import SecurityBaseApi._
  import SecurityBase._
  import WinBase._

  type ExplicitAccess = CStruct4[DWord, AccessMode.Type, DWord, TrusteeA]
  implicit class ExplicitAccessOps(ref: Ptr[ExplicitAccess]) {
    def accessPermisions: DWord     = ref._1
    def accessMode: AccessMode.Type = ref._2
    def inheritence: DWord          = ref._3
    def trustee: Ptr[TrusteeA]      = ref.at4

    def accessPermisions_=(v: DWord): Unit     = ref._1 = v
    def accessMode_=(v: AccessMode.Type): Unit = ref._2 = v
    def inheritence_=(v: DWord): Unit          = ref._3 = v
    def trustee_=(v: Ptr[TrusteeA]): Unit      = !ref.at4 = v
  }

  type TrusteeA = CStruct6[Ptr[Byte],
                           MultipleTruteeOperation.Type,
                           TrusteeForm.Type,
                           TrusteeType.Type,
                           Ptr[Byte],
                           CString]

  implicit class TrusteeAOps(ref: Ptr[TrusteeA]) {
    def multipleTrustee: Ptr[TrusteeA]                         = ref._1.asInstanceOf[Ptr[TrusteeA]]
    def multipleTrusteeOperation: MultipleTruteeOperation.Type = ref._2
    def trusteeForm: TrusteeForm.Type                          = ref._3
    def trusteeType: TrusteeType.Type                          = ref._4
    // union type
    def strName: CString = ref._5.asInstanceOf[CString]
    def sid: SIDPtr      = ref._5.asInstanceOf[SIDPtr]
    def objectsAndSid: Ptr[ObjectsAndSid] =
      ref._5.asInstanceOf[Ptr[ObjectsAndSid]]
    def objectsAndName: Ptr[ObjectsAndName] =
      ref._5.asInstanceOf[Ptr[ObjectsAndName]]

    def multipleTrustee_=(v: Ptr[TrusteeA]): Unit = {
      ref._1 = v.asInstanceOf[Ptr[Byte]]
    }
    def multipleTrusteeOperation_=(v: MultipleTruteeOperation.Type): Unit = {
      ref._2 = v
    }
    def trusteeForm_=(v: TrusteeForm.Type): Unit = { ref._3 = v }
    def trusteeType_=(v: TrusteeType.Type): Unit = { ref._4 = v }
    def strName_=(v: CString): Unit              = { ref._5 = v.asInstanceOf[Ptr[Byte]] }
    def sid_=(v: SIDPtr): Unit                   = { ref._5 = v.asInstanceOf[Ptr[Byte]] }
    def objectsAndSid_=(v: Ptr[ObjectsAndSid]): Unit = {
      ref._5 = v.asInstanceOf[Ptr[Byte]]
    }
    def objectsAndName_=(v: Ptr[ObjectsAndName]): Unit = {
      ref._5 = v.asInstanceOf[Ptr[Byte]]
    }
  }

  type GUID          = CStruct4[UInt, UShort, UShort, CArray[UByte, Nat._8]]
  type ObjectsAndSid = CStruct4[ObjectsPresent, GUID, GUID, SIDPtr]
  implicit class ObjectsAndSidOps(ref: Ptr[ObjectsAndSid]) {
    def objectsPresent: ObjectsPresent     = ref._1
    def objectTypeGuid: Ptr[GUID]          = ref.at2
    def inheritedObjectTypeGuid: Ptr[GUID] = ref.at3
    def sid: SIDPtr                        = ref._4

    def objectsPresent_=(v: ObjectsPresent): Unit     = ref._1 = v
    def objectTypeGuid_=(v: Ptr[GUID]): Unit          = !ref.at2 = v
    def inheritedObjectTypeGuid_=(v: Ptr[GUID]): Unit = !ref.at3 = v
    def sid_=(v: SIDPtr): Unit                        = ref._4 = v
  }

  type ObjectsAndName =
    CStruct5[ObjectsPresent, SecurityObjectType, CString, CString, CString]
  implicit class ObjectsAndNameOps(ref: Ptr[ObjectsAndName]) {
    def objectsPresent: ObjectsPresent   = ref._1
    def objectType: SecurityObjectType   = ref._2
    def objectTypeName: CString          = ref._3
    def inheritedObjectTypeName: CString = ref._4
    def strName: CString                 = ref._5

    def objectsPresent_=(v: ObjectsPresent): Unit   = ref._1 = v
    def objectType_=(v: SecurityObjectType): Unit   = ref._2 = v
    def objectTypeName_=(v: CString): Unit          = ref._3 = v
    def inheritedObjectTypeName_=(v: CString): Unit = ref._4 = v
    def strName_=(v: CString): Unit                 = ref._5 = v
  }

  type ObjectsPresent = DWord
  object ObjectsPresent {
    final val Object          = 1.toUInt
    final val InheritedObject = 2.toUInt
  }

}

@extern
object AccessMode {
  type Type = CInt
  @name("scalanative_win32_accctrl_not_used_access")
  def NotUsedAccess: CInt = extern
  @name("scalanative_win32_accctrl_grant_access")
  def GrantAccess: CInt = extern
  @name("scalanative_win32_accctrl_set_access")
  def SetAccess: CInt = extern
  @name("scalanative_win32_accctrl_deny_access")
  def DenyAccess: CInt = extern
  @name("scalanative_win32_accctrl_revoke_access")
  def RevokeAccess: CInt = extern
  @name("scalanative_win32_accctrl_set_audit_success")
  def SetAuditSuccess: CInt = extern
  @name("scalanative_win32_accctrl_set_audit_failure")
  def SetAuditFailure: CInt = extern
}

@extern
object MultipleTruteeOperation {
  type Type = CInt
  @name("scalanative_win32_accctrl_no_multiple_trustee")
  def NoMultipleTrustee: CInt = extern
  @name("scalanative_win32_accctrl_trustee_is_impersonate")
  def TrusteeIsImpersonate: CInt = extern
}

@extern
object TrusteeForm {
  type Type = CInt
  @name("scalanative_win32_accctrl_trustee_is_sid")
  def TrusteeIsSid: CInt = extern
  @name("scalanative_win32_accctrl_trustee_is_name")
  def TrusteeIsName: CInt = extern
  @name("scalanative_win32_accctrl_trustee_bad_form")
  def TrusteeBadForm: CInt = extern
  @name("scalanative_win32_accctrl_trustee_is_objects_and_sid")
  def TrusteeIsObjectsAndSid: CInt = extern
  @name("scalanative_win32_accctrl_trustee_is_objects_and_name")
  def TrusteeIsObjectsAndName: CInt = extern
}

@extern
object TrusteeType {
  type Type = CInt
  @name("scalanative_win32_accctrl_trustee_is_unknown")
  def TrusteeIsUnknown: CInt = extern
  @name("scalanative_win32_accctrl_trustee_is_user")
  def TrusteeIsUser: CInt = extern
  @name("scalanative_win32_accctrl_trustee_is_group")
  def TrusteeIsGroup: CInt = extern
  @name("scalanative_win32_accctrl_trustee_is_domain")
  def TrusteeIsDomain: CInt = extern
  @name("scalanative_win32_accctrl_trustee_is_alias")
  def TrusteeIsAlias: CInt = extern
  @name("scalanative_win32_accctrl_trustee_is_well_known_group")
  def TrusteeIsWellKnownGroup: CInt = extern
  @name("scalanative_win32_accctrl_trustee_is_deleted")
  def TrusteeIsDeleted: CInt = extern
  @name("scalanative_win32_accctrl_trustee_is_invalid")
  def TrusteeIsInvalid: CInt = extern
  @name("scalanative_win32_accctrl_trustee_is_computer")
  def TrusteeIsComputer: CInt = extern
}
@extern
object InheritFlags {
  @name("scalanative_win32_winnt_object_inherit_ace")
  def ObjectInheritAce: DWord = extern
  @name("scalanative_win32_winnt_container_inherit_ace")
  def containerInheritAce: DWord = extern
  @name("scalanative_win32_winnt_no_propagate_inherit_ace")
  def NoPropagateInheritAce: DWord = extern
  @name("scalanative_win32_winnt_inherit_only_ace")
  def InheritOnlyAce: DWord = extern
  @name("scalanative_win32_winnt_inherited_ace")
  def InheritedAce: DWord = extern
  @name("scalanative_win32_winnt_valid_inherit_flags")
  def ValidInheritFlags: DWord = extern
}
