#!/bin/sh
set -eu

export SCALANATIVE_TEST_PREFETCH_DEBUG_INFO=1
export SCALANATIVE_TEST_DEBUG_SIGNALS=1
# The test adapter starts one native test process per sbt test thread, and
# each Immix/Commix process defaults its max heap to physical memory. Cap it
# (as the Docker-based CI does) so several test processes and the sbt JVM fit
# in the VM's memory together.
export GC_MAXIMUM_HEAP_SIZE=1G
# Override -Xmx5G from .sbtopts; matches the Linux workflow.
SBT_MEM_OPTS="-J-Xmx3G"

# Show which process the OOM killer picked if a run is killed.
on_exit() {
  status=$?
  if [ "$status" -ne 0 ]; then
    echo "Tests failed with exit status $status; last kernel messages:"
    dmesg | tail -n 30 || true
  fi
}
trap on_exit EXIT

# The checkout retains the host runner's UID when copied into the root-run VM.
git config --global --add safe.directory "$PWD"
# The VM hostname is not necessarily published in DNS or its base hosts file.
printf '127.0.0.1 %s\n' "$(hostname)" >> /etc/hosts

uname -a
java -version
clang --version
IF6=$(ifconfig -l | tr ' ' '\n' | grep -v '^lo' | head -1)
ifconfig "$IF6" inet6 fd00:3630::1 prefixlen 64 alias
sleep 3
ifconfig "$IF6"
sysctl net.inet6.ip6.v6only
sbt --server --batch $SBT_MEM_OPTS --sbt-version "$SBT_VERSION" \
  "tests3/testOnly org.scalanative.testsuite.javalib.net.*"
