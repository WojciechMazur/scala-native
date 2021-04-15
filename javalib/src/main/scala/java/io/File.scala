package java.io

import java.nio.file.{FileSystems, Path}
import java.net.URI

import scala.annotation.tailrec
import scalanative.annotation.stub
import scalanative.posix.{fcntl, limits, unistd, utime}
import scalanative.posix.sys.stat
import scalanative.unsigned._
import scalanative.unsafe._
import scalanative.libc._, stdlib._, stdio._, string._
import scalanative.nio.fs.FileHelpers
import scalanative.runtime.{DeleteOnExit, Platform}
import scalanative.runtime.PlatformExt.isWindows
import unistd._
import scala.scalanative.windows
import scala.scalanative.windows.MinWinBase.{FileTime => WinFileTime}
import scala.scalanative.windows.{WinBaseApi, SecurityBaseApi}
import scala.scalanative.windows.HelperMethods._
import scala.scalanative.windows.SecurityBase._
import scala.scalanative.windows._
import scala.scalanative.windows.HandleApi.Handle
import scala.scalanative.windows.WinBase.SecurityInformation
import scala.scalanative.windows.SecurityBase._
import scala.scalanative.windows.accctrl._
import scala.scalanative.windows.winnt.{HelperMethods => WinNtHelpers, _}
import scala.scalanative.nio.fs.windows.WindowsException
import Acl.SecurityObjectType
import scala.scalanative.windows.File.FinalPathFlags
import scala.scalanative.windows.ErrorHandling.getLastError
import scala.scalanative.annotation.alwaysinline

class File(_path: String) extends Serializable with Comparable[File] {
  import File._

  if (_path == null) throw new NullPointerException()
  private val path: String = fixSlashes(_path)

  private[io] val properPath: String = File.properPath(path)
  private[io] val properPathBytes: Array[Byte] =
    File.properPath(path).getBytes("UTF-8")

  def this(parent: String, child: String) =
    this(
      Option(parent).map(p => p + File.separatorChar + child).getOrElse(child))

  def this(parent: File, child: String) =
    this(Option(parent).map(_.path).orNull, child)

  def this(uri: URI) =
    this(File.checkURI(uri).getPath())

  def compareTo(file: File): Int = {
    if (caseSensitive) getPath().compareTo(file.getPath())
    else getPath().compareToIgnoreCase(file.getPath())
  }

  def canExecute(): Boolean =
    Zone { implicit z =>
      if (isWindows) checkWindowsAccess(FileAccess.FILE_GENERIC_EXECUTE)
      else access(toCString(path), unistd.X_OK) == 0
    }

  def canRead(): Boolean =
    Zone { implicit z =>
      if (isWindows) checkWindowsAccess(FileAccess.FILE_GENERIC_READ)
      else access(toCString(path), unistd.R_OK) == 0
    }

  def canWrite(): Boolean =
    Zone { implicit z =>
      if (isWindows) checkWindowsAccess(FileAccess.FILE_GENERIC_WRITE)
      else access(toCString(path), unistd.W_OK) == 0
    }

  def setExecutable(executable: Boolean): Boolean =
    setExecutable(executable, ownerOnly = true)

  def setExecutable(executable: Boolean, ownerOnly: Boolean): Boolean = {
    if (isWindows) {
      val accessRights = FileAccess.FILE_GENERIC_EXECUTE
      updatePermissionsWindows(accessRights, executable, ownerOnly)
    } else {
      import stat._
      val mask = if (!ownerOnly) S_IXUSR | S_IXGRP | S_IXOTH else S_IXUSR
      updatePermissionsUnix(mask, executable)
    }
  }

  def setReadable(readable: Boolean): Boolean =
    setReadable(readable, ownerOnly = true)

  def setReadable(readable: Boolean, ownerOnly: Boolean): Boolean = {
    if (isWindows) {
      val accessRights = FileAccess.FILE_GENERIC_READ
      updatePermissionsWindows(accessRights, readable, ownerOnly)
    } else {
      import stat._
      val mask = if (!ownerOnly) S_IRUSR | S_IRGRP | S_IROTH else S_IRUSR
      updatePermissionsUnix(mask, readable)
    }
  }

  def setWritable(writable: Boolean): Boolean =
    setWritable(writable, ownerOnly = true)

