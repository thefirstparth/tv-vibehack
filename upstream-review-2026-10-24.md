# Upstream review: cleanyourtv since our fork (24 Oct 2026)

Upstream is **github.com/Gathin23/cleanyourtv**, the old tv-vibehack, renamed. We forked at **`19f3c224` (17 Oct)**.

**What has happened upstream since:**
- 2,078 commits and 7 releases (v2.1–v2.7).
- 1,212 files changed (+93,600 lines). By commits: debloat lists 869, launcher 494, docs 337, tools 174, add-ons 54.
- About 120 contributors. A bot now reviews every PR.

**What changed on our side in that time:**
- Our side has 161 commits.
- Our `tools/tv.py`, `b64push.py`, `disable.sh` and `restore.sh` are still exactly the fork-point versions.
- The launcher Java we still share with upstream is only `MainActivity`, `AppAdapter`, `GlassView` and `DockDecoration`. Upstream added about 50 launcher files we don't have.

**How this was checked:**
- I read the 7 sets of release notes and the commit logs.
- Three researchers each went through one area: launcher features, speed, and tools/safety/add-ons/debloat lists.
- I re-checked the key claims against our code myself.
- Nothing was changed on the TV. Nothing was changed in the repo except this file.

**Bottom line: don't merge upstream wholesale.** A test merge (`git merge-tree`) conflicts on the launcher, `CLAUDE.md`, `README.md`, `.gitignore`, the install script, and `examples/xiaomi-mitv-moor2/debloat.txt`. Upstream also has a MOOR2 folder at the same path, from another owner. A merge would also silently replace our four tools with upstream's versions, which changes how `restore.sh` behaves. **Pick individual changes by hand.**

---

## 1. Safety findings for our TV (no code; do these first)

1. **Our README's "back to the stock home" undo runs in a dangerous order.**
   - It enables `setupwraith` first and `launcherx` second (`examples/xiaomi-mitv-moor2/README.md:245-246`), and `tools/restore.sh` does the same.
   - In between, setupwraith is on and launcherx is off. On our own TV that state is the black RecoveryActivity screen.
   - On a sister model with the same firmware family (MOSR4), a reboot in that state **factory-reset the TV**. On an MZTU0 it **dropped Wi-Fi every minute**, which would cut ADB halfway through the undo.
   - Fix (docs only):
     1. enable launcherx;
     2. set it as Home;
     3. then enable setupwraith.
   - Add: "if `.RecoveryActivity` is in front, enable launcherx first and **don't reboot**".
   - Add: wait 15 s after the last `pm` command before any reboot.
2. **The remote's app buttons may be dead, and pressing them can disable other keys until a reboot.** *Unsure.*
   - On another MOOR2 with launcherx off:
     - the YouTube button (190) does nothing;
     - the Settings button (176) crashes a Xiaomi receiver.
   - After a few crashes, Android drops 176, 178 (Input) and 192 (Xiaomi TV) until the next reboot.
   - We never recorded which app buttons our remote has, or whether they work.
   - Check: ask Parth which buttons the remote has, or read `logcat | grep "process is bad"` from the TV.
   - Upstream's fix is an accessibility key service. It costs held-volume repeat (measured on the same firmware), so **don't copy it**. If it matters, use Key Mapper.
3. **Casting.**
   - On Chromecast hardware, casting needs setupwraith after a reboot, which we disabled.
   - Upstream's Xiaomi Mi Box S says casting still works there. Our A/Bs list "casting checked", but it's unclear whether that was a real phone cast after a reboot.
   - If "Untrusted device" ever appears when casting, this is why.
4. **A firmware update would quietly undo the debloat.** Upstream's `tools/check.py` (report-only) detects it: packages re-enabled, Home moved.
5. **Packages we disable that upstream warns about: none.**
   - All 22 of ours pass upstream's core-package refusal and keep-list checks.
   - Upstream's MOOR2 list disables `com.xiaomi.tvqs.overseas`. Other Xiaomi lists call it the **quick settings panel** and keep it on purpose, so our decision to keep it was right.
6. **ADB.** Upstream's `tv.py --probe` now prints `ro.adb.secure`. If it reads 0, anyone on the home Wi-Fi gets a shell on port 5555 without a prompt. Worth one read-only check.

## 2. Launcher: copy (A), already built ourselves (B), skip (C)

