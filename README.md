# tv-vibehack

**Your TV, minus the ads, and faster.** Turn an Android TV or Google TV into a clean, quick home
screen: switch off the bloat over ADB, replace the ad-filled launcher with a small Apple TV-style
one, and (optionally) put live sport, weather and the day's news on it.

You don't need to be an Android developer. Hand [`PROMPT.md`](PROMPT.md) to Claude Code (or another
coding assistant that can run commands), answer a few questions about the apps you use, and it does
the work on your real TV, checking each step with a screenshot.

| Stock home screen | After |
|---|---|
| ![Stock Google TV home with a sponsored ad](docs/stock-home.jpg) | ![tvOS-style launcher](docs/new-home.jpg) |

*(The "after" photo shows the first version of the launcher; the current one adds the sport and
weather lines and a light that follows the time of day.)*

**Everything is reversible.** Apps are disabled, never uninstalled; every setting that gets changed
is written down first, with the command that puts it back.

## Quick start

1. **On the TV:** Settings → Device Preferences → About → press OK on **Build** seven times → back
   → Developer options → turn on **USB debugging** (and Network/ADB debugging if you see it). Keep
   the TV on and on the same Wi-Fi as your computer.
2. **On your computer:** open Claude Code in a copy of this repo and paste the prompt from
   [`PROMPT.md`](PROMPT.md), with your TV model filled in.
3. **When asked:** accept "Allow USB debugging?" on the TV, then say which apps you use and which
   four go in the dock.

The assistant backs up the TV, switches off the bloat, builds and installs the launcher, and shows
you a screenshot after each step.

## What you get

**The home screen** (`launcher/app`, about 240 KB, made for TVs with 1–2 GB of RAM)

- A dock with your four main apps; press Down for the rest in a grid, in the order you choose.
- A big clock and the date. Above it, the time of day and the sun's next turn ("MIDDAY · SUNSET
  5:59 PM"), worked out on the TV itself.
- **Inputs** (your HDMI ports by name, e.g. "Xbox"), **Wi-Fi** and **Settings** buttons.
- A soft light behind the screen that follows the sun: dawn, golden hour, night. It greys out
  when rain is forecast. It's drawn once into a small image, so it costs nothing while you move.
- No ads, no autoplaying video, nothing running in the background.

**Optional: sport and weather** (needs a small feed you host; off unless you set one)

- What's live, just finished or next for the teams you follow, and the weather in three cities.
- One quick request each time you come back to Home. Never on a timer, never while you watch.
- If the feed is down, the last good copy shows with "as of 4:10 pm".

**Optional: the 1400 app** (`launcher/app1400`)

- A full-screen "paper" for the TV: sport, the day's news in a minute, and markets, as tabs.
  Left/Right move between tabs, Up/Down turn pages. It only runs while open.

The details for all three are in [`launcher/README.md`](launcher/README.md).

## How much faster

Measured on a Xiaomi MiTV-MOOR2 (Google TV, 2 GB RAM). Each figure is the median of 5 runs, with
the old and new builds taking turns, after a reboot:

| | Before | After |
|---|---|---|
| Opening the home screen from cold | 1479 ms | **612 ms** |
| Getting back to Home from YouTube | 792 ms | **403 ms** |
| Stutter while scrolling the apps (janky frames) | 0.74% | **0%** |
| Memory used by the home screen | 78.5 MB | **66.8 MB** |
| Size of the app | 1.7 MB | **0.24 MB** |
| Free storage on the TV (after the clean-up) | 1.1 GB | **1.5–1.7 GB** |

## What changed from the original project

The original tv-vibehack (by Gathin23, 17 October 2026) was a prompt, a pure-Python ADB tool, a
debloat list for the Mi TV 4A, and a black tvOS-style launcher. It worked. On 18 October we used
it on a newer Google TV set and kept going. In short:

| Area | Original | Now |
|---|---|---|
| TVs covered | Mi TV 4A (Android TV 10) | + MiTV-MOOR2 (Google TV), which needs an extra step to replace its home screen ([how](examples/xiaomi-mitv-moor2/README.md)) |
| Home screen | Black background, app name and colour glow over the dock | The sun-following light, sport and weather lines, the line above the clock, named inputs, a moon button for the 1400 app; the heavy glow removed |
| Grid | A–Z after the dock | Your own order (`grid_order`) |
| Speed | Debug build, reloaded every app on each return to Home | Shrunk release build, loads apps once and refreshes only when one is installed or removed (table above) |
| New apps | — | The 1400 app; a shared module both apps use |
| TV tuning | — | Measured system tweaks, kept only where they helped, each with its undo command |
| Records | A gotchas list | A full record of the TV's state with undo steps, and [LESSONS.md](LESSONS.md) |