  def setWritable(writable: Boolean, ownerOnly: Boolean = true): Boolean = {
    if (isWindows) {
      val accessRights = FileAccess.FILE_GENERIC_WRITE
      updatePermissionsWindows(accessRights, writable, ownerOnly)
    } else {
      import stat._
      val mask = if (!ownerOnly) S_IWUSR | S_IWGRP | S_IWOTH else S_IWUSR
      updatePermissionsUnix(mask, writable)
    }
  }

  private def updatePermissionsUnix(mask: stat.mode_t,
                                    grant: Boolean): Boolean =
    Zone { implicit z =>
      if (grant) {
        stat.chmod(toCString(path), accessMode() | mask) == 0
      } else {
        stat.chmod(toCString(path), accessMode() & (~mask)) == 0
      }
    }

  private def updatePermissionsWindows(accessRights: windows.DWord,
                                       grant: Boolean,
                                       ownerOnly: Boolean): Boolean =
    Zone { implicit z =>
      val filename              = toCString(path)
      val securityDescriptorPtr = stackalloc[Ptr[SecurityDescriptor]]
      val previousDacl, newDacl = stackalloc[ACLPtr]
      val usersGroupSid         = stackalloc[SIDPtr]

      val accessMode: AccessMode.Type =
        if (grant) AccessMode.GrantAccess
        else AccessMode.DenyAccess

      def getSecurityDescriptor() =
        AclApi.getNamedSecurityInfoA(
          filename,
          SecurityObjectType.FileObject,
          SecurityInformation.DACL,
          sidOwner = null,
          sidGroup = null,
          dacl = previousDacl,
          sacl = null,
          securityDescriptor = securityDescriptorPtr
        ) == 0.toUInt

      def setupNewAclEntry() = {
        val ea = alloc[ExplicitAccess]
        ea.accessPermisions = accessRights
        ea.accessMode = accessMode
        ea.inheritence = InheritFlags.NoPropagateInheritAce
        ea.trustee.trusteeForm = TrusteeForm.TrusteeIsSid

        if (ownerOnly) {
          withUserToken(AccessToken.Query) { userToken =>
            withTokenInformation(userToken, TokenInformationClass.User) {
              data: Ptr[SidAndAttributes] =>
                ea.trustee.trusteeType = TrusteeType.TrusteeIsUser
                ea.trustee.sid = data.sid
            }
          }
        } else {
          WinNtHelpers.setupUserGroupSid(usersGroupSid)
          ea.trustee.trusteeType = TrusteeType.TrusteeIsWellKnownGroup
          ea.trustee.sid = !usersGroupSid
        }

        AclApi.setEntriesInAclA(1.toUInt, ea, !previousDacl, newDacl) == 0.toUInt
      }

      def assignNewSecurityInfo() =
        AclApi.setNamedSecurityInfoA(filename,
                                     SecurityObjectType.FileObject,
                                     SecurityInformation.DACL,
                                     sidOwner = null,
                                     sidGroup = null,
                                     dacl = !newDacl,
                                     sacl = null) == 0.toUInt
      try {
        withLocalHandleCleanup(securityDescriptorPtr, previousDacl, newDacl) {
          getSecurityDescriptor() &&
          setupNewAclEntry() &&
          assignNewSecurityInfo()
        }
      } finally {
        SecurityBaseApi.freeSid(!usersGroupSid)
      }
    }

  def exists(): Boolean =
    Zone { implicit z =>
      val filename = toCString(path)
      if (isWindows) {
        val attrs      = FileApi.getFileAttributesA(filename)
        val pathExists = attrs != FileApi.InvalidFileAttributes
        val notSymLink = (attrs & FileAttributes.ReparsePoint) == 0.toUInt
        if (notSymLink) // fast path
          pathExists
        else {
          HelperMethods.withFile(
            filename,
            access = FileAccess.FILE_READ_ATTRIBUTES,
            allowInvalidHandle = true)(_ != HandleApi.InvalidHandleValue)
        }
      } else {
        access(filename, unistd.F_OK) == 0
      }
    }

  def toPath(): Path =
    FileSystems.getDefault().getPath(this.getPath(), Array.empty)

  def getPath(): String = path

  def delete(): Boolean =
    if (path.nonEmpty && isDirectory()) {
      deleteDirImpl()
    } else {
      deleteFileImpl()
    }

