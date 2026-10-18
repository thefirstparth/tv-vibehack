# The launcher and the 1400 app

Everything about the two apps in this folder, in detail. For the short version, see the
[main README](../README.md); for what we learned building them, see [LESSONS.md](../LESSONS.md).

| Folder | App | What it is |
|---|---|---|
| `app/` | `com.gathin.tvhome` | The home screen |
| `app1400/` | `com.gathin.tv1400` | The 1400 app (optional): a full-screen "paper" for the TV |
| `shared/` | library | What both need: sun times and mood, the feed, the personal days |

## The home screen

A small native Android TV home screen (`com.gathin.tvhome`), built for sets with 1 GB of RAM:

- The "Horizon" background (v2): a soft light behind the sport and weather lines that fades to black
  well before the dock. Its mood follows the sun: the TV works out sunrise and sunset for its city
  itself (NOAA's formula, no network) and moves through late night, dawn, morning, midday, golden
  hour, sunset, blue hour and night. A faint season (winter, summer, monsoon, post-monsoon) sits on
  top; rain or a storm in the feed's weather word mutes it with a grey haze; while a match is live a
  faint glow in the competition's colour sits under the sport lines; and personal days can have
  their own light and greeting. It is drawn once per combination into a small image, off the main
  thread after the first frame, and used as the window's background, so the screen still draws one
  layer; it never reacts to focus and never animates. The dock's top edge takes the light's colour.

  Personal days are private and live only in `launcher/local.properties` (not committed):

  ```
  tv.day.1=MM-DD|Greeting|core|spot|band|top
  ```

  with each colour as `r,g,b,a` (alpha 0 to 1). None set: no personal days.
- A dock at the bottom in a lighter rounded band with the four apps you choose. Press Down and the
  dock slides up to reveal the rest of your apps in a grid. (The large app name above the dock and
  the colour glow behind it were removed: the tile already shows the name, and the full-screen glow
  was the GPU's heaviest job. Without them the home screen measured smoother than before.)
- One highlight (a crisp rim and a soft glow around the tile, never over its logo) springs from tile
  to tile. The focused tile lifts and shows its name; every tile stays in full colour.
- Above the clock, the light's mood and the sun's next turn, worked out on the TV for the place in
  `launcher/shared/src/main/res/values/config.xml` (`horizon_lat`, `horizon_lon`): "MIDDAY · SUNSET
  5:59 PM", "NIGHT · SUNRISE 6:10 AM"; in the Sunset mood the time alone; on a personal day its
  greeting in the mood's place.
- A small newspaper pill at the start of the top bar opens the **1400** app (below); it hides itself when
  the app is not installed.
- Frosted pills top right: **Inputs** (a dropdown of HDMI ports, AV and Live TV), **Wi-Fi**
  (shows the network name) and **Settings**. A large thin clock and the date top left,
  set in [Outfit](https://fonts.google.com/specimen/Outfit).
- Deterministic D-pad navigation, last focused tile remembered, staggered entrance animation.
  Down from the top bar goes to the dock tile you were last on; pressing Home brings focus back
  to the last tile even if you left from a top-bar button. The Inputs menu sizes itself to its
  rows and shows a device found over HDMI-CEC (for example "Living room TV") on its port's row.
- No blur, no video, no background services, and no network, with one opt-in exception below.

**The weekend look.** All of Saturday and Sunday (the TV's local time), and never on a personal day:
- the line above the clock says **WEEKEND** (in tone 1) and four small drawn symbols (a football, a
  note, a cocktail glass, a heart) in place of the mood name, then the sun time as on weekdays:
  "WEEKEND ⚽ ♪ 🍸 ♥ · SUNSET 5:59 PM";
- the top-bar pills' icons are tinted (newspaper tone 1, Inputs tone 2, Wi-Fi tone 3, Settings gold
  `#F5D58B`); a focused pill looks as always;
- the dock's top edge is a thin gradient of tone 1 → gold → tone 2 → tone 3 at about 60%.

The tones follow the Horizon look in use: tone 1 from its core, tone 2 from its spot, tone 3 from its
band, each kept at its hue as a light pastel (HSV saturation 0.40, value 0.95). The heart is
`#F2A9BA`. In rain or a storm every weekend colour is softened by Horizon's own 0.62 (the football's
off-white stays). The Horizon light itself does not change. Worked out in `Weekend.java` (pure,
unit-tested) whenever the Horizon state is checked: the existing minute tick and when Home appears;
no new timers, no animation.
Not on the MOOR2 yet: its A/B (2026-10-19) was even on cold start, stutter and memory but 6 ms slower
on Home return (370 → 376 ms, inside the runs' spread), which the keep rule doesn't allow.
The cheaper version (key/claim, nothing on weekdays, built off the main thread) was 16 ms slower on a
weekday's Home return in its A/B (380 → 396 ms) although traces show its Home return doing no more work.

## Sports and weather (optional)

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
- One accent: the competition's colour on its name and on the countdown
  (`accent_colours` in `config.xml`). Everything else is white or grey. THEN and LATER line up
  in three columns (time, match, competition), and the headline shares its baseline with the
  big temperatures. The Wi-Fi pill shows signal bars, and the network's name while focused.

The Inputs menu can name a port: `input_names` in `config.xml` (`HDMI 2|Xbox`) shows "Xbox" with
"HDMI 2" beside it, and a games console gets a controller icon.

Configure it in [`app/src/main/res/values/config.xml`](app/src/main/res/values/config.xml):
dock order, hidden apps, Live TV package and columns. Build and install with

```bash
TV_HOST=192.168.1.50 ./build-and-install.sh
```

It installs the **release** build (`app/build/outputs/apk/release/app-release.apk`): shrunk by R8 with
unused resources removed (`launcher/app/proguard-rules.pro`, `res/raw/keep.xml`), not debuggable, and
signed with the same `~/.android/debug.keystore` as debug builds, so `pm install -r` replaces an
installed debug build in place and the Home setup stays. `./gradlew :app:assembleDebug` still builds
the debug APK for testing.

Measured on the TV (MiTV-MOOR2, 2026-10-18; the old debug build against this release build, alternating, 5 rounds
each, medians): cold start 1479 → 612 ms, return to Home from YouTube 792 → 403 ms, janky frames while scrolling
the grid 0.74% → 0%, memory (PSS) 78.5 → 66.8 MB. The return to Home is faster also because the app list is no
longer reloaded and the tiles' entrance is no longer replayed on every return (only on a package change and a
real start).

Needs JDK 17 and the Android SDK (platform 34, build-tools 34.0.0). On macOS:
`brew install openjdk@17 android-commandlinetools && sdkmanager "platforms;android-34" "build-tools;34.0.0"`.

## The 1400 app (optional)

`launcher/app1400` (`com.gathin.tv1400`) is a separate app in the same Gradle project: the companion
paper on the TV, in its night look (Horizon V3: the paper's fonts, flat, a halftone sun in the mood's
inks above a double rule, the nameplate and the sections as tabs in the foot). One row of sections:
**Sport**, **The day in a minute** and **Money** (under the clock and the three cities), then **This
week**, **Results** and **Where you stand** (under the next event in big type). It opens on Sport.
Left and Right go along the tabs (the one showing is bright, the rest dimmed), Up and Down turn the
pages of a section that has more than one (its tab says "2 / 3"), Back closes it. Nothing moves by
itself.

It reads `/api/live/tv?saver=1` from the same `tv.feedUrl`, only while it is open (when it opens, then
every 15 minutes, every minute while live), keeps the screen on, and closes itself after 3 hours
without a button press. Nothing runs when it is closed. Once a minute the content moves a few pixels
(burn-in). Its fonts are subsets of Newsreader, Libre Franklin, Source Serif 4, Titillium Web,
Playfair Display and UnifrakturMaguntia, all under the OFL (`launcher/app1400/FONT-LICENSES-OFL.txt`).

`launcher/shared` holds what both apps need and nothing else: the sun's times and the mood, the line
above the clock, the feed and how it is worded, and the personal days.

Build and install it: `./gradlew :app1400:assembleDebug` in `launcher/`, then push
`app1400/build/outputs/apk/debug/app1400-debug.apk` with `tools/b64push.py` and `pm install -r`. (The
1400 app has no signed release build yet, so `:app1400:assembleRelease` makes an APK that won't
install; the home screen's own release build is set up, see above.) Undo: `pm uninstall com.gathin.tv1400` (the newspaper pill hides itself) and take it out
of `grid_order`.
