# Xiaomi MiTV-MOOR2 (Google TV): what was done and how to pick it up again

This is the full record for this TV, so the work can continue from any computer or any Claude
session without the laptop or chat it was first done on. Everything needed is in this repo except
the private items listed under [Not in git](#not-in-git), which can all be recreated.

## The TV

| | |
|---|---|
| Model | Xiaomi MiTV-MOOR2, codename `venom` |
| OS | **Google TV** on Android TV 11, firmware `RTM5.220609.003.2674` |
| Network ADB | port 5555, IP was `192.168.0.15` on 2026-10-18 (DHCP, may change) |
| Stock home | `com.google.android.apps.tv.launcherx` (not `com.google.android.tvlauncher`) |
| Live TV app | `com.mitv.livetv` |

Another Android TV is on the same network, so always run `tools/tv.py --probe` and check for
`MiTV-MOOR2` before changing anything.

## Current state (2026-10-18)

1. **Debloat:** the 19 packages in [`debloat.txt`](debloat.txt) are disabled with `pm disable-user`
   (17 debloat entries plus `setupwraith` and `launcherx` for the Home switch below).
2. **Home screen:** the tvOS-style launcher (`com.gathin.tvhome`) is the Home app. It survives a reboot.
   To get there on Google TV, three things were done, one command at a time:
   ```
   pm disable-user --user 0 com.google.android.tungsten.setupwraith
   cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
   pm disable-user --user 0 com.google.android.apps.tv.launcherx
   ```
3. **Dock:** YouTube, Netflix, JioHotstar, FanCode. Grid below, 4 a row: Prime Video, Sony LIV,
   Apple TV, Spotify (row 2, the most used after the dock); 1400, F1 TV, Play Store, Settings (row 3);
   then the rest A–Z. Media Player and Google Play Movies & TV are hidden. All set in
   `launcher/app/src/main/res/values/config.xml` (`dock_apps`, `grid_order`, `hidden_apps`).
   The launcher is also listed in the TV's app list (it declares `LEANBACK_LAUNCHER`).
4. **Kept on purpose:** firmware and Xiaomi update services, Google Assistant voice, Chromecast
   built-in, Netflix, Play Services, Play Store, Live TV, every media/DRM/Dolby package.
5. **Storage cleanup (2026-10-18, /data free 1.1G → 1.7G):** `pm trim-caches 999G`; `pm clear` on the
   disabled `com.mitv.tvhome.atv`, `com.mitv.tvhome.mitvplus`, `com.mitv.tvhome.oemtab`,
   `com.google.android.play.games`; uninstalled ZEE5 (`com.graymatrix.did`), NBA
   (`com.nbaimd.gametime.nba2011`), MUBI (`com.mubi`), Tata Play (`com.tataskymore.open.uat`) and DAZN
   (`com.dazn`). Reinstall any of them from the Play Store.
6. **Tested after the switch and a reboot (2026-10-18):**
   - Home → new launcher, also after a reboot. Every dock app opens and Home returns to the dock.
   - YouTube search keyboard works (Gboard is the default IME).
   - Inputs menu opens; HDMI 1 switches via Live TV. (Rows without names: fixed in the launcher, the
     menu was too narrow. The device on HDMI 3 now shows as "Living room TV" on its port's row.)
   - Voice / Search key reaches Google Assistant, which first asks a one-time data-sharing consent
     on the TV; until that is answered it shows a spinner.
   - **Broken: the remote's Settings key.** Xiaomi's `GlobalKeyReceiver`
     (`com.xiaomi.android.tvsetup.partnercustomizer`) always opens launcherx's dashboard and crashes
     with launcherx disabled. Use the launcher's Settings pill, which opens Android TV Settings.
   - Launcher focus after Home and Down from the top bar: fixed (focus returns to the last tile; Down
     goes to the dock).

7. **1400 app (2026-10-18):** `com.gathin.tv1400` (launcher/app1400) is installed beside the launcher:
   the paper on the TV (sections as tabs; Left and Right move between them), opened from its tile (first in the grid) or the moon
   pill at the start of the top bar. It reads `/api/live/tv?saver=1` only while it is open and closes
   itself after 3 hours without a button press. It is an app, not the TV's screensaver: the screensaver
   settings were recorded and **not changed** (Google's Backdrop stays):
   ```
   settings get secure screensaver_components   # com.google.android.apps.tv.dreamx/.service.Backdrop
   settings get secure screensaver_enabled      # 1
   settings get system screen_off_timeout       # 600000 (10 minutes: the screensaver starts)
   settings get secure sleep_timeout            # 1800000 (30 minutes: the display turns off)
   ```
   The display's switch-off time is Settings > System > Power & energy > "When inactive" (Energy
   saver / "Turn off display" on some builds), the same value as `sleep_timeout` above.
8. **System speed and memory (2026-10-18).** Each step was measured after a reboot and 3 minutes'
   rest (cold open: `am force-stop`, then `am start -W`, 3 runs, median; Home return: the Home
   intent with the app in front). Recorded before anything changed:
   ```
   settings get global window_animation_scale        # 0.5 (already; not set by this work)
   settings get global transition_animation_scale    # 0.5 (already)
   settings get global animator_duration_scale       # 0.5 (already)
   cmd appops get <pkg> RUN_ANY_IN_BACKGROUND         # no override (allow) for all five below
   dumpsys package dexopt                             # speed-profile for YouTube, Netflix, JioHotstar,
                                                      # FanCode (bg-dexopt) and the launcher (install)
   df /data                                           # 2474896K used, 1623024K free
   ```
   - **Kept: pre-compile.** `cmd package bg-dexopt-job`, then `cmd package compile -m speed-profile -f`
     on `com.gathin.tvhome`, YouTube, Netflix, JioHotstar and FanCode. No measurable change (the apps
     were already `speed-profile`), kept as harmless: +33 MB on /data after the reboot.
   - **Tried and undone: `-m speed`.** Slower for every app (YouTube 720 → 815 ms, Netflix 603 → 712,
     JioHotstar 1258 → 1512, FanCode 2057 → 2717) and +164 MB: this TV's storage is slow, and bigger
     compiled files take longer to load. All five were put back with `-m speed-profile -f`.
   - **Animations:** already 0.5 before this work; nothing changed.
   - **Kept: background.** `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore` for
     `in.startv.hotstar`, `com.fancode.tv`, `com.sonyliv`, `com.apple.atve.androidtv.appletv` and
     `com.formulaone.production`. All five open; FanCode played live cricket. FanCode's open time with
     and without it is the same (2008–2145 ms against 2118 ms).
   - Not used: the "Background process limit" developer option.
   - Nothing else over 20 MB that runs at boot is safe to disable beyond what is kept on purpose. The
     Play Store (`com.android.vending`) uses up to 300 MB while it updates apps; turning off its
     auto-update would make that rarer (not done).

Check the live state any time:

```bash
tools/venv/bin/python -I tools/tv.py 'pm list packages -d'        # everything disabled
tools/venv/bin/python -I tools/tv.py 'cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME'
```

## Undo

Back to the stock Google TV home (keeps the debloat). Run each line separately:

```bash
T="tools/venv/bin/python -I tools/tv.py"
$T 'pm enable com.google.android.tungsten.setupwraith'
$T 'pm enable com.google.android.apps.tv.launcherx'
$T 'cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity'
```

The 1400 app (the moon pill hides itself when it is gone; then take `com.gathin.tv1400` out of
`grid_order` in `launcher/app/src/main/res/values/config.xml` and reinstall the launcher):

```bash
$T 'pm uninstall com.gathin.tv1400'
```

The system speed steps (Current state, item 8), back to how they were:

```bash
$T 'cmd appops set in.startv.hotstar RUN_ANY_IN_BACKGROUND default'
$T 'cmd appops set com.fancode.tv RUN_ANY_IN_BACKGROUND default'
$T 'cmd appops set com.sonyliv RUN_ANY_IN_BACKGROUND default'
$T 'cmd appops set com.apple.atve.androidtv.appletv RUN_ANY_IN_BACKGROUND default'
$T 'cmd appops set com.formulaone.production RUN_ANY_IN_BACKGROUND default'
# the compile state was speed-profile before and after; the TV's own background job keeps it so.
# The animation scales were 0.5 before this work; Android's default is 1:
$T 'settings put global window_animation_scale 0.5'
$T 'settings put global transition_animation_scale 0.5'
$T 'settings put global animator_duration_scale 0.5'
```

The screensaver settings, should anything ever change them, back to the values recorded above:

```bash
$T 'settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop'
$T 'settings put secure screensaver_enabled 1'
$T 'settings put system screen_off_timeout 600000'
$T 'settings put secure sleep_timeout 1800000'
```

Everything back to stock, debloat included: run the three lines above, then
`tools/restore.sh examples/xiaomi-mitv-moor2/debloat.txt com.google.android.apps.tv.launcherx`.

Last resort: a factory reset. It wipes the disabled-package state, so every app comes back
(you lose app logins). Uninstalled user apps can be reinstalled from the Play Store.

## Start again from a new computer

1. On the TV: Developer options → USB debugging on (it stays on unless you turned it off).
2. On the computer, on the same Wi-Fi:
   ```bash
   git clone -b claude/confident-meitner-sbddva https://github.com/thefirstparth/tv-vibehack
   cd tv-vibehack
   python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
   export TV_HOST=<TV IP>            # Settings → Network on the TV, or arp -a
   tools/venv/bin/python -I tools/tv.py --probe
   ```
   A new computer has a new ADB key, so the TV asks "Allow USB debugging?" again. Accept it.
3. Only needed to rebuild the launcher: JDK 17 and Android SDK platform 34 + build-tools 34.0.0.
   On a Mac without Homebrew they were installed to `~/Library/Java/jdk-17*` and
   `~/Library/Android/sdk`; with Homebrew see the main README.

### Keep the signing key

Both the release builds that go on the TV and debug builds are signed with `~/.android/debug.keystore`
on the computer that built them. Keep a private copy of that file (never commit it). A build signed
with a different key cannot update the
installed launcher: it would have to be uninstalled first, which drops it as the Home app, so the
Home setup above (set-home-activity, with setupwraith and launcherx disabled) would have to be
done again.

### Rebuild and reinstall the launcher

Don't run `launcher/build-and-install.sh` while `setupwraith` is enabled: it disables the stock
launcher straight away, which gives a black Home screen on this TV. Install by hand instead:

```bash
export JAVA_HOME=$(ls -d ~/Library/Java/jdk-17*)/Contents/Home ANDROID_HOME=~/Library/Android/sdk
cd launcher
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleRelease
../tools/venv/bin/python -I ../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/tvhome.apk
../tools/venv/bin/python -I ../tools/tv.py 'pm install -r /data/local/tmp/tvhome.apk'
../tools/venv/bin/python -I ../tools/tv.py 'rm -f /data/local/tmp/tvhome.apk'
```

The 1400 app the same way: `./gradlew :app1400:assembleRelease`, then push and `pm install -r`
`app1400/build/outputs/apk/release/app1400-release.apk`. Keep the APK that was on the TV in
`backup/launcher-builds/` before installing a new one, so it can go straight back.

### Prompt for a new Claude session

> This is tv-vibehack. My TV is the Xiaomi MiTV-MOOR2 (Google TV). Read
> `examples/xiaomi-mitv-moor2/README.md` first: it records what was done and how to undo it.
> Connect with `tools/tv.py`, confirm the model with `--probe`, and check the live state against
> that file before changing anything. Run one command per call and stop at the first error.

## Open items (where this was left, 2026-10-18)

Nothing here blocks using the TV. Pick up any of it later.

**The next session's plan is in [NEXT.md](NEXT.md)** (humidity layout, QA, the video lag test).

- **Known issue: Home lags after watching video, until a restart.** Measured 2026-10-18: after a
  reboot Home is smooth (0% janky frames, 6 ms a frame); after 45 s of video (FanCode live) it is
  60–70% janky (38 ms a frame), and stays so after closing the app, closing every app, standby and
  power on, or opening Live TV. Opening apps without playing video does not cause it. The original
  launcher lags the same way (75%), so it is the TV's video/graphics drivers, not the launcher.
  Only a restart clears it (hold power → Restart). A Xiaomi firmware update may fix it.

- **The remote's Settings key** is still broken (see Current state, item 6). An accessibility service
  that catches the key and opens Android TV Settings would fix it, but it would run all the time, so
  it was left out. The Settings pill works.
- **A screensaver** (the 1400 screens shown when the TV is idle) was designed and then dropped in
  favour of the 1400 app. The app already draws those screens, so a screensaver would be one small
  DreamService in `launcher/app1400` plus the screensaver settings (record and undo them as above).
- **A before/after comparison against the stock Google TV home** (speed and memory) was offered and
  never run.
- **The personal days** (dates, greetings, colours) and the feed address live only in
  `launcher/local.properties` on the Mac. They are not in git; recreate them there (format in the
  main README) on a new computer.

## Lessons from this TV

The lessons for everyone (speed, design, safety) are in [LESSONS.md](../../LESSONS.md); these are
the ones specific to this TV.

- **It's Google TV, not Android TV.** Disabling `launcherx` alone makes `setupwraith`'s
  `RecoveryActivity` take over Home with a black screen, whatever `set-home-activity` says.
- **Disabling only that component fails over ADB** (`SecurityException: Shell cannot change
  component state`). Disabling the whole `setupwraith` package works.
- **`tools/tv.py --batch` keeps going after an error.** For risky steps, run one command per call
  and check the output.
- **macOS has no `timeout` command.** Run `reboot` in the background; the ADB connection drops.
- **Package names differed from the guesses:** FanCode is `com.fancode.tv`, Live TV is `com.mitv.livetv`.
- **"User-installed" isn't only your own apps:** `pm list packages -3` also lists Google TV / Play
  Movies (`com.google.android.videos`).

## Not in git

Kept out of the repo on purpose (`.gitignore`). None of it is needed to carry on.

| Item | Where it was | Why it's not in git | Without it |
|---|---|---|---|
| Pre-change backup | `backup/moor2-20261018-054750/` on the work Mac | Contains the TV's serial number and device IDs | Optional. Copy it to personal storage if you want it |
| ADB key | `tools/adbkey/` | Private key that controls the TV. Never share it | A new computer makes its own; accept the prompt on the TV |
| Python venv, JDK, Android SDK | `tools/venv/`, `~/Library/...` | Reinstallable | Reinstall with the steps above |
