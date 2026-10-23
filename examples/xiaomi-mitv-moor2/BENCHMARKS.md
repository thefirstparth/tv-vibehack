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
| 2026-10-20 07:00 | current (`4cec0f2`, on the TV) | short weekday, 2 rounds current → new | 595 ms (594, 596) | 93.5 ms (77–122, n=20) | 0% | 70.8 MB | noise taken from the 05:56 test (1.5 ms) |
| 2026-10-20 07:00 | weather highlights (`97d3e73`) | short weekday, 2 rounds current → new | 632.5 ms (649, 616) | 92.5 ms (79–99, n=20) | 0% | 67.6 MB | **fail**: cold start +37.5 ms (allowed 20); Home return −1.0 ms, −3.2 MB; **not installed** |
| 2026-10-20 07:17 | current (`4cec0f2`) | re-test, 3 rounds current → new, **5 cold starts a slot** | 588 ms (572–626, n=15) | 89.5 ms (82–106, n=30) | 0% | 67.4 MB | noise from the 05:56 test (1.5 ms) |
| 2026-10-20 07:17 | weather highlights (`97d3e73`) | re-test, 3 rounds current → new, **5 cold starts a slot** | 606 ms (574–618, n=15) | 88.5 ms (78–100, n=30) | 0% | 68.2 MB | **pass**: cold +18 ms (allowed 20), Home return −1.0 ms, +0.8 MB; p99 frame 9 vs 10 ms; installed 07:43 |
| 2026-10-20 07:58 | current (weather highlights, `97d3e73`) | short weekday, 2 rounds current → new, 5 cold starts a slot | 604 ms (584–616, n=10) | 92.5 ms (85–101, n=20) | 0% | 69.6 MB | noise from the 05:56 test (1.5 ms) |
| 2026-10-20 07:58 | our side stands out (`264f568`) | short weekday, 2 rounds current → new, 5 cold starts a slot | 593 ms (582–622, n=10) | 93.0 ms (84–109, n=20) | 0% | 68.9 MB | **pass**: Home return +0.5 ms, cold −11 ms, −0.6 MB; installed 08:16 |
| 2026-10-20 08:21 | current (`264f568`) | short weekday, 2 rounds current → new, 5 cold starts a slot | 588.5 ms (569–617, n=10) | 95.5 ms (82–112, n=20) | 0% | 69.5 MB | noise from the 05:56 test (1.5 ms) |
| 2026-10-20 08:21 | tabular digits (`7fe2268`) | short weekday, 2 rounds current → new, 5 cold starts a slot | 600 ms (572–623, n=10) | 98.0 ms (84–111, n=20) | 0% | 67.4 MB | **pass**: Home return +2.5 ms, cold +11.5 ms, −2.1 MB; installed 08:39 |
| 2026-10-20 08:57 | current (`7fe2268`) | short weekday, 2 rounds current → new, 5 cold starts a slot | 598.5 ms (579–612, n=10) | 99.5 ms (84–114, n=20) | 0% | 68.6 MB | started from the screensaver (idle); noise from the 05:56 test (1.5 ms) |
| 2026-10-20 08:57 | icon-only top bar, Wi-Fi problems shown (`1bfd00c`) | short weekday, 2 rounds current → new, 5 cold starts a slot | 600.5 ms (579–635, n=10) | 97.0 ms (82–108, n=20) | 0% | 68.7 MB | **pass**: Home return −2.5 ms, cold +2 ms, +0.1 MB; installed 09:16 |
| 2026-10-20 17:29 | current (`1bfd00c`) | short weekday, 2 rounds current → new, 5 cold starts a slot | 609 ms (592–639, n=10) | 100.0 ms (75–109, n=20) | 0% | 68.4 MB | started from the screensaver (idle); noise from the 05:56 test (1.5 ms) |
| 2026-10-20 17:29 | feed asked again while Home stays on screen (`9bb14ef`) | short weekday, 2 rounds current → new, 5 cold starts a slot | 613 ms (592–633, n=10) | 97.0 ms (81–108, n=20) | 0.07% | 68.3 MB | **pass**: Home return −3 ms, cold +4 ms, janky +0.07 points, −0.1 MB; installed 17:48 |
| 2026-10-20 19:22 | current (`9bb14ef`) | Horizon v3 at 960x540, CN, cut after 2 of 5 rounds, 5 cold starts a slot | 594.5 ms (n=10) | 99.5 ms (n=20) | 0% | 67.8 MB | noise from the 05:56 test (1.5 ms) |
| 2026-10-20 19:22 | Horizon v3, 960x540 (`a065555`) | same | 608 ms (n=10) | 94.0 ms (n=20) | 0% | 79.2 MB | **fail on memory only** (+11.4 MB, allowed 3); cut early to leave time for 480 |
| 2026-10-20 19:41 | current (`9bb14ef`) | Horizon v3 at 480x270, full: CN x 5 after a restart, 5 cold starts a slot | 603 ms (n=25) | 97.0 ms (n=50) | 0% | 68.4 MB | noise from the 05:56 test (1.5 ms) |
| 2026-10-20 19:41 | Horizon v3, 480x270 (`a28b832`) | same | 603 ms (n=25) | 96.5 ms (n=50) | 0% | 66.6 MB | **pass**: Home return −0.5 ms, cold 0 ms, −1.8 MB; installed 20:22 |
| 2026-10-20 21:33 | current (`a28b832`) | short weekday, 2 rounds current → new, 3 cold starts a slot | 607 ms (n=6) | 99.5 ms (n=20) | 0% | 69.5 MB | noise from the 05:56 test (1.5 ms) |
| 2026-10-20 21:33 | quick re-ask, live-match rate (`e1cc510`) | same | 612.5 ms (n=6) | 99.0 ms (88–415, n=20) | 0% | 66.3 MB | **pass**: Home return −0.5 ms, cold +5.5 ms, −3.2 MB; one 415 ms return straight after an install; installed 21:51 |
| 2026-10-21 02:33 | current (`e1cc510`) | short weekday, 2 rounds current → new, 5 cold starts a slot | 612 ms (n=10) | 104.0 ms (n=20) | 0% | 54.96 MB | memory from a separate run (02:58, 2 a build): the grid's meminfo by package name now prints the whole-system report on this TV, so it reads by pid (`tools/ab/grid.txt`) |
| 2026-10-21 02:33 | insights diary and daily summary (`1bb593d`) | same | 604.5 ms (n=10) | 96.0 ms (n=20) | 0% | 54.96 MB | **pass**: cold −7.5 ms, Home return −8 ms, janky 0, memory 0 MB; installed 03:03 |

