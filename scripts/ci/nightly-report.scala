//> using scala 3.9
//> using toolkit default

/** Reports results of a scheduled CI run as GitHub issues, used by
 *  .github/workflows/nightly-report.yml.
 *
 *    - First attempt with failed jobs: re-runs the failed jobs once and stops.
 *      The script runs again when the re-run completes.
 *    - Otherwise: one rolling issue per failing job (label `ci-nightly`). A new
 *      failure opens an issue or comments on the open one, a job passing again
 *      gets its issue commented and closed.
 *
 *  Environment: GH_TOKEN, GH_REPO, RUN_ID, RUN_ATTEMPT, HEAD_SHA, RUN_URL
 */

val Label = "ci-nightly"
val TitlePrefix = "Nightly CI failure: "
// Jobs not tied to the code under test
val IgnoredJobs = Set("Plan", "CI OK")
val FailedConclusions = Set("failure", "timed_out", "cancelled")

def env(name: String): String =
  sys.env.getOrElse(name, sys.error(s"Missing environment variable $name"))

/** Runs `gh <command> --<flag> <value>...` in the repository from GH_REPO. The
 *  command is split on spaces, flag values are passed as they are.
 */
def gh(command: String, flags: (String, String)*): String =
  val args =
    command.split(' ').toSeq ++ flags.flatMap((f, v) => Seq(s"--$f", v))
  os.proc("gh" +: args).call(stderr = os.Pipe).out.text()

def ghJson(command: String): ujson.Value =
  val out = gh(command)
  if out.trim.isEmpty then ujson.Null else ujson.read(out)

case class Job(name: String, conclusion: Option[String])

def runJobs(repo: String, runId: String): Seq[Job] =
  val jobs = s"repos/$repo/actions/runs/$runId/jobs?filter=latest&per_page=100"
  Iterator
    .from(1)
    .map(page => ghJson(s"api $jobs&page=$page")("jobs").arr.toSeq)
    .takeWhile(_.nonEmpty)
    .flatten
    .map(j => Job(j("name").str, j("conclusion").strOpt))
    .toSeq

@main def run(): Unit =
  val repo = env("GH_REPO")
  val runId = env("RUN_ID")
  val attempt = env("RUN_ATTEMPT").toInt
  val sha = env("HEAD_SHA")
  val runUrl = env("RUN_URL")

  val executed = runJobs(repo, runId)
    .filterNot(job => IgnoredJobs(job.name))
    .filter(_.conclusion.exists(_ != "skipped"))
  if executed.isEmpty then
    println("Nightly was skipped (no new commits), nothing to report")
    return

  val failed = executed
    .filter(_.conclusion.exists(FailedConclusions))
    .map(_.name)
    .sorted
  val passed =
    executed.filter(_.conclusion.contains("success")).map(_.name).toSet
  println(s"Attempt $attempt: ${executed.size} jobs, ${failed.size} failed")

  if failed.nonEmpty && attempt == 1 then
    println(s"Re-running failed jobs once before reporting:")
    failed.foreach(name => println(s"  $name"))
    gh(s"run rerun $runId --failed")
    return

  val label = s"$Label --force --color B60205"
  gh(s"label create $label", "description" -> "Failure in a scheduled CI run")
  val issues = s"issue list --label $Label --state open --limit 200"
  val openIssues: Map[String, Int] =
    ghJson(s"$issues --json number,title").arr
      .map(issue => issue("title").str -> issue("number").num.toInt)
      .collect {
        case (title, number) if title.startsWith(TitlePrefix) =>
          title.stripPrefix(TitlePrefix) -> number
      }
      .toMap

  for job <- failed do
    val title = TitlePrefix + job
    val body =
      s"Job `$job` failed in the [scheduled CI run]($runUrl) for $sha (also after a re-run)."
    openIssues.get(job) match
      case Some(number) =>
        gh(s"issue comment $number", "body" -> body)
        println(s"Commented on #$number: $job")
      case None =>
        gh(s"issue create --label $Label", "title" -> title, "body" -> body)
        println(s"Opened issue: $job")

  for (job, number) <- openIssues if passed(job) do
    val comment = s"Passing again in the [scheduled CI run]($runUrl) for $sha."
    gh(s"issue close $number", "comment" -> comment)
    println(s"Closed #$number: $job")
