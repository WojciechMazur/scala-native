package scala.scalanative.windows

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.language.implicitConversions
import scala.scalanative.libc.string.strcpy

@extern
object FileApi {
  import HandleApi._
  import MinWinBase._
  import File._

  @name("CreateFileA")
  def createFileA(filename: CString,
                  desiredAccess: DWord,
                  shareMode: DWord,
                  securityAttributes: SecurityAttributes,
                  creationDisposition: DWord,
                  flagsAndAttributes: UInt,
                  templateFile: Handle): Handle = extern

  @name("CreateDirectoryA")
  def createDirectoryA(filename: CString,
                       securityAttributes: Ptr[SecurityAttributes]): Boolean =
    extern

  @name("DeleteFileA")
  def deleteFileA(filename: CString): Boolean = extern

  @name("FindFirstFileA")
  def findFirstFileA(filename: CString,
                     findFileData: Ptr[Win32FindDataA]): Handle =
    extern

  @name("FindNextFileA")
  def findNextFileA(searchHandle: Handle,
                    findFileData: Ptr[Win32FindDataA]): Boolean =
    extern

  @name("FindClose")
  def findClose(searchHandle: Handle): Boolean = extern
  @name("FlushFileBuffers")
  def flushFileBuffers(handle: Handle): Boolean = extern

  @name("GetFileAttributesA")
  def getFileAttributesA(filename: CString): DWord = extern

  @name("GetFileInformationByHandle")
  def getFileInformationByHandle(
      file: Handle,
      fileInformation: Ptr[ByHandleFileInformation]): Boolean =
    extern

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

  @name("GetFileSizeEx")
  def getFileSizeEx(file: Handle, fileSize: Ptr[LargeInteger]): Boolean = extern

  @name("GetFileTime")
  def getFileTime(file: Handle,
                  creationTime: Ptr[FileTime],
                  lastAccessTime: Ptr[FileTime],
                  lastWriteTime: Ptr[FileTime]): Boolean = extern

  @name("GetTempFileNameA")
  def getTempFileNameA(pathName: CString,
                       prefixString: CString,
                       unique: UInt,
                       tempFileName: CString): UInt = extern

  @name("GetTempPathA")
  def getTempPathA(bufferLength: DWord, buffer: CString): DWord = extern

  @name("GetVolumePathNameA")
  def getVolumePathNameA(filename: CString,
                         volumePathName: CString,
                         bufferLength: DWord): Boolean = extern

  @name("MoveFileExA")
  def moveFileExA(existingFileName: CString,
                  newFileName: CString,
                  flags: DWord): Boolean = extern

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

  @name("SetFileAttributesA")
  def setFileAttributesA(filename: CString, fileAttributes: DWord): Boolean =
    extern

  @name("SetFilePointerEx")
  def setFilePointerEx(file: Handle,
                       distanceToMove: LargeInteger,
                       newFilePointer: Ptr[LargeInteger],
                       moveMethod: DWord): Boolean = extern

  @name("SetFileTime")
  def setFileTime(file: Handle,
                  creationTime: Ptr[FileTime],
                  lastAccessTime: Ptr[FileTime],
                  lastWriteTime: Ptr[FileTime]): Boolean = extern

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
  import MinWinBase._
  import HelperMethods._

  final object FilePointerMoveMethods {
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