  private def deleteDirImpl(): Boolean = Zone { implicit z =>
    val filename = toCString(path)
    if (isWindows) {
      FileApi.removeDirectoryA(filename)
    } else
      remove(toCString(path)) == 0
  }

  private def deleteFileImpl(): Boolean = Zone { implicit z =>
    val filename = toCString(path)
    if (isWindows) {
      setReadOnlyWindows(enabled = false)
      FileApi.deleteFileA(filename)
    } else {
      unlink(filename) == 0
    }
  }

  override def equals(that: Any): Boolean =
    that match {
      case that: File if caseSensitive =>
        this.path == that.path
      case that: File =>
        this.path.toLowerCase == that.path.toLowerCase
      case _ =>
        false
    }

  def getAbsolutePath(): String = properPath

  def getAbsoluteFile(): File = new File(this.getAbsolutePath())

  def getCanonicalPath(): String =
    Zone { implicit z =>
      if (exists()) {
        fromCString(simplifyExistingPath(toCString(properPath)))
      } else {
        simplifyNonExistingPath(fromCString(resolve(toCString(properPath))))
      }
    }

  /**
   * Finds the canonical path for `path`, using `realpath`.
   * The file must exist, because the result of `realpath` doesn't
   * match that of Java on non-existing file.
   */
  private def simplifyExistingPath(path: CString)(implicit z: Zone): CString = {
    if (isWindows) {
      val resolvedName = alloc[Byte](FileApi.MaxAnsiPathSize)
      FileApi.getFullPathNameA(path,
                               FileApi.MaxAnsiPathSize,
                               resolvedName,
                               null)
      resolvedName
    } else {
      val resolvedName = alloc[Byte](limits.PATH_MAX.toUInt)
      realpath(path, resolvedName)
      resolvedName
    }
  }

  /**
   * Finds the canonical path for `path`.
   */
  private def simplifyNonExistingPath(path: String): String =
    path
      .split(separatorChar)
      .foldLeft(List.empty[String]) {
        case (acc, "..") => if (acc.isEmpty) List("..") else acc.tail
        case (acc, ".")  => acc
        case (acc, "")   => acc
        case (acc, seg)  => seg :: acc
      }
      .reverse
      .filterNot(_.isEmpty())
      .mkString(separator, separator, "")

  @throws(classOf[IOException])
  def getCanonicalFile(): File = new File(getCanonicalPath())

  def getName(): String = {
    val separatorIndex: Int = path.lastIndexOf(separatorChar)
    if (separatorIndex < 0) path
    else path.substring(separatorIndex + 1, path.length())
  }

  def getParent(): String =
    path.split(separatorChar).filterNot(_.isEmpty()) match {
      case Array() if !isAbsolute()  => null
      case Array(_) if !isAbsolute() => null
      case parts if !isAbsolute()    => parts.init.mkString(separator)
      case parts if isAbsolute() =>
        parts.init.mkString(separator, separator, "")
    }

  def getParentFile(): File = {
    val parent = getParent()
    if (parent == null) null
    else new File(parent)
  }

  override def hashCode(): Int =
    if (caseSensitive) path.hashCode ^ 1234321
    else path.toLowerCase.hashCode ^ 1234321

  def isAbsolute(): Boolean =
    File.isAbsolute(path)

  def isDirectory(): Boolean =
    Zone { implicit z =>
      if (isWindows)
        fileAttributeIsSet(FileAttributes.Directory)
      else
        stat.S_ISDIR(accessMode()) != 0
    }

  def isFile(): Boolean =
    Zone { implicit z =>
      if (isWindows)
        fileAttributeIsSet(FileAttributes.Normal) || !isDirectory()
      else
        stat.S_ISREG(accessMode()) != 0
    }

  def isHidden(): Boolean = {
    if (isWindows)
      fileAttributeIsSet(FileAttributes.Hidden)
    else getName().startsWith(".")
  }

  def lastModified(): Long =
    Zone { implicit z =>
      if (isWindows) {
        import HelperMethods._
        withFile(toCString(path), access = FileAccess.FILE_GENERIC_READ) {
          handle =>
            val lastModified = stackalloc[WinFileTime]
            FileApi.getFileTime(handle,
                                creationTime = null,
                                lastAccessTime = null,
                                lastWriteTime = lastModified)
            WinFileTime.toUnixEpochMillis(!lastModified)
        }
      } else {
        val buf = alloc[stat.stat]
        if (stat.stat(toCString(path), buf) == 0) {
          buf._8 * 1000L
        } else {
          0L
        }
      }
    }

