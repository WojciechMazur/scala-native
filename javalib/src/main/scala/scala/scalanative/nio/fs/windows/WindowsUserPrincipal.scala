package scala.scalanative.nio.fs.windows

import java.nio.file.attribute._
import scalanative.unsafe._
import scalanative.unsigned._
import scalanative.windows._

object WindowsUserPrincipal {
  import SecurityBase._
  import MinWinBase._
  import WinBase._
  import HelperMethods._
  import Acl._

  case class User(sidString: String, accountName: String, sidType: SidNameUse)
      extends UserPrincipal {
    def getName(): String = accountName
  }
  class Group(sidString: String, accountName: String, sidType: SidNameUse)
      extends User(sidString, accountName, sidType)
      with GroupPrincipal

  def apply(sidRef: SIDPtr): UserPrincipal = {
    import SidNameUse._
    val sidString = {
      val sidCString = stackalloc[CString]
      if (!Sddl.convertSidToStringSidA(sidRef, sidCString)) {
        throw WindowsException("Unable to convert SID to string")
      }
      fromCString(!sidCString)
    }

    val (accountName, accountType) =
      try {
        val nameSize, domainSize = stackalloc[DWord]
        !nameSize = 255.toUInt
        !domainSize = 255.toUInt
        val nameRef   = stackalloc[Byte](!nameSize)
        val domainRef = stackalloc[Byte](!domainSize)
        val useRef    = stackalloc[SidNameUse]
        if (!WinBaseApi.lookupAccountSidA(systemName = null,
                                          sid = sidRef,
                                          name = nameRef,
                                          nameSize = nameSize,
                                          referencedDomainName = domainRef,
                                          referencedDomainNameSize = domainSize,
                                          use = useRef)) {
          throw WindowsException("Failed to lookup account info")
        }

        val accountName = fromCString(domainRef) + "\\" + fromCString(nameRef)
        (accountName, !useRef)
      } catch {
        case ex: WindowsException => (sidString, SidTypeUnknown)
      }

    val groupTypes = SidTypeGroup | SidTypeWellKnownGroup | SidTypeAlias
    val isGroup    = (accountType & groupTypes) != 0
    if (isGroup)
      new Group(sidString, accountName, accountType)
    else
      new User(sidString, accountName, accountType)
  }
}
