package java.lang.process

import java.io.{File, IOException, InputStream, OutputStream}
import java.util.concurrent.TimeUnit
import java.util.ScalaOps._
import java.io.FileDescriptor
import java.lang.ProcessBuilder._

import scala.scalanative.unsigned._
import scala.scalanative.unsafe._
import scalanative.windows._
import scalanative.windows.HandleApi.Handle
import scalanative.windows.{
  ProcessThreadsApi,
  WinBaseApi,
  SynchApi,
  NamedPipeApi
}
import scalanative.windows.ProcessThreads.{
  ExitCodes,
  ProcessInformation,
  ProcessInformationOps
}
import scalanative.windows.WinBase.RegisterWaitObjectFlags
import scalanative.runtime.{Intrinsics, fromRawPtr, toRawPtr}
import WindowsProcess._

private[lang] class WindowsProcess private (
    val handle: Handle,
    builder: ProcessBuilder,
    inHandle: Handle,
    outHandle: Handle,
    errHandle: Handle
) extends GenericProcess {

  private[lang] def checkResult(): CInt =
    if (isAlive()) -1
    else exitValue()

  private lazy val pid = ProcessThreadsApi.getProcessId(handle)

  override def destroy(): Unit =
    ProcessThreadsApi.terminateProcess(handle, 1.toUInt)

  override def destroyForcibly(): Process = {
    destroy()
    this
  }

  override def exitValue(): scala.Int = {
    val exitCode: Ptr[DWord] = stackalloc[DWord]
    if (ProcessThreadsApi.getExitCodeProcess(handle, exitCode)) {
      (!exitCode) match {
        case ExitCodes.StillActive =>
          throw new IllegalThreadStateException(
            s"Process $pid has not exited yet")
        case v => v.toInt
      }
    } else {
      throw new IllegalThreadStateException(
        s"Cannot get exit code of process $pid")
    }
  }

  override def getErrorStream(): InputStream = _errorStream

  override def getInputStream(): InputStream = _inputStream

  override def getOutputStream(): OutputStream = _outputStream

  override def isAlive(): scala.Boolean = {
    val exitCode: Ptr[DWord] = stackalloc[DWord]
    ProcessThreadsApi.getExitCodeProcess(handle, exitCode)
    !exitCode == ExitCodes.StillActive
  }

  override def toString = s"WindowsProcess($pid)"

  override def waitFor(): scala.Int = {
    SynchApi.waitForSingleObject(handle, Constants.Infinite())
    exitValue()
  }

  override def waitFor(timeout: scala.Long, unit: TimeUnit): scala.Boolean = {
    def hasValidTimeout = timeout > 0L
    def hasFinished =
      SynchApi.waitForSingleObject(handle, unit.toMillis(timeout).toUInt) != Synch.WaitResult.Timeout

    !isAlive() ||
    (hasValidTimeout && hasFinished)
  }

  private[this] val _inputStream =
    PipeIO[PipeIO.Stream](this,
                          new FileDescriptor(outHandle),
                          builder.redirectOutput())
  private[this] val _errorStream =
    PipeIO[PipeIO.Stream](this,
                          new FileDescriptor(errHandle),
                          builder.redirectError())
  private[this] val _outputStream =
    PipeIO[OutputStream](this,
                         new FileDescriptor(inHandle),
                         builder.redirectInput())
}

object WindowsProcess {
  type PipeHandles = CArray[Handle, Nat._2]
  private final val readEnd  = 0
  private final val writeEnd = 1

  def zeroMemory[T: Tag](ptr: Ptr[T]) {
    import scalanative.libc.string.memset
    memset(ptr.asInstanceOf[Ptr[Byte]], 0, sizeof[T])
  }

  def apply(builder: ProcessBuilder): Process = Zone { implicit z =>
    val (inRead, inWrite) =
      createPipeOrThrow(builder.redirectInput(),
                        ConsoleExt.stdIn,
                        isStdIn = true,
                        "Couldn't create std input pipe.")
    val (outRead, outWrite) =
      createPipeOrThrow(builder.redirectOutput(),
                        ConsoleExt.stdOut,
                        isStdIn = false,
                        "Couldn't create std output pipe.")
    val (errRead, errWrite) = {
      if (builder.redirectErrorStream()) (outRead, outWrite)
      else
        createPipeOrThrow(builder.redirectError(),
                          ConsoleExt.stdErr,
                          isStdIn = false,
                          "Couldn't create std error pipe.")
    }

    val cmd  = builder.command().scalaOps.toSeq
    val dir  = toCString(builder.directory().getAbsolutePath())
    val argv = toCString(cmd.map(quoted).mkString(" "))
    val envp = nullTerminatedBlock {
      builder
        .environment()
        .entrySet()
        .scalaOps
        .toSeq
        .map(e => s"${e.getKey()}=${e.getValue()}")
    }

    val startupInfo = stackalloc[ProcessThreads.StartupInfo]
    import ProcessThreads.StartupInfoOps
    zeroMemory(startupInfo)
    startupInfo.cb = sizeof[ProcessThreads.StartupInfo].toUInt
    startupInfo.stdInput = inRead
    startupInfo.stdOutput = outWrite
    startupInfo.stdError = errWrite
    startupInfo.flags = ProcessThreads.StartupInfoFlags.UseStdHandles

    val processInfo = stackalloc[ProcessThreads.ProcessInformation]
    zeroMemory(processInfo)

    val created = ProcessThreadsApi.createProcessA(
      applicationName = null,
      commandLine = argv,
      processAttributres = null,
      threadAttributes = null,
      inheritHandle = true,
      creationFlags = 0.toUInt,
      environment = envp,
      currentDirectory = dir,
      startupInfo = startupInfo,
      processInformation = processInfo
    )

    if (created) {
      import HandleApi.closeHandle
      closeHandle(inRead)
      closeHandle(outWrite)
      closeHandle(errWrite)
      closeHandle(processInfo.thread)

      new WindowsProcess(processInfo.process,
                         builder,
                         inWrite,
                         outRead,
                         errRead)
    } else {
      throw new IOException(
        s"Failed to create process for command: $cmd: ${ErrorHandling.getLastError()}")
    }
  }

