//> using scala 3.9
//> using toolkit default

/** Selects the CI jobs to run for the current event, used by the plan job of
 *  .github/workflows/ci.yml. Writes `tier`, `partest_full` and one entry per
 *  `Category` to $GITHUB_OUTPUT.
 *
 *  Environment: EVENT_NAME, GH_REPO, SHA, GH_TOKEN, GITHUB_OUTPUT; for pull
 *  requests PR_NUMBER, PR_DRAFT, PR_BODY, PR_LABELS (JSON list of names);
 *  DISPATCH_TIER for workflow_dispatch.
 */
import java.nio.file.{FileSystems, Paths}

import scala.util.matching.Regex

/** Path pattern matched against repository-relative paths */
sealed trait Pattern:
  def matches(path: String): Boolean

/** `*` matches within a path segment, `**` across segments */
final case class Glob(glob: String) extends Pattern:
  private val matcher = FileSystems.getDefault.getPathMatcher(s"glob:$glob")
  def matches(path: String): Boolean = matcher.matches(Paths.get(path))

final case class Re(regex: Regex) extends Pattern:
  def matches(path: String): Boolean = regex.findFirstIn(path).isDefined

def globs(patterns: String*): Seq[Pattern] = patterns.map(Glob(_))

extension (patterns: Seq[Pattern])
  def matchAny(path: String): Boolean = patterns.exists(_.matches(path))

/** Extra jobs on pull requests enabled by modified paths */
enum Category(val output: String, val paths: Seq[Pattern]):
  // tools tests for Scala 2.12 and 2.13
  case Tools
      extends Category(
        output = "tools",
        paths = globs(
          "tools/**",
          "nir/**",
          "util/**",
          "nscplugin/**",
          "test-runner/**",
          "test-interface*/**",
          "testing-compiler*/**",
          "project/**"
        )
      )

  // cross compilation for all supported Scala versions
  case Compiler
      extends Category(
        output = "compiler",
        paths = globs(
          "nscplugin/**",
          "junit-plugin/**",
          "scalalib/**",
          "scala3lib/**",
          "project/ScalaVersions.scala"
        )
      )

  // GC rows of the runtime extension matrix
  case GC
      extends Category(
        output = "gc",
        paths = globs("nativelib/src/main/resources/scala-native/gc/**")
      )

  // test-gc on every OS
  case NativeRuntime
      extends Category(
        output = "native_runtime",
        paths = globs("nativelib/src/main/resources/**")
      )

  // NetBSD
  case Posix
      extends Category(
        output = "posix",
        paths = globs(
          "posixlib/**",
          "clib/**",
          "nativelib/src/main/resources/scala-native/platform/posix/**"
        )
      )

  // tvOS, watchOS and iOS device link-only builds
  case Apple
      extends Category(
        output = "apple",
        paths = Seq(Re("(?i)darwin|apple|macos|(^|[/_.-])ios([/_.-]|$)".r))
      )

  // scripted tests (sbt 2) on every OS
  case SbtPlugin
      extends Category(
        output = "sbt_plugin",
        paths = globs(
          "sbt-scala-native/**",
          "scripted-tests/**",
          "project/**",
          "tools/**",
          "test-runner/**"
        )
      )

  // partest infrastructure smoke test
  case Partest
      extends Category(
        output = "partest",
        paths = globs("scala-partest*/**", "scripts/partest-check-files.scala")
      )

enum Tier:
  case Skip, Docs, Draft, PR, Nightly

  def output: String = toString.toLowerCase

object Tier:
  def fromOutput(value: String): Tier =
    values
      .find(_.output == value)
      .getOrElse(sys.error(s"Unknown tier: $value"))

/** Changes to the CI or build definition run every pull request job */
val fullPrPaths = globs(
  ".github/**",
  "ci-docker/**",
  "scripts/ci/**",
  "build.sbt",
  "project/*.sbt",
  "project/build.properties",
  "project/Build.scala",
  "project/Settings.scala",
  "project/Commands.scala",
  "project/MultiScalaProject.scala"
)

