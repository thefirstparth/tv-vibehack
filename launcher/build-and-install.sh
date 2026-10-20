#!/usr/bin/env bash
# Build the launcher APK and install it on the TV as the default home screen.
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
STOCK_LAUNCHER="${STOCK_LAUNCHER:-com.google.android.tvlauncher}"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export PATH="$JAVA_HOME/bin:$PATH"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
# the release build: shrunk by R8, not debuggable, signed with ~/.android/debug.keystore (as debug builds are), so
# pm install -r updates an installed debug build in place
./gradlew :app:assembleRelease -q --console=plain
PY=../tools/venv/bin/python
APK=app/build/outputs/apk/release/app-release.apk
"$PY" -I ../tools/b64push.py "$APK" /data/local/tmp/tvhome.apk
# Install on its own: in the batch below a failed install (e.g. "not enough space" on a full /data)
# would not stop the stock launcher from being disabled.
OUT=$("$PY" -I ../tools/tv.py 'pm install -r /data/local/tmp/tvhome.apk; rm -f /data/local/tmp/tvhome.apk')
echo "$OUT"
case "$OUT" in
  *Success*) ;;
  *) echo "ERROR: pm install failed; the stock launcher was left untouched." >&2; exit 1 ;;
esac
printf '%s\n' \
  "pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION 2>/dev/null; echo location-granted" \
  "cmd package set-home-activity --user 0 com.gathin.tvhome" \
  "pm disable-user --user 0 $STOCK_LAUNCHER" \
  | "$PY" -I ../tools/tv.py --batch
echo "Installed. Press Home on the remote to see it."
