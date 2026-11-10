# Upstream review: cleanyourtv since 24 Oct (10 Nov 2026)

This follows [upstream-review-2026-10-24.md](upstream-review-2026-10-24.md) and covers only what is new since then, plus the three parts of the website's add-ons page.

**How this was checked:**
- I cloned upstream fresh (head `3b6b3a15`, 9 Nov) and read it only. I ran none of its scripts and built nothing.
- I read the release notes for v3.0 to v3.4 and the commit log since 24 Oct. I read every change to the Xiaomi `venom` folders (MOOR2, MOOR0, MOOR6), MOEU0 and MOSR4. I also read the new safety notes in `docs/TROUBLESHOOTING.md`, `PROMPT.md` and `docs/firmware-updates.md`.
- The website page draws itself in the browser. I took its data from the JSON embedded in the page instead: 120 settings, 35 add-ons plus 21 community add-ons (the "56"), and 28 themes.
- I checked the claims against our code, our MOOR2 README and our 18 Oct snapshot (`backup/moor2-20261018-054750/`).
- Nothing on the TV was touched. No ADB was used. Nothing in the repo changed except this file.

**Bottom line:**
- Upstream added nothing that would make our TV faster.
- Two things matter:
  1. **A second Xiaomi was factory-reset** by the setupwraith-on, launcherx-off state. This time it happened while someone was trying to fix casting.
  2. Our 18 Oct snapshot shows **two Xiaomi data-sharing switches still on**.
- The rest is either for upstream's own launcher (none of its 120 settings exist in ours) or adds services this TV doesn't need.

---

## 1. What changed upstream since 24 Oct

**Size of the change:**
- 2,206 commits, not counting merges (3,764 with them).
- Five releases: v3.0 (25 Oct), v3.1 (27 Oct), v3.2 (29 Oct), v3.3 (1 Nov) and v3.4 (5 Nov). Four more days of work are not released yet.
- Commits by area: lists of TVs 963, docs 486, tests 466, tools 286, launcher 187, add-ons 143, themes 95.