  type PathMax           = Nat.Digit3[Nat._2, Nat._6, Nat._0]
  type FileName          = CArray[Byte, PathMax]
  type AlternateFileName = CArray[Byte, Nat.Digit2[Nat._1, Nat._4]]
  type Win32FindDataA = CStruct13[DWord,
                                  FileTimeStruct,
                                  FileTimeStruct,
                                  FileTimeStruct,
                                  DWord,
                                  DWord,
                                  DWord,
                                  DWord,
                                  FileName,
                                  AlternateFileName,
                                  DWord,
                                  DWord,
                                  Word]
  implicit class Win32FileDataAOps(ref: Ptr[Win32FindDataA]) {
    def fileAttributes: DWord       = ref._1
    def creationTime: FileTime      = ref.at2.fileTime
    def lastAccessTime: FileTime    = ref.at3.fileTime
    def lastWriteTime: FileTime     = ref.at4.fileTime
    private def fileSizeHigh: DWord = ref._5
    private def fileSizeLow: DWord  = ref._6
    def fileSize: ULargeInteger =
      dwordPairToULargeInteger(fileSizeHigh, fileSizeLow)
    def reserved0: DWord           = ref._7
    def reserved1: DWord           = ref._8
    def fileName: CString          = ref._9.at(0)
    def alternateFileName: CString = ref._10.at(0)
    // following fields are not used on some devices, though should not be written
    def fileType: DWord    = ref._11
    def creatorType: DWord = ref._12
    def finderFlags: Word  = ref._13

    def fileAttributes_=(v: DWord): Unit    = ref._1 = v
    def creationTime_=(v: FileTime): Unit   = ref.at2.fileTime = v
    def lastAccessTime_=(v: FileTime): Unit = ref.at3.fileTime = v
    def lastWriteTime_=(v: FileTime): Unit  = ref.at4.fileTime = v
    def fileSize_=(v: ULargeInteger): Unit =
      uLargeIntegerToDWordPair(v, ref.at5, ref.at6)
    def reserved0_=(v: DWord): Unit           = ref._7 = v
    def reserved1_=(v: DWord): Unit           = ref._8 = v
    def fileName_=(v: CString): Unit          = strcpy(ref.at9.at(0), v)
    def alternateFileName_=(v: CString): Unit = strcpy(ref.at9.at(0), v)
  }

  type ByHandleFileInformation = CStruct10[DWord,
                                           FileTimeStruct,
                                           FileTimeStruct,
                                           FileTimeStruct,
                                           DWord,
                                           DWord,
                                           DWord,
                                           DWord,
                                           DWord,
                                           DWord]

  implicit class ByHandleFileInformationOps(ref: Ptr[ByHandleFileInformation]) {
    def fileAttributes: DWord       = ref._1
    def creationTime: FileTime      = ref.at2.fileTime
    def lastAccessTime: FileTime    = ref.at3.fileTime
    def lastWriteTime: FileTime     = ref.at4.fileTime
    def volumeSerialNumber: DWord   = ref._5
    private def fileSizeHigh: DWord = ref._6
    private def fileSizeLow: DWord  = ref._7
    def fileSize: ULargeInteger =
      dwordPairToULargeInteger(fileSizeHigh, fileSizeLow)
    def numberOfLinks: DWord         = ref._8
    private def fileIndexHigh: DWord = ref._9
    private def fileIndexLow: DWord  = ref._10
    def fileIndex: ULargeInteger =
      dwordPairToULargeInteger(fileIndexHigh, fileIndexLow)

    def fileAttributes_=(v: DWord): Unit     = ref._1 = v
    def creationTime_=(v: FileTime): Unit    = ref.at2.fileTime = v
    def lastAccessTime_=(v: FileTime): Unit  = ref.at3.fileTime = v
    def lastWriteTime_=(v: FileTime): Unit   = ref.at4.fileTime = v
    def volumeSerialNumber_=(v: DWord): Unit = ref._5
    def fileSize_=(v: ULargeInteger): Unit =
      uLargeIntegerToDWordPair(v, ref.at6, ref.at7)
    def numberOfLinks_=(v: DWord): Unit = ref._8
    def fileIndex_=(v: ULargeInteger): Unit =
      uLargeIntegerToDWordPair(v, ref.at9, ref.at10)
  }

  object MoveFileFlags {
    final val MOVEFILE_REPLACE_EXISTING      = 0x1.toUInt
    final val MOVEFILE_COPY_ALLOWED          = 0x2.toUInt
    final val MOVEFILE_DELAY_UNTIL_REBOOT    = 0x4.toUInt
    final val MOVEFILE_WRITE_THROUGH         = 0x8.toUInt
    final val MOVEFILE_CREATE_HARDLINK       = 0x10.toUInt
    final val MOVEFILE_FAIL_IF_NOT_TRACKABLE = 0x20.toUInt
  }
}