### A. Worth copying: bug fixes in code we still share (all verified in our code)
| # | Upstream | Problem in ours | Size, risk |
|---|---|---|---|
| A1 | `50a72f70` (EXTRA_REPLACING) | An app update fires REMOVED + ADDED + REPLACED (`MainActivity.java:212-215`). That is 3 tile rebuilds on the main thread while Home is open, and the updated app's tile flickers out. | Small, low risk |
| A2 | `673c853c` (#47) | The highlight chases a target that keeps moving while the grid scrolls (`MainActivity` onScrolled, `springY.animateToFinalPosition`). Up/Down overshoot more than Left/Right. | Small; changes the feel, so Parth should look at it |
| A3 | `0f73adcf` (#544) | `onResume` makes the Wi-Fi binder calls and `getInputState` before the first frame on every return. Both are already kept current by callbacks. | Small; Home return about −5 to −15 ms |
| A4 | `ee5dc4bb` (#583) | Every app's icon is decoded even when it has a banner (`AppAdapter.java:110`). | One line; cold start −10 to −40 ms, −1 to −4 MB |
| A5 | `ea658e1d` (#536, card part) | The dark tile fill is painted under fully opaque banners. Overdraw on a GPU that is limited by how many pixels it paints. | Small; GPU headroom |
| A6 | `22fd5459` (#539, part) | After the time or time zone changes, the greeting and Horizon wait up to 60 s. The clocks can show a wrong time before network time arrives. | Small |
| A7 | `cf426d88` (#224) | After a Settings or Wi-Fi side panel closes, focus jumps off the pill. Very likely on Google TV; not checked on the TV. | Small; changes focus behaviour |
| A8 | `890899bd` (DockDecoration part) | `findViewById` runs on every frame for the dock tiles. | Trivial |

Only if the problem shows up on this TV:
- `9ae586da`: a square banner shows as a blurry tile.
- `94a24df0`: an app with two identical Leanback entries gets two tiles.
- `0313bf22`: click sounds on the D-pad. We play none on any move.
- `a11334c1`/`c7ea3b68`: the Live TV row opens the tuner. Only if anyone watches antenna or cable.

### B. Could have copied, but we built our own
| Upstream | Ours |
|---|---|
| #666 instant return to Home, #552 no replayed entrance | Our package receiver and reload only when something changed (Home return 792 → 403 ms, 18 Oct) |
| #231/#409/#524 time-of-day sky, horizon glow | Horizon v3/v4 |
| #580 real sunrise and sunset | `SunTimes` / `SunLine` |
| #405 CEC device on its port's row, #291 input names | `deviceOnPort`, `input_names`, the Xbox `InputTile` |
| #122/#556 Ethernet, green Wi-Fi, compact pills | `WifiPill` / `WifiBars` |
| #138 Down returns to the dock tile, #118 Home on Home | `lastDockFocused`, `onNewIntent` |
| #119 GPU highlight, #247 corner radius | `GlassView` hardware layer |
| #225/#370 fonts built once | `Fonts.java` |
| #40 release build | R8 release build, recorded in BENCHMARKS |
| #172 `greeting_name` | Personal days |
| #385 F1 race weekend | The house-of-1400 feed's F1 lines |
| Cinematic shelf "last opened" | Diary / insights |
| Install-script Home takeover | Done by hand, every step and its undo in the MOOR2 README |

### C. Skip
- **Themes, module cards and add-on apps:** a runtime config surface and a security surface we don't need.
- **On-TV launcher settings, reorder, app library, edit dock:** Parth edits `config.xml`.
- **Accessibility key services (`app_keys`, YouTube, Input and Settings keys, gamepad passthrough):** a background service filtering every key press, and it breaks held-volume repeat on this firmware. Revisit only if a dead button matters.
- **Quick settings panel / Settings key:** our remote has no Settings key.
- **Wallpapers, sky, dock glass, app glow, glow rings, cinematic shelf, fade rows:** these conflict with Horizon, or were removed on purpose.
- **Stats line, plain grid, more than 4 columns, names under every tile, reduced motion, focus overshoot:** a different design.
- **VPN badge, Hijri and Hebrew calendars, festive skin, every translation and right-to-left layout:** not used in this house.
- **`stock_home_redirect`, boot to Live TV, default-home tile, hiding pills, VIEW_INPUTS:** not needed here.
- **China Xiaomi, TCL, Coocaa, YaOS and Fire OS source handling; targetSdk 34; CI and lint gates:** other firmware, or not our constraint.
- **`#585` dropping tiles on low memory:** it would make the next Home return a full rebuild, which breaks the speed rule.

## 3. Speed

**Upstream's speed work, checked against ours:**
- **Already have:**
  - #40 shrunk release build
  - #119 GPU highlight
  - #225/#370 fonts built once
  - #552 no replayed entrance
  - #584 one Leanback query
  - #666 instant return
- **Missing but applicable:**
  - #544 pills after the first frame (A3)
  - #583 no icon decode when a tile has a banner (A4)
  - #536 no card fill under opaque banners (A5)
  - #667 Horizon picture as a GPU-only bitmap, with the previous one freed: about −2 to −4 MB
  - #370 dock lookup (A8)
- **Not applicable:** wallpaper decoding, the screensaver glow, the app library.
- **Not wanted:** #585 tiles dropped on low memory (slower return); #669 no per-minute network refresh (our Wi-Fi bars need it).

**Further opportunities in our own code (ranked):**
1. Move all of onResume's non-essential work after the first frame:
   - the Wi-Fi and input calls;
   - `homeLines.tick()`, which re-renders every line;
   - the second Horizon check.
2. Stop re-laying out when nothing changed:
   - the Wi-Fi and name pills call `setCompoundDrawablePadding` every time;
   - the lines re-render and re-measure every minute.
3. Hand the Horizon picture over as a GPU-only bitmap and free the old one (memory).
4. Skip the icon decode when there's a banner (A4).
5. Load tiles off the cold-start path (dock first, grid after the first frame): cold start perhaps −30 to −100 ms. Moderate risk.
6. The compile state of the launcher (`speed-profile` with our own baseline profile). Mostly makes measurements match daily use.

Not code: our A/B cold-start command (`tools/ab/cold.txt`) starts the launcher without the Home category. Upstream (#888) found that this creates an extra Home task. It doesn't favour either build, but it makes the numbers less clean. Fix: add `-a MAIN -c HOME` to the command.

**Verdict.** The big wins are already in: cold start went 1479 → 620 ms, Home return 792 → 100 ms, and jank is 0%. What's left:
- **Home return:** perhaps −10 to −20 ms. That's near the test's noise and not something you'd notice.
- **Cold start:** perhaps −50 to −100 ms. Cold starts only happen after a reboot.
- **Memory:** −3 to −8 MB.

All of it is modest. The sensible course is one bundled low-risk build (A1, A3, A4, A5, A8, plus the Horizon GPU bitmap), checked on the emulator, then one full A/B whenever Parth frees the TV.

## 4. Tools, add-ons, debloat lists

**Copy:**
- `tools/tv.py`:
  - `9316dcbb`: `--pull` timeout 300 s (our APK backups pull over Wi-Fi).
  - `dd2a1026`: `--reboot` through ADB's own reboot service.
  - `35530319`/`4b026f84`: `--screenshot`.
  - `be264d20`: `--probe` shows `ro.adb.secure`.
  - A mistyped flag is no longer sent to the TV as a shell command.
- `tools/b64push.py` `b82492df`: stages to `.part`, checks md5, then moves the file into place.
- `tools/check.py` in report-only mode, with the `recovery.py` it needs: detects firmware-update drift. Untested with our tv.py.
- Upstream `disable.sh` guards: Home guard, Wi-Fi guard, core-package refusal. Only if a list is ever applied again.

**We built our own:**
- `tools/bench.py`, `trace.py`, `screendiff.py` → our `tools/ab/*`. trace.py would still help to see where the 620 ms goes.
- The weather and capacity add-ons → our house-of-1400 feed and the insights site.
- `theme.sh` → `config.xml`. **Don't run theme.sh on our TV:** it force-stops our launcher and rewrites accessibility settings.

**Skip:**
- `restore.sh` record-and-replay: it needs a record taken before the debloat.
- The Windows `.ps1` scripts, `relay.py`/Tailscale, `adb-closed.py`. ADB stays open on purpose.
- All 11 add-ons:
  - Every one plugs in through module cards, which we don't have.
  - Brightness and TV Static run always-on services.
  - The rest duplicate our feed or don't apply.
  - Sleep Timer and Headphones only if the family asks.
- Themes.
- Other TVs' debloat lists and device notes. The MOSR4 notes were read for safety (section 1).

Upstream's own MOOR2 list (another owner, same firmware):
- It disables `tvqs.overseas` (keep ours: it's the quick settings panel) and `com.mitv.toolhouse` (optional; purpose still unknown).
- It adds 5 optional leftovers (skip: none runs at idle).
- We disable 3 that it doesn't (YouTube Music, feedback, launcherx). None is warned about anywhere upstream.
- SHOWCASE.md lists another fork for this same model: CleanXiaomiTV.

## 5. Not verified
- Which buttons the remote has, and whether they work.
- Whether casting from a phone works after a reboot.
- `ro.adb.secure` on the TV.
- Display density (decides whether `ab75d36f` matters).
- Whether the TV has a tuner.
- Whether Settings opens as a side panel.
- Whether upstream `check.py` runs with our tv.py.
- Every speed figure above is an estimate until it's A/B'd on the TV.