## 2026-10-20

- Weekday test only, 3 full rounds of current → new → current after a restart (05:56–06:27), judged by
  `tools/ab/judge.py`: **pass** on every point. Saturday test skipped by Parth's choice: the weekend code didn't change
  and the TV's clock was not to be touched.
- New build's frame-time tail: p99 10 ms against 7 ms (p95 7 against 6), with fewer frames drawn per grid run (707 against
  813). Still well inside a 16.7 ms frame, and janky frames stayed 0%.
- Two earlier starts were discarded: one round with Home returns from YouTube (stopped when YouTube was found signed out;
  `backup/ab/weekday-youtube-round1-discarded.log`) and one slot with Home returns from Settings (all ten read the same
  stale 227 ms; see Method).
- 07:00 short weekday A/B of the weather highlights (`97d3e73`, 2 rounds of current → new after a restart, judged with
  tonight's noise): **fail** on cold start (+37.5 ms, two samples: 649 and 616 ms against 594 and 596). Everything else
  passed. Not installed; `4cec0f2` stays on the TV. The highlights are worked out after the first frame (the lines are
  built then), so the code isn't an obvious cause, but under the rule the result stands; a longer A/B (3 rounds of
  current → new → current) would be the next step if the change is wanted.
- 07:17 re-test of the weather highlights, measured properly (Parth): 3 rounds of current → new after a restart, **five cold
  starts per slot** (`AB_COLDS=5`), judged on the median of all 15 per build: **pass** on every point, cold start
  +18 ms against the allowed 20 (close to the limit). The 07:00 short test's fail (+37.5 ms) came from two samples a
  build; one slot alone tonight ranged 572–626 ms. Installed with `pm install -r` at 07:43; Home, the dock, Inputs,
  Settings, voice and casting checked.
