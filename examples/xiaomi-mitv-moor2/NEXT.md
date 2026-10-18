# Next session: the plan (parked 2026-10-19, start only when Parth says "start")

Three parts, in this order (Part 1 is already decided). Times are caps, not estimates to stretch.

## Part 1: Humidity — decided, no change needed

Parth chose (2026-10-19) the layout that is already on the TV: "Humidity 94%" on its own grey line
under H/L, for **all three cities**, and the alert word (amber) **under** humidity when there is one.

- **No droplet icon** (it reads as rain), **no phrases or extra wording** ("Umbrella by 4 PM",
  "6° colder than home": he finds them too much), no new colours.
- So skip the three-layout comparison; only check it in Part 2's QA (evening "… tomorrow" word under
  humidity, columns equal and tight).

## Part 1b: Right at the end of a row jumps to Settings — about 15 min

**Seen by Parth:** Right on the last tile of a row (FanCode in the dock, Spotify on row 2) moves focus
up to the **Settings pill** instead of the first tile of the next row. Android's focus search looks for
the nearest thing to the right, and the only thing there is the top bar.

**Fix:** at the last tile of a row, Right goes to the first tile of the next row (and on the very last
tile, nowhere); to match, Left on the first tile of a row goes to the last tile of the row above
(Left on YouTube stays put). Up and Down unchanged; the top bar is reached only with Up.

**Test:** a unit test for the row maths; on the emulator, Right along every row and Left back; on the
TV, the same keys in the Part 2 QA. It goes in the same build as humidity, so one A/B covers both.

## Part 2: QA of the launcher changes since the last full test — about 25 min

The weather-gap fix, the newspaper icon and humidity went on the TV with only a 2-round check
(or none). Before merging them into `main`:

- Full A/B on the TV: `tvhome-release-rows.apk` (last fully tested) against the new build,
  alternating, 5 rounds each, medians, after a restart (`abrun.sh`). Keep only if cold start is no
  more than 20 ms slower and Home return, janky frames and memory are no worse.
- Check: Right along each row wraps to the next row (never to Settings), Left back; Home, the four dock apps, Inputs, the newspaper pill (opens 1400, "1400" shows on focus),
  Settings, the grid, the weather in the evening (from 6 pm the long "… tomorrow" word wraps inside
  Bengaluru's column, columns stay tight).
- Then merge the work branch into `main` (PR).

## Part 3: Video lag test — FanCode, JioHotstar, Netflix — hard cap 60 min

**What we know:** after FanCode plays video, Home stays 60–70% janky until a restart (any launcher).
YouTube playback does not do it. Changing picture settings from the computer does not survive a
restart; changes made in the TV's own menu do. Right now MEMC is **off** (set in the menu) and AI
super-resolution is **on** (3).

**Each run** (about 10 min): restart → measure Home → play 60 s in the app, *proved* by the audio
player state (`dumpsys audio`, a player "started") and the player activity → Home → measure.
Real remote press = stop, restore, Home.

| # | Run | Answers | Cap |
|---|---|---|---|
| 1 | FanCode, MEMC off (as it is now) | Does MEMC off fix FanCode? | 0:10 |
| 2 | JioHotstar, same settings | Does Hotstar's player cause it too? | 0:20 |
| 3 | Netflix, same settings | And Netflix? | 0:30 |
| 4 | For any app that still lags: AI super-resolution off **in the TV's menu** (driven with remote keys, screenshot each step) | Is AISR the cause? | 0:42 |
| 5 | If still lagging: the launcher asks for "minimal post-processing" (the TV supports it) while Home shows — a ready-built APK, one install | Can Home avoid it by itself? | 0:55 |
| — | Restore what didn't help, restart, leave on Home, write results here and in LESSONS.md | | 1:00 |

Prepared before the clock starts (on the Mac, no TV): the playback and measurement scripts and the
minimal-post-processing APK. The menu path to AI super-resolution is found at the start of run 4.

**The answer at the end:** for each app, lags or not; which setting (if any) fixes it and what it
costs in picture; or that it's the TV's drivers and the workaround stays "restart after watching".
