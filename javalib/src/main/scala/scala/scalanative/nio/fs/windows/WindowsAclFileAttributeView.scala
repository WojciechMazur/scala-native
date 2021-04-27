package scala.scalanative.nio.fs.windows

import java.util.{HashMap, HashSet, Set}
import java.util.concurrent.TimeUnit
import java.nio.file.{LinkOption, Path}
import java.nio.file.attribute._

import scalanative.unsigned._
import scalanative.unsafe._
import scalanative.libc._
import scala.scalanative.windows._
import java.{util => ju}

class WindowsAclFileAttributeView(path: Path, options: Array[LinkOption])
    extends AclFileAttributeView {
  import SecurityBase._
  import MinWinBase._
  import WinBase._
  import HelperMethods._
  import Acl._

  def name(): String = "acl"

  def getOwner(): UserPrincipal =
    Zone { implicit z =>
      val filename = toCWideStringUTF16LE(path.toString)
      val ownerSid = stackalloc[SIDPtr]

      if (AclApi.GetNamedSecurityInfoW(
            filename,
            SecurityObjectType.FileObject,
            SecurityInformation.Owner,
            sidOwner = ownerSid,
            sidGroup = null,
            dacl = null,
            sacl = null,
            securityDescriptor = null
          ) != 0.toUInt) {
        throw WindowsException("Failed to get ownership info")
      }
      WindowsUserPrincipal(!ownerSid)
    }

  def setOwner(owner: UserPrincipal): Unit = Zone { implicit z =>
    val filename = toCWideStringUTF16LE(path.toString)

    val sidCString = owner match {
      case WindowsUserPrincipal.User(sidString, _, _) =>
        toCWideStringUTF16LE(sidString)
      case _ =>
        throw WindowsException(
          "Unsupported user principal type " + owner.getClass.getName)
    }
    val newOwnerSid = stackalloc[SIDPtr]

    if (!Sddl.ConvertStringSidToSidW(sidCString, newOwnerSid)) {
      throw WindowsException("Cannot convert user principal to sid")
    }

    withLocalHandleCleanup(newOwnerSid) {
      if (AclApi.SetNamedSecurityInfoW(
            filename,
            SecurityObjectType.FileObject,
            SecurityInformation.Owner,
            sidOwner = !newOwnerSid,
            sidGroup = null,
            dacl = null,
            sacl = null
          ) != 0.toUInt) {
        throw WindowsException("Failed to set new owner")
      }
    }
  }
  def getAcl(): ju.List[AclEntry]        = ???
  def setAcl(x: ju.List[AclEntry]): Unit = ???
}
