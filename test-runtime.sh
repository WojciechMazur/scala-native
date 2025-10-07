#!/bin/bash
set -euo pipefail

monitor_loop() {
  while :; do
    echo "===== RESOURCE SNAPSHOT $(date -u +"%Y-%m-%dT%H:%M:%SZ") ====="
    top -b -n1 | head -5 || true

    echo "--- free -h ---"
    free -h || true

    echo "--- top processes by RSS (KB) ---"
    ps -eo pid,ppid,pcpu,pmem,rss,etime,comm --sort=-rss | head -n 15 || true

    echo "--- processes ending with '-test' (per-process memory) ---"
    hits="$(ps -eo pid,pcpu,pmem,rss,comm --sort=-rss \
      | awk '$5 ~ /-test$/ {printf "pid=%s cpu=%s%% mem=%s%% rss=%sK cmd=%s\n",$1,$2,$3,$4,$5}')"

    if [ -n "${hits}" ]; then
      printf "%s\n" "${hits}"
      printf "%s\n" "${hits}" | while read -r line; do
        pid="$(printf "%s" "$line" | awk -F'[ =]' '{print $2}')"
        if [ -r "/proc/${pid}/status" ]; then
          rss_kb="$(awk '/VmRSS:/ {print $2}' "/proc/${pid}/status" 2>/dev/null || echo 0)"
          if [ -r "/proc/${pid}/smaps_rollup" ]; then
            pss_kb="$(awk '/Pss:/ {print $2}' "/proc/${pid}/smaps_rollup" 2>/dev/null || echo 0)"
            echo "  pid=${pid} VmRSS=${rss_kb}kB PSS=${pss_kb}kB"
          else
            echo "  pid=${pid} VmRSS=${rss_kb}kB"
          fi
        fi
      done
    else
      echo "(none)"
    fi

    echo
    sleep 5
  done
}

monitor_loop & MONITOR_PID=$!
trap 'kill ${MONITOR_PID} 2>/dev/null || true' EXIT INT TERM

# ✅ close the quote here
sbt "test-runtime $1"
