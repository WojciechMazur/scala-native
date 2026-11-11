bin=$(ls -d target/out/native0.5/*/*/external-scenarios/configurations/boehm-mt/cpp* | head -1)
echo "binary: $bin"
fail=0; reclaim=0
for i in $(seq 1000); do
  out=$("$bin" CALL-03 --multithreaded 2>&1)
  if ! echo "$out" | grep -q "COMPLETE CALL-03"; then
    fail=$((fail+1)); echo "$out" | grep -q RECLAIM-FAIL && reclaim=$((reclaim+1)); echo "--- run $i"; echo "$out" | tail -4
  fi
done
echo "STRESS FAILS=$fail RECLAIM=$reclaim / 1000"
