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

/** The screensaver: the screen is lit (it counts for hours on and energy) but nobody chose to watch it. */
export const SAVER = "com.google.android.apps.tv.dreamx";

/** Minutes of screensaver over some days. */
export const saverMinutes = days => days.reduce((s, d) => s + (d.apps ?? []).filter(a => a.pkg === SAVER).reduce((t, a) => t + (a.minutes || 0), 0), 0);

/** Apps summed over some days, most minutes first: [{pkg, name, minutes, opens}]. The screensaver is left out (Parth, 21 Oct). */
export function topApps(days) {
  const by = new Map();
  for (const d of days) for (const a of d.apps ?? []) {
    if (a.pkg === SAVER) continue;
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
export const OTHER = "#a39a88";   // 3:1 against the strip's background, so it is seen
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
  "com.formulaone.production": "#8a1538",   // not YouTube's red
  "com.google.android.apps.tv.dreamx": "#a99f8c",
  "com.mitv.livetv": "#107c10",   // the TV's input app: in this house, the Xbox
};
const SPARE = ["#147a40", "#b5650d", "#0f6e8c", "#8c2f6e", "#4b5b9a", "#6b6b2a"];
export function colourOf(pkg) {
  if (BRAND[pkg]) return BRAND[pkg];
  let h = 0;
  for (const c of String(pkg)) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  return SPARE[h % SPARE.length];
}

/** What a person calls it: the screensaver's package says "Backdrop". */
/**
 * What a person calls it: the screensaver's package says "Backdrop"; the TV's input app ("Live TV") is the Xbox here
 * (Parth, 21 Oct): Android can't tell this TV's HDMI ports apart, and the Xbox is the only thing he uses on them.
 */
export const nameOf = a => (a.pkg === "com.google.android.apps.tv.dreamx" ? "Screensaver" : a.pkg === "com.mitv.livetv" ? "Xbox (TV input)" : a.name || a.pkg);

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

// ---- Insights: the same days looked at across a month, a year or all time ----------------------------------------

/** The days of a period ending at `date`: "month", "year" or "all". */
export function periodOf(days, date, period) {
  if (period === "all") return days.filter(d => d.date <= date);
  const n = period === "year" ? 4 : 7;
  return days.filter(d => d.date.slice(0, n) === date.slice(0, n) && d.date <= date);
}

/** The period before: last month or last year (null for all time). */
export function periodBefore(days, date, period) {
  if (period === "all") return null;
  let y = +date.slice(0, 4), m = +date.slice(5, 7);
  if (period === "year") y--; else if (--m === 0) { m = 12; y--; }
  const key = period === "year" ? String(y) : `${y}-${String(m).padStart(2, "0")}`;
  return days.filter(d => d.date.startsWith(key));
}

/** Minutes on in each hour of the day (0–23), summed over the days, from the screen's on stretches. */
export function hourHeat(days) {
  const h = new Array(24).fill(0);
  for (const d of days) for (const s of d.on?.spans ?? []) {
    let a = minuteOf(s[0]);
    const b = minuteOf(s[1], true);
    while (a < b) {
      const next = Math.min(b, (Math.floor(a / 60) + 1) * 60);
      h[Math.floor(a / 60)] += next - a;
      a = next;
    }
  }
  return h;
}

/** The two hours in a row with the most time on: [start hour, minutes], or null when there is nothing. */
export function busiestHours(heat) {
  let best = null;
  for (let i = 0; i < 24; i++) {
    const v = heat[i] + heat[(i + 1) % 24];
    if (v > 0 && (!best || v > best[1])) best = [i, v];
  }
  return best;
}

/** "8 to 10 PM", "11 PM to 1 AM" for a start hour and a length of two hours. */
export function hoursWords(start) {
  const w = h => ({ n: h % 12 || 12, ap: h < 12 ? "AM" : "PM" });
  const a = w(start), b = w((start + 2) % 24);
  return a.ap === b.ap ? `${a.n} to ${b.n} ${b.ap}` : `${a.n} ${a.ap} to ${b.n} ${b.ap}`;
}

/** Average minutes on for each weekday, Monday first, over the days recorded on that weekday (null when none). */
export function weekdays(days) {
  const sum = new Array(7).fill(0), n = new Array(7).fill(0);
  for (const d of days) {
    const i = (new Date(d.date + "T12:00:00Z").getUTCDay() + 6) % 7;
    sum[i] += d.on?.minutes || 0;
    n[i]++;
  }
  return sum.map((s, i) => (n[i] ? s / n[i] : null));
}

const medianOf = xs => {
  const s = xs.filter(Number.isFinite).sort((a, b) => a - b);
  if (!s.length) return null;
  const i = s.length >> 1;
  return s.length % 2 ? s[i] : Math.round((s[i - 1] + s[i]) / 2);
};

/**
 * When the TV usually goes on and off, as minutes after midnight: the median of each day's first switch-on and its
 * last switch-off. A day still on at midnight ends after midnight the next day, so off can pass 1440.
 */
export function usualOnOff(days) {
  const byDate = new Map(days.map(d => [d.date, d]));
  const on = [], off = [];
  for (const d of days) {
    const spans = (d.on?.spans ?? []).filter(s => s[0] !== "00:00" || !byDate.has(prevDate(d.date)));
    if (!spans.length) continue;
    on.push(minuteOf(spans[0][0]));
    let end = minuteOf(d.on.spans[d.on.spans.length - 1][1], true);
    if (end >= 1440) {   // went on past midnight: where it stopped the next day
      const next = byDate.get(nextDate(d.date));
      const first = next?.on?.spans?.[0];
      end = first && first[0] === "00:00" ? 1440 + minuteOf(first[1], true) : 1440;
    }
    off.push(end);
  }
  return { on: medianOf(on), off: medianOf(off) };
}
const prevDate = d => new Date(Date.parse(d + "T12:00:00Z") - 86400000).toISOString().slice(0, 10);
const nextDate = d => new Date(Date.parse(d + "T12:00:00Z") + 86400000).toISOString().slice(0, 10);

/** "7:40 PM" for minutes after midnight (past 1440 is the next day). */
export function clockWords(min) {
  if (min == null) return "—";
  const m = Math.round(min) % 1440, h = Math.floor(m / 60);
  return `${h % 12 || 12}:${String(m % 60).padStart(2, "0")} ${h < 12 ? "AM" : "PM"}`;
}

/** The longest single stretch: of the screen on ({date, minutes}) and of one app ({date, minutes, pkg, name}). */
export function records(days) {
  let on = null, app = null;
  const len = s => minuteOf(s[1], true) - minuteOf(s[0]);
  for (const d of days) {
    for (const s of d.on?.spans ?? []) if (!on || len(s) > on.minutes) on = { date: d.date, minutes: len(s) };
    for (const a of d.apps ?? []) for (const s of a.spans ?? []) if (!app || len(s) > app.minutes) app = { date: d.date, minutes: len(s), pkg: a.pkg, name: a.name };
  }
  return { on, app };
}

/** The change in time on a day between two periods, as a whole percent, or null when either has no days. */
export function change(now, before) {
  const avg = ds => (ds?.length ? ds.reduce((s, d) => s + (d.on?.minutes || 0), 0) / ds.length : null);
  const a = avg(now), b = avg(before);
  return a == null || !b ? null : Math.round(((a - b) / b) * 100);
}
