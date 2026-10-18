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
3. **Dock:** YouTube, Netflix, JioHotstar, FanCode (see `launcher/app/src/main/res/values/config.xml`).
   The launcher is also listed in the TV's app list (it declares `LEANBACK_LAUNCHER`).
4. **Kept on purpose:** firmware and Xiaomi update services, Google Assistant voice, Chromecast
   built-in, Netflix, Play Services, Play Store, Live TV, every media/DRM/Dolby package.
5. **Planned, not done yet (all still installed at 06:15 on 2026-10-18):** uninstall ZEE5
   (`com.graymatrix.did`), NBA (`com.nbaimd.gametime.nba2011`), MUBI (`com.mubi`), Tata Play
   (`com.tataskymore.open.uat`) and DAZN (`com.dazn`), plus `pm trim-caches`.
6. **Tested after the switch and a reboot (2026-10-18):**
   - Home → new launcher, also after a reboot. Every dock app opens and Home returns to the dock.
   - YouTube search keyboard works (Gboard is the default IME).
   - Inputs menu opens; HDMI 1 switches via Live TV. Two HDMI-CEC entries show no name, only "Connected".
   - Voice / Search key reaches Google Assistant, which first asks a one-time data-sharing consent
     on the TV; until that is answered it shows a spinner.
   - **Broken: the remote's Settings key.** Xiaomi's `GlobalKeyReceiver`
     (`com.xiaomi.android.tvsetup.partnercustomizer`) always opens launcherx's dashboard and crashes
     with launcherx disabled. Use the launcher's Settings pill, which opens Android TV Settings.
   - Launcher focus: after Home, focus can stay on the top bar, and Down from the top bar skips the
     dock into the grid.

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

### Rebuild and reinstall the launcher

Don't run `launcher/build-and-install.sh` while `setupwraith` is enabled: it disables the stock
launcher straight away, which gives a black Home screen on this TV. Install by hand instead:

```bash
export JAVA_HOME=$(ls -d ~/Library/Java/jdk-17*)/Contents/Home ANDROID_HOME=~/Library/Android/sdk
cd launcher
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug
../tools/venv/bin/python -I ../tools/b64push.py app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/tvhome.apk
../tools/venv/bin/python -I ../tools/tv.py 'pm install -r /data/local/tmp/tvhome.apk'
../tools/venv/bin/python -I ../tools/tv.py 'rm -f /data/local/tmp/tvhome.apk'
```

### Prompt for a new Claude session

> This is tv-vibehack. My TV is the Xiaomi MiTV-MOOR2 (Google TV). Read
> `examples/xiaomi-mitv-moor2/README.md` first: it records what was done and how to undo it.
> Connect with `tools/tv.py`, confirm the model with `--probe`, and check the live state against
> that file before changing anything. Run one command per call and stop at the first error.

## Lessons from this TV

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
