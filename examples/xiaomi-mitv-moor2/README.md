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

1. **Debloat:** the 22 packages in [`debloat.txt`](debloat.txt) are disabled with `pm disable-user`
   (20 debloat entries plus `setupwraith` and `launcherx` for the Home switch below). Three were added
   on 2026-10-20 (item 10).
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
   built-in, Netflix, Play Services, Play Store, Live TV, every media/DRM/Dolby package (Mi Music
   excepted since 2026-10-20).
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
   - **The remote's Settings key: doesn't apply to this remote** (it has no Settings key; noted
     2026-10-20). On remotes that have one, Xiaomi's `GlobalKeyReceiver`
     (`com.xiaomi.android.tvsetup.partnercustomizer`) always opens launcherx's dashboard and crashes
     with launcherx disabled. The launcher's Settings pill opens Android TV Settings.
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
9. **Launcher look (2026-10-19):** the 1400 pill is a newspaper; Left/Right on the tiles go along the
   rows in reading order (never up to the top bar); humidity under each city.
   **The weekend look is on the TV** (2026-10-19, 06:21, `87ddafb`): it passed the night's weekday and
   Saturday tests under the qualifying rule (all measurements in [BENCHMARKS.md](BENCHMARKS.md)).
   Undo: `pm install -r` of `backup/launcher-builds/tvhome-on-tv-before-golive.apk`.
