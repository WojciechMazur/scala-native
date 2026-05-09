package scala.scalanative
package build
package cache

import java.nio.charset.StandardCharsets
/** Deterministic cache keys for the cached-library build mode.
  *
  * Layout (two-layer):
  *   `~/.cache/scala-native/{snVersion}/{globalConfigHash}/{org}/{artifact}/{version}/{libContentSubKey}/`
  *
  * `globalConfigHash` is a SHA-256 hex digest of a UTF-8 string built from (in order):
  *   - literal `GLOBAL|v1|`
  *   - resolved target triple (from [[NativeConfig.configuredOrDetectedTriple]])
  *   - GC name, multithreading mode string (`true` / `false` / `detect`), [[Mode]] name, [[LTO]] name
  *   - `sourceLevelDebuggingConfig.enabled` and `generateFunctionSourcePositions`
  *   - sanitizer name or `none`
  *   - sorted linktime property entries as `key=value` lines (see [[serializeLinktimeProperties]])
  *   - clang and clang++ absolute paths followed by `|VERSION=` lines where version is taken from
  *     `clang --version` first line if available, else `unknown` (best-effort; empty paths use `empty`)
  *   - compile options joined with `\0`, then linking options joined with `\0` (order-preserving)
  *
  * `libContentSubKey` is a SHA-256 hex digest of:
  *   - literal `LIB|v1|`
  *   - JAR content SHA-256 (see [[ClasspathPartition.jarContentSha256]])
  *   - literal `|LINKTIME_SUBSET|`
  *   - sorted subset of linktime keys that appear in the serialized global string (for PoC: same as full
  *     linktime map until per-library property tracking is wired from reachability)
  */
private[scalanative] object CacheKey {

  private val Utf8 = StandardCharsets.UTF_8

  def globalConfigHash(native: NativeConfig): String = {
    val sb = new StringBuilder()
    sb.append("GLOBAL|v1|")
    sb.append(native.configuredOrDetectedTriple.toString)
    sb.append('|')
    sb.append(native.gc.name)
    sb.append('|')
    sb.append(native.multithreading.map(_.toString).getOrElse("detect"))
    sb.append('|')
    sb.append(native.mode.toString)
    sb.append('|')
    sb.append(native.lto.name)
    sb.append('|')
    sb.append(native.sourceLevelDebuggingConfig.enabled)
    sb.append('|')
    sb.append(native.sourceLevelDebuggingConfig.generateFunctionSourcePositions)
    sb.append('|')
    sb.append(native.sanitizer.map(_.name).getOrElse("none"))
    sb.append('\n')
    sb.append(serializeLinktimeProperties(native.linktimeProperties))
    sb.append("CLANG|")
    sb.append(pathWithVersion(native.clang))
    sb.append("\nCLANGPP|")
    sb.append(pathWithVersion(native.clangPP))
    val z = "\u0000"
    sb.append("\nCOMPILE_OPTS").append(z)
    sb.append(native.compileOptions.mkString(z))
    sb.append("\nLINK_OPTS").append(z)
    sb.append(native.linkingOptions.mkString(z))
    sha256Hex(sb.toString)
  }

  def libContentSubKey(jarContentSha256Hex: String, native: NativeConfig): String = {
    val sb = new StringBuilder()
    sb.append("LIB|v1|")
    sb.append(jarContentSha256Hex)
    sb.append("|LINKTIME_SUBSET|")
    sb.append(serializeLinktimeProperties(native.linktimeProperties))
    sha256Hex(sb.toString)
  }

  private def serializeLinktimeProperties(props: NativeConfig.LinktimeProperites): String =
    props.toSeq.sortBy(_._1).map { case (k, v) => s"$k=${linktimeValueString(v)}" }.mkString("", "\n", "\n")

  private def linktimeValueString(v: Any): String =
    v match {
      case s: String            => s
      case b: Boolean           => b.toString
      case n: nir.Val           => n.toString
      case n: java.lang.Number  => n.toString
      case other                => other.toString
    }

  private def pathWithVersion(p: java.nio.file.Path): String = {
    val pathStr = if (p == null || p.toString.isEmpty) "empty" else p.toAbsolutePath.toString
    val ver =
      if (pathStr == "empty") "skipped"
      else try {
        val proc =
          new ProcessBuilder(pathStr, "--version").redirectErrorStream(true).start()
        try {
          val reader = scala.io.Source.fromInputStream(proc.getInputStream(), "UTF-8")
          try {
            reader.getLines().take(1).mkString.trim match {
              case s if s.nonEmpty => s
              case _               => "unknown"
            }
          } finally reader.close()
        } finally proc.destroyForcibly()
      } catch {
        case _: Exception => "unknown"
      }
    s"$pathStr|VERSION=$ver"
  }

  def sha256Hex(s: String): String =
    sha256HexBytes(s.getBytes(Utf8))

  def sha256HexBytes(data: Array[Byte]): String =
    PureSha256(data).map(b => f"$b%02x").mkString
}
