package scala.scalanative
package build
package store

import java.nio.file.{Files, Paths}

/** Holds the lock of an action in another process, for `DiskStoreTest`: locks
 *  the action `name` of the store at `root`, creates `locked`, and releases the
 *  lock once `release` exists.
 */
object ActionLockProbe {
  def key(name: String): ActionKey =
    ActionKey.builder("test", 1).add("name", name).result()

  def main(args: Array[String]): Unit = {
    val Array(root, name, locked, release) = args
    val lock = new DiskStore(Paths.get(root)).lockAction(key(name), () => ())
    try {
      Files.createFile(Paths.get(locked))
      while (!Files.exists(Paths.get(release))) Thread.sleep(20)
    } finally lock.close()
  }
}
