#!/usr/bin/env bash
# Usage: run-storm.sh <binary> <runs> <seconds-per-run> <outdir>
# Each run gets a watchdog; hangs are sampled, crash reports collected.
set -u
bin=$1 runs=$2 secs=$3 out=$4
mkdir -p "$out"
ok=0 sigill=0 crash=0 hang=0
for i in $(seq 1 "$runs"); do
  "$bin" "$secs" >"$out/run-$i.log" 2>&1 &
  pid=$!
  limit=$((secs * 4 + 30)) waited=0
  while kill -0 $pid 2>/dev/null && [ $waited -lt $limit ]; do
    sleep 1; waited=$((waited + 1))
  done
  if kill -0 $pid 2>/dev/null; then
    echo "run $i: HANG"; hang=$((hang + 1))
    sample $pid 5 -file "$out/sample-$i.txt" >/dev/null 2>&1 || true
    kill -9 $pid; wait $pid 2>/dev/null
    continue
  fi
  wait $pid; rc=$?
  if [ $rc -eq 0 ] && grep -q STORM-OK "$out/run-$i.log"; then ok=$((ok + 1))
  elif [ $rc -eq 132 ]; then echo "run $i: SIGILL"; sigill=$((sigill + 1))
  else echo "run $i: exit $rc"; crash=$((crash + 1)); fi
done
cp ~/Library/Logs/DiagnosticReports/*.ips "$out/" 2>/dev/null || true
echo "SUMMARY ok=$ok sigill=$sigill crash=$crash hang=$hang runs=$runs"
[ $ok -eq "$runs" ]
