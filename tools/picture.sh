#!/usr/bin/env bash
# Save, compare or restore every picture setting (the TV's `picture_*` and `tv_picture_*` global settings).
# Restore sets only the ones that differ, picture mode first (other values can be per mode), colour temperature next
# (the TV works out the red/green/blue gains from it), the gains last. Changes apply at once; set from the computer they
# may not survive a restart, so run restore again after one if the picture looks different.
#
# Usage: TV_HOST=192.168.0.15 tools/picture.sh save    examples/xiaomi-mitv-moor2/picture/<name>.txt
#        TV_HOST=192.168.0.15 tools/picture.sh diff    examples/xiaomi-mitv-moor2/picture/<name>.txt
#        TV_HOST=192.168.0.15 tools/picture.sh restore examples/xiaomi-mitv-moor2/picture/<name>.txt
set -euo pipefail
cd "$(dirname "$0")/.."
CMD="${1:?usage: picture.sh save|diff|restore <file>}"
FILE="${2:?usage: picture.sh save|diff|restore <file>}"
PY=tools/venv/bin/python

# key value, one a line, sorted; is_support_* is what the TV can do, not a setting
now() {
  "$PY" -I tools/tv.py 'settings list global' </dev/null 2>/dev/null | tr -d '\r' \
    | grep -E '^(picture_|tv_picture_|tv_dynamic_backlight=)' | sed 's/=/ /' | sort
}
saved() { grep -vE '^\s*(#|$)' "$FILE" | sort; }
# mode first, colour temperature next, the gains last, the rest between
order() { awk '{p = $1 == "picture_mode" ? 0 : $1 == "picture_color_temperature" ? 1 : $1 ~ /_gain$/ && $1 ~ /^picture_(red|green|blue)/ ? 3 : 2; print p, $0}' | sort -k1,1n -s | cut -d' ' -f2-; }

case "$CMD" in
save)
  mkdir -p "$(dirname "$FILE")"
  { echo "# Picture settings of the TV, saved $(date '+%Y-%m-%d %H:%M %Z')."
    echo "# Restore: TV_HOST=<ip> tools/picture.sh restore $FILE"
    now; } > "$FILE"
  echo "saved $(grep -vc '^#' "$FILE") settings to $FILE"
  ;;
diff)
  d=$(diff <(saved) <(now) || true)
  if [ -z "$d" ]; then echo "the TV matches $FILE"; else echo "< $FILE   > the TV now"; echo "$d"; fi
  ;;
restore)
  todo=$(comm -23 <(saved) <(now) | order)
  if [ -z "$todo" ]; then echo "the TV already matches $FILE"; exit 0; fi
  echo "$todo" | awk '{print "settings put global " $1 " " $2}' | "$PY" -I tools/tv.py --batch </dev/null
  d=$(diff <(saved) <(now) || true)
  if [ -z "$d" ]; then echo "restored: the TV matches $FILE"; else echo "still different:"; echo "$d"; exit 1; fi
  ;;
*) echo "usage: picture.sh save|diff|restore <file>"; exit 2 ;;
esac
