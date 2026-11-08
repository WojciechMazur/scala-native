#!/usr/bin/env bash
# Repro helper: runs `sbt <cmd>` N times and reports the failure count.
# usage: repro-loop.sh <iterations> <first sbt command> <repeated sbt command>
set -u
n=$1 first=$2 repeated=$3
fails=0
for i in $(seq 1 "$n"); do
  cmd=$repeated
  [ "$i" = 1 ] && cmd=$first
  echo "=== repro iteration $i/$n: $cmd"
  start=$(date +%s)
  if sbt -J-Xmx5G "$cmd"; then
    echo "=== repro iteration $i/$n: OK ($(( $(date +%s) - start ))s)"
  else
    fails=$((fails + 1))
    echo "::warning::repro iteration $i/$n FAILED ($(( $(date +%s) - start ))s)"
  fi
done
echo "=== repro failures: $fails/$n"
echo "repro-failures=$fails/$n" >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
[ "$fails" = 0 ]
