# Next session: the plan (parked 2026-10-19, start only when Parth says "start")

Three parts, in this order (Part 1 is already decided). Times are caps, not estimates to stretch.

## The plan to run: about an hour, hard stop at 70 minutes (Parth, 2026-10-19; overrides the times below)

Full rigour: the 5-round A/B, and every app tested from a *measured* clean state with playback proved.
Timings are from today's runs: an A/B round ~2 min 15 s; a restart plus rest ~4.5 min; a Home
measurement (warm-up + 2 runs) ~3 min; 60 s of proved playback ~1.5 min.

1. **TV, from 0:00 — Part 3, restart only when needed.** Restart, rest, measure Home (clean). Then
   JioHotstar: play 60 s, measure. If Home stayed smooth, the TV is still clean: Netflix next without a
   restart, then FanCode (MEMC off, as now) last. After any app that lags: restart, re-measure clean,
   carry on. ~21 min if only FanCode lags, ~35 if all three do.
2. **Mac, at the same time — Part 1b,** limit 15 min: row-end fix, unit test, emulator on every row,
   release build.
3. **TV — Part 2,** the full A/B (5 alternating rounds each, medians, the keep rules), reusing the
   restart after FanCode. ~23 min (27 if a fresh restart is needed).
4. **Key check** (row wrap, dock, Inputs, newspaper pill, Settings, grid), install, **merge to `main`**
   (PR), Home, commit, push, report. ~7 min.

**Expected ~51 min; worst case ~69; hard stop at 70** — at the stop: put back the build the TV had,
restart if Home lags, press Home, push what's committed, report where it stopped.
Not included: fix attempts for an app that still lags (AI super-resolution off, minimal
post-processing for Home), ~12 min each — reported for the next session.

Start prompt:

```
Start the plan in examples/xiaomi-mitv-moor2/NEXT.md ("about an hour, hard stop at 70 minutes").
Pull first. Follow CLAUDE.md's rules. Part 1 is decided, skip it.
Update me at each milestone with an ETA. If the TV is in use, ask before touching it.
At the end: TV on Home, everything committed and pushed, a short report of what got fixed and what didn't.
```

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
