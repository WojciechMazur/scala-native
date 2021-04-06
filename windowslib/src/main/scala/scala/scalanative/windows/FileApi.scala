package scala.scalanative.windows
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

@extern
object FileApi {
  import HandleApi._

  @name("CreateFileA")
  def createFileA(filename: CString,
                  desiredAccess: DWord,
                  shareMode: DWord,
                  securityAttributes: SecurityAttributes,
                  creationDisposition: DWord,
                  flagsAndAttributes: UInt,
                  templateFile: Handle): Handle = extern

  @name("DeleteFileA")
  def deleteFileA(filename: CString): Boolean = extern

  @name("FlushFileBuffers")
  def flushFileBuffers(handle: Handle): Boolean = extern

  @name("GetFileAttributesA")
  def getFileAttributesA(filename: CString): DWord = extern

  @name("GetFinalPathNameByHandleA")
  def getFinalPathNameByHandleA(handle: Handle,
                                buffer: CString,
                                bufferSize: DWord,
                                flags: DWord): DWord = extern

  @name("GetFullPathNameA")
  def getFullPathNameA(filename: CString,
                       bufferLength: DWord,
                       buffer: CString,
                       filePart: Ptr[CString]): DWord = extern
  @name("ReadFile")
  def readFile(fileHandle: Handle,
               buffer: Ptr[Byte],
               bytesToRead: DWord,
               bytesReadPtr: Ptr[DWord],
               overlapped: Ptr[Byte]): Boolean = extern

  @name("RemoveDirectoryA")
  def removeDirectoryA(filename: CString): Boolean = extern

  @name("SetEndOfFile")
  def setEndOfFile(file: Handle): Boolean = extern

  @name("SetFilePointerEx")
  def setFilePointerEx(file: Handle,
                       distanceToMove: LargeInteger,
                       newFilePointer: Ptr[LargeInteger],
                       moveMethod: DWord): Boolean = extern

  @name("WriteFile")
  def writeFile(fileHandle: Handle,
                buffer: Ptr[Byte],
                bytesToRead: DWord,
                bytesWritten: Ptr[DWord],
                overlapped: Ptr[Byte]): Boolean = extern

  // utils
  @name("scalanative_win32_file_max_path")
  def MaxAnsiPathSize: DWord = extern

  @name("scalanative_win32_invalid_file_attributes")
  def InvalidFileAttributes: DWord = extern
}

object File {
  object FilePointerMoveMethods {
    final val Begin   = 0.toUInt
    final val Current = 1.toUInt
    final val End     = 2.toUInt
  }

  object FinalPathFlags {
    final val FileNameNormalized = 0.toUInt
    final val FileNameOpened     = 8.toUInt

    final val VolumeNameDos  = 0.toUInt
    final val VolumeNameGuid = 1.toUInt
    final val VolumeNameNt   = 2.toUInt
    final val VolumeNameNone = 4.toUInt
  }
}