val docsPaths = globs("docs/**", "*.md", "**/*.md")

def env(name: String): String = sys.env.getOrElse(name, "")

/** Runs `gh <command>` in the repository from GH_REPO and parses its JSON
 *  output. The command is split on spaces.
 */
def gh(command: String): ujson.Value =
  val args = "gh" +: command.split(' ').toSeq
  val out = os.proc(args).call(stderr = os.Pipe).out.text()
  if out.trim.isEmpty then ujson.Null else ujson.read(out)

def changedFiles(repo: String, pr: String): Seq[String] =
  Iterator
    .from(1)
    .map(page => gh(s"api repos/$repo/pulls/$pr/files?per_page=100&page=$page"))
    .map(_.arr.toSeq)
    .takeWhile(_.nonEmpty)
    .flatten
    .flatMap(f =>
      f("filename").str +: f.obj.get("previous_filename").map(_.str).toSeq
    )
    .toSeq

def lastNightlySha(): Option[String] =
  val query = "--workflow ci.yml --event schedule --status completed --limit 1"
  try gh(s"run list $query --json headSha").arr.headOption.map(_("headSha").str)
  catch
    case e: os.SubprocessException =>
      // e.g. no scheduled run yet: run the nightly
      println(s"Cannot find the last nightly run: ${e.result.err.text().trim}")
      None

case class Plan(tier: Tier, categories: Set[Category], partestFull: Boolean)

def plan(): Plan =
  val all = Category.values.toSet
  val repo = env("GH_REPO")
  env("EVENT_NAME") match
    case "schedule" =>
      val previous = lastNightlySha()
      if previous.contains(env("SHA")) then
        println(
          s"Nothing committed since the last nightly (${previous.get}), skipping"
        )
        Plan(Tier.Skip, Set.empty, partestFull = false)
      else Plan(Tier.Nightly, all, partestFull = true)

    case "workflow_dispatch" =>
      val tier = Option(env("DISPATCH_TIER"))
        .filter(_.nonEmpty)
        .fold(Tier.Nightly)(Tier.fromOutput)
      Plan(tier, all, partestFull = tier == Tier.Nightly)

    case "pull_request" =>
      val body = env("PR_BODY")
      val labels = ujson
        .read(Option(env("PR_LABELS")).filter(_.nonEmpty).getOrElse("[]"))
        .arr
        .map(_.str)
        .toSet
      val files = changedFiles(repo, env("PR_NUMBER"))
      println(s"${files.size} changed files")
      val partestFull = body.contains("[test-partest]") || labels("ci:partest")
      if body.contains("[test-extended]") || labels("ci:full") then
        Plan(Tier.Nightly, all, partestFull)
      else if env("PR_DRAFT") == "true" then
        Plan(Tier.Draft, Set.empty, partestFull = false)
      else if files.nonEmpty && files.forall(docsPaths.matchAny) then
        Plan(Tier.Docs, Set.empty, partestFull = false)
      else if files.isEmpty || files.exists(fullPrPaths.matchAny) then
        println("CI or build definition changed: running every PR job")
        Plan(Tier.PR, all, partestFull)
      else
        val enabled = all.filter(c => files.exists(c.paths.matchAny))
        Plan(Tier.PR, enabled, partestFull)

    case other =>
      sys.error(s"Unsupported event: $other")

@main def run(): Unit =
  val result = plan()
  val lines =
    s"tier=${result.tier.output}" +:
      s"partest_full=${result.partestFull}" +:
      Category.values.toSeq.map(c =>
        s"${c.output}=${result.categories.contains(c)}"
      )
  lines.foreach(println)
  os.write.append(os.Path(env("GITHUB_OUTPUT")), lines.mkString("", "\n", "\n"))
