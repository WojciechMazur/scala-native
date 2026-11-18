// GitHub CLI helpers shared by the CI scripts, included with `//> using file gh.scala`

def env(name: String): String = sys.env.getOrElse(name, "")

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

/** All elements of a paginated GitHub API endpoint, `extract` selects the list
 *  from a page.
 */
def ghApiPages(
    endpoint: String,
    extract: ujson.Value => ujson.Value = identity
): Seq[ujson.Value] =
  val separator = if endpoint.contains('?') then '&' else '?'
  Iterator
    .from(1)
    .map(page =>
      extract(ghJson(s"api $endpoint${separator}per_page=100&page=$page"))
    )
    .map(_.arr.toSeq)
    .takeWhile(_.nonEmpty)
    .flatten
    .toSeq
