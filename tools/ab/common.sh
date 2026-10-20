# Sourced by the A/B scripts. Works from the repo root; results go to $AB_OUT (default backup/ab, gitignored).
set -u
AB=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO=$(cd "$AB/../.." && pwd)
cd "$REPO" || exit 1
: "${TV_HOST:?set TV_HOST to the TV IP address}"
export TV_HOST
OUT=${AB_OUT:-$REPO/backup/ab}
mkdir -p "$OUT"
EV=/data/local/tmp/ev.txt   # the remote-press watcher's log on the TV
t() { python3 -I "$AB/tvrun.py" "${T:-110}" - "$@" 2>&1 | grep -v "Broken pipe"; }      # one command
tb() { python3 -I "$AB/tvrun.py" 300 "$1" --batch 2>&1 | grep -v "Broken pipe"; }      # a file of commands
pause() { perl -e "sleep $1"; }                                                       # macOS has no timeout(1)
# Real remote presses are key events (type 0001) in getevent; scripted `input keyevent` presses never appear.
touched() { [ "$(t "grep -c '^/dev/input/event[0-9]*: 0001 ' $EV 2>/dev/null" | tr -dc 0-9)" != "0" ]; }
watch_start() {
  t "pkill getevent; rm -f $EV" >/dev/null
  (python3 -I "$AB/tvrun.py" 3700 - "timeout 3600 getevent > $EV 2>/dev/null" </dev/null >/dev/null 2>&1 &)
  pause 4
}
# getevent can take a moment to exit after pkill: check, and only give up after kill -9 (no watcher is left running).
watch_stop() {
  local i
  for i in 1 2 3 4 5; do
    t "pkill getevent" >/dev/null; pause 1
    [ -z "$(t 'pgrep getevent' | tr -dc 0-9)" ] && break
  done
  t "pkill -9 getevent; rm -f $EV" >/dev/null
  [ -z "$(t 'pgrep getevent' | tr -dc 0-9)" ] || echo "$(ts) WARNING: getevent still running on the TV"
}
# Refuse to run against any other device (another Android TV is on the same network).
model_check() {
  local want=${AB_MODEL:-MiTV-MOOR2} got
  got=$(T=30 t "getprop ro.product.model" | tr -d '\r\n')
  [ "$got" = "$want" ] || { echo "STOP: the device at $TV_HOST is '$got', not '$want'"; exit 5; }
}
ts() { date +%T; }
