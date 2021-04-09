package scala.scalanative.windows

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
@extern
object FileAccess {
  @name("scalanative_win32_file_access_file_generic_all")
  def GENERIC_ALL: DWord = extern

  //execute
  @name("scalanative_win32_file_access_file_generic_execute")
  def FILE_GENERIC_EXECUTE: DWord = extern

  @name("scalanative_win32_file_access_file_execute")
  def FILE_EXECUTE: DWord = extern

  @name("scalanative_win32_file_access_file_read_attributes")
  def STANDARD_RIGHTS_EXECUTE: DWord = extern

  //read
  @name("scalanative_win32_file_access_file_generic_read")
  def FILE_GENERIC_READ: DWord = extern

  @name("scalanative_win32_file_access_file_read_attributes")
  def FILE_READ_ATTRIBUTES: DWord = extern

  @name("scalanative_win32_file_access_file_read_data")
  def FILE_READ_DATA: DWord = extern

  @name("scalanative_win32_file_access_file_read_ea")
  def FILE_READ_EA: DWord = extern

  @name("scalanative_win32_file_access_standard_rights_read")
  def STANDARD_RIGHTS_READ: DWord = extern

  //write
  @name("scalanative_win32_file_access_file_generic_write")
  def FILE_GENERIC_WRITE: DWord = extern

  @name("scalanative_win32_file_access_file_append_data")
  def FILE_APPEND_DATA: DWord = extern

  @name("scalanative_win32_file_access_file_write_attributes")
  def FILE_WRITE_ATTRIBUTES: DWord = extern

  @name("scalanative_win32_file_access_file_write_data")
  def FILE_WRITE_DATA: DWord = extern

  @name("scalanative_win32_file_access_file_write_ea")
  def FILE_WRITE_EA: DWord = extern

  @name("scalanative_win32_file_access_standard_rights_write")
  def STANDARD_RIGHTS_WRITE: DWord = extern

  @name("scalanative_win32_file_access_synchronize")
  def SYNCHRONIZE: DWord = extern
}

object FileSharing {
  //https://docs.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-createfilea
  /** Prevents other processes from opening a file or device if they request
   *  delete, read, or write access.
   */
  final val NotShared = 0.toUInt

  /** Enables subsequent open operations on a file or device to request read access */
  final val ShareRead = 0x01.toUInt

  /** Enables subsequent open operations on a file or device to request write access */
  final val ShareWrite = 0x02.toUInt

  /** Enables subsequent open operations on a file or device to request delete access */
  final val ShareDelete = 0x04.toUInt

  final val ShareAll = ShareRead | ShareWrite | ShareDelete
}

object FileDisposition {

  /** Create a new file if it does not alreay exists, or fail with error code ERROR_fILE_EXISTS */
  final val CreateNew = 0x01.toUInt

  /** Create a new file, overwrite if exists and set last error code to ERROR_ALREADY_EXIST */
  final val CreateAlways = 0x02.toUInt

  /** Open a file if exists or fail with ERROR_FILE_NOT_FOUND */
  final val OpenExisting = 0x03.toUInt

  /** Open a file, if it does not exists and path is valid writable location it creates a new file
   * and set last error code to ERROR_ALREADY_EXISTS
   */
  final val OpenAlways = 0x04.toUInt

  /** Opens a file and truncates it only if it exists, otherwise fails with error code ERROR_FILE_NOT_FOUND    */
  final val TruncateExisting = 0x05.toUShort
}

object FileAttributes {
  final val ReadOnly            = 0x00000001.toUInt
  final val Hidden              = 0x00000002.toUInt
  final val System              = 0x00000004.toUInt
  final val Directory           = 0x00000010.toUInt
  final val Archive             = 0x00000020.toUInt
  final val Device              = 0x00000040.toUInt
  final val Normal              = 0x00000080.toUInt
  final val Temporary           = 0x00000100.toUInt
  final val SparseFile          = 0x00000200.toUInt
  final val ReparsePoint        = 0x00000400.toUInt
  final val Compressed          = 0x00000800.toUInt
  final val Offline             = 0x00001000.toUInt
  final val NotContextIndexed   = 0x00002000.toUInt
  final val Encrypted           = 0x00004000.toUInt
  final val IntegrityStream     = 0x00008000.toUInt
  final val Virtual             = 0x00010000.toUInt
  final val NoScrubData         = 0x00020000.toUInt
  final val EA                  = 0x00040000.toUInt
  final val Pinned              = 0x00080000.toUInt
  final val Unpinned            = 0x00100000.toUInt
  final val RecallOnOpen        = 0x00040000.toUInt
  final val RecallOnDataAcccess = 0x00400000.toUInt
}

object FileFlags {
  final val FILE_FLAG_BACKUP_SEMANTICS   = 0x02000000.toUInt
  final val FILE_FLAG_DELETE_ON_CLOSE    = 0x04000000.toUInt
  final val FILE_FLAG_NO_BUFFERING       = 0x20000000.toUInt
  final val FILE_FLAG_OPEN_NO_RECALL     = 0x00100000.toUInt
  final val FILE_FLAG_OPEN_REPARSE_POINT = 0x00200000.toUInt
  final val FILE_FLAG_OVERLAPPED         = 0x40000000.toUInt
  final val FILE_FLAG_POSIX_SEMANTICS    = 0x01000000.toUInt
  final val FILE_FLAG_RANDOM_ACCESS      = 0x10000000.toUInt
  final val FILE_FLAG_SESSION_AWARE      = 0x00800000.toUInt
  final val FILE_FLAG_SEQUENTIAL_SCAN    = 0x08000000.toUInt
  final val FILE_FLAG_WRITE_THROUGH      = 0x80000000.toUInt

}
