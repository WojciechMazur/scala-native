package scala.scalanative.windows

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.language.implicitConversions
import scala.scalanative.libc.wchar.wcscpy
import scala.scalanative.libc.string.strcpy

@extern
object FileApi {
  import HandleApi._
  import MinWinBase._
  import File._

  def CreateFileA(filename: CString,
                  desiredAccess: DWord,
                  shareMode: DWord,
                  securityAttributes: SecurityAttributes,
                  creationDisposition: DWord,
                  flagsAndAttributes: UInt,
                  templateFile: Handle): Handle = extern

  def CreateFileW(filename: CWString,
                  desiredAccess: DWord,
                  shareMode: DWord,
                  securityAttributes: SecurityAttributes,
                  creationDisposition: DWord,
                  flagsAndAttributes: UInt,
                  templateFile: Handle): Handle = extern

  def CreateDirectoryA(filename: CString,
                       securityAttributes: Ptr[SecurityAttributes]): Boolean =
    extern
  def CreateDirectoryW(filename: CWString,
                       securityAttributes: Ptr[SecurityAttributes]): Boolean =
    extern
  def DeleteFileA(filename: CString): Boolean = extern
  def DeleteFileW(filename: CWString): Boolean = extern
  def FindFirstFileA(filename: CString,
                     findFileData: Ptr[Win32FindDataA]): Handle = extern
  def FindNextFileA(searchHandle: Handle,
                    findFileData: Ptr[Win32FindDataA]): Boolean = extern

  def FindFirstFileW(filename: CWString,
                     findFileData: Ptr[Win32FindDataW]): Handle = extern
  def FindNextFileW(searchHandle: Handle,
                    findFileData: Ptr[Win32FindDataW]): Boolean = extern
  def FindClose(searchHandle: Handle): Boolean                  = extern
  def FlushFileBuffers(handle: Handle): Boolean                 = extern
  def GetFileAttributesA(filename: CString): DWord              = extern
  def GetFileAttributesW(filename: CWString): DWord             = extern

  def GetFileInformationByHandle(
      file: Handle,
      fileInformation: Ptr[ByHandleFileInformation]): Boolean =
    extern

  def GetFinalPathNameByHandleA(handle: Handle,
                                buffer: CString,
                                bufferSize: DWord,
                                flags: DWord): DWord = extern

  def GetFinalPathNameByHandleW(handle: Handle,
                                buffer: CWString,
                                bufferSize: DWord,
                                flags: DWord): DWord = extern

  def GetFullPathNameA(filename: CString,
                       bufferLength: DWord,
                       buffer: CString,
                       filePart: Ptr[CString]): DWord = extern

  def GetFullPathNameW(filename: CWString,
                       bufferLength: DWord,
                       buffer: CWString,
                       filePart: Ptr[CWString]): DWord = extern

  def GetFileSizeEx(file: Handle, fileSize: Ptr[LargeInteger]): Boolean = extern

  def GetFileTime(file: Handle,
                  creationTime: Ptr[FileTime],
                  lastAccessTime: Ptr[FileTime],
                  lastWriteTime: Ptr[FileTime]): Boolean = extern

  def GetTempFileNameW(pathName: CWString,
                       prefixString: CWString,
                       unique: UInt,
                       tempFileName: CWString): UInt = extern

  def GetTempPathA(bufferLength: DWord, buffer: CString): DWord = extern
  def GetTempPathW(bufferLength: DWord, buffer: CWString): DWord = extern

  def GetVolumePathNameW(filename: CWString,
                         volumePathName: CWString,
                         bufferLength: DWord): Boolean = extern

   def ReadFile(fileHandle: Handle,
               buffer: Ptr[Byte],
               bytesToRead: DWord,
               bytesReadPtr: Ptr[DWord],
               overlapped: Ptr[Byte]): Boolean = extern

  def RemoveDirectoryW(filename: CWString): Boolean = extern
  def SetEndOfFile(file: Handle): Boolean           = extern
  def SetFileAttributesA(filename: CString, fileAttributes: DWord): Boolean =
    extern
  def SetFileAttributesW(filename: CWString, fileAttributes: DWord): Boolean =
    extern

  def SetFilePointerEx(file: Handle,
                       distanceToMove: LargeInteger,
                       newFilePointer: Ptr[LargeInteger],
                       moveMethod: DWord): Boolean = extern

  def SetFileTime(file: Handle,
                  creationTime: Ptr[FileTime],
                  lastAccessTime: Ptr[FileTime],
                  lastWriteTime: Ptr[FileTime]): Boolean = extern

  def WriteFile(fileHandle: Handle,
                buffer: Ptr[Byte],
                bytesToRead: DWord,
                bytesWritten: Ptr[DWord],
                overlapped: Ptr[Byte]): Boolean = extern

  // utils
  @name("scalanative_win32_file_max_path")
  def MaxPathSize: DWord = extern

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
  type FileName[C]          = CArray[C, PathMax]
  type AlternateFileName[C] = CArray[C, Nat.Digit2[Nat._1, Nat._4]]
  type Win32FindData[C] = CStruct13[DWord,
                                  FileTimeStruct,
                                  FileTimeStruct,
                                  FileTimeStruct,
                                  DWord,
                                  DWord,
                                  DWord,
                                  DWord,
                                  FileName[C],
                                  AlternateFileName[C],
                                  DWord,
                                  DWord,
                                  Word]
  type Win32FindDataW = Win32FindData[WChar]
  type Win32FindDataA = Win32FindData[CChar]
  abstract class Win32FileDataOps[C: Tag](ref: Ptr[Win32FindData[C]]) {
    def fileAttributes: DWord       = ref._1
    def creationTime: FileTime      = ref.at2.fileTime
    def lastAccessTime: FileTime    = ref.at3.fileTime
    def lastWriteTime: FileTime     = ref.at4.fileTime
    private def fileSizeHigh: DWord = ref._5
    private def fileSizeLow: DWord  = ref._6
    def fileSize: ULargeInteger =
      dwordPairToULargeInteger(fileSizeHigh, fileSizeLow)
    def reserved0: DWord            = ref._7
    def reserved1: DWord            = ref._8
    def fileName: Ptr[C]          = ref._9.at(0)
    def alternateFileName: Ptr[C] = ref._10.at(0)
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
    def reserved0_=(v: DWord): Unit = ref._7 = v
    def reserved1_=(v: DWord): Unit = ref._8 = v
    def fileName_=(v: Ptr[C]): Unit
    def alternateFileName_=(v: Ptr[C]): Unit
  }
  implicit class Win32FileDataWOps(ref: Ptr[Win32FindDataW]) extends Win32FileDataOps(ref){
    override def fileName_=(v: CWString): Unit     =
      wcscpy(ref.at9.at(0).asInstanceOf[CWideString],
        v.asInstanceOf[CWideString])

    override def alternateFileName_=(v: CWString): Unit =
      wcscpy(ref.at10.at(0).asInstanceOf[CWideString],
        v.asInstanceOf[CWideString])
  }
  implicit class Win32FileDataAOps(ref: Ptr[Win32FindDataA]) extends Win32FileDataOps(ref){
    override def fileName_=(v: CString): Unit     = strcpy(ref.at9.at(0),v)
    override def alternateFileName_=(v: CString): Unit = strcpy(ref.at10.at(0), v)
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


}
