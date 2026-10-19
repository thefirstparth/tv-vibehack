#!/bin/bash
# night.sh <test> <minutes> <current.apk> <new.apk> <pattern> <rounds>
#   Restart the TV, rest a minute, start the remote-press watcher, then <rounds> rounds of slots. <pattern> is CNC
#   (current, new, current: the two current slots measure the noise) or CN. A slot that would start too close to the
#   time limit is not started. On a remote press or a failure: put <current.apk> back, press Home, exit non-zero.
#   Always stops the watcher. The log is $AB_OUT/<test>.log; judge it with judge.py.
source "$(dirname "$0")/common.sh"
test=$1; minutes=$2; cur=$3; new=$4; pattern=$5; rounds=$6
deadline=$(( $(date +%s) + minutes * 60 )); log="$OUT/$test.log"
model_check
stop() {   # <rc> <why>
  echo "$(ts) $test: STOP ($2), putting the current build back" | tee -a "$log"
  "$AB/put.sh" "$cur" >/dev/null || echo "$(ts) $test: RESTORE FAILED, install $cur by hand" | tee -a "$log"
  t "input keyevent KEYCODE_HOME" >/dev/null; watch_stop; exit "$1"
}
echo "$(ts) $test: restart ($pattern x $rounds, current $(basename "$cur"), new $(basename "$new"))" | tee -a "$log"
T=30 t reboot >/dev/null 2>&1; pause 75
for i in $(seq 1 24); do [ "$(T=20 t getprop sys.boot_completed | tr -dc 0-9)" = 1 ] && break; pause 10; done
[ "$(T=20 t getprop sys.boot_completed | tr -dc 0-9)" = 1 ] || stop 6 "no boot"
pause 60
echo "$(ts) $test: TV clock $(T=20 t date)" | tee -a "$log"
watch_start
for r in $(seq 1 "$rounds"); do
  for (( k = 0; k < ${#pattern}; k++ )); do
    c=${pattern:$k:1}
    if [ "$c" = N ]; then apk=$new; label=N-r$r
    elif [ "$pattern" = CNC ]; then apk=$cur; [ $k = 0 ] && label=C1-r$r || label=C2-r$r
    else apk=$cur; label=C-r$r; fi
    [ $(( $(date +%s) + 200 )) -gt $deadline ] && { echo "$(ts) $test: TIME LIMIT, stopped before $label" | tee -a "$log"; break 2; }
    out=$("$AB/slot.sh" "$apk" "$label"); rc=$?
    echo "$out" | tee -a "$log"
    [ $rc -ne 0 ] && stop $rc "slot $label rc=$rc"
  done
done
# the round's last slot may have been the new build: the current build goes back on in every case
"$AB/put.sh" "$cur" >/dev/null || stop 4 "restore"
t "input keyevent KEYCODE_HOME" >/dev/null
touched && stop 3 "remote pressed at the end"
watch_stop
echo "$(ts) $test: DONE (current build back on, Home)" | tee -a "$log"
