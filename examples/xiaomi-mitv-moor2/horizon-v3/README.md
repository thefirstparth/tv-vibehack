# Horizon v3 (the home screen's light): the approved mockup

`mockup.html` is the mockup Parth approved on 20 Oct 2026 (open it in a browser). Its script is the
reference for the launcher build: the sun and moon maths (SunCalc-style), the star list, the
projection, the drawing order and the readability guard.

- **Light:** v2's eight mood palettes blended continuously by the minute (anchors relative to sunrise
  and sunset); the glow sits where the real sun is (hour angle across, altitude up), at night where
  the moon is.
- **Moon:** light only, never a disc: a cool glow on its side and a cooler, brighter sky, by phase.
- **Stars:** the 909 stars of magnitude 4.5 or brighter (`STARS`: RA°, Dec°, magnitude, B-V, from the
  Yale Bright Star Catalogue via d3-celestial's `stars.6.json`, BSD licence), placed for Bengaluru at
  that moment, fading in from blue hour, washed out by the moon, hidden by cloud, and at a third of
  their brightness behind text. The faint Milky Way on clear moonless nights. (The mockup's meteor
  streaks are left out of the first build.)
- **Nothing solid:** no moon disc, no planets, no sun disc.
- **Weather:** the feed's `sky` per city (clear, partly, overcast, fog, rain, storm): cloud bands,
  haze, a dark storm sky with a cold edge.
- **Readability guard:** the mockup kept each block's faintest text at least as readable as v2's
  screen at the same moment. The build uses a simpler fixed floor instead: the faintest text in each
  block keeps at least 4.5:1 against its background (90th-percentile pixel); where it would not, a
  soft oval shadow is added under that block until it does. v2 measured 3.4 to 4.5:1 across the
  mockup's frames, so the floor is never below today.
- **Grain:** fine noise baked into the picture, which is 960×540 (v2's is 480×270).

Not in this build (Parth has not approved them): the match ribbon and the after-match glows
(frames 13 to 15 of the mockup).
