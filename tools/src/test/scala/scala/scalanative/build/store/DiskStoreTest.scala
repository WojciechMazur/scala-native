package scala.scalanative
package build
package store

import java.nio.file.attribute.FileTime
import java.nio.file.{Files, Path}

import org.junit.Assert._
import org.junit.Assume.assumeFalse
import org.junit.Test

class DiskStoreTest {

  private def withDir(body: Path => Unit): Unit = {
    val dir = Files.createTempDirectory("sn-store-test")
    try body(dir)
    finally IO.deleteRecursive(dir)
  }

  private def file(dir: Path, name: String, content: String): Path = {
    val path = dir.resolve(name)
    IO.write(path, content)
    path
  }

  private def read(path: Path): String = new String(Files.readAllBytes(path))

  private def key(name: String) =
    ActionKey.builder("test", 1).add("name", name).result()

  private def age(path: Path, millis: Long): Unit =
    Files.setLastModifiedTime(
      path,
      FileTime.fromMillis(System.currentTimeMillis() - millis)
    )

  @Test def keyFieldsDoNotRunTogether(): Unit = {
    def keyOf(name: String, value: String) =
      ActionKey.builder("test", 1).add(name, value).result()
    assertNotEquals(keyOf("a", "bc"), keyOf("ab", "c"))
    assertEquals(keyOf("a", "bc"), keyOf("a", "bc"))
    assertNotEquals(
      ActionKey.builder("test", 1).addAll("flags", Seq("a", "b")).result(),
      ActionKey.builder("test", 1).addAll("flags", Seq("ab")).result()
    )
    assertNotEquals(
      ActionKey.builder("test", 1).result(),
      ActionKey.builder("test", 2).result()
    )
  }

  @Test def storesAndRestoresActionOutputs(): Unit = withDir { dir =>
    val store = new DiskStore(dir.resolve("store"))
    val output = file(dir, "out.o", "object code")
    val digest = store.putBlob(output)
    assertEquals(Digest.of(output), digest)
    store.putAction(key("a"), ActionResult(Seq("object" -> digest)))

    val result = store.getAction(key("a")).get
    assertEquals(Some(digest), result.output("object"))
    val restored = dir.resolve("restored/out.o")
    assertTrue(store.getBlob(digest, restored))
    assertEquals("object code", read(restored))
    if (!scala.util.Properties.isWin) {
      // A link to the blob, which no build may write to
      assertTrue(Files.isSameFile(store.casPath(digest), restored))
      assertFalse(Files.isWritable(restored))
    }
    assertEquals(None, store.getAction(key("b")))
  }

  @Test def resultsWithoutBlobsAreNotPublished(): Unit = withDir { dir =>
    val store = new DiskStore(dir.resolve("store"))
    val missing = Digest.of("never stored")
    assertThrows(
      classOf[IllegalStateException],
      () => store.putAction(key("a"), ActionResult(Seq("o" -> missing)))
    )
    assertEquals(None, store.getAction(key("a")))
  }

  @Test def missingOrCorruptBlobsAreMisses(): Unit = withDir { dir =>
    val store = new DiskStore(dir.resolve("store"))
    val a = store.putBlob(file(dir, "a", "content of a"))
    val b = store.putBlob(file(dir, "b", "content of b"))
    store.putAction(key("a"), ActionResult(Seq("o" -> a)))
    store.putAction(key("b"), ActionResult(Seq("o" -> b)))

    Files.delete(store.casPath(a))
    assertEquals("missing blob", None, store.getAction(key("a")))

    // Same size, different content
    store.casPath(b).toFile().setWritable(true)
    IO.write(store.casPath(b), "content of c")
    val target = file(dir, "target", "previous")
    assertFalse(store.getBlob(b, target))
    assertEquals("target untouched", "previous", read(target))
    assertFalse("corrupt blob deleted", Files.exists(store.casPath(b)))
    assertEquals(None, store.getAction(key("b")))
  }

  @Test def unreadableResultsAreMisses(): Unit = withDir { dir =>
    val store = new DiskStore(dir.resolve("store"))
    val blob = store.putBlob(file(dir, "a", "a"))
    store.putAction(key("a"), ActionResult(Seq("o" -> blob)))
    val ac = dir.resolve("store/ac").resolve(key("a").hash.take(2))
    IO.write(ac.resolve(key("a").hash), "truncated")
    assertEquals(None, store.getAction(key("a")))
  }

