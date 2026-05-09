package scala.scalanative
package build

/** Filters [[nir.Attr.Link]] names so the final link only sees libraries for the host target.
  *
  * Reached NIR can still mention Windows-only `@link` attributes (e.g. from `windowslib` in the
  * runtime bundle) even when link-time branches would normally exclude them; cached-header /
  * split-classpath linking can widen reachability. Unix hosts must not pass `-lkernel32` etc.
  */
private[scalanative] object HostNativeLinks {

  /** Windows system DLL / import library names used by Scala Native's `windowslib`. */
  private val windowsSystemLibs: Set[String] = Set(
    "kernel32",
    "advapi32",
    "userenv",
    "ws2_32",
    "user32",
    "shell32",
    "ole32",
    "gdi32",
    "comdlg32",
    "winmm",
    "iphlpapi",
    "crypt32",
    "bcrypt",
    "ncrypt",
    "version",
    "wininet",
    "shlwapi",
    "wsock32",
    "netapi32",
    "dbghelp",
    "oleaut32",
    "rpcrt4",
    "secur32",
    "sspicli"
  ).map(_.toLowerCase)

  /** Typical Unix `@link` names that are not valid Windows link libraries. */
  private val unixStyleLibs: Set[String] = Set(
    "pthread",
    "dl",
    "m",
    "rt",
    "resolv"
  ).map(_.toLowerCase)

  /** True if this link name should be forwarded to the system linker for `config`'s target. */
  def keepForHost(config: Config, linkName: String): Boolean = {
    val n = linkName.toLowerCase
    if (config.targetsWindows) !unixStyleLibs.contains(n)
    else !windowsSystemLibs.contains(n)
  }
}
