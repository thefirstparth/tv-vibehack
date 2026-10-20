# Material for bhatia.page

Screenshots and short recordings from Parth's TV (a Xiaomi MiTV-MOOR2, Google TV), showing what this fork changed
from Gathin's original launcher. Everything here is public; see "What was hidden" for each file.

## The files

All taken on 21 Oct 2026 between 01:19 and 01:25 (IST) from the TV, at full resolution (1920x1080), with the
launcher `e1cc510` installed. The light in each mood is set with the launcher's screenshot-only `at` extra (the light's
time, not the TV's clock), so **the big clock and date show the real time the picture was taken** (1:19–1:24 AM,
Wednesday 21 October), not the mood's time; the line above the clock does follow the mood. The extra was cleared by a
plain Home intent afterwards (the grid shot, taken after the weekend shot, already shows the real light again). The TV's
clock and settings were not changed.

The sport and weather are live data from that night. Bengaluru's "Rain likely" puts Horizon v3 in its overcast look,
so every mood here is a little greyer and dimmer than on a clear day.

| File | What it shows | Light set to | Taken |
|---|---|---|---|
| `home-now.png` | Home as it was: the line above the clock, clock and date, the four top-bar icons (1400, Inputs, Wi-Fi, Settings), sport on the left, three cities' weather on the right, the dock with YouTube focused | real (late night) | 01:19 |
| `mood-late-night.png` | the light at late night | 02:30 | 01:19 |
| `mood-dawn.png` | dawn | 05:50 | 01:19 |
| `mood-morning.png` | morning | 08:30 | 01:19 |
| `mood-midday.png` | midday | 12:30 | 01:20 |
| `mood-golden-hour.png` | golden hour | 17:20 | 01:20 |
| `mood-sunset.png` | sunset | 17:55 | 01:20 |
| `mood-blue-hour.png` | blue hour | 18:20 | 01:20 |
| `mood-night.png` | night | 21:30 | 01:20 |
| `weekend.png` | the weekend look: "WEEKEND" and its four symbols above the clock, tinted top-bar icons, the gradient on the dock's edge | Saturday 24 Oct, 11:00 (light only; the clock still says Wednesday) | 01:21 |
| `dock-focus.png` | the dock with focus moved to JioHotstar (the rim and glow highlight, the tile's lift) | real | 01:21 |
| `grid.png` | after pressing Down: the dock slid up and the grid of the other apps, Apple TV focused with its name | real | 01:21 |
| `app1400-first.png` | the 1400 app, its first page: Sport (tonight's match in big type, then the next two) under the clock and three cities | its own | 01:24 |
| `app1400-tab2.png` | the 1400 app, The day in a minute (page 1 of 2): the day's headlines from the 20 October edition | its own | 01:22 |
| `rec-dock.mp4` | focus moving right along the dock and back (6.5 s) | real | 01:22 |
| `rec-grid.mp4` | Down to the grid, further down, and back up (5.6 s) | real | 01:23 |
| `rec-home.mp4` | the TV's Media Player, then Home pressed and the launcher appearing (3.5 s; the recording stops once the screen stops changing, so it ends on Home) | real | 01:23 |

**Couldn't get:** the stock Google TV home (item 1g). It is disabled on this TV, and showing it would mean re-enabling it,
which the brief rules out; Gathin's own before/after pictures are in `docs/` (from his Mi TV 4A, not this TV).
The sport and weather lines (item 1e) are in every Home picture, with live data; no separate picture was needed.

**What was hidden, cropped or blurred:** nothing needed it. Checked in every picture and in frames of each recording: no
Wi-Fi name (the Wi-Fi pill shows only an icon), no IP addresses, no account names or avatars, no notifications, and no
personal day (none falls on 21 or 24 Oct; checked against `local.properties` without printing it). The Media Player at
the start of `rec-home.mp4` shows only "Couldn't find videos". **One thing to decide yourself:** the three weather
cities (Bengaluru, Ranchi, Prayagraj) are in every Home and 1400 picture. They aren't on the brief's private list, but if they say where family lives and you'd rather not show that, say so and they can be
blurred.

## What changed from Gathin's original, as it looks on screen

Gathin's original (17 Oct, `19f3c22`): a pitch-black home screen, a greeting and a big thin clock, three frosted
pills (Inputs, Wi-Fi with the network's name, Settings), a dock of four apps with the focused app's name in large type
above it and a colour glow sampled from its banner, and a translucent glass highlight that springs between tiles.

What someone comparing the two would notice. **Measured** means timed on the TV, with the number; **described** means
it is only what you see.

- **The light.** Black is replaced by a soft light at the top of the screen that follows the real sun for Bengaluru,
  worked out on the TV: dawn, morning, midday, golden hour, sunset, blue hour, night and late night, blending
  gradually from one to the next, with the moon in its real phase and real stars at night. Rain or fog in the
  weather dims and greys it. It fades to black well before the dock and never moves while you look at it (it is
  redrawn at most every 5 minutes). *Described.* Its cost, *measured* (20 Oct, 5 alternating rounds after a restart):
  cold start 0 ms, Home return −0.5 ms, memory −1.8 MB against the build before. Drawn at full 960x540 it cost 11.4 MB
  more memory and failed, so it is drawn at 480x270 and scaled up.
- **The line above the clock.** The greeting is replaced by where the sun is: "MIDDAY · SUNSET 5:59 PM",
  "NIGHT · SUNRISE 6:10 AM". *Described.*
- **Sport and weather.** Two blocks of text on the light: on the left, what is live, just finished or next for the
  teams and players Parth follows, with the side he doesn't follow dimmed; on the right, three cities with the
  temperature, feels-like, the day's high and low (the extremes tinted warm or cool) and at most one word or warning.
  They come from his site and are refreshed when Home opens, then every 15 minutes while Home stays on screen (2 during
  a live match); never while an app is open. A stale copy says "as of 8:21". *Described.*
- **The 1400 app.** A newspaper pill at the start of the top bar opens the House of 1400 on the TV: Sport, The day in
  a minute, Money, This week, Results and Where you stand, as tabs. Nothing in it moves by itself. *Described.*
- **What was removed.** The large app name above the dock and the colour glow behind it. *Measured* against the
  original on 18 Oct (3 runs each): GPU time per frame (p90) 10 vs 15–17 ms, memory 74–79 vs 86–91 MB, fewer janky
  frames (3/3/3 vs 6/2/4 per run).
- **The highlight.** A crisp rim and soft glow drawn on the GPU replace the translucent glass; it still springs from
  tile to tile, and comes back on the tile you left when you return to Home from an app. Every tile stays in full colour.
  *Measured:* no slower (Home return −0.5 ms, 20 Oct).
- **Named inputs.** The Inputs menu shows what is plugged in over HDMI-CEC ("Living room TV") on its port's row.
  *Described.*
- **An icon-only top bar.** Inputs, Wi-Fi and Settings are icons; the Wi-Fi one no longer shows the network name, and
  shows an amber "!" with "No internet" when the connection drops. *Described.*
- **Steady digits.** The clock and the scores use fixed-width digits, so they don't shift as they change.
  *Described.*
- **The weekend look.** On Saturday and Sunday the line above the clock says "WEEKEND" with four small symbols, the
  top-bar icons are tinted and the dock's top edge is a thin colour gradient. *Measured:* no slower (Home return +4.5
  ms weekday, −6.0 ms Saturday; allowed 17 ms).
- **Speed.** *Measured:* a cold start of the home screen went from 1.48 s to 0.61 s, and Home return from 792 to
  403 ms (18 Oct). Most of that came from shipping it as a release build (shrunk and optimised) instead of the
  debug build, and from not reloading the apps every time Home appears; it has stayed about 0.6 s since. Gathin's
  original as a release build was never timed, so "twice as fast as his" isn't measured; "twice as fast as the first
  build on this TV" is.

## Corrections to the site text

Each quoted line is followed by the suggested replacement and why.

> Tagline: my TV, minus the ads, and faster

Fine, if "faster" means faster than when it started. The stock Google TV home was never timed against it, so don't
say "faster than Google's". Suggested: *my TV, minus the ads, and measured faster.*

> I ran it on my own Google TV, then kept going: a light that follows the sun, the day's sport and weather, my
> newspaper on the big screen, and a home screen that opens more than twice as fast.

True as measured (1.48 s → 0.61 s, 2.4 times), but the comparison is with the first build on this TV, and most of the
gain came from building it as a release build. Suggested: *... and a home screen that opens in 0.6 seconds instead
of 1.5.*

> Nothing is uninstalled, and it checks each step with a screenshot of the real TV.

Not quite: the bloat is switched off, not uninstalled, but five apps Parth didn't use were uninstalled (ZEE5, NBA,
MUBI, Tata Play, DAZN; reinstallable from the Play Store). Suggested: *Nothing built into the TV is uninstalled, only
switched off, and it checks each step with a screenshot of the real TV.*

> A small launcher replaces the stock one: a big clock, a dock of four apps with the rest one press away, and no ads,
> no autoplaying video and nothing running in the background.

"Nothing running in the background" is true of the launcher (no background service); the TV's own system still runs.
Suggested: *... no ads, no autoplaying video, and the home screen runs nothing in the background.*

> Behind it, a soft light follows the sun through eight moods, from dawn to golden hour to night, worked out on the TV
> itself.

Since 20 Oct (Horizon v3) it blends gradually between the eight instead of stepping, and adds the moon's real phase,
real stars and the weather. The sun is worked out on the TV; the weather comes from the site. Suggested: *Behind it, a
soft light follows the sun from dawn to golden hour to night, blending through eight moods, with the real moon and
stars at night. The sun is worked out on the TV itself.*

> The line above the clock says where the sun is: midday, sunset at 5:59.

Fine (on screen: "MIDDAY · SUNSET 5:59 PM").

> The day's sport and the weather in three cities come from my newspaper's site, asked once each time Home opens,
> never on a timer and never while you watch.

No longer true since 20 Oct: while Home stays on screen it asks again every 15 minutes (every 2 during a live match),
and 8 seconds after an old copy. "Never while you watch" is still true: it stops as soon as an app opens. Suggested:
*... asked when Home opens and every 15 minutes while it stays on screen, and never while you watch.*

> Gathin's idea was the clever part: ... I added two rules of my own. Never make the TV slower. And on a TV, nothing
> should move by itself, and everything should say where you are.

Fine. In practice "never slower" has a tolerance: a cold start up to 20 ms slower is allowed, because the test's own
noise is about that size; Home return, stutter and memory must be no worse. Optional: *Never make the TV slower, and
measure it to prove it.*

> I forked it the next morning and built on top of it with Claude, on my own Xiaomi Google TV.

Fine (first commits 18 Oct, 06:00).

> Every change was timed on the real TV against the build before it, and kept only if it was no slower.

Overstated: every launcher change was timed, but the first two on 18 Oct only with 3 runs each, and one of them (the
sport lines and the light) measured slower (1.55 → 1.8 s) and was fixed afterwards rather than removed. Since the
evening of 18 Oct every launcher change had an alternating A/B on the TV. Suggested: *From the first evening, every
change to the home screen was timed on the real TV against the build before it, and kept only if it was no slower.*

> Two nicer-looking animations failed that test and came out.

Close: it was spring motion and glass tints that followed focus (plus the colour glow), and a fair test showed them
stuttering after a one-run test had passed them. A lighter, GPU-drawn version of the springing highlight came back
later and passed. Suggested: *Two nicer-looking animations and a colour glow failed that test and came out.*

> A cold start went from 1.5 seconds to 0.6.

True (1,479 → 612 ms, medians of 5 alternating runs, 18 Oct; about 0.6 s since).
