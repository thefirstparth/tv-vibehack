# Instructions for Claude (or any coding agent) working on this repo

You are picking up tv-vibehack for Parth. It runs his own TV: a **Xiaomi MiTV-MOOR2 (Google TV)** in
his living room, which his family uses every day. Earlier sessions ran on another Claude account and
computer; nothing from them carries over except this repo. Read this file first, then the two files
it points to.

## Read before you touch anything

1. [`examples/xiaomi-mitv-moor2/README.md`](examples/xiaomi-mitv-moor2/README.md): the TV's exact
   current state (what's disabled, the Home switch, every setting changed) and the undo command for
   each. It is the source of truth. Update it whenever you change the TV.
2. [`LESSONS.md`](LESSONS.md): what worked and what didn't. Most mistakes you could make here were
   already made once; it says which.
3. [`launcher/README.md`](launcher/README.md): how the home screen, the 1400 app and the feed work.

## Where the work lives

- **Branch:** `claude/confident-meitner-sbddva` holds all the work so far; it is **not merged into
  `main`**. Work on that branch (or a branch from it), not `main`, unless Parth says otherwise.
- **Pull before you push.** Two sessions have pushed to the same branch before. Never force-push or
  rewrite history; merge instead.
- **Commit each change separately**, with a clear message, and push.

## You must run on a computer at home

The TV is only reachable on the home Wi-Fi. A cloud session cannot reach it: if you are not on a
computer on the same network as the TV, you can edit code and build, but say so and don't claim
anything was tested on the TV.

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.0.15        # was this on 2026-10-18; DHCP, may change (arp -a, or the TV's Settings → Network)
tools/venv/bin/python -I tools/tv.py --probe     # must say MiTV-MOOR2: another Android TV is on this network
```

A new computer means a new ADB key: the TV shows "Allow USB debugging?", and Parth must accept it
with the remote. Ask him to.

## Hard rules

These come from Parth. Don't break them, and don't argue them away.

1. **Never make the TV slower.** Every launcher change gets an A/B on the TV against the build it
   replaces (method in [LESSONS.md](LESSONS.md#speed-measuring-it): alternate old/new, 5 rounds,
   medians, first run after install discarded, after a reboot). Keep it only if cold start is no
   more than 20 ms slower and Home return, stutter and memory are no worse.
2. **Install with `pm install -r` only. Never uninstall the launcher** (`com.gathin.tvhome`): it
   drops it as the Home app and the Google TV Home switch must be redone.
3. **Same signing key, or don't install.** Builds are signed with `~/.android/debug.keystore` from
   the computer that first built them. **A different computer has a different key, and its build
   cannot update the installed launcher.** Ask Parth for his private copy of that keystore before
   installing anything. If he doesn't have it, stop and tell him; don't uninstall to get around it.
4. **Save the APK that's on the TV before installing a new one,** so it can go straight back. On a
   new computer `backup/` is empty, so pull it from the TV first:
   `tools/tv.py 'pm path com.gathin.tvhome'`, then `tools/tv.py --pull <that path> backup/launcher-builds/<name>.apk`.
5. **Record every setting before changing it, with its exact undo command,** in the MOOR2 README.
6. **Don't disturb people watching.** Check what's on screen
   (`dumpsys activity activities | grep mResumedActivity`) before any TV test; if it isn't Home,
   ask first. If someone presses the remote during a test, stop, put the previous build back and
   press Home. Test on an emulator when you can (see LESSONS.md).
7. **Leave the TV on the Home screen** at the end, with no test loops or watchers running.
8. **Private data stays out of git.** Names, dates, the feed's address and anything with the TV's
   serial go in `launcher/local.properties` or `backup/`, both gitignored. Never commit `tools/adbkey/`.
9. **No LLM calls** from the launcher, the 1400 app or the feed.
10. **Don't use the "Background process limit" developer option** (it closes the launcher too).
11. **Ask before anything paid, anything needing his login, or anything that could lose data.**

## Building

JDK 17 and the Android SDK (platform 34, build-tools 34.0.0). On Parth's Mac they're at
`~/Library/Java/jdk-17*` and `~/Library/Android/sdk`.

```bash
cd launcher
export JAVA_HOME=$(ls -d ~/Library/Java/jdk-17*)/Contents/Home     # or your JDK 17
./gradlew :app:assembleRelease :app:testDebugUnitTest :shared:testDebugUnitTest :app1400:testDebugUnitTest
```

- **The launcher** ships as the release build: `app/build/outputs/apk/release/app-release.apk`.
- **The 1400 app** ships as the debug build (`./gradlew :app1400:assembleDebug`); it has no signed
  release build yet, so its release APK won't install.
- **Private settings** go in `launcher/local.properties` (not in git). Without them the apps build
  and work, just with no sport/weather feed and no personal days:
  ```
  sdk.dir=/path/to/Android/sdk
  tv.feedUrl=https://.../api/live/tv     # ask Parth; never commit it
  tv.day.1=MM-DD|Greeting|core|spot|band|top
  ```
  If the existing builds on the TV show the feed and yours wouldn't, don't install yours: ask Parth
  for his `local.properties`.

Install: push with `tools/b64push.py <apk> /data/local/tmp/x.apk`, then
`tools/tv.py 'pm install -r /data/local/tmp/x.apk'`, then remove the temp file.

## Talking to the TV

- One command per `tools/tv.py` call for anything risky; `--batch` keeps going after errors.
- `tv.py` gives up after 120 s; a long command (a compile) may still be running on the TV. Check.
- Run scripts with `</dev/null` and a time limit; background ADB jobs that wait on input hang.
- Feed the TV keys with `input keyevent KEYCODE_DPAD_DOWN` and so on; take screenshots with
  `tools/tv.py --screenshot <local.png>` (or `screencap -p /data/local/tmp/x.png` then `--pull`). `tv.py --reboot`
  restarts the TV cleanly. (tv.py and b64push.py are upstream cleanyourtv's versions from 24 Oct.)

## The feed

The sport, weather, news and markets come from Parth's site, `house-of-1400` (a separate repo), at
`/api/live/tv` (the launcher) and `/api/live/tv?saver=1` (the 1400 app). The site decides what to
show; the TV only words and draws it. Changes to what's in the feed belong in that repo, not here.

## Open items

See "Open items" in the [MOOR2 README](examples/xiaomi-mitv-moor2/README.md#open-items-where-this-was-left-2026-10-18).
Ideas raised but not started: a "Raining now" weather word (the site), cutting the 1400 app to three
tabs (Sport, The Day in a Minute, Money) with Sport's lists as pages, bigger small text in 1400, and a
signed release build for the 1400 app.

## How Parth likes to work

- Plain, short answers. Give an ETA on long jobs and a short update at each milestone.
- Report honestly: what was measured, what wasn't, what got rolled back and why.
- When a choice is genuinely his (design, what to keep), ask with a recommendation; otherwise
  decide sensibly and say what you chose.
