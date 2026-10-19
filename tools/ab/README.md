# The launcher A/B on the TV

The scripts behind [BENCHMARKS.md](../../examples/xiaomi-mitv-moor2/BENCHMARKS.md): they compare the launcher build on
the TV ("current") with a new one, alternating, on a freshly restarted TV, and judge the result against the qualifying
rule written there. They use `tools/tv.py` (set `TV_HOST`; `tools/venv` must exist), never uninstall the launcher, and
refuse to run unless the device's model is `MiTV-MOOR2` (set `AB_MODEL` to test on an emulator).

Results go to `backup/ab/` (gitignored; set `AB_OUT` to change it). Run from anywhere; each script works from the repo
root. They take a while: a restart plus rest is ~3 min, each slot ~3 min.

```bash
export TV_HOST=192.168.0.15
CUR=backup/launcher-builds/<the APK pulled from the TV>.apk
NEW=backup/launcher-builds/<the candidate>.apk
tools/ab/night.sh weekday 35 "$CUR" "$NEW" CNC 3 </dev/null    # 3 rounds of current → new → current, 35 min cap
python3 -I tools/ab/judge.py backup/ab/weekday.log             # prints the noise, the numbers and PASS/FAIL
tools/ab/saturday.sh 30 "$CUR" "$NEW" 3 </dev/null             # the clock set to the coming Saturday 13:00, CN rounds
python3 -I tools/ab/judge.py backup/ab/saturday.log --noise <the weekday noise>
```

| File | What it does |
|---|---|
| `night.sh <test> <minutes> <current> <new> <CNC\|CN> <rounds>` | Restart, a minute's rest, the remote-press watcher, then the slots; stops before a slot that would end past the time limit. Ends with the current build back on, Home pressed and the watcher stopped. |
| `saturday.sh <minutes> <current> <new> <rounds> [YYYY-MM-DDTHH:MM]` | Records `auto_time` and `auto_time_zone`, turns automatic time off, sets a Saturday, runs `night.sh saturday … CN`, then sets the clock from this computer and puts both settings back as recorded, even after a stop. |
| `slot.sh <apk> <label>` | One slot: install, warm-up grid run (discarded), 1 cold start, 1 grid run (janky %, frame-time percentiles, memory), 10 Home returns from YouTube, crash/freeze check. One line of results. |
| `judge.py <log> [--noise MS]` | Side-by-side medians and the rule. Only rounds with both builds count. Also reads the 2026-10-19 logs (it reproduces that night's published numbers). |
| `put.sh <apk>` | `pm install -r` of one APK; fails unless the TV says Success. |
| `grid.txt`, `cold.txt` | The scripted D-pad run across the dock and grid, and the cold start, as `tv.py --batch` input. |
| `common.sh`, `tvrun.py` | Shared helpers; `tvrun.py` kills a `tv.py` call when the TV stops answering (macOS has no `timeout`). |

**Someone presses the remote:** real presses show up in `getevent` (scripted `input keyevent` presses don't). The
watcher is checked before and after each slot; on a press the test stops, the current build goes back on and Home is
pressed (`saturday.sh` then restores the time). Exit codes: 3 remote pressed, 4 install failed, 5 wrong device or
bad time, 6 the TV didn't come back after the restart.