  private def accessMode()(implicit z: Zone): stat.mode_t = {
    val buf = alloc[stat.stat]
    if (stat.stat(toCString(path), buf) == 0) {
      buf._13
    } else {
      0.toUInt
    }
  }

  @alwaysinline
  private def fileAttributeIsSet(attribute: windows.DWord): Boolean = Zone {
    implicit z =>
      (FileApi.getFileAttributesA(toCString(path)) & attribute) == attribute
  }

  def setLastModified(time: Long): Boolean =
    if (time < 0) {
      throw new IllegalArgumentException("Negative time")
    } else
      Zone { implicit z =>
        if (isWindows) {
          import HelperMethods._
          withFile(toCString(path), access = FileAccess.FILE_GENERIC_WRITE) {
            handle =>
              val lastModified = stackalloc[WinFileTime]
              !lastModified = WinFileTime.fromUnixEpoch(time)
              FileApi.setFileTime(handle,
                                  creationTime = null,
                                  lastAccessTime = null,
                                  lastWriteTime = lastModified)
          }
        } else {
          val statbuf = alloc[stat.stat]
          if (stat.stat(toCString(path), statbuf) == 0) {
            val timebuf = alloc[utime.utimbuf]
            timebuf._1 = statbuf._8
            timebuf._2 = time / 1000L
            utime.utime(toCString(path), timebuf) == 0
          } else {
            false
          }
        }
      }

  def setReadOnly(): Boolean =
    Zone { implicit z =>
      if (isWindows) setReadOnlyWindows(enabled = true)
      else {
        import stat._
        val mask =
          S_ISUID | S_ISGID | S_ISVTX | S_IRUSR | S_IXUSR | S_IRGRP | S_IXGRP | S_IROTH | S_IXOTH
        val newMode = accessMode() & mask
        chmod(toCString(path), newMode) == 0
      }
    }

  private def setReadOnlyWindows(enabled: Boolean)(implicit z: Zone) = {
    val filename          = toCString(path)
    val currentAttributes = FileApi.getFileAttributesA(filename)
    def newAttributes =
      if (enabled) currentAttributes | FileAttributes.ReadOnly
      else (currentAttributes & ~FileAttributes.ReadOnly)

    def setNewAttributes = FileApi.setFileAttributesA(filename, newAttributes)

    currentAttributes != FileApi.InvalidFileAttributes && setNewAttributes
  }

  def length(): Long = Zone { implicit z =>
    if (isWindows) {
      withFile(toCString(path), access = FileAccess.FILE_READ_ATTRIBUTES) {
        handle =>
          val size = stackalloc[windows.LargeInteger]
          if (FileApi.getFileSizeEx(handle, size)) (!size).toLong
          else 0L
      }
    } else {
      val buf = alloc[stat.stat]
      if (stat.stat(toCString(path), buf) == 0) {
        buf._6
      } else {
        0L
      }
    }
  }

  def list(): Array[String] =
    list(FilenameFilter.allPassFilter)

  def list(filter: FilenameFilter): Array[String] = {
    if (!isDirectory() || !canRead()) {
      null
    } else
      Zone { implicit z =>
        val elements =
          FileHelpers.list(properPath, (n, _) => n, allowEmpty = true)
        if (elements == null)
          Array.empty[String]
        else
          elements.filter(filter.accept(this, _))
      }
  }

  def listFiles(): Array[File] =
    listFiles(FilenameFilter.allPassFilter)

  def listFiles(filter: FilenameFilter): Array[File] =
    list(filter).map(new File(this, _))

  def listFiles(filter: FileFilter): Array[File] = {
    val filenameFilter =
      new FilenameFilter {
        override def accept(dir: File, name: String): Boolean =
          filter.accept(new File(dir, name))
      }
    listFiles(filenameFilter)
  }

  def mkdir(): Boolean =
    Zone { implicit z =>
      if (isWindows)
        FileApi.createDirectoryA(toCString(path), securityAttributes = null)
      else {
        val mode = octal("0777")
        stat.mkdir(toCString(path), mode) == 0
      }
    }

