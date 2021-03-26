package scala.scalanative.windows
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

@extern
object FileApi {
  import HandleApi._

  @name("scalanative_win32_file_max_path")
  final def maxAsciiPathSize: CInt = extern

  type SecurityAttributes = Ptr[Byte]

  @name("ReadFile")
  def readFile(fileHandle: Handle,
               buffer: Ptr[Byte],
               bytesToRead: UInt,
               bytesReadPtr: Ptr[UInt],
               overlapped: Ptr[Byte]): Boolean = extern

  @name("WriteFile")
  def writeFile(fileHandle: Handle,
                buffer: Ptr[Byte],
                bytesToRead: UInt,
                bytesWritten: Ptr[UInt],
                overlapped: Ptr[Byte]): Boolean = extern

  @name("CreateFileA")
  def createFile(filename: CString,
                 desiredAccess: UInt,
                 shareMode: UInt,
                 securityAttributes: SecurityAttributes,
                 creationDisposition: UInt,
                 flagsAndAttributes: UInt,
                 templateFile: Handle): Handle = extern

  @name("FlushFileBuffers")
  def flushFileBuffers(handle: Handle): Boolean = extern
}