  @Test def garbageCollectionRemovesLibrariesOfEvictedActions(): Unit =
    withDir { dir =>
      assumeFalse("Libraries are links", scala.util.Properties.isWin)
      val store = new DiskStore(dir.resolve("store"))
      def library(name: String): Path = {
        val output = store.libraries.resolve(s"libsn-$name.so")
        IO.write(output, s"code of $name")
        val blob = store.putBlob(output)
        store.putAction(key(name), ActionResult(Seq("library" -> blob)))
        IO.write(
          store.libraries.resolve(s"programs/$name"),
          output.getFileName.toString
        )
        age(
          dir
            .resolve("store/ac")
            .resolve(key(name).hash.take(2))
            .resolve(key(name).hash),
          20000
        )
        age(store.casPath(blob), 20000)
        age(output, 20000)
        output
      }
      val used = library("used")
      val evicted = library("evicted")
      assertTrue(store.getAction(key("used")).isDefined)

      store.collectGarbage(maxSizeBytes = 1500, grace = 5000)
      assertTrue(Files.exists(used))
      assertTrue(Files.exists(store.libraries.resolve("programs/used")))
      assertFalse(Files.exists(evicted))
      assertFalse(Files.exists(store.libraries.resolve("programs/evicted")))
    }

  @Test def garbageCollectionEvictsLeastRecentlyUsed(): Unit = withDir { dir =>
    val store = new DiskStore(dir.resolve("store"))
    def put(name: String, size: Int, ageMillis: Long): Digest = {
      val blob = store.putBlob(file(dir, name, name * size))
      store.putAction(key(name), ActionResult(Seq("o" -> blob)))
      val ac = dir.resolve("store/ac").resolve(key(name).hash.take(2))
      age(ac.resolve(key(name).hash), ageMillis)
      age(store.casPath(blob), ageMillis)
      blob
    }
    val oldest = put("x", 4000, ageMillis = 30000)
    val older = put("y", 4000, ageMillis = 20000)
    val recent = put("z", 4000, ageMillis = 10000)
    // A blob of no action, as left by a build that failed to publish
    val orphan = store.putBlob(file(dir, "orphan", "orphan"))
    age(store.casPath(orphan), 10000)
    val fresh = store.putBlob(file(dir, "fresh", "fresh"))

    // Using a result protects it from eviction
    assertTrue(store.getAction(key("x")).isDefined)

    val stats = store.collectGarbage(maxSizeBytes = 13000, grace = 5000)
    assertEquals(1, stats.evictedActions)
    assertTrue(store.getAction(key("x")).isDefined)
    assertEquals(None, store.getAction(key("y")))
    assertTrue(store.getAction(key("z")).isDefined)
    assertFalse(Files.exists(store.casPath(older)))
    assertFalse(Files.exists(store.casPath(orphan)))
    assertTrue("within grace", Files.exists(store.casPath(fresh)))
    assertTrue(Files.exists(store.casPath(oldest)))
    assertTrue(Files.exists(store.casPath(recent)))
  }

  @Test def sourceTreesIgnoreTheirLocation(): Unit = withDir { dir =>
    def tree(root: String) = {
      file(Files.createDirectories(dir.resolve(root)), "a.c", "int a;")
      file(Files.createDirectories(dir.resolve(root).resolve("sub")), "b.h", "")
      SourceTree(dir.resolve(root), _ => true)
    }
    val first = tree("first")
    val second = tree("second")
    assertEquals(first.digest, second.digest)
    assertEquals(Seq("a.c", "sub/b.h"), first.files.map(_._1))

    IO.write(dir.resolve("second/sub/b.h"), "#define B")
    assertNotEquals(
      first.digest,
      SourceTree(dir.resolve("second"), _ => true).digest
    )

    val store = new DiskStore(dir.resolve("store"))
    val copy = store.sources(first)
    assertEquals(copy, store.sources(first))
    assertEquals("int a;", read(copy.resolve("a.c")))
  }
}
