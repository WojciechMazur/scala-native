package build

/* Note to Contributors:
 *   Scala Native supports a number of Scala versions. These can be
 *   described as Major.Minor.Path.
 *
 *   Support for Scala 2.12.lowest is provided by binary compatibility with
 *   Scala 2.12.highest.
 *
 *   This means that Continuous Integration (CI) is run using
 *   the highest patch version. Scala Native may or may not build from
 *   from scratch when using lower patch versions.
 *
 *   This information can save time and frustration when preparing
 *   contributions for submission: Build privately using highest,
 *   not lowest, patch version.
 */

object ScalaVersions {
  // Versions of Scala used for publishing compiler plugins
  val crossScala212 = crossScalaVersions("2.12", 14 to 20)
  val crossScala213 = crossScalaVersions("2.13", 8 to 17)
  val crossScala3 = List(
    extraCrossScalaVersion("3.").toList,
    scala3RCVersions,
    // windowslib fails to compile with 3.1.{0-1}
    crossScalaVersions("3.1", 2 to 3),
    crossScalaVersions("3.2", 0 to 2),
    crossScalaVersions("3.3", 0 to 7), // LTS
    crossScalaVersions("3.4", 0 to 3),
    crossScalaVersions("3.5", 0 to 2),
    crossScalaVersions("3.6", 2 to 4), // 3.6.0 is broken, 3.6.1 is hotfix
    crossScalaVersions("3.7", 0 to 4)
  ).flatten.distinct

  // Version of Scala 3 standard library sources used for publishing
  // Workaround allowing to produce NIR for Scala 3.2.x+ and allowing to consume existing libraries using 3.1.x
  // 3.3.0 is the last version which can be compiled using 3.1.3 compiler
  val scala3libSourcesVersion = "3.3.0"


  // Tested in scheduled nightly CI to check compiler plugins
  // List maintains only upcoming releases, removed from the list after reaching stable status
  lazy val scala3RCVersions = List("3.8.0-RC1")

  // Scala versions used for publishing libraries
  val scala212: String = crossScala212.last
  val scala213: String = crossScala213.last
  val scala3: String = "3.1.3"

  // minimum version rationale:
  //   1.5 is required for Scala 3 and
  //   1.5.8 has log4j vulnerability fixed
  //   1.9.0 is required in order to use Java >= 21
  //   1.9.4 fixes (Common Vulnerabilities and Exposures) CVE-2022-46751
  //   1.9.6 is current

  val sbt10Version: String = "1.9.6"
  val sbt10ScalaVersion: String = scala212

  val libCrossScalaVersions: Seq[String] = Seq(
    crossScala212,
    crossScala213,
    crossScala3,
  ).flatten.distinct

  private def extraCrossScalaVersion(binVersionPrefix: String) = sys.env
    .get("EXTRA_CROSS_SCALA_VERSION")
    .filter(_.startsWith(binVersionPrefix))

  private def crossScalaVersions(
      baseVersion: String,
      patches: Range.Inclusive
  ): List[String] = {
    patches.map(v => s"$baseVersion.$v") ++
      extraCrossScalaVersion(baseVersion)
  }.distinct.toList
}
