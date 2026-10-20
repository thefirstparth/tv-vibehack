# Launcher benchmarks on the MiTV-MOOR2

Every recorded speed measurement of the home screen (`com.gathin.tvhome`) on this TV, oldest first, with
tonight's go-live test for the weekend look (2026-10-19).

## Method (tonight)

The scripts are in [`tools/ab/`](../../tools/ab/README.md) (`night.sh`, `saturday.sh`, `judge.py`); from 2026-10-20
they also record the grid run's frame-time percentiles (p50/p90/p95/p99), and the Home returns start from **Media
Player** (`com.xiaomi.mitv.mediaexplorer`), not YouTube (Parth's rule: no test touches YouTube or any app's data).
Settings can't be used for it: on Google TV it is a side panel over Home, so Home never leaves and every "return"
reads the same stale time.

- After a restart and about a minute's rest, with nobody using the TV (a remote press stops the test).
- Each **slot** installs one build (`pm install -r`), warms it up (one scripted grid run, discarded), then
  measures: **1 cold start** (Settings in front, `am force-stop` the launcher, `am start -W`, TotalTime);
  **1 scripted grid run** (janky frames %, from `dumpsys gfxinfo`, and memory, TOTAL PSS from
  `dumpsys meminfo`); **10 Home returns** (YouTube opened, 4 s, then the Home intent with `am start -W`,
  TotalTime); and a crash/freeze check (Home on top, no launcher crash in the crash log).
- **Weekday test:** 3 rounds of current → weekend → current. The two current slots against each other give
  tonight's **noise** (the gap between their Home return medians).
- **Saturday test:** automatic time off, the clock set to Saturday 24 October 13:00, restart; rounds of
  current → weekend; then automatic time and time zone back on and the clock checked.
- Time limits cut the last slot of each test: a round counts only with both builds in it.
- Tonight's Home returns (~100 ms) are lower than in the earlier A/Bs (~380 ms): those measured one Home
  return per round, straight after the install and cold start; tonight measures ten in a row per slot.
  Compare rows within one test only.

## The qualifying rule (per test, on tonight's side-by-side numbers)

