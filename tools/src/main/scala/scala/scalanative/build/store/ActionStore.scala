package scala.scalanative
package build
package store

import java.nio.file.Path

/** Storage of action results and the blobs they refer to.
 *
 *  Same split as Bazel's disk cache and the remote execution API: an action
 *  cache maps action keys to results, a content-addressed store holds blobs by
 *  digest. A result is published only after all its blobs, and a result whose
 *  blobs are missing is a miss, so readers never observe partial entries.
 */
private[scalanative] trait ActionStore {

  /** The result stored for `key`, if it and all its output blobs exist. */
  def getAction(key: ActionKey): Option[ActionResult]

  /** Publishes `result` for `key`. Its output blobs must already be stored. */
  def putAction(key: ActionKey, result: ActionResult): Unit

  /** Copies the blob with `digest` to `target`, replacing it. Returns false if
   *  the blob is missing or its content does not match the digest.
   */
  def getBlob(digest: Digest, target: Path): Boolean

  /** Stores the content of `file` and returns its digest. */
  def putBlob(file: Path): Digest
}
