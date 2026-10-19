# Lessons

What we learned taking tv-vibehack from a working debloat-and-launcher kit to a fast, everyday home
screen on a Google TV (Xiaomi MiTV-MOOR2, October 2026). Each lesson says what happened and what to
do about it. Skim the bold lines; read on where it's useful.

- [Speed: measuring it](#speed-measuring-it)
- [Speed: what actually helped](#speed-what-actually-helped)
- [Design for a TV](#design-for-a-tv)
- [Talking to the TV (ADB)](#talking-to-the-tv-adb)
- [Google TV and Xiaomi quirks](#google-tv-and-xiaomi-quirks)
- [Working safely on a TV people use](#working-safely-on-a-tv-people-use)
- [Working with an AI coding assistant](#working-with-an-ai-coding-assistant)
- [What went badly](#what-went-badly)

## Speed: measuring it

**Measure on the real TV, never on your computer or an emulator.** The emulator is fine for "does
it open and does focus move"; it says nothing about speed on a TV's slow chip and storage.

**Take turns: old, new, old, new.** Five rounds each, then compare medians. A single run on each
build once told us two new animations were fine; a fair comparison showed they made the screen
stutter, and they were removed.

**Throw away the first run after an install.** Android compiles the app as it runs, so the first run
is always slow.

**One app's video player leaves this TV slow until a restart.** After FanCode's player runs, Home
goes from 0% to 60–70% janky frames, for any launcher (the original too), and stays that way after
closing the app, standby and power on, opening Live TV, or turning motion smoothing off. YouTube,
Netflix and JioHotstar video didn't do it. Test one app at a time from a clean restart, and prove the
video is really playing (an audio player "started", the player on screen): two of our runs "played"
nothing at first.
If a TV "feels laggy", play nothing, restart, and measure again before blaming the code.

**Reboot and rest before measuring.** After hours of use this TV gets into a state where every
build stutters (70–80% janky frames) and the load average sits around 60. We nearly blamed a good
build for it; re-running the *old* build showed the TV, not the app, was slow. A reboot fixes it.

**Watch for something else using the TV while you measure.** Our final pass was skewed by the Play
Store updating apps in the background (up to 300 MB). Netflix looked 200 ms slower; a re-check
showed it unchanged.

**Measure the noise in the same test.** A "no slower" rule failed a good build twice: the same build's
Home return varied by 40 ms between runs. Running current → candidate → current, with ten Home returns
per slot, measured tonight's noise (15.5 ms) and let the rule allow for it; the weekend look then passed.
Every measurement is in [BENCHMARKS.md](examples/xiaomi-mitv-moor2/BENCHMARKS.md).

**Decide the rules before you look at the numbers.** We wrote "keep it only if cold start is no more
than 20 ms slower, Home return no slower, stutter and memory no worse" before testing. That made the
keep-or-roll-back decision mechanical.

**What to measure and how** (all over ADB):

| What | How |
|---|---|
| Cold start | Open Settings, `am force-stop` the launcher, then `am start -W` it; read `TotalTime` |
| Return to Home | With an app in front, `am start -W -a android.intent.action.MAIN -c android.intent.category.HOME` (what the Home key does) |
| Stutter | `dumpsys gfxinfo <pkg> reset`, a scripted run of D-pad presses, then `dumpsys gfxinfo <pkg>`: janky %, 90th percentile |
| Memory | `dumpsys meminfo <pkg>`: TOTAL PSS |

## Speed: what actually helped

**Ship the release build.** Switching the launcher from the debug build to a shrunk release build
(R8, unused resources removed) took cold start from 1479 to 612 ms and the app from 1.7 MB to
0.24 MB. Sign it with the same key as before so `pm install -r` updates it in place.

**Don't redo work every time Home appears.** The launcher reloaded the whole app list and replayed
the tiles' entrance on every return. Now it loads once and refreshes only when an app is installed,
removed or updated. With the release build, Home return went from 792 to 403 ms.

**Do the non-essential work after the first frame.** The line above the clock and the moon button
first added 55 ms to startup; moving them to just after the first frame took it back to the old
time.

**This GPU is limited by how many pixels it paints.** Big translucent layers, full-screen glows,
blur and clipped paths all hurt. The colour glow behind the dock was the heaviest thing on screen;
removing it made Home smoother. Draw fixed decoration once into a small image and use it as the
window's background.

**Nothing should animate on its own or react to every focus move.** Springy motion and glass
effects that followed focus made scrolling stutter. Changing a font's weight at runtime was worst
(up to 26% janky frames): each step builds a new font.

**Fully pre-compiling apps made them slower.** `cmd package compile -m speed` made YouTube, Netflix,
JioHotstar and FanCode all open 13–32% slower and used 164 MB more storage: bigger compiled files
take longer to read from slow storage. The default (`speed-profile`, only the parts you use) is
right, and the TV's own nightly job already does it, so forcing it again changed nothing.

**Stopping apps running in the background didn't make them slower to open**
(`cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore`), and keeps rarely used apps quiet. Don't use
the "Background process limit" developer option: it closes the launcher too, so Home gets slower.

**Check before you change.** The system animation scales were already 0.5 on this TV; there was
nothing to gain there.

## Design for a TV

**Everything must say where you are.** The first 1400 app had two modes (Today and Sport) that it
switched between by itself, Left/Right flipped them with nothing on screen showing it, and Up/Down
moved through tabs laid out sideways. The fix: one row of tabs, the current one bright and the rest
dimmed; Left/Right along the tabs, Up/Down through pages ("2 / 3"). The arrow keys should match what
you see.

**Nothing should move by itself.** Pages that turn every 25 seconds move away while you're reading.
If it's a display you glance at, let the person choose.

**Open in the same place every time.** Opening on whichever mode the match schedule picked was a
surprise; it now always opens on Sport.

**Long text must never collide.** A long Wi-Fi name ran into the clock. Measure the room you have and
shorten names step by step (full name, short name, without the competition) rather than cutting
with "…".

**Small text is unreadable from the sofa.** Labels at 7.5–8 sp are too small across a room.

**Say what the data really is.** "Rain likely" is a forecast for the whole day (a 60% chance and
2.5 mm or more), not "raining now". A feed that is down should say so ("as of 4:10 pm", "Feed not
answering") rather than show old news as current.

**Personal touches stay private.** Names, dates and the feed's address live in
`launcher/local.properties`, which is never committed.

## Talking to the TV (ADB)

**On macOS, `adb connect` may say "No route to host"** while the port is open: macOS's Local Network
privacy blocks the `adb` binary. `tools/tv.py` (pure Python) avoids it.

**Some TVs have no "wireless debugging" switch** (Xiaomi). USB debugging alone opens network ADB on
port 5555.

**Run one `pm` command per call.** `pm disable a; pm disable b` can silently stop after the first.
`tools/tv.py --batch` runs one per line but keeps going after an error, so run risky steps one at a
time and read the output.

**Large uploads hang** through the Python client; send APKs with `tools/b64push.py` (chunks plus an
md5 check).

**Long commands time out.** `tools/tv.py` gives up after 120 s, but the command may still be running
on the TV (a big compile kept going). Check the result rather than assuming it failed.

**Scripts that call ADB in the background can hang** if they wait on input; give them no input
(`</dev/null`) and a time limit. macOS has no `timeout` command; use a small wrapper.

**`getevent` on the TV shows real remote presses** (scripted `input keyevent` presses don't appear),
which makes a good "is someone using the TV?" check. Count only key events (type `0001`): a Bluetooth
remote waking up sends other events without a press.

**Know your network.** The TV's IP comes from DHCP and can change; another Android TV was on the same
network. Run `tools/tv.py --probe` and check the model before changing anything.

## Google TV and Xiaomi quirks

**Replacing the Google TV home screen needs three steps, in this order:** disable `setupwraith`, set
the new home activity, then disable `launcherx`. Disabling `launcherx` alone gives a black Home
screen from `setupwraith`'s recovery screen.

**Disable the stock launcher only after the new one is installed**, or Home lands on a factory test
app.

**Never uninstall your launcher to update it.** Use `pm install -r` with the same signing key.
Uninstalling drops it as the Home app and the setup has to be redone. Keep a copy of
`~/.android/debug.keystore`.

**The remote's Settings key breaks** once `launcherx` is disabled (Xiaomi's key handler only opens
launcherx). Put a Settings button on the home screen instead.

**The on-screen keyboard can vanish** after switching launchers: the default input method pointed at
an uninstalled Baidu keyboard. `ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME`.

**Package names aren't what you'd guess** (FanCode is `com.fancode.tv`, Live TV `com.mitv.livetv`).
List them from the TV; don't guess.

**Two of the launcher's images were JPEGs named `.png`.** Debug builds didn't care; the release build
refused them until PNG crunching was turned off.

## Working safely on a TV people use

**Write down every setting before you change it, with the command that undoes it,** in the TV's
README. Then anything can be put back by anyone.

**Keep a copy of the build that's on the TV** (`backup/launcher-builds/`) before installing a new one,
so a bad build can go straight back.

**If someone picks up the remote, stop.** During long tests we watched for real key presses; on one,
the test stopped, put the old build back and pressed Home.

**Leave the TV on the home screen** at the end of every session, with no test loops running.

**Ask before anything that touches the TV while someone wants to watch.** Testing moved to an
emulator on the Mac so the TV stayed free.

## Working with an AI coding assistant

**Give it the rules, not just the task:** "never slower", "pm install -r only", "record before you
change", "stop if the remote is pressed". Hard rules like these kept the TV safe throughout.

**Ask for milestones and an ETA on long jobs.** Silence for 20 minutes during a test run is
unnerving; a short update at each step is not.

**Ask it to measure, and to say when something didn't help.** Several "improvements" were rolled
back because the numbers said so; the useful report is the one that says so plainly.

**Two sessions on the same branch will collide.** A second session pushed a README change while the
first was working; it had to be merged. Use one session per branch, or pull before you push.

**Check the claims in its own docs.** A README step written by one session (`:app1400:assembleRelease`)
produces an APK that won't install, because that module's release build was never signed.

## What went badly

- **Effects that looked nice made Home stutter.** Spring motion, glass tints and a colour glow were
  built, shipped, then removed after a fair test.
- **A one-run comparison gave the wrong answer.** It called those effects fine; five alternating
  runs showed they weren't.
- **The plan changed three times.** A screensaver was specified, then became a standalone "Today"
  app, then was re-skinned as the 1400 app and Today was deleted. Settle the shape before building.
- **The first 1400 app was hard to use** (hidden modes, auto-turning pages, sideways tabs on Up/Down).
- **The TV's slow state** cost a test round and nearly sank a good build.
- **The feed once said "not answering"** when the site was only slow (over 5 s); the fix was a longer
  cache on the site and treating an old copy as "as of" rather than missing.
- **Screenshots for sharing were awkward:** the launcher's screenshot-only time setting changes the
  light but not the big clock, so "dawn" shots still read 2:00 PM.
- **The weather word confused us:** "Rain likely" on screen while it was already raining outside.
- **Tools hung:** background ADB jobs waiting on input, shell word-splitting in loops, a 120 s timeout
  cutting off a compile, and an emulator whose app installs hung until app verification was turned
  off.
- **A false alarm stopped a test:** the remote waking up looked like a key press until the check was
  narrowed to real key events.