- Home return median no more than max(17 ms, tonight's noise) slower, and never more than 30 ms slower;
- cold start no more than 20 ms slower; janky frames no more than 0.5 points higher; memory no more than
  3 MB higher; no crashes or freezes; the seven weekend/weekday cases look right.

## Tonight's decision

The weekend build (`tvhome-release-weekend2.apk`, `87ddafb`) **passed both tests**, so **rule 1** applied
("a candidate passed both the weekday and the Saturday test: install it"). It was installed with
`pm install -r` at 06:21 IST; Home, the four dock apps, Inputs, the 1400 pill and Settings all checked.
No fix attempt was needed. The previous build is `backup/launcher-builds/tvhome-on-tv-before-golive.apk`
(same as `tvhome-on-tv-before-weekend.apk`).

## Results

| Date | Build (commit) | Test | Cold start | Home return median (range) | Janky | Memory | Notes |
|---|---|---|---|---|---|---|---|
| 2026-10-18 | Startup work (`dc84c0b`) | before → after | 1.84 s → 1.44 s | — | — | 87–92 → 82–85 MB | fonts set once, Horizon off the main thread |
| 2026-10-18 | Lines and Horizon (`92bba7c`) | 3 warm runs | 1.55 s → 1.8 s | — | 3/3/2 → 4/4/2 (lines), 2/3/2 (Horizon) runs | 74–79 → 85–89 MB | janky as counts per run |
| 2026-10-18 | Effects dropped (`979258b`) vs Gathin23's original | 3 warm runs each | 1.52 s vs 1.57 s | — | 3/3/3 vs 6/2/4 runs | 74–79 vs 86–91 MB | frame p90 5–6 vs 7 ms, GPU p90 10 vs 15–17 ms |
| 2026-10-18 | Sun line after first frame (`a4f83e4`) | 20–25 cold starts each, alternating | 1,507 → 1,462 ms (previous build 1,464) | — | — | — | |
| 2026-10-18 | Release build + no reload on Home (`e76f35e`, `5902d7d`) vs the debug build | 5+5 alternating, medians | 1,479 → 612 ms | 792 → 403 ms | 0.74% → 0% | 78.5 → 66.8 MB | kept |
| 2026-10-19 | Row wrap, weather fix, newspaper, humidity (`313eab5`…, PR #2) | 5+5 alternating, medians | 591 → 581 ms | 382 → 374 ms | 0% → 0% | 68.6 → 69.0 MB | kept |
| 2026-10-19 | Weekend look, first (`3c66f9f`) | 5+5 weekday | 592 → 593 ms | 370 → 376 ms | 0% → 0% | 66.4 → 66.1 MB | not kept ("no slower" rule then) |
| 2026-10-19 | Weekend look, cheaper (`87ddafb`) | 5+5 weekday | 587 → 572 ms | 380 → 396 ms | 0% → 0% | 66.8 → 66.3 MB | not kept; traces: launch 121 → 105 ms, onResume 24.0 → 17.8 ms, main-thread CPU 26.7 → 26.4 ms |
| 2026-10-19 night | current (`tvhome-on-tv-before-weekend`) | weekday, 5 slots | 584 ms (575–610) | 104.0 ms (81–162, n=50) | 0% | 70.3 MB | noise: slot 1 93.5 ms vs slot 3 109.0 ms = **15.5 ms** |
| 2026-10-19 night | weekend (`87ddafb`) | weekday, 3 slots | 589 ms (584–594) | 108.5 ms (85–135, n=30) | 0% | 69.4 MB | **pass**: +4.5 ms (allowed 17), cold +5 ms, −0.9 MB |
| 2026-10-19 night | current | Saturday (clock set), 2 full rounds | 570.5 ms (562–579) | 102.5 ms (74–139, n=20) | 0% | 74.9 MB | round 3's current slot (601 ms, 82–120) ran but had no pair |
| 2026-10-19 night | weekend (`87ddafb`) | Saturday (clock set), 2 full rounds | 585.5 ms (578–593) | 96.5 ms (84–150, n=20) | 0% | 74.2 MB | **pass**: −6.0 ms, cold +15 ms, −0.7 MB; installed |
| 2026-10-20 | current (`tvhome-on-tv-before-gpu`, = `87ddafb`) | weekday, 6 slots, Home returns from Media Player | 595 ms (577–613) | 88.5 ms (75–109, n=60) | 0% | 65.4 MB | noise: C1 89.5 vs C2 88.0 ms = **1.5 ms**; frame times p50/p90/p95/p99 5/6/6/7 ms (813 frames a run) |
| 2026-10-20 | Home on Home, GPU highlight, highlight on return (`4cec0f2`) | weekday, 3 slots, Home returns from Media Player | 586 ms (574–604) | 88.0 ms (79–101, n=30) | 0% | 66.1 MB | **pass**: −0.5 ms (allowed 17), cold −9 ms, +0.7 MB; p50/p90/p95/p99 5/6/7/10 ms (707 frames a run); Saturday test skipped (Parth); installed 06:27 |

## 2026-10-20

- Weekday test only, 3 full rounds of current → new → current after a restart (05:56–06:27), judged by
  `tools/ab/judge.py`: **pass** on every point. Saturday test skipped by Parth's choice: the weekend code didn't change
  and the TV's clock was not to be touched.
- New build's frame-time tail: p99 10 ms against 7 ms (p95 7 against 6), with fewer frames drawn per grid run (707 against
  813). Still well inside a 16.7 ms frame, and janky frames stayed 0%.
- Two earlier starts were discarded: one round with Home returns from YouTube (stopped when YouTube was found signed out;
  `backup/ab/weekday-youtube-round1-discarded.log`) and one slot with Home returns from Settings (all ten read the same
  stale 227 ms; see Method).