  private def createPipeOrThrow(
      redirect: Redirect,
      stdHandle: Handle,
      isStdIn: Boolean,
      msg: => String)(implicit z: Zone): (Handle, Handle) = {

    val securityAttributes = stackalloc[SecurityAttributes]
    securityAttributes.length = sizeof[SecurityAttributes].toUInt
    securityAttributes.inheritHandle = true
    securityAttributes.securityDescriptor = null

    val pipe: PipeHandles                = stackalloc[PipeHandles]
    val pipeEnds @ (pipeRead, pipeWrite) = (pipe.at(readEnd), pipe.at(writeEnd))
    val pipeCreated =
      NamedPipeApi.createPipe(pipeRead, pipeWrite, null, 0.toUInt)
    if (!pipeCreated)
      throw new IOException(s"$msg Error code: ${ErrorHandling.getLastError()}")

    val (childEnd, parentEnd) =
      if (isStdIn) pipeEnds
      else pipeEnds.swap

    setupRedirect(redirect, childEnd, stdHandle)

    HandleApi.setHandleInformation(!childEnd, HandleFlags.Inherit, 1.toUInt)
    HandleApi.setHandleInformation(!parentEnd, HandleFlags.Inherit, 0.toUInt)

    (!pipeRead, !pipeWrite)
  }

  @inline private def setupRedirect(redirect: ProcessBuilder.Redirect,
                                    childHandle: Ptr[Handle],
                                    stdHandle: Handle): Unit = {

    @inline def openRedirectFd(
        access: DWord,
        disposition: DWord,
        flagsAndAttributes: DWord = FileAttributes.FILE_ATTRIBUTE_NORMAL,
        sharing: DWord = FileSharing.NotShared) = Zone { implicit z =>
      val handle = FileApi.createFile(
        filename = toCString(redirect.file.getAbsolutePath()),
        desiredAccess = access,
        shareMode = sharing,
        securityAttributes = null,
        creationDisposition = disposition,
        flagsAndAttributes = flagsAndAttributes,
        templateFile = null
      )
      if (handle == HandleApi.InvalidHandleValue) {
        throw new IOException(
          s"Unable to open file ${redirect.file()} (${ErrorHandling.getLastError()})")
      }
      handle
    }

    def duplicateOrThrow(handle: Handle, kind: String): Unit = {
      val hasSucceded = HandleApi.duplicateHandle(
        sourceProcess = ProcessThreadsApi.getCurrentProcess(),
        source = handle,
        targetProcess = ProcessThreadsApi.getCurrentProcess(),
        target = childHandle,
        desiredAccess = 0.toUInt,
        inheritHandle = true,
        options = DuplicateHandleOptions.SameAccess
      )

      if (!hasSucceded) {
        throw new IOException(
          s"Couldn't duplicate $kind file descriptor ${ErrorHandling.getLastError()}")
      }
    }

    redirect.`type`() match {
      case ProcessBuilder.Redirect.Type.INHERIT =>
        duplicateOrThrow(stdHandle, "inherit")

      case ProcessBuilder.Redirect.Type.PIPE =>
        !childHandle = HandleApi.InvalidHandleValue

      case ProcessBuilder.Redirect.Type.READ =>
        val fd = openRedirectFd(
          access = FileAccess.FILE_GENERIC_READ,
          flagsAndAttributes = FileAttributes.FILE_ATTRIBUTE_READONLY,
          disposition = FileDisposition.OpenExisting,
          sharing = FileSharing.ShareRead
        )
        duplicateOrThrow(fd, "read")

      case ProcessBuilder.Redirect.Type.WRITE =>
        val fd = openRedirectFd(access = FileAccess.FILE_GENERIC_WRITE,
                                disposition = FileDisposition.CreateAlways)
        duplicateOrThrow(fd, "write")

      case ProcessBuilder.Redirect.Type.APPEND =>
        val fd = openRedirectFd(
          access = FileAccess.FILE_GENERIC_WRITE | FileAccess.FILE_APPEND_DATA,
          disposition = FileDisposition.OpenAlways
        )
        duplicateOrThrow(fd, "append")
    }
  }

  @inline private def nullTerminatedBlock(seq: collection.Seq[String])(
      implicit z: Zone): CString = {
    import scalanative.libc.string._
    // (All strings + null-terminate) + additional null terminate as end of block
    val totalSize = (seq :+ "").foldLeft(0)(_ + _.size + 1) + 1
    val block     = alloc[CChar](totalSize)
    val blockEnd = seq.foldLeft(block) { (blockHead, str) =>
      val cString = toCString(str)
      // we need to include null-termination char
      val length = strlen(cString) + 1.toUInt
      memcpy(blockHead, cString, length)
      blockHead + length
    }

    assert(!blockEnd == 0.toByte)
    assert(!(blockEnd - 1) == 0.toByte)
    block
  }

  def quoted(str: String): String = {
    if (str.nonEmpty && str.head == '"' && str.last == '"') str
    else "\"" + str + "\""
  }

}
