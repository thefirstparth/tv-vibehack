// The two numbers the page's energy and cost come from. Pure, so it is unit-tested.

export const DEFAULTS = { rate: 6.7, watts: 95 };

/** Settings as stored, falling back to the environment, then the defaults (BESCOM's flat rate plus surcharges, 21 Oct). */
export function effective(stored, env = {}) {
  return {
    rate: num(stored?.rate) ?? num(env.TV_RATE) ?? DEFAULTS.rate,
    watts: num(stored?.watts) ?? num(env.TV_WATTS) ?? DEFAULTS.watts,
  };
}

/** What the page may save: ₹0–50 a unit, 10–500 W; anything else is refused with a reason. */
export function cleanSettings(input) {
  const rate = num(input?.rate), watts = num(input?.watts);
  if (rate === null || rate < 0 || rate > 50) throw new Error("The rate must be between ₹0 and ₹50 a unit.");
  if (watts === null || watts < 10 || watts > 500) throw new Error("The TV's power must be between 10 and 500 W.");
  return { rate: Math.round(rate * 100) / 100, watts: Math.round(watts) };
}

const num = v => (v === null || v === undefined || v === "" || !Number.isFinite(+v) ? null : +v);

/** What each field of a day means: the top of an export, so a person or an AI can read the file on its own. */
export const FIELDS = {
  date: "The day, in India time (IST).",
  "on.minutes": "Minutes the TV's screen was on (apps, home screen and screensaver).",
  "on.spans": "When the screen was on: [start, end] in 24-hour IST, each stretch of a minute or more.",
  apps: "Each app: minutes on screen, how many times it was opened, and when ([start, end] IST). The screensaver is com.google.android.apps.tv.dreamx.",
  home: "The home screen: how often it opened that day, its median and 90th-percentile time to draw (ms), and opens over 100 ms (slow).",
  cold: "Cold starts of the home screen: how many, and their median time (ms).",
  feed: "Sport and weather checks the home screen made: how many, and how many came back out of date.",
  tv: "Restarts that day, hours since the last restart, free memory (MB), free storage (MB), and times the Wi-Fi dropped.",
  send: "Trouble the TV had sending earlier summaries: failed tries, the last problem and when.",
  received: "When this site received the summary (UTC).",
  build: "The version of the TV's home screen that sent it.",
};