  def mkdirs(): Boolean =
    if (exists()) {
      false
    } else if (mkdir()) {
      true
    } else {
      val parent = getParentFile()
      if (parent == null) {
        false
      } else {
        parent.mkdirs() && mkdir()
      }
    }

  def createNewFile(): Boolean =
    FileHelpers.createNewFile(path, throwOnError = true)

  def renameTo(dest: File): Boolean =
    Zone { implicit z =>
      rename(toCString(properPath), toCString(dest.properPath)) == 0
    }

  override def toString(): String = path

  def deleteOnExit(): Unit = DeleteOnExit.addFile(this.getAbsolutePath())

  @stub
  def toURL(): java.net.URL = ???

  // Ported from Apache Harmony
  def toURI(): URI = {
    val path = getAbsolutePath().map {
      case '\\' => '/'
      case c    => c
    }
    if (!path.startsWith("/")) {
      // start with sep.
      new URI(
        "file",
        null,
        new StringBuilder(path.length + 1).append('/').append(path).toString,
        null,
        null)
    } else if (path.startsWith("//")) {
      // UNC path
      new URI("file", "", path, null)
    } else {
      new URI("file", null, path, null, null)
    }
  }

  private[this] def checkWindowsAccess(access: windows.DWord)(
      implicit zone: Zone): Boolean = {
    // based on this article https://blog.aaronballman.com/2011/08/how-to-check-access-rights/
    val accessStatus = stackalloc[Boolean]

    def withFileSecurityDescriptor(
        fn: Ptr[SecurityDescriptor] => Unit): Unit = {
      val securityDescriptorPtr = stackalloc[Ptr[SecurityDescriptor]]
      val filename              = toCString(path)
      val securityInfo =
        SecurityInformation.Owner() |
          SecurityInformation.Group |
          SecurityInformation.DACL()

      val result = AclApi.getNamedSecurityInfoA(
        filename,
        SecurityObjectType.FileObject,
        securityInfo,
        sidOwner = null,
        sidGroup = null,
        dacl = null,
        sacl = null,
        securityDescriptor = securityDescriptorPtr
      )
      if (result != 0.toUInt)
        throw WindowsException("Cannot retrive file security descriptor")
      try {
        fn(!securityDescriptorPtr)
      } finally {
        WinBaseApi.localFree(!securityDescriptorPtr)
      }
    }

    withFileSecurityDescriptor { securityDescriptor =>
      withImpersonatedToken { impersonatedToken =>
        val genericMapping = stackalloc[GenericMapping]
        genericMapping.genericRead = FileAccess.FILE_GENERIC_READ
        genericMapping.genericWrite = FileAccess.FILE_GENERIC_WRITE
        genericMapping.genericExecute = FileAccess.FILE_GENERIC_EXECUTE
        genericMapping.genericAll = FileAccess.GENERIC_ALL

        val accessMask = stackalloc[windows.DWord]
        !accessMask = access

        val privilegeSetLength = stackalloc[windows.DWord]
        !privilegeSetLength = SecurityBaseApi.emptyPriviligesSize().toUInt

        val privilegeSet = stackalloc[Byte](!privilegeSetLength)
        memset(privilegeSet, 0, !privilegeSetLength)

        val grantedAcccess = stackalloc[windows.DWord]
        !grantedAcccess = 0.toUInt

        SecurityBaseApi.mapGenericMask(accessMask, genericMapping)
        SecurityBaseApi.accessCheck(
          securityDescriptor = securityDescriptor,
          clientToken = impersonatedToken,
          desiredAccess = access,
          genericMapping = genericMapping,
          privilegeSet = privilegeSet,
          privilegeSetLength = privilegeSetLength,
          grantedAccess = grantedAcccess,
          accessStatus = accessStatus
        )
      }
    }
    
    !accessStatus
  }
}

object File {

  private val random = new java.util.Random()

  private def octal(v: String): UInt =
    Integer.parseInt(v, 8).toUInt

  private def getUserDir(): String =
    Zone { implicit z =>
      val res = if (isWindows) {
        val buffSize = WinBaseApi.getCurrentDirectoryA(0.toUInt, null)
        val buff     = alloc[CChar](buffSize + 1.toUInt)
        WinBaseApi.getCurrentDirectoryA(buffSize, buff)
        buff
      } else {
        val buff: CString = alloc[CChar](4096.toUInt)
        getcwd(buff, 4095.toUInt)
      }
      fromCString(res)
    }

