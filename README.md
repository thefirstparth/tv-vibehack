# tv-vibehack

**Your TV, minus the ads.** Debloat any Android TV / Google TV over ADB and give it a clean,
tvOS-inspired launcher. Hand the prompt to Claude Code or any coding assistant, answer a few
questions about which apps you use, and it does the rest on the real TV.

| Stock home screen | After |
|---|---|
| ![Stock Google TV home with a sponsored ad](docs/stock-home.jpg) | ![tvOS-style launcher](docs/new-home.jpg) |

Everything is reversible: packages are disabled, never uninstalled, and a restore script puts the
stock launcher back.

## Quick start

1. On the TV: Settings → Device Preferences → About → press OK on **Build** seven times → back →
   Developer options → turn on **USB debugging** (and Network/ADB debugging if your TV shows it).
   Keep the TV on and on the same Wi-Fi as your computer.
2. Open Claude Code (or your assistant of choice) on your computer and paste the prompt from
   [`PROMPT.md`](PROMPT.md), filling in your TV model.
3. Accept the "Allow USB debugging?" prompt on the TV when told to, then answer the checklist
   questions (which apps you use, which four go in the dock).

That's it. The assistant backs up, debloats, builds and installs the launcher, and verifies each
step with screenshots from the TV. If you'd rather do it by hand, the pieces are below.

## What's in the repo

| Path | What it is |
|---|---|
| [`PROMPT.md`](PROMPT.md) | The prompt. Brand-neutral; the assistant discovers your TV's bloat. |
| [`launcher/`](launcher/) | The tvOS-style launcher. Edit `app/src/main/res/values/config.xml` for your dock, hidden apps and Live TV package, then run `build-and-install.sh`. |
| [`tools/tv.py`](tools/tv.py) | Pure-Python ADB client (shell, batch, push, pull). Works where the stock `adb` is blocked. |
| [`tools/b64push.py`](tools/b64push.py) | Uploads larger files (APKs) as base64 chunks with an md5 check. |
| [`tools/disable.sh`](tools/disable.sh), [`tools/restore.sh`](tools/restore.sh) | Apply or undo a debloat list. |
| [`examples/`](examples/) | Per-TV debloat lists. Add yours, see [CONTRIBUTING.md](CONTRIBUTING.md). |

## The launcher

A small native Android TV home screen (`com.gathin.tvhome`), built for sets with 1 GB of RAM:

- The "Horizon" background: a soft band of light behind the sport and weather lines that fades to
  black well before the dock, in one of four colours by time of day (morning, day, evening, night),
  with a faint edge of the same colour on top of the dock. It is drawn once into a small image and
  used as the window's background, so the screen still draws one layer as with plain black; it
  changes at most four times a day, never with focus, and never animates.