- 07:58 short weekday A/B of "our side stands out" (`264f568`; the feed's new `ours`, house-of-1400 `638b1ff`): 2 rounds of
  current → new after a restart, five cold starts a slot: **pass** on every point. Installed with `pm install -r` at
  08:16; Home, the dock, Inputs, Settings, voice and casting checked.
- 08:21 short weekday A/B of Outfit's tabular digits (`7fe2268`: `tnum` on the clock, the headline scores and the
  THEN/LATER match text): **pass** on every point. Installed with `pm install -r` at 08:39; Home, the dock, Inputs,
  Settings, voice and casting checked.
- 08:57 short weekday A/B of the icon-only top bar (`1bfd00c`: Inputs and Settings icon only, the Wi-Fi pill's amber "!"
  and "No internet" from one network callback): **pass** on every point. The TV was on the screensaver (idle), not in
  use. Installed with `pm install -r` at 09:16; Home, the dock, Inputs, Settings, voice and casting checked, and on the
  TV: Right walks 1400 → Inputs → Wi-Fi → Settings and stays, Down goes to the dock; all four pills icon only, no "!".
- 17:29 short weekday A/B of the feed refresh (`9bb14ef`: while Home stays on screen, one more request a minute after a
  saved copy, and every 15 minutes otherwise, on the existing minute tick): **pass** on every point. Installed with
  `pm install -r` at 17:48; all checks passed; the TV's log shows each request (`HomeLines: feed asked: …`).
- 19:22 Horizon v3 (the light from the approved mockup; Parth's brief): at 960x540 the A/B failed on memory alone (+11.4 MB
  over 2 full rounds; Home return −5.5 ms, cold start +13.5 ms, janky 0%), so it was stopped there to leave time and the
  picture switched to 480x270 as planned. 19:41 the full A/B (LESSONS.md: 5 alternating rounds after a restart, medians;
  BENCHMARKS.md rule) passed on every point. Installed with `pm install -r` at 20:22; all checks passed. On the TV that
  night the readability floor took the sport and weather blocks from 4.46 to 4.50 and 4.51:1 (4 shadow passes).
- 21:33 short A/B of the quick re-ask (`e1cc510`: 8 s after an old or failed copy, twice at most; every 2 minutes during
  a live match): **pass**. Trimmed for time at Parth's request: 3 cold starts a slot (n=6 a build), no emulator run, a
  short check after the install (Home, Netflix, the request log). Installed with `pm install -r` at 21:51.
- 02:33 short A/B of the insights diary (`1bb593d`: a few KB a day of the launcher's own timings, and once a day a
  summary sent to the insights site, never while an app is open): **pass** on every point. The memory column failed in
  all four slots (see the row); measured again at 02:58 with the fixed command, two alternating installs a build:
  55.7 / 54.2 MB current, 53.8 / 56.1 MB new. Installed with `pm install -r` at 03:03; usage access granted.