10. **2026-10-20:** the launcher build `4cec0f2` is on the TV (installed 06:27 with `pm install -r`): Home
    pressed on Home goes back to the first dock tile, the focus highlight is drawn on the GPU and comes
    back on every return to Home. It passed the weekday A/B ([BENCHMARKS.md](BENCHMARKS.md)); the
    Saturday test was skipped by Parth's choice (the weekend code is unchanged). Undo: `pm install -r` of
    `backup/launcher-builds/tvhome-on-tv-before-gpu.apk` (the build on the TV before; same as
    `tvhome-release-weekend2.apk`).
    Three more packages disabled, one at a time, with Home, the dock apps, Inputs, Settings, voice and
    casting checked after each: `com.google.android.partnersetup`, `com.xiaomo.tv.milegal`,
    `com.xiaomi.mimusic2`. Free RAM at rest (after a restart and 3 minutes): MemAvailable 974.5 → 995.7 MB,
    "Free RAM" 703.8 → 715.1 MB. Undo, one command per call:
    ```
    pm enable com.google.android.partnersetup
    pm enable com.xiaomo.tv.milegal
    pm enable com.xiaomi.mimusic2
    ```
    Nothing else was changed: the clock and automatic time were not touched tonight (`auto_time` and
    `auto_time_zone` both 1).
    **The weather highlights** (`97d3e73`: the warmest/coolest temperature tinted, the highest high, lowest low
    and most humid value lit; mockup option 5) are on the TV since 07:43. A short A/B first failed on cold start
    (two samples a build); the re-test with five cold starts a slot passed (+18 ms, allowed 20; see
    [BENCHMARKS.md](BENCHMARKS.md)). Undo: `pm install -r` of `backup/launcher-builds/tvhome-release-rim-4cec0f2.apk`.
    **Our side stands out** (`264f568`, on the TV since 08:16): in the sport lines the side Parth doesn't follow is
    dimmed (65% white in the headline; the lines' 45% dim in the THEN/LATER rows), from the feed's new `ours` (the
    site, house-of-1400 `638b1ff`). It passed its short A/B. Undo: `pm install -r` of
    `backup/launcher-builds/tvhome-release-weather-97d3e73.apk`.
    **Tabular digits** (`7fe2268`, on the TV since 08:39): Outfit's `tnum` for the clock and the sport scores, so
    they keep their width as the digits change. Passed its short A/B. Undo: `pm install -r` of
    `backup/launcher-builds/tvhome-release-ours-264f568.apk`.
    **Icon-only top bar** (`1bfd00c`, on the TV since 09:16): Inputs and Settings show only their icon, the name
    while focused (as the 1400 and Wi-Fi pills); with no network or no internet the Wi-Fi icon gets an amber "!" and
    the pill says "No internet" until the connection is back (one network callback, no polling). Passed its short
    A/B. The no-internet look was checked on the emulator only (with the launch extra `net=offline`): cutting the
    TV's Wi-Fi would also cut ADB. Undo: `pm install -r` of `backup/launcher-builds/tvhome-release-tnum-7fe2268.apk`.
    **The feed refreshes while Home stays on screen** (`9bb14ef`, on the TV since 17:48). Before, the sport and weather
    were fetched only when Home appeared, and the site's cache answers the first request after a quiet spell with
    its old copy (making a fresh one at the same moment), so the TV could sit on "as of" an old time (20 Oct: "as of
    4:51 pm" at 5:18). Now one more request comes a minute after a saved copy and every 15 minutes otherwise, on the
    minute tick, only while Home is in front. Each request is logged: `logcat -s HomeLines:I`. Passed its short A/B.
    Undo: `pm install -r` of `backup/launcher-builds/tvhome-release-topbar-1bfd00c.apk`.
11. **Horizon v3 is the home screen's light** (`a28b832`, on the TV since 20:22 on 20 Oct; spec and approved mockup in
    [horizon-v3/](horizon-v3/README.md)): the light follows the real sun and moon, the moon as light only, 909 stars
    and the Milky Way at night, the feed's new `sky` for Bengaluru (house-of-1400 `067cdea`), grain, and a readability
    floor (4.5:1 for each block's faintest text, with a soft shadow where needed). The picture is 480x270: at 960x540
    the A/B failed on memory (+11.4 MB). It passed the full A/B at 480x270 ([BENCHMARKS.md](BENCHMARKS.md)). Not in
    this build: meteors, the match ribbon, the after-match glows. The floor's figures are logged:
    `logcat -s Horizon:I`. Undo: `pm install -r` of `backup/launcher-builds/tvhome-on-tv-before-horizon3.apk` (the
    build before, `9bb14ef`).
12. **Quicker feed re-asks** (`e1cc510`, on the TV since 21:51 on 20 Oct): after an old or failed copy the home screen
    asks again 8 s later (twice at most) instead of on the next minute tick, so "as of" clears within about 10 s of
    waking the TV or pressing Home (it took about a minute at 9:21 pm); while a match is live the feed is asked every
    2 minutes instead of 15. Passed its short A/B. Undo: `pm install -r` of
    `backup/launcher-builds/tvhome-release-horizon3-480-a28b832.apk`.
13. **Picture: "real, with a bit of life"** (21 Oct, 01:10–01:15, set from the computer, one step at a time with Parth
    judging each against the old value): colour temperature `2` → `3` (warmer white: the TV's gains went from
    R1034/G1024/B1019 to R1055/G959/B833), saturation `50` → `55`, contrast `50` → `56`. Everything else as before:
    picture mode `0`, brightness 50, backlight 80, sharpness 10, hue 0, motion smoothing (MEMC) off, noise reduction
    and MPEG noise reduction off. Later that night (Parth's choice): AI super-resolution `3` → `2`
    (`tv_picture_video_aisr`, less sharpening when upscaling) and black stretch `0` → `1` (`picture_black_stretch`, on).
    Contrast 55, not 56: Parth set it in the TV's menu (01:28).
    **Every picture setting (90) is saved** in `picture/2026-10-21-tuned.txt` (this look) and
    `picture/before-2026-10-21.txt` (as it was before that night). `tools/picture.sh diff <file>` shows what differs on
    the TV, `tools/picture.sh restore <file>` sets back only what differs and checks it, and `tools/picture.sh save
    <file>` saves a new one. Settings made from the computer may not survive a
    restart; if the picture changes after one, set them again (Undo has the lines; the new values instead of the
    old). Don't change **brightness** for "more life": at 80 it greyed the blacks (tried 21 Oct, rolled back).
    Undo: see "The picture settings" under Undo. **Picture settings are kept per input** (seen 21 Oct, 03:04): these
    values are the apps' (Android TV source); the HDMI inputs (the Xbox, `HW5`, "Game console") have their own,
    untouched (backlight 63, colour temperature 2, contrast 50, saturation 50, black stretch off).

14. **The daily summary for the insights app** (`1bb593d`, on the TV since 03:03 on 21 Oct; `insights/`, a private
    Vercel app at tv-insights-chi.vercel.app). The launcher keeps a small diary (files/diary/, a few KB a day, 35 days:
    each Home return's time to draw, cold starts, feed answers, Wi-Fi drops) and, the first time Home has been on screen
    5 s after 4 am, sends each unsent day's summary (with Android's usage stats: when the screen was on, which apps and
    when) to the site, up to 30 days back; off days as 0 minutes, send problems with the next good send. The record
    starts 04:00 IST on 21 Oct. Needs **usage access**, granted 03:03:
    `appops set com.gathin.tvhome GET_USAGE_STATS allow` (was `default`). Passed its short A/B (BENCHMARKS.md).
    Undo: `pm install -r` of `backup/launcher-builds/tvhome-release-quick-e1cc510.apk`, then
    `appops set com.gathin.tvhome GET_USAGE_STATS default`. Off without undoing: remove `tv.diaryUrl` from
    `launcher/local.properties` and rebuild.

15. **No grain in the light, and an Xbox tile** (`90cd971`, on the TV since 04:42 on 22 Oct). Horizon v3's baked-in grain
    is gone (at 480x270 stretched 4x it read as blotches). The dock's 4th tile is now the Xbox (HDMI 2, input
    `com.mediatek.tvinput/.hdmi.HDMIInputService/HW5`): grey while the Xbox is off, Xbox green while it is on (the
    port's state through TvInputCallback), one press switches to it; FanCode moved to the start of row 2. Its short A/B
    was over the limits on memory (+10 MB) and by 0.5 ms on cold start; installed at Parth's call ("no-grain is
    non-negotiable"), see BENCHMARKS.md. Undo: `pm install -r` of
    `backup/launcher-builds/tvhome-release-insights-1bb593d.apk`.

16. **The diary update and the Xbox tile's final look** (`0573edd`, on the TV since 05:06 on 22 Oct): each day's summary
    is kept as first made (files/diary/sent/), the input picked on the Inputs menu is noted, and the Xbox tile is Parth's
    "3B" (a controller half-risen over a horizon in a green night sky when on; greys and "Off" when off). Tested as
    `d9c6c4b` (short A/B, pass on every point, BENCHMARKS.md); the tile's drawing changed after it, installed without its
    own test at Parth's call. Since 05:13 the tile is his final pick, "starry rise" (`0d2893a`): a fun controller rising
    out of the tile's bottom edge into a bright starry green night; "Off" in grey when off. Undo: `pm install -r` of
    `backup/launcher-builds/tvhome-release-xbox-90cd971.apk`.

17. **No dither in the light** (`43c12f9`, on the TV since 05:16 on 22 Oct): Horizon's 480x270 picture is drawn without
    Skia's dither; its fixed dot pattern, stretched 4x, showed as a grid once the grain was gone (Parth: "worse now").
    Installed straight away without its own A/B at Parth's call (it removes work; the picture is otherwise the same).
    Undo: `pm install -r` of `backup/launcher-builds/tvhome-release-starry-0d2893a.apk`.

18. **Horizon back to its approved spec, 960x540 with fine grain and dither** (`0ad75e1`, on the TV since 05:20 on 22
    Oct). At 480x270 the 4x stretch showed the grain as blotches, then the dither as a dot grid, then banding without
    either. Memory, read fairly at rest (2 readings each, alternating): 52.5 MB (43c12f9) vs 60.7 MB (+8.2 MB, over the
    3 MB limit); kept at Parth's call. Speed for 960x540 was measured on 20 Oct (Home return -5.5 ms, cold +13.5 ms).
    Undo: `pm install -r` of `backup/launcher-builds/tvhome-release-nodither-43c12f9.apk`.

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

The picture settings (Current state, item 13), back to how they were before 21 Oct, all 90 at once:
`tools/picture.sh restore examples/xiaomi-mitv-moor2/picture/before-2026-10-21.txt`, or just the ones changed:

```bash
$T 'settings put global picture_color_temperature 2'
$T 'settings put global picture_saturation 50'
$T 'settings put global picture_contrast 50'
$T 'settings put global tv_picture_video_aisr 3'
$T 'settings put global picture_black_stretch 0'
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

- **Built, not on the TV: the next insights diary build** (`db827f3`, candidate
  `backup/launcher-builds/tvhome-release-diary2-29477b0.apk`; site side `29477b0`, not deployed). Nothing on the TV
  changed. (1) Each day's summary is frozen at its first send try in files/diary/sent/ (35 days) and that copy is
  what's sent, so a day failing longer than Android keeps usage detail (~10 days) still arrives whole. (2) Picking an
  input on Home's Inputs menu writes a diary line (`I`, e.g. `com.mediatek.tvinput/.hdmi.HDMIInputService/HW5`, "Xbox")
  and the summary gets per-input minutes for the input app's stretch that follows; the TV's own source button stays
  unnamed. Unit tests and an emulator run passed (BENCHMARKS.md has no row yet). **Left for next week (Parth, 21 Oct):**
  the short A/B against `1bb593d` (`backup/launcher-builds/tvhome-release-insights-1bb593d.apk`, memory by pid), then
  `pm install -r` only if it passes (undo: `pm install -r` of that 1bb593d APK; the frozen copies in files/diary/sent/
  are then just ignored); deploy `insights/` (the page and day.js take days with or without `inputs`); and check a week
  of real days on the site and `DaySummary.SLOW_MS` (100 ms) against the real Home times (sign-in needed).
  **The site's address:** since 21 Oct the insights app also answers at `https://tv.bhatia.page` (custom domain, CNAME
  at Spaceship). The TV sends to `https://tv-insights-chi.vercel.app/api/day` (`tv.diaryUrl` in `launcher/local.properties`),
  and the saved candidate has that address built in. When this build is A/B'd and installed, `tv.diaryUrl` may become
  `https://tv.bhatia.page/api/day` (then rebuild, and A/B that build, not the saved one). Keep the vercel.app alias
  working regardless: an older build put back with `pm install -r` still sends there.

- **Known issue: Home lags after watching video, until a restart.** Measured 2026-10-18: after a
  reboot Home is smooth (0% janky frames, 6 ms a frame); after 45 s of video (FanCode live) it is
  60–70% janky (38 ms a frame), and stays so after closing the app, closing every app, standby and
  power on, or opening Live TV. Opening apps without playing video does not cause it. The original
  launcher lags the same way (75%), so it is the TV's video/graphics drivers, not the launcher.
  Only a restart clears it (hold power → Restart). A Xiaomi firmware update may fix it.
  **Narrowed down 2026-10-19:** it is **FanCode's video player**. From a clean restart: JioHotstar,
  Netflix and FanCode's home-screen previews (audio and video playing) left Home at 0%; YouTube
  playback too; FanCode's player (`PlaybackComposeActivity`, a clip) took Home to 61–66%, also with
  motion smoothing (MEMC) **off** — so MEMC is not the cause. Untested: AI super-resolution off (set in
  the TV's menu; changes from the computer don't survive a restart), and Netflix/JioHotstar full
  players beyond their previews. Workaround: restart after watching FanCode.

- **The remote sometimes can't turn the TV on (Parth: for 2 to 3 months; seen again 20 Oct, 16:40–16:50);
  the button on the TV always works.** Diagnosed from the TV's own log, nothing changed: at 14:13 the TV went into
  quick standby by itself (30 minutes idle, `sleep_timeout`); this firmware's quick standby (Suspend-to-RAM,
  `persist.vendor.mitv.str.framework=true`) switches Bluetooth off ("Start STR disabling bluetooth") and relies on
  the Bluetooth/Wi-Fi chip (MediaTek MT7663) to wake on the paired remote (`bluetooth.wake_up_rc` = the one bonded
  "Xiaomi RC"). After 2 h 37 min asleep the remote did nothing; the TV's button woke it at 16:50:12, and 4 s later the
  chip reported a hardware error (`bt_hci: Ctlr H/w error event - code:0xf2`) and Android logged "Bluetooth crashed"
  (16:50:18) and restarted it; the remote reconnected at 16:50:22. So the chip had stopped listening for the remote
  during standby: a firmware/driver fault in the TV, not the launcher or the debloat (it predates both; no disabled
  package touches Bluetooth or wake). The firmware clears its logs on boot, so earlier cases can't be read back.
  Read in Xiaomi's power service (`services.jar`, disassembled on the Mac): Bluetooth is switched off on every quick
  standby, with no setting to keep it on, so waking on the remote is the chip's job alone; after 30 quick standbys
  (`persist.sys.max_str_count`, default 30) the TV restarts fully instead (likely the 13:42 boot). Related settings,
  recorded and not changed: `global mitv_go_suspend=1`, `go_to_str_direct=1`, `sleep_mode_mitv_go_suspend=1`,
  `persist.vendor.wow=0` (wake on Wi-Fi off), `hdmi_control_auto_wakeup_enabled=0`, `secure sleep_timeout=1800000`.
  Options, none applied yet: a Xiaomi system update (the only fix to the chip itself); fresh remote batteries;
  re-pair the remote; a test, with Parth and the remote, of the power button doing a full shutdown instead
  (`mitv_go_suspend=0`; undo `settings put global mitv_go_suspend 1`): slower to turn on, and whether the remote can
  then turn it on is unknown. A longer auto-standby was considered and not recommended (it only changes how often
  the TV sleeps by itself; the remote's power button uses the same standby).
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