  /** The purpose of this method is to take a path and fix the slashes up. This
   *  includes changing them all to the current platforms fileSeparator and
   *  removing duplicates.
   */
  // Ported from Apache Harmony
  private def fixSlashes(path: String): String = {
    val length    = path.length
    var newLength = 0

    var uncIndex =
      if (separatorChar == '/') 0 // UNIX world
      else if (length > 2 && path.charAt(1) == ':')
        2    // Windows, but starts with C:...
      else 1 // Possible UNC path name

    var foundSlash = false
    val newPath    = path.toCharArray()
    var i          = 0
    while (i < length) {
      val currentChar = newPath(i)

      if ((separatorChar == '\\' && currentChar == '\\') || currentChar == '/') {
        // UNC Name requires 2 leading slashes
        if ((foundSlash && i == uncIndex) || !foundSlash) {
          newPath(newLength) = separatorChar
          newLength += 1
          foundSlash = true
        }
      } else {
        // check for leading slashes before a drive
        if (currentChar == ':'
            && uncIndex > 0
            && (newLength == 2 || (newLength == 3 && newPath(1) == separatorChar))
            && newPath(0) == separatorChar) {
          newPath(0) = newPath(newLength - 1)
          newLength = 1
          // allow trailing slash after drive letter
          uncIndex = 2
        }
        newPath(newLength) = currentChar
        newLength += 1
        foundSlash = false
      }

      i += 1
    }

    if (foundSlash && (newLength > (uncIndex + 1) || (newLength == 2 && newPath(
          0) != separatorChar))) {
      newLength -= 1
    }

    new String(newPath, 0, newLength)
  }

  /**
   * Returns a string representing the proper path of this file. If this file
   * path is absolute, the user.dir property is not prepended, otherwise it
   * is.
   */
  // Ported from Apache Harmony
  private def properPath(path: String): String = {
    if (isAbsolute(path)) path
    else if (isWindows) Zone { implicit z =>
      val pathCString = toCString(path)
      val bufSize     = FileApi.getFullPathNameA(pathCString, 0.toUInt, null, null)
      val buf         = stackalloc[Byte](bufSize)
      if (FileApi.getFullPathNameA(pathCString,
                                   FileApi.MaxAnsiPathSize,
                                   buf,
                                   filePart = null) == 0.toUInt) {
        throw new IOException("Failed to resolve correct path")
      }
      fromCString(buf)
    }
    else {
      val userdir =
        Option(getUserDir())
          .getOrElse(
            throw new IOException(
              "getcwd() error in trying to get user directory."))

      if (path.isEmpty()) userdir
      else if (userdir.endsWith(separator)) userdir + path
      else userdir + separator + path
    }
  }

  def isAbsolute(path: String): Boolean =
    if (separatorChar == '\\') { // Windows. Must start with `\\` or `X:(\|/)`
      (path.length > 1 && path.startsWith(separator + separator)) ||
      (path.length > 2 && path(0).isLetter && path(1) == ':' && (path(2) == '/' || path(
        2) == '\\'))
    } else {
      path.length > 0 && path.startsWith(separator)
    }

  /**
   * Resolve a symbolic link. While the path resolves to an existing path,
   * keep resolving. If an absolute link is found, resolve the parent
   * directories if resolveAbsolute is true.
   */
  // Ported from Apache Harmony
  private def resolveLink(
      path: CString,
      resolveAbsolute: Boolean,
      restart: Boolean = false)(implicit z: Zone): CString = {
    val resolved =
      readLink(path) match {
        // path is not a symlink
        case null =>
          path

        // found an absolute path. continue from there.
        case link if link(0) == separatorChar =>
          resolveLink(link, resolveAbsolute, restart = resolveAbsolute)

        // found a relative path. append to the current path, and continue.
        case link =>
          val linkLength = strlen(link)
          val pathLength = strlen(path)
          val `1UL`      = 1.toULong
          var last       = pathLength - `1UL`
          while (path(last) != separatorChar) last -= `1UL`
          last += `1UL`

          // previous path up to last /, plus result of resolving the link.
          val newPathLength = last + linkLength + `1UL`
          val newPath       = alloc[Byte](newPathLength)
          strncpy(newPath, path, last)
          strncat(newPath, link, linkLength)

          resolveLink(newPath, resolveAbsolute, restart)
      }

    if (restart) resolve(resolved)
    else resolved
  }

