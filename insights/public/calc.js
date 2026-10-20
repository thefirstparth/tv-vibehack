// The page's sums, kept apart from the drawing so they are unit-tested (tests/calc.test.mjs).

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
