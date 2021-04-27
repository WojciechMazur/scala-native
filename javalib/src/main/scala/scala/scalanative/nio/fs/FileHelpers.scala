package scala.scalanative.nio.fs

import scalanative.unsafe._
import scalanative.unsigned._
import scalanative.libc._
import scalanative.posix.dirent._
import scalanative.posix.{errno => e, fcntl, unistd}, e._, unistd.access
import scalanative.unsafe._, stdlib._, stdio._, string._
import scalanative.runtime.PlatformExt.isWindows
import scala.collection.mutable.UnrolledBuffer
import scala.reflect.ClassTag
import java.io.{File, IOException}
import scala.scalanative.windows.{
  DWord,
  ShlwApi,
  FileApi,
  HandleApi,
  ErrorCodes,
  ErrorHandling,
  FileAccess,
  FileAttributes,
  FileDisposition,
  FileSharing,
  HelperMethods
}
import scala.scalanative.windows.File._
import scala.scalanative.nio.fs.unix.UnixException
import scala.scalanative.nio.fs.windows.WindowsException

import java.nio.file.attribute.FileAttribute

object FileHelpers {
  sealed trait FileType
  object FileType {
    case object Normal    extends FileType
    case object Directory extends FileType
    case object Link      extends FileType

    private[scalanative] def unixFileType(tpe: CInt) =
      if (tpe == DT_LNK) Link
      else if (tpe == DT_DIR) Directory
      else Normal

    private[scalanative] def windowsFileType(attributes: DWord) = {
      def isSet(attr: DWord): Boolean = {
        (attributes & attr) == attr
      }

      if (isSet(FileAttributes.ReparsePoint)) Link
      else if (isSet(FileAttributes.Directory)) Directory
      else Normal
    }
  }

  private[this] lazy val random = new scala.util.Random()
  final case class Dirent(name: String, tpe: CShort)
  def list[T: ClassTag](path: String,
                        f: (String, FileType) => T,
                        allowEmpty: Boolean = false): Array[T] = Zone {
    implicit z =>
      lazy val buffer = UnrolledBuffer.empty[T]

      def collectFile(name: String, fileType: FileType): Unit = {
        // java doesn't list '.' and '..', we filter them out.
        if (name != "." && name != "..") {
          buffer += f(name, fileType)
        }
      }

      def listUnix() = {
        val dir = opendir(toCString(path))

        if (dir == null) {
          if (!allowEmpty) throw UnixException(path, errno.errno)
          null
        } else {
          Zone { implicit z =>
            var elem = alloc[dirent]
            var res  = 0
            while ({ res = readdir(dir, elem); res == 0 }) {
              val name     = fromCString(elem._2.at(0))
              val fileType = FileType.unixFileType(elem._3)
              collectFile(name, fileType)
            }
            closedir(dir)
            res match {
              case e if e == EBADF || e == EFAULT || e == EIO =>
                throw UnixException(path, res)
              case _ => buffer.toArray
            }
          }
        }
      }

      def listWindows() = Zone { implicit z =>
        val searchPath = path + raw"\*"
        if (searchPath.length.toUInt > FileApi.MaxAnsiPathSize)
          throw new IOException("File name to long")

        val fileData = stackalloc[Win32FindDataA]
        val searchHandle =
          FileApi.FindFirstFileA(toCString(searchPath), fileData)
        if (searchHandle == HandleApi.InvalidHandleValue) {
          if (allowEmpty) Array.empty[T]
          else throw WindowsException.onPath(path)
        } else {
          try {
            while ({
              val name     = fromCString(fileData.fileName)
              val fileType = FileType.windowsFileType(fileData.fileAttributes)
              collectFile(name, fileType)
              FileApi.FindNextFileA(searchHandle, fileData)
            }) ()
          } finally {
            FileApi.FindClose(searchHandle)
          }

          ErrorHandling.GetLastError() match {
            case ErrorCodes.ERROR_NO_MORE_FILES => buffer.toArray
            case err                            => throw WindowsException.onPath(path)
          }
        }
      }

      if (isWindows) listWindows()
      else listUnix()
  }

  def createNewFile(path: String, throwOnError: Boolean = false): Boolean =
    if (path.isEmpty()) {
      throw new IOException("No such file or directory")
    } else if (exists(path)) {
      false
    } else
      Zone { implicit z =>
        if (isWindows) {
          HelperMethods.withFile(toCString(path),
                                 access = FileAccess.FILE_GENERIC_WRITE,
                                 shareMode = FileSharing.ShareAll,
                                 disposition = FileDisposition.CreateNew,
                                 allowInvalidHandle = true) { handle =>
            ErrorHandling.GetLastError() match {
              case ErrorCodes.ERROR_FILE_EXISTS => false
              case _                            => handle != HandleApi.InvalidHandleValue
            }
          }
        } else {
          fopen(toCString(path), c"w") match {
            case null =>
              if (throwOnError) throw UnixException(path, errno.errno)
              else false
            case fd => fclose(fd); exists(path)
          }
        }
      }

  def createTempFile(prefix: String,
                     suffix: String,
                     dir: File,
                     minLength: Boolean,
                     throwOnError: Boolean = false): File =
    if (prefix == null) throw new NullPointerException
    else if (minLength && prefix.length < 3)
      throw new IllegalArgumentException("Prefix string too short")
    else {
      val tmpDir       = Option(dir).fold(tempDir)(_.toString)
      val newSuffix    = Option(suffix).getOrElse(".tmp")
      var result: File = null
      do {
        result = genTempFile(prefix, newSuffix, tmpDir)
      } while (!createNewFile(result.toString, throwOnError))
      result
    }

  def exists(path: String): Boolean =
    Zone { implicit z =>
      if (isWindows())
        ShlwApi.PathFileExistsA(toCString(path))
      else
        access(toCString(path), unistd.F_OK) == 0
    }

  lazy val tempDir: String = {
    if (isWindows) {
      val buffer = stackalloc[Byte](FileApi.MaxAnsiPathSize)
      FileApi.GetTempPathA(FileApi.MaxAnsiPathSize, buffer)
      fromCString(buffer)
    } else {
      val dir = getenv(c"TMPDIR")
      if (dir == null) {
        System.getProperty("java.io.tmpdir") match {
          case null => "/tmp"
          case d    => d
        }
      } else {
        fromCString(dir)
      }
    }
  }

  private def genTempFile(prefix: String,
                          suffix: String,
                          directory: String): File = {
    val id = random.nextLong() match {
      case l if l == java.lang.Long.MIN_VALUE => 0
      case l                                  => math.labs(l)
    }
    val fileName = prefix + id + suffix
    new File(directory, fileName)
  }
}
