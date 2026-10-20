#!/bin/bash
# saturday.sh <minutes> <current.apk> <new.apk> <rounds> [YYYY-MM-DDTHH:MM]
#   The Saturday test: record auto_time and auto_time_zone, turn automatic time off, set the TV's clock to a Saturday
#   (default: the coming Saturday 13:00 in the TV's time zone), run night.sh saturday (it restarts the TV), then set
#   the clock from this computer and put both settings back as recorded. It restores even when night.sh stops.
source "$(dirname "$0")/common.sh"
minutes=$1; cur=$2; new=$3; rounds=$4; at=${5:-}
model_check
tz=$(T=20 t "getprop persist.sys.timezone" | tr -d '\r\n')
ms=$(python3 -I - "$tz" "$at" <<'PY'
import sys, datetime as d
from zoneinfo import ZoneInfo
z = ZoneInfo(sys.argv[1] or "Asia/Kolkata")
if sys.argv[2]:
    when = d.datetime.fromisoformat(sys.argv[2]).replace(tzinfo=z)
else:
    now = d.datetime.now(z)
    when = (now + d.timedelta(days=(5 - now.weekday()) % 7 or 7)).replace(hour=13, minute=0, second=0, microsecond=0)
assert when.weekday() == 5, "not a Saturday"
print(int(when.timestamp() * 1000))
PY
) || { echo "STOP: bad Saturday time"; exit 5; }
read -r a_t a_tz <<<"$(T=20 t 'echo $(settings get global auto_time) $(settings get global auto_time_zone)')"
echo "$(ts) saturday: recorded auto_time=$a_t auto_time_zone=$a_tz (tz $tz)" | tee -a "$OUT/saturday.log" > "$OUT/time-before.txt"
restore() {
  T=20 t "cmd alarm set-time $(( $(date +%s) * 1000 ))" >/dev/null
  T=20 t "settings put global auto_time $a_t; settings put global auto_time_zone $a_tz" >/dev/null
  for i in 1 2 3 4 5 6; do [ "$(T=20 t 'date +%Y-%m-%d' | tr -d '\r\n')" = "$(date +%Y-%m-%d)" ] && break; pause 10; done
  echo "$(ts) saturday: TV clock now $(T=20 t 'date; echo auto_time=$(settings get global auto_time) auto_time_zone=$(settings get global auto_time_zone)' | tr '\n' ' ')(Mac: $(date))" | tee -a "$OUT/saturday.log"
}
T=20 t "settings put global auto_time 0; cmd alarm set-time $ms" >/dev/null
echo "$(ts) saturday: TV clock set to $(T=20 t date)" | tee -a "$OUT/saturday.log"
"$AB/night.sh" saturday "$minutes" "$cur" "$new" CN "$rounds"; rc=$?
restore
exit $rc
