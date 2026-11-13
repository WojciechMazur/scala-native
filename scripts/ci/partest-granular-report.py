#!/usr/bin/env python3
"""Compares partest runs with today's builds and granular builds.

Reads the artifacts of .github/workflows/partest-granular.yml, one directory
per leg with summary.env, stats.csv (written by MainGenericRunner) and
failed.txt, and prints a Markdown report.
"""

import csv
import statistics
import sys
from pathlib import Path

LEGS = ["baseline", "static", "dynamic", "dynamic-lld"]


def read_env(path):
    values = {}
    if path.exists():
        for line in path.read_text().splitlines():
            if "=" in line:
                key, value = line.split("=", 1)
                values[key] = value
    return values


def read_stats(path):
    rows = []
    if path.exists():
        with path.open() as f:
            for row in csv.reader(f):
                if len(row) == 6:
                    test, at, build, run, size, passed = row
                    rows.append(
                        {
                            "test": test,
                            "at": int(at),
                            "build": int(build),
                            "run": int(run),
                            "size": int(size),
                            "passed": passed == "true",
                        }
                    )
    return sorted(rows, key=lambda r: r["at"])


def percentile(values, p):
    if not values:
        return 0
    values = sorted(values)
    return values[min(len(values) - 1, int(round(p / 100 * (len(values) - 1))))]


def seconds(ms):
    return f"{ms / 1000:.1f} s"


def duration(total_seconds):
    total_seconds = int(total_seconds)
    return f"{total_seconds // 3600}h{total_seconds % 3600 // 60:02d}m"


def size(n):
    n = float(n)
    for unit in ["B", "KB", "MB", "GB"]:
        if n < 1024 or unit == "GB":
            return f"{n:.1f} {unit}" if unit != "B" else f"{int(n)} B"
        n /= 1024


def main(root):
    root = Path(root)
    legs = {}
    for leg in LEGS:
        directory = root / f"partest-{leg}"
        if directory.exists():
            legs[leg] = {
                "env": read_env(directory / "summary.env"),
                "stats": read_stats(directory / "stats.csv"),
                "failed": set(
                    (directory / "failed.txt").read_text().split()
                    if (directory / "failed.txt").exists()
                    else []
                ),
            }

    print("## Partests: today's builds vs granular builds\n")
    print(
        "Scala 2.13, debug mode, Linux x86_64. `static`: library units compiled "
        "once into the build cache and linked statically; `dynamic`: linked as "
        "shared libraries of the cache. The cache starts empty in each run.\n"
    )

    header = ["", *legs.keys()]
    rows = []

    def row(name, f):
        rows.append([name, *[f(data) for data in legs.values()]])

    row("Partest wall time", lambda d: duration(int(d["env"].get("wall_seconds", 0))))
    row("Exit code", lambda d: d["env"].get("exit", "?"))
    row("Programs built", lambda d: str(len(d["stats"])))
    row("Build time, sum", lambda d: duration(sum(r["build"] for r in d["stats"]) / 1000))
    row("Build time, median", lambda d: seconds(percentile([r["build"] for r in d["stats"]], 50)))
    row("Build time, p90", lambda d: seconds(percentile([r["build"] for r in d["stats"]], 90)))
    row("Build time, max", lambda d: seconds(max([r["build"] for r in d["stats"]], default=0)))
    row(
        "First build (cold cache)",
        lambda d: seconds(d["stats"][0]["build"]) if d["stats"] else "-",
    )
    row("Run time, sum", lambda d: duration(sum(r["run"] for r in d["stats"]) / 1000))
    row(
        "Executable size, median",
        lambda d: size(percentile([r["size"] for r in d["stats"] if r["size"] >= 0], 50)),
    )
    row(
        "Executables, total",
        lambda d: size(sum(r["size"] for r in d["stats"] if r["size"] >= 0)),
    )
    row("Build cache size", lambda d: size(d["env"].get("store_bytes", 0)))
    row("Shared libraries", lambda d: d["env"].get("libraries", "-"))
    row("Shared libraries, size", lambda d: size(d["env"].get("library_bytes", 0)))
    row("Cached actions", lambda d: d["env"].get("actions", "-"))
    row("Max RSS (kB)", lambda d: d["env"].get("max_rss_kb", "-"))
    row("Failed tests", lambda d: str(len(d["failed"])))

    baseline = legs.get("baseline", {}).get("failed")
    if baseline is not None:
        row("Failing only here", lambda d: str(len(d["failed"] - baseline)))
        row("Passing only here", lambda d: str(len(baseline - d["failed"])))

    print("| " + " | ".join(header) + " |")
    print("|" + "---|" * len(header))
    for r in rows:
        print("| " + " | ".join(r) + " |")

    if baseline is not None:
        for leg, data in legs.items():
            if leg == "baseline":
                continue
            regressions = sorted(data["failed"] - baseline)
            fixed = sorted(baseline - data["failed"])
            if regressions:
                print(f"\n### Failing only with `{leg}` ({len(regressions)})\n")
                for test in regressions:
                    print(f"- `{test}`")
            if fixed:
                print(f"\n### Passing only with `{leg}` ({len(fixed)})\n")
                for test in fixed:
                    print(f"- `{test}`")

    # Slowest builds after the cache is warm show what is left to share
    for leg, data in legs.items():
        if leg == "baseline" or len(data["stats"]) < 2:
            continue
        slowest = sorted(data["stats"][1:], key=lambda r: -r["build"])[:10]
        print(f"\n### Slowest builds with `{leg}`\n")
        print("| test | build | run | executable |")
        print("|---|---|---|---|")
        for r in slowest:
            print(f"| `{r['test']}` | {seconds(r['build'])} | {seconds(r['run'])} | {size(r['size'])} |")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "results")