- 22 Oct, no grain (`bf2b584`, then with the Xbox tile `90cd971`), three short A/Bs after a restart, 3 cold starts a
  slot: 03:47 (1 round: cold +37 ms, memory +8.2 MB; one sample each), 04:04 (stopped after 3 slots by a remote
  press; memory 68.3 / 84.3 / 67.1 MB), 04:24 (2 rounds, below). Speed: cold 608 → 628.5 ms (+20.5, limit 20), Home
  return 101 → 100 ms, janky 0 → 0. Memory: 68.4 / 65.3 MB current, 84.2 / 69.7 MB new (median +10 MB, limit 3); the
  new build read higher in all three runs, cause not found. **Over the limits; installed at 04:42 at Parth's call**
  (no grain was non-negotiable). Worth finding the memory cause before the next build.
- 22 Oct 04:46, the memory question settled: both builds read **at rest on Home** (20 s after Home, by pid, alternating
  installs): 1bb593d 55.2 / 68.5 MB, 90cd971 58.8 / 65.4 MB, medians 61.8 vs 62.1 MB (+0.3). The swing is the GPU's
  graphics memory (15-21 MB either build); the emulator (no GPU accounting) has no-grain 2 MB *lighter*. **A single
  memory reading on this TV swings about ±8 MB**: judge memory on several readings, and don't read it as a build's
  cost from one slot.
- 22 Oct 04:49 short A/B (2 rounds, 3 cold starts a slot), 90cd971 vs `d9c6c4b` (no grain, diary update, Xbox tile):
  **pass** on every point. Cold 627.5 → 626 ms, Home return about the same (101 → 98), janky 0 → 0, frame p99 9-10 vs
  10-11 ms, memory 70.4 / 68.3 vs 68.4 / 69.3 MB. Installed as `0573edd` (the same plus the tile's final drawing) at 05:06.
- 22 Oct 05:18, Horizon at 960x540 with grain (`0ad75e1`) vs 480x270 without grain or dither (`43c12f9`), memory at
  rest (20 s after Home, by pid, alternating): 52.6 / 52.4 vs 63.4 / 58.0 MB, medians 52.5 vs 60.7 (+8.2 MB: about 2 MB
  native for the bigger picture, 4-9 MB GPU). Over the limit; installed at Parth's call for the approved look.
- 22 Oct 05:32 short A/B (2 rounds, CN), `0ad75e1` vs `5596b2e` (no ordered dither, grain over the shadows): **pass** on
  every point. Cold +6.5 ms, Home return -6 ms, janky +0, frame p99 10.5 vs 10 ms, memory +2.1 MB. (judge.py says FAIL
  only for "noise known": a CN run has no noise slot.) `5596b2e` showed contour lines on the TV; its follow-up `28e427c`
  (cosine fades, each shadow drawn once) changes only the background drawing, off the main thread, and was installed at
  05:47 without its own A/B at Parth's call (he wanted the TV back). To be measured when the TV is next free.
- 22 Oct 06:05, on the emulator (tv1400qa, not the TV), the background drawing alone (HorizonBackground.draw, timed in
  throwaway builds): `0ad75e1` vs `28e427c`'s Horizon, storm sky, 3 alternating rounds of 10 draws (the first after each
  install discarded). Median **95.6 → 42.5 ms**; every round lower (109/94/89 vs 45/42/42). The shadows now reach the
  floor in fewer passes (the cosine fade is fuller) and nothing is dithered. It runs off the main thread, so cold start
  and Home return are not touched; with `38586f4` (layout only) Parth chose this check over another TV A/B.
- 23 Oct 07:04, Horizon v4 (`a480b49`, no stars or Milky Way) vs `38586f4` (v3 at 960x540, on the TV): full weekday
  test after a restart, **5 rounds of current → v4 → current**, 5 cold starts a slot. **Pass on every point.**
  Home return 99.5 vs 99.5 ms (n=100 / 50; noise C1 98.0 vs C2 100.0 = 2.0 ms); cold start 621.5 → 619 ms (n=50 / 25);
  janky 0% → 0%; frame p50/p90/p95/p99 5/7/8/10 ms both; memory 77.4 → 76.9 MB; no crash or freeze. Installed at
  08:01 with `pm install -r`.