| Area | What changed | Matters to us? |
|---|---|---|
| Launcher (3.0 to 3.4) | Many new opt-in settings (section 3). Holding OK on a tile opens a Move / Remove / App info menu, on by default since 3.3. Clocks keep moving after standby on TCL. Quick Settings opens Xiaomi's own picture page (`com.android.tv.settings/com.xiaomi.mitv.settings.picture.PictureActivity`). VPN pill. Inputs list hides empty ports. | Barely. Our launcher reads 9 settings of its own. The only new fix to code we still share is `c5e8a5c4` (section 6, item 5). Upstream's `refitGlass` and `glow_snap` fixes are for code we don't have. |
| Speed | `glow_snap`: on a MOEU0's Mali-G52, fading the full-screen glow made 60% of Home's frames late, against 27% without the fade. A glass-highlight position fix after a cold start. Nothing else. | No. We have no full-screen glow fade. The lesson agrees with ours: repainting the whole screen is what costs on this class of GPU. |
| `tools/tv.py`, `b64push.py` | +660 and +122 lines, mostly clearer errors. Useful bits: `--pull` leaves the local file as it was on failure (`54db366e`); b64push accepts the TV's success marker only as a line of its own (`0dac0258`); a new ADB key is written at 0600 (`f8063025`). Ours are still exactly the 24 Oct versions. | Low. Our private key is already 0600. Don't replace the files wholesale. |
| New tools | `manage.py` (add-ons, themes, settings), `match.py` (works offline: how many lists disable each of your packages), `theme_restart.py`, `philips-api.py`, Docker builds. | No. `match.py` is harmless to read, but our debloat is done. |
| Lists of TVs | 300 → 442 models. New MOOR0 folder (same `venom` board). MOOR2 list: a 4th unit (older firmware `.1917`) and a 5th (MOOR6, same `.2674` as ours). | Read for safety (section 2). None of the new warnings is about a package we disable. |
| Add-ons | 20 → 33 in the repo, 35 on the site, plus 21 community add-ons. Security: `manage.py` only accepts an add-on id shaped like a real package name. | Section 4. |
| Themes | 28 on the site (Diwali until 11 Nov, Christmas, aerial…). | No (section 5). |
| Safety docs | The setupwraith/launcherx warning now covers casting (#3578). New notes: the cached-app freezer and casting; an unused Ethernet port set to static keeps `system_server` busy; local-VPN apps that are gone after a boot on small TVs; wait 15 s before rebooting. | Yes, section 2. |

## 2. Safety findings for our TV

1. **Never turn setupwraith back on while launcherx is off, not even for a minute, and not even to fix casting.** *High confidence.*
   - New since 24 Oct: on a MiTV-MOEU0 (#3578, `94b76faa`, `d5f123a8`), someone re-enabled setupwraith with launcherx still disabled, to get casting back.
   - Home went to `.RecoveryActivity`. After one reboot the TV never got past the boot animation. "Cannot load Android system" followed, and only a factory reset brought it back.
   - That makes two Xiaomi factory resets from this state (MOSR4 and MOEU0).
   - Our README's undo order (launcherx first, setupwraith last) is already right. Add the casting case to it in so many words.
2. **Don't install "Cast Fix"** (community add-on, `thayford/castfix`). *High confidence.*
   - At every boot it turns setupwraith on, lets the Cast receiver load its keys, then turns setupwraith off again.
   - On this firmware family, that is exactly the state in item 1, repeated at every boot.
3. **Don't follow upstream's "re-enable setupwraith before a factory reset".** *Medium confidence.*
   - The upstream MOOR2 list says to do that.
   - Done on its own it gives the dangerous state.
   - Our README's way (remove the Google account, then Factory reset from the Settings menu) never passes through it.
   - If setupwraith ever has to go back on, turn launcherx on and make it Home first.
4. **If the TV is ever reset and the Home switch redone, check who owns the saved Wi-Fi first.** *High confidence.*
   - On three of the new units (the 4th MOOR2, the MOOR6 and the 4th MOEU0), the saved network belonged to setupwraith (the first-run wizard).
   - Disabling setupwraith then drops that network, and ADB with it.
   - Check: `dumpsys wifi | grep -o 'cname=[^ ]*'`. If it names setupwraith, forget the network and join it again before disabling setupwraith.
   - Our README's Home steps (item 2) don't mention this.
5. **Casting and the cached-app freezer.** *Medium confidence that it doesn't affect us.*
   - With launcherx off, Android 14 sets froze the Cast receiver (`com.google.android.apps.mediashell`) a few minutes after each wake, and phones lost the TV.
   - The fix was `settings put global cached_apps_freezer disabled` and a reboot.
   - On Android 11 the freezer is off by default, as far as I know from AOSP. Our snapshot has no `cached_apps_freezer` key, so it's on `device_default`.
   - So this probably doesn't apply here. It is still the first thing to check if casting ever fails (section 6, item 4). The open item from the last review, a real phone cast after a reboot, is still open.
6. **Factory-test Home app.** *Low risk.*
   - On the MOOR0 (same board), `com.duokan.factorytest/.FactoryLauncher` claims Home at priority 0.
   - It isn't in our 18 Oct package list, so probably not installed for our user. One read-only check confirms it (section 6, item 2).
7. **No new warnings about any of our 22 disabled packages.** I checked every name against everything upstream added to its docs, tools and prompt.
8. **Two items from the last review's "not verified" list are now answered by our 18 Oct snapshot:**
   - `ro.adb.secure` = `1`, so a new computer must be accepted on the TV.
   - The screen density is 320.

## 3. Settings page (`cleanyour.tv/addons#settings`, 120 items)

**What these are:** they are **not Android settings**. They are switches in upstream's launcher (`config.json` or `config.xml`, pushed with `theme.sh` or `manage.py`).
- Our launcher (`com.gathin.tvhome`, now our own code) reads none of them.
- Its only settings are `dock_apps`, `dock_input`, `dock_input_name`, `grid_order`, `hidden_apps`, `live_tv_package`, `accent_colours`, `input_names` and `columns`.

So "we have it?" below means "our launcher already does this its own way". Every "yes" would mean writing it ourselves, then a full A/B.

| Group (every key listed) | What it does | We have it? | Recommend? | Why |
|---|---|---|---|---|
| Dock and grid: `dock_apps`, `fill_dock`, `hidden_apps`, `columns`, `rows_below_dock`, `dock_title`, `dock_row`, `single_row`, `dock_band_all`, `dock_band`, `dock_glass`, `dock_even_padding`, `dock_opacity`, `always_show_names`, `uniform_tiles`, `icon_tiles`, `app_labels`, `app_banners`, `launch_actions`, `top_bar_apps`, `reorder_button` | Which apps go where, tile style, dock look | Yes: `dock_apps`, `grid_order`, `hidden_apps`, `columns`, the Xbox `InputTile` (our `launch_actions … input:`) | No | Our layout is fixed on purpose; Parth edits `config.xml` |
| `tile_menu` (on by default upstream since 3.3) | Hold OK or Menu on a tile: Move / Remove / App info | No | No | It adds a mode the family could get stuck in, and nobody here reorders from the remote |
| Inputs: `live_tv_package`, `live_tv_action`, `live_tv_input_id`, `source_panel_service`, `source_panel_action`, `source_panel_extra`, `coocaa_sources`, `input_action`, `input_uri_scheme`, `input_sources`, `handle_view_inputs`, `show_inputs_pill`, `icon_only_inputs` | How the Inputs menu opens ports on each brand | Yes: `live_tv_package`, `input_names`, `deviceOnPort`, icon-only Inputs | No | Ours already works on this TV |
| `inputs_connected_only`, `inputs_cec_state_only` | Hide empty HDMI ports; show state only for CEC devices | No | **No** | On this TV, HDMI 2's state only updates while HDMI 2 is on screen, and the Xbox has CEC off (README item 25). Either switch would hide or blank the Xbox. |
| Top bar: `show_network_pill`, `icon_only_buttons`, `top_menu`, `launcher_settings_button`, `wifi_status_color`, `wifi_status_warnings`, `wifi_label_width`, `library_button`, `show_library_pill` | Pills and their look | Yes: icon-only top bar, the amber "!" for no internet (`1bfd00c`) | No | Already done our way |
| `vpn_app`, `vpn_pill`, `icon_only_vpn` | Shows when a VPN is up (green) or off (red) | No | Maybe, as an idea only | ProtonVPN being left on was behind the 30 Oct lag. A small "VPN" mark would make that visible. It would hang off the network callback we already have. Ask Parth first; it needs an A/B. |
| App library: `app_library`, `app_library_selection`, `library_ok_opens`, `library_apps`, `library_excluded_apps` | A page of every app, with add/remove | No | No | More UI, nothing the family asked for |
| Header and dates: `shamsi_date`, `lunar_date`, `lunar_utc_offset`, `date_below_clock`, `locale`, `greeting_name`, `show_greeting`, `header_white`, `clock_format` | Calendars, greeting, clock format | Greeting and personal days, yes | No | Not used in this house |
| `show_stats` | CPU, free memory and storage under the clock | No | No | The insights site covers it; on screen it would be clutter |
| `race_calendar`, `game_day` | F1 weekend panel; next game for a team | Yes: the house-of-1400 feed's sport and F1 lines | No | The feed does more |
| Festive skins: `festival`, `seasonal_skin`, `festival_until`, `greeting`, `accent_color` | Diwali, Halloween… skins | Personal days | No | Personal days already do this |
| Look: `background_color`, `sky_intensity`, `wallpaper`, `wallpaper_rotation_minutes`, `wallpaper_order`, `wallpaper_dim`, `wallpaper_captions`, `wallpaper_moods`, `glow_style`, `glass_style`, `ambient_glow`, `app_glow`, `cinematic_home`, `cinematic_dots`, `cinematic_white_focus`, `cinematic_scrim`, `cinematic_art` | Wallpaper, sky, glow, cinematic shelf | Horizon v5 replaces all of it | No | It conflicts with Horizon |
| `wallpaper_true_color`, `fade_rows` | 32-bit wallpaper (8 MB instead of 4 MB at 1080p); rows fade under the clock (about +6 MB in a hardware layer) | No | No | Both cost memory on a 2 GB TV |
| `glow_snap` | The glow changes colour in one frame instead of fading | Not needed (no glow fade) | No | Nothing to change; it confirms our design choice |
| Motion: `animations`, `advanced_effects`, `reduced_motion`, `focus_overshoot` | How much motion | Our spring highlight | No | The A2 idea in the last review already covers highlight feel |
| Boot and power: `boot_to_live_tv`, `open_on_start_choice`, `open_on_start_network_wait`, `open_on_start_on_wake`, `power_on_home`, `power_on_home_always`, `show_home_settings_shortcut` | What opens at power-on | No | No | `power_on_home*` need the key helper (held-volume cost; Parth's no-remap rule). The family resumes where it left off. |
| Idle clock: `home_idle_clock_minutes`, `back_opens_clock`, `clock_wallpaper`, `clock_wallpaper_dim`, `clock_glow` | A clock screensaver drawn by the launcher | No | No | The family uses Ambient Mode |
| Remote keys: `settings_key_scancodes`, `settings_key_clear_task`, `settings_key_keycodes`, `settings_key_panel`, `settings_fallback_components`, `restore_key_services`, `input_key_keycodes`, `app_keys`, `key_filter`, `gamepad_passthrough`, `home_ignore_channel_keys`, `consume_volume_keys`, `stock_home_redirect`, `home_long_press_home` | Accessibility key helpers that remap or repair buttons | No (reverted 30 Oct) | **No** | Parth: no remapped buttons. On this platform any key filter stops held volume repeating (MOOR0, MOOR6, our emulator). `restore_key_services` also needs `WRITE_SECURE_SETTINGS`. |
| `module_cards` | Add-on cards on Home | No | No | A runtime plug-in surface we don't want |

## 4. Add-ons page (`cleanyour.tv/addons#addons`, 35 + 21 community)

**How they work:** each is its own small app that adds a card to upstream's launcher. They have no card in ours, so each would only work as a stand-alone app or screensaver.
- There are no binaries: each would have to be built from upstream's code and installed.
- That is an install on the family TV, and every one with a background service costs memory on 2 GB.

| Add-on | What it does | We have it? | Recommend? | Why |
|---|---|---|---|---|
| Tatami, Koi Pond, Digital Rain, Neon Dream, Haunted, Flock | Screensavers or toys drawn live on the TV, no network | No | No | The family uses Ambient Mode. Haunted was never tested on a real TV. |
| Photo Prints, Photo screensaver | Your own photos as the screensaver | No (Backdrop) | No | Only if the family wants own photos instead of Ambient |
| News screensaver | RSS headlines as the screensaver | 1400 app | No | 1400 does this, from the feed |
| Weather | One city from Open-Meteo | Feed (3 cities) | No | Duplicate |
| Game Day | Next game per team from calendar links | Feed | No | Duplicate |
| Capacity Stats | RAM and storage card | Insights | No | Duplicate |
| Scoreboard | App-use leaderboard (`PACKAGE_USAGE_STATS`) | Insights diary | No | Duplicate |
| Sources | Inputs named by CEC | `input_names`, Xbox tile | No | Duplicate; the Xbox has CEC off anyway |
| TV Inspector | Shows what each remote button sends; decoders and HDR | No | No (use `getevent -lt` instead) | A plain activity with no permissions or services (checked its manifest). But `tools/tv.py 'getevent -lt'` while Parth presses buttons answers the same question with nothing installed. Keys the system takes first (Assistant, Netflix) never reach either. |
| Sleep Timer | 15–120 min countdown, then standby | Not checked whether the TV's own Settings has one | Only if the family asks | Its accessibility service doesn't filter keys (checked: no key-event flag), so held volume is safe. But it turns `accessibility_enabled` on again and keeps a bound service running. |
| Quiet Hours | Caps the volume at night | No | No | An always-on foreground service. The soundbar has its own remote. |
| Brightness | Dims below the TV's minimum over every app | No | No | An overlay service running all the time; the picture is tuned |
| TV Static | Snow or colour bars at each screen-on | No | No | An overlay plus a boot service; a gimmick |
| Open on wake | Opens a chosen app at screen-on | No | No | A service, and it opens over the Xbox input |
| Headphones, Bluetooth Connect, Bluetooth Toggle | Bluetooth audio helpers | No | No | Nobody here has reported a Bluetooth problem. A Bluetooth fault is behind the remote not waking the TV; leave Bluetooth alone. |
| Remote Access | Shows ADB state, with an off button | No | No | ADB stays open on purpose |
| AirPlay Status | How Apple devices see the TV | No | No | Not a receiver; nothing to see without one |
| Smart Home, Ad Blocking, Frigate Glance | Home Assistant, Pi-hole/AdGuard Home, camera pop-ups | No | No | No such servers here. Frigate needs overlay, accessibility and a boot service. |
| Startup & Branding, World Clock, TV Hub, Prayer Times, Jewish Calendar, Nepali Calendar, Lịch Vạn Niên | Welcome screen, clocks, IPTV, religious calendars | No | No | Not used in this house (Prayer Times was tested on a MOOR2, for what it's worth) |
| **Community:** Cast Fix | Turns setupwraith on and off at each boot for casting | No | **Never** | Section 2, item 2: risk of a factory reset |
| Community: Webmote, QuickVol TV, Kids Mode | Phone remote; colour buttons as volume presets; PIN kids' home | No | No | All accessibility services. QuickVol filters keys (held-volume cost, a remap). Kids Mode takes Home. |
| Community: Weather Clock, DVD screensaver, 3D Pipes, Departures, Picture Window, AuroraTv-LiveFlow | Screensavers and backgrounds | No | No | Ambient Mode stays. Weather Clock and Departures go online. |
| Community: Sony input shortcuts | A tiny app per HDMI input, giving it a named tile | Xbox tile | No | Already done |
| Community: Corner Player, Homelab companion, Forslag, Wolf Games, Art Frame, TV schedule + Shizuku, AirPlay receiver installer, coocaa-home-shim, Aerial Xiaomi Home Bridge, NoGmsVoice | Camera PiP, homelab, catalogue browsing, game streaming, other firmware | No | No | Other hardware, other servers or other firmware. The AirPlay receiver would be a resident service, and the family has no stated need for it. |

## 5. Themes (`#themes`, 28)

- Themes are `config.json` plus wallpapers for upstream's launcher. Ours reads none of them.
- **Still don't run `tools/theme.sh` on our TV.** It force-stops the launcher. Upstream added a step that restarts Home after a push.
- Nothing to take. Two notes:
  - Diwali runs until 11 Nov.
  - The `aerial` theme uses ISS photos, which might suit a future Horizon look as an idea.

## 6. Top recommendations, ranked

**1. Docs only: add the new safety lines to the MOOR2 README.** *High confidence; no TV needed.*
- Under "Undo":
  > Never enable setupwraith while launcherx is disabled, for casting or anything else, even briefly: on a MiTV-MOEU0 this ended in a factory reset (upstream #3578), as on the MOSR4. Don't install Cast Fix or anything that switches setupwraith at boot.
- Under "Selling or giving away": "don't re-enable setupwraith first; if you must, launcherx first".
- Under "Current state", item 2 (redoing the Home switch):
  > before `pm disable-user … setupwraith`, run `dumpsys wifi | grep -o 'cname=[^ ]*'`; if a saved network belongs to setupwraith, forget it and join again first.
- Undo: revert the doc commit.

**2. One read-only check session, when Home is on screen and nobody is watching.** *High confidence it's safe: reads only.*
```
dumpsys activity activities | grep mResumedActivity          # must be Home first
settings get global user_experience_enabled                  # 18 Oct: 1
settings get secure upload_log_pref                          # 18 Oct: 1
settings get global cached_apps_freezer                      # expect null (device_default)
dumpsys activity settings | grep -i freez                    # expect use_freezer=false on Android 11
dumpsys ethernet | grep -i -E 'IP assignment|Static|state'   # only matters if eth0 exists
logcat -d | grep -c 'IpClient.eth0'                          # expect 0
cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME
pm list packages -u | grep -i duokan                         # expect nothing
```
- If `dumpsys ethernet` shows `IP assignment: STATIC` with an empty static configuration, and the `IpClient.eth0` count is climbing, that is upstream's "unused Ethernet port keeps system_server busy" bug.
  - The fix is in the TV's menu: Settings → Network & Internet → Ethernet → IP settings → DHCP.
  - Upstream measured idle CPU going from 189% to about 360% of 400% on a Vu.
  - Unlikely here, but it's the only new item that could be a real speed win.
- Also, at some point: a real phone cast after a reboot, plus `dumpsys activity processes | grep -A3 mediashell` (look for `isFrozen`). This closes the casting item from the last review.

**3. Turn off Xiaomi's two data-sharing switches.** *Medium confidence it's harmless; low that it changes anything you'd notice.*
- Our 18 Oct snapshot has both on. Upstream turned both off on a MOEU0 (Google TV 14, first day only); it wasn't tested on a MOOR2.
- The two apps most likely to read them (`com.miui.tv.analytics`, `com.xiaomi.statistic`) are already disabled here. So this is extra caution for privacy, not a speed change.
- Record the current values first (item 2), then, one command per call:
```
settings put global user_experience_enabled 0
settings put secure upload_log_pref 0
```
- Undo (put back the values recorded in item 2; 1 and 1 on 18 Oct):
```
settings put global user_experience_enabled 1
settings put secure upload_log_pref 1
```
- Settings written from the computer may not survive a restart on this firmware (that happened with the picture settings). Re-read them after the next reboot.
- Add both to the MOOR2 README with the undo (rule 5).

**4. Only if casting ever fails:** check the freezer before blaming setupwraith. *Medium confidence.*
- If `dumpsys activity processes | grep -A3 mediashell` shows `isFrozen=true` and logcat shows `freezing <pid> com.google.android.apps.mediashell`:
```
settings put global cached_apps_freezer disabled     # then reboot
```
- Undo: `settings put global cached_apps_freezer device_default`, then reboot.
- This is the Developer options switch "Suspend execution for cached apps". It covers every cached app, so don't set it ahead of time.
- **Never** fix casting by turning setupwraith on (section 2, item 1).

**5. Launcher: one line to carry with A4, if the bundled low-risk build from the last review is ever made.** *High confidence it's correct; tiny effect.*
- Upstream `c5e8a5c4` sets `h.icon.setImageDrawable(null)` before binding a banner, so a recycled tile doesn't keep a hidden icon.
- Our `AppAdapter.java:152-156` has the same pattern.
- It only frees memory once A4 (`ee5dc4bb`, no icon decode when there's a banner) is also in, because today every entry holds its icon anyway.
- Same A/B as the rest of that build.

**Not recommended, for completeness:**
- Private DNS (`dns.adguard-dns.com`), in upstream's README and the MOOR2 list.
  - Android TV has no Settings page to switch it off. If the resolver goes down, the TV shows "No internet" and only ADB fixes it.
  - It doesn't block YouTube ads, and it could break an ad-funded stream (JioHotstar, FanCode).
  - Too much risk for a family TV, for little gain.

## 7. What I could not verify

- Nothing was read from the TV. Every value from the TV is from the 18 Oct snapshot or the MOOR2 README. In particular:
  - whether the data-sharing switches are still 1;
  - whether the TV has an Ethernet port at all;
  - the freezer's live state.
- That Android 11 has the freezer off by default is from my knowledge of AOSP, not checked on this firmware.
- Whether casting works after a reboot (carried over from the last review).
- Whether the TV's own Settings offers a sleep timer.
- What `user_experience_enabled` and `upload_log_pref` control on this firmware, beyond their names and upstream's one MOEU0 note.
- The website data is as served on 10 Nov, read from the page's embedded JSON rather than the rendered page.
- No upstream code was built or run. The add-on claims (permissions, services) were checked by reading the manifests of TV Inspector, Sleep Timer and Quiet Hours only. The rest are from upstream's own descriptions.