  @tailrec private def resolve(path: CString, start: UInt = 0.toUInt)(
      implicit z: Zone): CString = {
    val partSize =
      if (isWindows) FileApi.MaxAnsiPathSize
      else limits.PATH_MAX.toUInt
    val part: CString = alloc[Byte](partSize)
    val `1U`          = 1.toUInt
    // Find the next separator
    var i = start
    while (i < strlen(path) && path(i) != separatorChar) i += `1U`

    if (i == strlen(path)) resolveLink(path, resolveAbsolute = true)
    else {
      // copy path from start to next separator.
      // and resolve that subpart.
      strncpy(part, path, i + `1U`)

      val resolved = resolveLink(part, resolveAbsolute = true)

      strcpy(part, resolved)
      strcat(part, path + i + `1U`)

      if (strncmp(resolved, path, i + `1U`) == 0) {
        // Nothing changed. Continue from the next segment.
        resolve(part, i + `1U`)
      } else {
        // The path has changed. Start over.
        resolve(part, 0.toUInt)
      }
    }

  }

  /**
   * If `link` is a symlink, follows it and returns the path pointed to.
   * Otherwise, returns `None`.
   */
  private def readLink(link: CString)(implicit z: Zone): CString = {
    val bufferSize =
      if (isWindows) FileApi.MaxAnsiPathSize
      else limits.PATH_MAX.toUInt
    val buffer: CString = alloc[Byte](bufferSize)

    if (isWindows) {
      withFile(link, access = FileAccess.FILE_GENERIC_READ) { fileHandle =>
        val finalPathFlags = FinalPathFlags.FileNameNormalized
        val pathLength = FileApi.getFinalPathNameByHandleA(
          fileHandle,
          buffer = buffer,
          bufferSize = bufferSize,
          flags = finalPathFlags
        )
        if (pathLength == 0) null
        else buffer
      }
    } else {
      readlink(link, buffer, bufferSize - 1.toUInt) match {
        case -1 =>
          null
        case read =>
          // readlink doesn't null-terminate the result.
          buffer(read) = 0.toByte
          buffer
      }
    }
  }

  val pathSeparatorChar: Char        = if (Platform.isWindows()) ';' else ':'
  val pathSeparator: String          = pathSeparatorChar.toString
  val separatorChar: Char            = if (Platform.isWindows()) '\\' else '/'
  val separator: String              = separatorChar.toString
  private var counter: Int           = 0
  private var counterBase: Int       = 0
  private val caseSensitive: Boolean = !Platform.isWindows()

  def listRoots(): Array[File] =
    if (Platform.isWindows()) ???
    else {
      var array = new Array[File](1)
      array(0) = new File("/")
      return array
    }

  @throws(classOf[IOException])
  def createTempFile(prefix: String, suffix: String): File =
    createTempFile(prefix, suffix, null)

  @throws(classOf[IOException])
  def createTempFile(prefix: String, suffix: String, directory: File): File =
    FileHelpers.createTempFile(prefix,
                               suffix,
                               directory,
                               minLength = true,
                               throwOnError = true)

  // Ported from Apache Harmony
  private def checkURI(uri: URI): URI = {
    def throwExc(msg: String): Unit =
      throw new IllegalArgumentException(s"$msg: $uri")
    def compMsg(comp: String): String =
      s"Found $comp component in URI"

    if (!uri.isAbsolute()) {
      throwExc("URI is not absolute")
    } else if (!uri.getRawSchemeSpecificPart().startsWith("/")) {
      throwExc("URI is not hierarchical")
    } else if (uri.getScheme() == null || !(uri.getScheme() == "file")) {
      throwExc("Expected file scheme in URI")
    } else if (uri.getRawPath() == null || uri.getRawPath().length() == 0) {
      throwExc("Expected non-empty path in URI")
    } else if (uri.getRawAuthority() != null) {
      throwExc(compMsg("authority"))
    } else if (uri.getRawQuery() != null) {
      throwExc(compMsg("query"))
    } else if (uri.getRawFragment() != null) {
      throwExc(compMsg("fragment"))
    }
    uri
    // else URI is ok
  }
}