What didn't work, and what we'd do differently, is in [LESSONS.md](LESSONS.md).

## What we learned (the short version)

The full list, with the story behind each, is in **[LESSONS.md](LESSONS.md)**. The ten that matter
most:

1. **Measure on the real TV, before and after, taking turns.** One run on each build fooled us
   once; two "nicer" animations that looked fine made the screen stutter and were removed.
2. **Reboot before you measure.** This TV slowly gets into a state where *everything* stutters.
   We nearly blamed a good build for it.
3. **The release build is the cheapest speed-up there is.** Switching from the debug build to a
   shrunk release build halved the start time without touching a line of the app.
4. **Don't redo work on every return to Home.** The launcher reloaded every app and replayed its
   entrance each time; stopping that, together with the release build, halved the return to Home.
5. **Faster-sounding isn't faster.** Fully pre-compiling apps (`-m speed`) made every one *slower*:
   bigger files to read from slow storage. The TV's own nightly compile was already right.
6. **On Google TV, replacing the home screen takes three steps**, in a set order, or the Home button
   gives a black screen.
7. **Write down every setting before you change it, with the command that puts it back.**
8. **Keep a copy of the build that's on the TV** before installing a new one, and install with
   `pm install -r` only (never uninstall the launcher: you lose the Home setup).
9. **On a TV, nothing should move by itself and everything should say where you are.** The first
   1400 app turned pages on its own and hid two modes behind the arrows; it was hard to use.
10. **A forecast isn't the weather.** "Rain likely" is about the whole day; it can be raining with
    the screen saying "likely", or dry with it saying rain.

## What's in the repo

| Path | What it is |
|---|---|
| [`PROMPT.md`](PROMPT.md) | The prompt to give your assistant |
| [`LESSONS.md`](LESSONS.md) | Everything we learned, including what went wrong |
| [`CLAUDE.md`](CLAUDE.md) | Instructions for an AI agent picking this up: the rules for working on the TV safely |
| [`launcher/`](launcher/) | The home screen and the 1400 app ([details](launcher/README.md)). Your dock, grid order and hidden apps are in `launcher/app/src/main/res/values/config.xml` |
| [`tools/tv.py`](tools/tv.py) | A small ADB client in Python. Works where the usual `adb` is blocked (macOS) |
| [`tools/b64push.py`](tools/b64push.py) | Sends an app file (APK) to the TV in pieces and checks it arrived whole |
| [`tools/disable.sh`](tools/disable.sh), [`tools/restore.sh`](tools/restore.sh) | Switch a debloat list off, or put it all back |
| [`examples/`](examples/) | One folder per TV: what to disable, and (for the MOOR2) the full record and undo steps |

## Build and install the launcher yourself

Needs JDK 17 and the Android SDK (platform 34, build-tools 34.0.0). On macOS:
`brew install openjdk@17 android-commandlinetools && sdkmanager "platforms;android-34" "build-tools;34.0.0"`.

```bash
TV_HOST=192.168.1.50 launcher/build-and-install.sh      # your TV's IP
```

It builds the release version and installs it as the Home app. To talk to the TV by hand:

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50
tools/venv/bin/python -I tools/tv.py --probe                 # model, Android version, firmware
tools/venv/bin/python -I tools/tv.py 'pm list packages'      # any one command
tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt        # apply a list
tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt        # undo it, stock launcher back
```

## Supported TVs

Anything running Android TV or Google TV: Xiaomi/Redmi, TCL, Hisense, Sony, OnePlus, Realme,
Nvidia Shield and most others. Not Samsung (Tizen), LG (webOS) or Amazon Fire TV. Tested on the
models in [`examples/`](examples/).

## Contributing

Add your TV's debloat list, improve the launcher or sharpen the prompt: see
[CONTRIBUTING.md](CONTRIBUTING.md). If you learn something the hard way, add it to
[LESSONS.md](LESSONS.md).

## Licence

MIT, see [`LICENSE`](LICENSE). The fonts are under the SIL Open Font License:
`launcher/FONT-LICENSE-OFL.txt` (Outfit) and `launcher/app1400/FONT-LICENSES-OFL.txt` (the 1400 app's).
