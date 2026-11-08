#!/usr/bin/env bash
# Dumps all-thread stacks of Scala Native test binaries that appear hung.
#
# CI steps are killed on timeout without any diagnostics, so start this in the
# background before running sbt:
#
#   scripts/ci/hang-watchdog.sh "$RUNNER_TEMP/hang-dumps" &
#
# A test process alive for longer than PROCESS_THRESHOLD seconds is dumped
# once. After DEADLINE seconds every running test process is dumped and the
# watchdog exits, so keep DEADLINE below the step's timeout-minutes. Dumps are
# printed to the log and kept in OUT_DIR for upload. The watchdog stops when
# its parent shell exits.
set -u

OUT_DIR=${1:?usage: hang-watchdog.sh <out-dir> [process-threshold-sec] [deadline-sec]}
PROCESS_THRESHOLD=${2:-1500}
DEADLINE=${3:-2460}
POLL_INTERVAL=30
# Native test binaries linked by sbt, e.g. target/out/native0.5/scala-3.x/tests/tests-test
PATTERN='/target/out/native[^ ]*-test(\.exe)?( |$)'

mkdir -p "$OUT_DIR"
parent=$PPID
started=$(date +%s)

elapsed_seconds() {
  # ps etime format: [[dd-]hh:]mm:ss
  ps -o etime= -p "$1" 2>/dev/null | tr -d ' ' |
    awk -F'[-:]' '{ n = NF; t = $n + 60 * $(n - 1)
      if (n >= 3) t += 3600 * $(n - 2)
      if (n >= 4) t += 86400 * $(n - 3)
      print t }'
}

dump_process() {
  local pid=$1 reason=$2
  local file="$OUT_DIR/$pid.txt"
  {
    echo "# $reason"
    echo "# $(date -u +%Y-%m-%dT%H:%M:%SZ) pid=$pid etime=$(ps -o etime= -p "$pid" | tr -d ' ')"
    ps -o command= -p "$pid"
    case "$(uname -s)" in
      Darwin)
        sample "$pid" 3 -mayDie -file /dev/stdout 2>&1
        if [ -n "${HANG_WATCHDOG_LLDB:-}" ]; then
          echo "# lldb"
          sudo -n lldb -p "$pid" --batch -o "thread backtrace all" \
            -o "register read --all" -o "detach" 2>&1 | head -4000
        fi
        ;;
      Linux)
        if command -v gdb >/dev/null 2>&1; then
          gdb -p "$pid" -batch -ex "thread apply all bt" 2>&1
        elif command -v eu-stack >/dev/null 2>&1; then
          eu-stack -p "$pid" 2>&1
        else
          echo "no stack dumper available (install gdb or elfutils)"
        fi
        ;;
      *)
        echo "stack dumps not supported on $(uname -s)"
        ;;
    esac
  } > "$file" 2>&1
  echo "::group::Hang watchdog: stacks of pid $pid ($reason)"
  cat "$file"
  echo "::endgroup::"
  if [ -n "${HANG_WATCHDOG_KILL:-}" ]; then
    # SIGABRT leaves an .ips report and lets the remaining sbt commands run.
    kill -ABRT "$pid" 2>/dev/null
    sleep 5
    kill -KILL "$pid" 2>/dev/null
  fi
}

dumped=" "
while kill -0 "$parent" 2>/dev/null; do
  now=$(date +%s)
  at_deadline=$((now - started >= DEADLINE))
  for pid in $(pgrep -f "$PATTERN"); do
    case "$dumped" in *" $pid "*) continue ;; esac
    age=$(elapsed_seconds "$pid")
    [ -n "$age" ] || continue
    if [ "$at_deadline" = 1 ]; then
      dump_process "$pid" "watchdog deadline of ${DEADLINE}s reached"
      dumped="$dumped$pid "
    elif [ "$age" -ge "$PROCESS_THRESHOLD" ]; then
      dump_process "$pid" "running for over ${PROCESS_THRESHOLD}s"
      dumped="$dumped$pid "
    fi
  done
  [ "$at_deadline" = 1 ] && exit 0
  sleep "$POLL_INTERVAL"
done
