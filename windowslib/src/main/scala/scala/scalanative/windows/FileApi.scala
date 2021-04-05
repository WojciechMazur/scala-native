package scala.scalanative.windows
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

@extern
object FileApi {
  import HandleApi._

  @name("scalanative_win32_file_max_path")
  final def maxAsciiPathSize: CInt = extern


  @name("ReadFile")
  def readFile(fileHandle: Handle,
               buffer: Ptr[Byte],
               bytesToRead: DWord,
               bytesReadPtr: Ptr[DWord],
               overlapped: Ptr[Byte]): Boolean = extern

  @name("WriteFile")
  def writeFile(fileHandle: Handle,
                buffer: Ptr[Byte],
                bytesToRead: DWord,
                bytesWritten: Ptr[DWord],
                overlapped: Ptr[Byte]): Boolean = extern

  @name("CreateFileA")
  def createFile(filename: CString,
                 desiredAccess: DWord,
                 shareMode: DWord,
                 securityAttributes: SecurityAttributes,
                 creationDisposition: DWord,
                 flagsAndAttributes: UInt,
                 templateFile: Handle): Handle = extern

  @name("FlushFileBuffers")
  def flushFileBuffers(handle: Handle): Boolean = extern
}
