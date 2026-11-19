package build

import sbt.Keys._
import sbt._

/** Resolves from the mirror given by MAVEN_CENTRAL_MIRROR before Maven Central,
 *  used in CI where Maven Central rate limits requests. lm-coursier ignores the
 *  coursier.mirrors setting.
 */
object MavenMirror extends AutoPlugin {
  override def trigger = allRequirements

  override def projectSettings: Seq[Setting[?]] =
    sys.env.get("MAVEN_CENTRAL_MIRROR").filter(_.nonEmpty).toSeq.map { url =>
      externalResolvers := Def.uncached {
        val resolvers = externalResolvers.value
        val mirror = MavenRepository("maven-central-mirror", url)
        val central = resolvers.indexWhere {
          case repo: MavenRepository => repo.root.contains("repo1.maven.org")
          case _                     => false
        }
        if (central < 0) resolvers :+ mirror
        else resolvers.patch(central, Seq(mirror), 0)
      }
    }
}