- A dock at the bottom in a lighter rounded band with the four apps you choose. Press Down and the
  dock slides up to reveal the rest of your apps in a grid. (The large app name above the dock and
  the colour glow behind it were removed: the tile already shows the name, and the full-screen glow
  was the GPU's heaviest job. Without them the home screen measured smoother than before.)
- One translucent glass highlight springs from tile to tile. The focused tile lifts and shows its
  name.
- Frosted pills top right: **Inputs** (a dropdown of HDMI ports, AV and Live TV), **Wi-Fi**
  (shows the network name) and **Settings**. Greeting, a large thin clock and the date top left,
  set in [Outfit](https://fonts.google.com/specimen/Outfit).
- Deterministic D-pad navigation, last focused tile remembered, staggered entrance animation.
  Down from the top bar goes to the dock tile you were last on; pressing Home brings focus back
  to the last tile even if you left from a top-bar button. The Inputs menu sizes itself to its
  rows and shows a device found over HDMI-CEC (for example "Living room TV") on its port's row.
- No blur, no video, no background services, and no network, with one opt-in exception below.

### Sports and weather (optional)

Above the dock, on plain black: what is live, just finished or next among the teams and players
you follow (left, under the clock), and now, feels-like, high and low with at most one word or
warning for three cities (right). The launcher reads it from a single feed of your own (the
companion site's `/api/live/tv`), which decides what to show; the TV only words the times and
draws the lines.

- **Off unless you set it.** Put the feed's address in `launcher/local.properties` (not committed,
  as the address may be private):

  ```
  tv.feedUrl=https://your-site.example/api/live/tv
  ```

  Empty or missing, the launcher never touches the network and looks exactly as before.
- **One request each time the home screen appears** (TV on, Home, back from an app), on a
  background thread with a 5 second timeout and no retries. Never on a timer, never while you
  watch: no services, no WorkManager. Every minute, with the clock, the times are worded again from
  what is already on screen ("Tonight 12:30 am   IN 17H", then "Started"), without a request.
- **When the site does not answer,** the last good copy is shown with "as of h:mm am" in amber, and
  anything whose start has passed is dropped unless it was live less than ten minutes before.
- Lines never end in "…": a long line steps down to shorter names, then the competition's short
  form, then without it.
- One accent: the competition's colour on its name and on the countdown pill
  (`accent_colours` in `config.xml`). Everything else is white.

The Inputs menu can name a port: `input_names` in `config.xml` (`HDMI 2|Xbox`) shows "Xbox" with
"HDMI 2" beside it, and a games console gets a controller icon.

Configure it in [`launcher/app/src/main/res/values/config.xml`](launcher/app/src/main/res/values/config.xml):
dock order, hidden apps, Live TV package and columns. Build and install with

```bash
TV_HOST=192.168.1.50 launcher/build-and-install.sh
```

Needs JDK 17 and the Android SDK (platform 34, build-tools 34.0.0). On macOS:
`brew install openjdk@17 android-commandlinetools && sdkmanager "platforms;android-34" "build-tools;34.0.0"`.

## Talking to the TV by hand

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50                                   # your TV's IP
tools/venv/bin/python -I tools/tv.py --probe                 # model, Android version, firmware
tools/venv/bin/python -I tools/tv.py 'pm list packages'      # one command
printf 'cmd1\ncmd2\n' | tools/venv/bin/python -I tools/tv.py --batch
tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt        # apply a list
tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt        # undo it, restore stock launcher
```

### Gotchas we hit

- **macOS: `adb connect` says "No route to host"** while the port is clearly open. Recent macOS
  treats the Google-signed `adb` as its own app under Local Network privacy and silently blocks it.
  `tools/tv.py` avoids the binary entirely.
- **No "wireless debugging" toggle** on some TVs (Xiaomi among them). USB debugging alone opens
  network ADB on port 5555.
- **Chained `pm` commands** (`pm disable a; pm disable b`) may silently stop after the first. Run
  one per shell call; `--batch` does this.
- **Large pushes hang** through the Python client. Use `tools/b64push.py` for APKs.
- **Disable the stock launcher only after the new one is installed**, or the Home button lands on
  a factory test app.
- **Third-party launchers** often charge for basics like a custom wallpaper. Hence `launcher/`.
- **No on-screen keyboard** after switching launchers (seen on Xiaomi): the default input method
  pointed at a Baidu IME that isn't installed. Fix with
  `tools/tv.py 'ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME'`.

## Supported TVs

Anything running Android TV or Google TV: Xiaomi/Redmi, TCL, Hisense, Sony, OnePlus, Realme,
Nvidia Shield and most others. Not Samsung Tizen, LG webOS or Amazon Fire TV (different OS, no ADB
launcher replacement). Tested so far on the models in [`examples/`](examples/).

## Contributing

Add your TV's debloat list, improve the launcher, or sharpen the prompt. See
[CONTRIBUTING.md](CONTRIBUTING.md).

## Licence

MIT, see [`LICENSE`](LICENSE). The Outfit typeface is under the SIL Open Font License, see
`launcher/FONT-LICENSE-OFL.txt`.
