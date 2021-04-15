package scala.scalanative.windows

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import HandleApi.Handle
import scala.scalanative.windows.SecurityBase.{
  AccessToken,
  SecurityImpersonationLevel
}
import scala.scalanative.windows.winnt.TokenInformationClass
import scala.scalanative.windows.MinWinBase._
import java.io.IOException

object HelperMethods {
  def withUserToken[T](desiredAccess: DWord)(fn: Handle => T): T = {
    val tokenHandle = stackalloc[Handle]
    def getProcessToken() =
      ProcessThreadsApi.openProcessToken(ProcessThreadsApi.getCurrentProcess(),
                                         desiredAccess,
                                         tokenHandle)
    def getThreadToken() =
      ProcessThreadsApi.openThreadToken(ProcessThreadsApi.getCurrentThread(),
                                        desiredAccess,
                                        openAsSelf = true,
                                        tokenHandle)
    if (getProcessToken() || getThreadToken()) {
      try {
        fn(!tokenHandle)
      } finally {
        HandleApi.closeHandle(!tokenHandle)
      }
    } else {
      throw new RuntimeException("Cannot get user token")
    }
  }

  def withImpersonatedToken[T](fn: Handle => T): T = {
    withUserToken(
      AccessToken.Impersonate | AccessToken.Read | AccessToken.Duplicate) {
      tokenHandle =>
        val impersonatedToken = stackalloc[Handle]
        if (!SecurityBaseApi.duplicateToken(
              tokenHandle,
              SecurityImpersonationLevel.Impersonation(),
              impersonatedToken)) {
          throw new RuntimeException("Cannot impersonate access token")
        }

        try {
          fn(!impersonatedToken)
        } finally HandleApi.closeHandle(!impersonatedToken)
    }
  }

  def withTokenInformation[T, R](token: Handle,
                                 informationClass: TokenInformationClass.Type)(
      fn: Ptr[T] => R)(implicit z: Zone): R = {
    val dataSize = stackalloc[DWord]

    if (!SecurityBaseApi.getTokenInformation(token,
                                             informationClass,
                                             information = null,
                                             informationLength = 0.toUInt,
                                             returnLength = dataSize)) {
      throw new RuntimeException(
        s"Cannot determinate size for token informaiton $informationClass")
    }

    val data = alloc[Byte](!dataSize)
    if (!SecurityBaseApi.getTokenInformation(token,
                                             informationClass,
                                             information = data,
                                             informationLength = !dataSize,
                                             returnLength = null)) {
      throw new RuntimeException(
        s"Failed to get for token informaiton $informationClass")
    }

    fn(data.asInstanceOf[Ptr[T]])
  }

  /**
   * Execute given function and free Windows internal allocations
   *
   * @param handles List of ScalaNative pointers containing pointer to Windows local allocated memory
   */
  def withLocalHandleCleanup[T](handles: Ptr[_ <: Ptr[_]]*)(fn: => T): T = {
    try {
      fn
    } finally {
      handles.foreach { ref =>
        if (ref != null) {
          WinBaseApi.localFree(!ref)
        }
      }
    }
  }

  def withFile[T](path: CString,
                  access: DWord,
                  shareMode: DWord = FileSharing.ShareAll,
                  disposition: DWord = FileDisposition.OpenExisting,
                  attributes: DWord = FileAttributes.Normal,
                  allowInvalidHandle: Boolean = false)(fn: Handle => T): T = {
    val handle = FileApi.createFileA(
      path,
      desiredAccess = access,
      shareMode = shareMode,
      securityAttributes = null,
      creationDisposition = disposition,
      flagsAndAttributes = attributes,
      templateFile = null
    )
    if (handle != HandleApi.InvalidHandleValue || allowInvalidHandle) {
      try { fn(handle) }
      finally HandleApi.closeHandle(handle)
    } else {
      throw new IOException(
        s"Cannot open file ${fromCString(path)}: ${ErrorHandling.getLastError}")
    }
  }

  def dwordPairToULargeInteger(high: DWord, low: DWord): ULargeInteger = {
    if (high == 0.toUInt) low
    else (high.toULong << 32) | low
  }

  def uLargeIntegerToDWordPair(v: ULargeInteger,
                               high: Ptr[DWord],
                               low: Ptr[DWord]): Unit = {
    val mask = 0xFFFFFFFF.toUInt
    !high = ((v >> 32) & mask).toUInt
    !low = (v & mask).toUInt
  }
}
