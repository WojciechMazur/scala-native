#!/usr/bin/env bash
# Usage: with-watchdog.sh <seconds> <cmd...>; samples and kills on timeout.
secs=$1; shift
"$@" &
pid=$!
for _ in $(seq 1 "$secs"); do
  kill -0 $pid 2>/dev/null || break
  sleep 1
done
if kill -0 $pid 2>/dev/null; then
  echo "HANG after ${secs}s: $*"
  sample $pid 3 -file /tmp/sample.txt >/dev/null 2>&1
  sed -n '/Call graph:/,/Total number in stack/p' /tmp/sample.txt | head -80
  kill -9 $pid
  wait $pid 2>/dev/null
  exit 124
fi
wait $pid
rc=$?
echo "exit=$rc"
exit $rc
