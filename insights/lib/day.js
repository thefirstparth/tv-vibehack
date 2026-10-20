// One day's summary from the TV, checked and cut down to known fields before it is stored: the TV is trusted with a key,
// but what is kept should never be more than this shape. Pure, so it is unit-tested.

const DATE = /^\d{4}-\d{2}-\d{2}$/;
const HM = /^\d{2}:\d{2}$/;
const PKG = /^[A-Za-z0-9_.]{1,120}$/;
export const MAX_BYTES = 32 * 1024;   // 40 apps with 24 stretches each fits

const int = (v, max) => (Number.isFinite(+v) ? Math.max(0, Math.min(max, Math.round(+v))) : null);
const text = (v, max) => (typeof v === "string" ? v.replace(/[\u0000-\u001f]/g, "").slice(0, max) : null);
/** [["19:40","21:05"], ...] kept as given when well formed; anything else left out. */
const spans = (list, max) =>
  Array.isArray(list) ? list.filter(s => Array.isArray(s) && HM.test(s[0]) && HM.test(s[1])).slice(0, max).map(s => [s[0], s[1]]) : null;
const drop = o => Object.fromEntries(Object.entries(o).filter(([, v]) => v !== null && v !== undefined));

/** The stored day, or throws Error with a short reason. */
export function cleanDay(input) {
  if (!input || typeof input !== "object") throw new Error("not an object");
  if (input.v !== 1) throw new Error("unknown version");
  if (!DATE.test(input.date ?? "")) throw new Error("bad date");
  const d = { v: 1, date: input.date };
  d.build = text(input.build, 40);
  d.sent = text(input.sent, 40);
  if (input.on && typeof input.on === "object") {
    d.on = drop({ minutes: int(input.on.minutes, 1440), spans: spans(input.on.spans, 48) ?? [] });
  }
  if (Array.isArray(input.apps)) {
    d.apps = input.apps
      .filter(a => a && PKG.test(a.pkg ?? ""))
      .slice(0, 40)
      .map(a => drop({ pkg: a.pkg, name: text(a.name, 60), minutes: int(a.minutes, 1440), opens: int(a.opens, 10000), spans: spans(a.spans, 24) }))
      .filter(a => a.minutes != null);
  }
  if (input.home && typeof input.home === "object") {
    const h = input.home;
    d.home = drop({ opens: int(h.opens, 100000), median_ms: int(h.median_ms, 60000), p90_ms: int(h.p90_ms, 60000), max_ms: int(h.max_ms, 600000), slow: int(h.slow, 100000) });
  }
  if (input.cold && typeof input.cold === "object") {
    d.cold = drop({ count: int(input.cold.count, 10000), median_ms: int(input.cold.median_ms, 600000) });
  }
  if (input.feed && typeof input.feed === "object") {
    const f = input.feed;
    d.feed = drop({ asks: int(f.asks, 100000), fresh: int(f.fresh, 100000), old: int(f.old, 100000), failed: int(f.failed, 100000) });
  }
  if (input.tv && typeof input.tv === "object") {
    const t = input.tv;
    d.tv = drop({ boots: int(t.boots, 1000), uptime_h: int(t.uptime_h, 100000), mem_free_mb: int(t.mem_free_mb, 100000), storage_free_mb: int(t.storage_free_mb, 10000000), net_drops: int(t.net_drops, 10000) });
  }
  return drop(d);
}

/** The month file's name for a day: "months/2026-10.json". */
export const monthPath = date => `months/${date.slice(0, 7)}.json`;

/** A month file with this day put in (replacing the same date), days in date order. */
export function withDay(month, day) {
  const days = (month?.days ?? []).filter(x => x.date !== day.date);
  days.push(day);
  days.sort((a, b) => (a.date < b.date ? -1 : 1));
  return { month: day.date.slice(0, 7), days };
}

/** The last n months' names, newest first, from "2026-10". */
export function lastMonths(ym, n) {
  let [y, m] = ym.split("-").map(Number);
  const out = [];
  for (let i = 0; i < n; i++) {
    out.push(`${y}-${String(m).padStart(2, "0")}`);
    if (--m === 0) { m = 12; y--; }
  }
  return out;
}
