// The page's sums, kept apart from the drawing so they are unit-tested (tests/calc.test.mjs).

/** Rupees for some kWh at a rate a unit: "₹2.68". */
export const rupees = (kwhs, rate) => `₹${(Math.round((kwhs || 0) * rate * 100) / 100).toFixed(2)}`;

/** kWh for minutes on at a given draw: an estimate, not a meter reading. */
export const kwh = (minutes, watts) => Math.round(((minutes || 0) * watts) / 60 / 1000 * 100) / 100;

/** "4 h 10 m", "45 m", "0 m". */
export function hm(minutes) {
  const m = Math.round(minutes || 0);
  return m >= 60 ? `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, "0")} m` : `${m} m`;
}

/** Apps summed over some days, most minutes first: [{pkg, name, minutes, opens}]. */
export function topApps(days) {
  const by = new Map();
  for (const d of days) for (const a of d.apps ?? []) {
    const t = by.get(a.pkg) ?? { pkg: a.pkg, name: a.name || a.pkg, minutes: 0, opens: 0 };
    t.minutes += a.minutes || 0;
    t.opens += a.opens || 0;
    if (a.name) t.name = a.name;
    by.set(a.pkg, t);
  }
  return [...by.values()].filter(a => a.minutes > 0).sort((a, b) => b.minutes - a.minutes);
}

const median = xs => {
  const s = xs.filter(Number.isFinite).sort((a, b) => a - b);
  if (!s.length) return null;
  const i = s.length >> 1;
  return s.length % 2 ? s[i] : (s[i - 1] + s[i]) / 2;
};

/** Totals for a run of days. */
export function totals(days, watts) {
  const on = days.reduce((s, d) => s + (d.on?.minutes || 0), 0);
  const withOn = days.filter(d => d.on?.minutes != null);
  const busiest = withOn.reduce((b, d) => (!b || d.on.minutes > b.on.minutes ? d : b), null);
  return {
    days: days.length,
    on,
    perDay: withOn.length ? on / withOn.length : 0,
    kwh: kwh(on, watts),
    busiest: busiest ? { date: busiest.date, minutes: busiest.on.minutes } : null,
    homeMedian: median(days.map(d => d.home?.median_ms)),
    homeOpens: days.reduce((s, d) => s + (d.home?.opens || 0), 0),
    slow: days.reduce((s, d) => s + (d.home?.slow || 0), 0),
    feedFailed: days.reduce((s, d) => s + (d.feed?.failed || 0), 0),
    boots: days.reduce((s, d) => s + (d.tv?.boots || 0), 0),
  };
}

/** The days in the same month as `date` ("2026-10-21"), up to and including it. */
export const monthOf = (days, date) => days.filter(d => d.date.slice(0, 7) === date.slice(0, 7) && d.date <= date);

/** The last n days up to and including `date`, by date (missing days are simply absent). */
export function lastDays(days, date, n) {
  const end = new Date(date + "T12:00:00Z");
  const start = new Date(end.getTime() - (n - 1) * 86400000).toISOString().slice(0, 10);
  return days.filter(d => d.date >= start && d.date <= date);
}

/** "Wed 21 Oct" from "2026-10-21". */
export const dayName = date =>
  new Date(date + "T12:00:00Z").toLocaleDateString("en-GB", { weekday: "short", day: "numeric", month: "short", timeZone: "UTC" });

/** Minutes after midnight for "19:40"; an end time is inclusive of its minute, so it adds one. */
export const minuteOf = (hhmm, end = false) => {
  const [h, m] = hhmm.split(":").map(Number);
  return h * 60 + m + (end ? 1 : 0);
};

/** A day's strip: [{left, width, colour, pkg}] in % of 24 h. The screen's on time first (as "other"), apps on top. */
export function strip(day) {
  const out = [];
  for (const s of day.on?.spans ?? []) out.push(seg(s, OTHER, null));
  for (const a of day.apps ?? []) for (const s of a.spans ?? []) out.push(seg(s, colourOf(a.pkg), a.pkg));
  return out;
}
const seg = (s, colour, pkg) => {
  const a = minuteOf(s[0]), b = Math.max(a + 1, minuteOf(s[1], true));
  return { left: (a / 1440) * 100, width: ((b - a) / 1440) * 100, colour, pkg };
};

/** Apps in their own colours, so the strip reads at a glance; others get a steady colour from their name. */
export const OTHER = "#c9c2b3";
const BRAND = {
  "com.sonyliv": "#1d1d1d",
  "com.google.android.youtube.tv": "#ff0000",
  "com.netflix.ninja": "#8f0a12",
  "in.startv.hotstar": "#7a2fc0",
  "com.fancode.tv": "#ff5a00",
  "com.amazon.amazonvideo.livingroom": "#00a8e1",
  "com.apple.atve.androidtv.appletv": "#5b5d63",
  "com.spotify.tv.android": "#1db954",
  "com.gathin.tv1400": "#2442e8",
  "com.formulaone.production": "#e10600",
  "com.google.android.apps.tv.dreamx": "#a99f8c",
};
const SPARE = ["#147a40", "#b5650d", "#0f6e8c", "#8c2f6e", "#4b5b9a", "#6b6b2a"];
export function colourOf(pkg) {
  if (BRAND[pkg]) return BRAND[pkg];
  let h = 0;
  for (const c of String(pkg)) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  return SPARE[h % SPARE.length];
}

/** What a person calls it: the screensaver's package says "Backdrop". */
export const nameOf = a => (a.pkg === "com.google.android.apps.tv.dreamx" ? "Screensaver" : a.name || a.pkg);

/** "26 s", "5 min", "3 h", "2 days": never rounded up. */
export function ago(ms) {
  const s = Math.max(0, Math.floor(ms / 1000));
  if (s < 60) return `${s} s`;
  if (s < 3600) return `${Math.floor(s / 60)} min`;
  if (s < 86400 * 2) return `${Math.floor(s / 3600)} h`;
  return `${Math.floor(s / 86400)} days`;
}

/** The newest day's arrival, from the server's "received". A day arrives each morning, so over 30 h is late. */
export function freshness(days, now) {
  const r = days.map(d => Date.parse(d.received ?? "")).filter(Number.isFinite);
  if (!r.length) return null;
  const age = now - Math.max(...r);
  return { age, late: age > 30 * 3600 * 1000 };
}
