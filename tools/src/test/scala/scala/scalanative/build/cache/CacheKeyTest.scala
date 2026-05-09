package scala.scalanative
package build
package cache

import java.nio.file.Paths

import org.junit.Assert.*
import org.junit.Test

import scala.scalanative.build.{GC, LTO, Mode, NativeConfig, SourceLevelDebuggingConfig}

class CacheKeyTest {

  @Test def globalConfigHashStableForEqualConfig(): Unit = {
    def mk =
      NativeConfig.empty
        .withClang(Paths.get("/usr/bin/clang"))
        .withClangPP(Paths.get("/usr/bin/clang++"))
        .withTargetTriple(Some("x86_64-unknown-linux-gnu"))
        .withGC(GC.immix)
        .withMode(Mode.debug)
        .withLTO(LTO.none)
        .withMultithreading(Some(false))
        .withLinktimeProperties(Map("a" -> 1, "b" -> true))
        .withCompileOptions(Seq("-fPIC"))
        .withLinkingOptions(Seq("-fuse-ld=lld"))
        .withSourceLevelDebuggingConfig(SourceLevelDebuggingConfig.disabled)

    val h1 = CacheKey.globalConfigHash(mk)
    val h2 = CacheKey.globalConfigHash(mk)
    assertEquals(h1, h2)
  }

  @Test def emptyStringSha256MatchesRfc(): Unit = {
    assertEquals(
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      CacheKey.sha256Hex("")
    )
  }

  @Test def libContentSubKeyDependsOnJarHash(): Unit = {
    val native = NativeConfig.empty
    val k1 = CacheKey.libContentSubKey("aaa", native)
    val k2 = CacheKey.libContentSubKey("bbb", native)
    assertNotEquals(k1, k2)
  }
}
