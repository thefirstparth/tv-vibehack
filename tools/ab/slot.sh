#!/bin/bash
# slot.sh <apk> <label>: one measurement slot, as in examples/xiaomi-mitv-moor2/BENCHMARKS.md. Install it, warm it up
# (one grid run, discarded), then 1 cold start (AB_COLDS=5: five), 1 scripted grid run (janky %, frame-time percentiles, memory), 10 Home
# returns from Media Player (the Home intent's TotalTime; from 2026-10-20 not YouTube, and not Settings, a side panel
# over Home on Google TV, so Home never leaves; no other app is force-stopped
# or has its data touched), and a crash/freeze check. Prints one line.
# Exit 3 on a real remote press, 4 if the install fails.
source "$(dirname "$0")/common.sh"
apk=$1; label=$2
touched && { echo "$(ts) $label TOUCHED (before the slot)"; exit 3; }
"$AB/put.sh" "$apk" >/dev/null || { echo "$(ts) $label INSTALL FAILED"; exit 4; }
t "logcat -b crash -c" >/dev/null
tb "$AB/grid.txt" >/dev/null
# The cold start is started as Home (-a MAIN -c HOME): a plain -n start makes a non-Home task, and the next Home press
# then adds a second Home task beside it (upstream cleanyourtv #888, 24 Oct review).
# AB_COLDS cold starts in a row (default 1); cold= is their median, colds=[...] every one (judge.py pools them)
: > "$OUT/colds.txt"; for i in $(seq 1 "${AB_COLDS:-1}"); do cat "$AB/cold.txt" >> "$OUT/colds.txt"; done
colds=$(tb "$OUT/colds.txt" | awk '/^\$ am start -W .*-n com.gathin.tvhome\//{m=1; next} /^\$ /{m=0} /TotalTime/{if(m) printf "%s,", $2}')
cold=$(echo "$colds" | tr ',' '\n' | grep . | sort -n | awk '{v[NR]=$1} END{if(NR%2) print v[(NR+1)/2]; else print int((v[NR/2]+v[NR/2+1])/2)}')
nav=$(tb "$AB/grid.txt" | awk '/^Total frames/{n=$NF} /^Janky frames:/{j=$4} /^50th percentile/{p50=$3} /^90th percentile/{p90=$3}
  /^95th percentile/{p95=$3} /^99th percentile/{p99=$3} /TOTAL PSS/{m=$3}
  END{printf "janky=%s frames=%s p50=%s p90=%s p95=%s p99=%s pssKB=%s", j, n, p50, p90, p95, p99, m}')
: > "$OUT/rets.txt"
for i in 1 2 3 4 5 6 7 8 9 10; do
  printf '%s\n' "am start -W -n com.xiaomi.mitv.mediaexplorer/.NewScraperMainEntryActivity" \
    "sleep 4" "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME" "sleep 1.5" >> "$OUT/rets.txt"
done
rets=$(tb "$OUT/rets.txt" | awk '/^\$ am start -W -n/{m="y"} /^\$ am start -W -a/{m="h"} /TotalTime/{if(m=="h") printf "%s,", $2}')
top=$(t "dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity' | grep -oE 'u0 [^ ]+'; logcat -b crash -d | grep -c gathin" | tr '\n' ' ')
line="$(ts) $label cold=$cold colds=[$colds] $nav rets=[$rets] state=[$top]"
touched && { echo "$line TOUCHED"; exit 3; }
echo "$line"
