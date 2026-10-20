// One day's summary from the TV, checked and cut down to known fields before it is stored: the TV is trusted with a key,
// but what is kept should never be more than this shape. Pure, so it is unit-tested.

const DATE = /^\d{4}-\d{2}-\d{2}$/;
const HM = /^([01]\d|2[0-3]):[0-5]\d$/;
/** A real calendar date ("2026-02-30" is not). */
const realDate = s => {
  if (!DATE.test(s ?? "")) return false;
  const t = new Date(s + "T00:00:00Z");
  return !Number.isNaN(t.getTime()) && t.toISOString().slice(0, 10) === s;
};
const PKG = /^[A-Za-z0-9_.]{1,120}$/;
/** A TV input's id ("com.mediatek.tvinput/.hdmi.HDMIInputService/HW5") or "livetv" for the Live TV row. */
const INPUT_ID = /^[A-Za-z0-9_.\/:-]{1,160}$/;
export const MAX_BYTES = 32 * 1024;   // 40 apps with 24 stretches each fits

const int = (v, max) => (v === null || v === undefined || v === "" || !Number.isFinite(+v) ? null : Math.max(0, Math.min(max, Math.round(+v))));
const text = (v, max) => (typeof v === "string" ? v.replace(/[\u0000-\u001f]/g, "").slice(0, max) : null);
/** [["19:40","21:05"], ...] kept as given when well formed; anything else left out. */
const spans = (list, max) =>
  Array.isArray(list) ? list.filter(s => Array.isArray(s) && HM.test(s[0]) && HM.test(s[1]) && s[0] <= s[1]).slice(0, max).map(s => [s[0], s[1]]) : null;
const drop = o => Object.fromEntries(Object.entries(o).filter(([, v]) => v !== null && v !== undefined));

/** The stored day, or throws Error with a short reason. `now`: nothing dated after tomorrow in India is taken. */
export function cleanDay(input, now = new Date()) {
  if (!input || typeof input !== "object") throw new Error("not an object");
  if (input.v !== 1) throw new Error("unknown version");
  if (!realDate(input.date)) throw new Error("bad date");
  const tomorrow = new Date(now.getTime() + (5.5 + 24) * 3600 * 1000).toISOString().slice(0, 10);
  if (input.date > tomorrow || input.date < "2020-01-01") throw new Error("date out of range");
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
  // the TV input app's time by the input picked on the home screen's Inputs menu (from build 1bb593d's successor; optional)
  if (Array.isArray(input.inputs)) {
    d.inputs = input.inputs
      .filter(a => a && INPUT_ID.test(a.id ?? ""))
      .slice(0, 12)
      .map(a => drop({ id: a.id, name: text(a.name, 60), minutes: int(a.minutes, 1440), opens: int(a.opens, 10000), spans: spans(a.spans, 24) }))
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
  if (input.send && typeof input.send === "object") {
    d.send = drop({ failed: int(input.send.failed, 100000), last: text(input.send.last, 60), at: text(input.send.at, 25) });
  }
  return drop(d);
}

/** How much a copy of a day holds: so a resend with less (lost usage access, a cleared TV) never replaces more. */
const weight = d => (d.on?.minutes ?? -1) * 10 + (d.apps?.length ?? 0) * 100 + (d.home?.opens ?? 0);

/** The month file's name for a day: "months/2026-10.json". */
export const monthPath = date => `months/${date.slice(0, 7)}.json`;

/** A month file with this day put in, days in date order. A day already there is replaced only by a copy holding as much or more. */
export function withDay(month, day) {
  const old = (month?.days ?? []).find(x => x.date === day.date);
  const days = (month?.days ?? []).filter(x => x.date !== day.date);
  days.push(old && weight(old) > weight(day) ? { ...old, send: day.send ?? old.send, resent: day.received ?? null } : day);
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

/**
 * The first day of the record, kept only from a time of day: stretches before it are cut off, the minutes worked out
 * again from what is left, and the day's home-screen numbers dropped (before it, they are tests). Days after are as sent.
 */
export function fromStart(day, start) {
  if (day.date !== start.date) return day;
  const cut = spans => (spans ?? []).filter(s => s[1] >= start.time).map(s => [s[0] < start.time ? start.time : s[0], s[1]]);
  const mins = spans => spans.reduce((n, s) => n + (toMin(s[1]) + 1 - toMin(s[0])), 0);
  const d = { ...day, from: start.time };
  if (day.on) { const s = cut(day.on.spans); d.on = { minutes: mins(s), spans: s }; }
  if (day.apps) d.apps = day.apps.map(a => { const s = cut(a.spans); return { ...a, spans: s, minutes: mins(s) }; }).filter(a => a.minutes > 0);
  if (day.inputs) d.inputs = day.inputs.map(a => { const s = cut(a.spans); return { ...a, spans: s, minutes: mins(s) }; }).filter(a => a.minutes > 0);
  delete d.home; delete d.cold; delete d.feed;
  return d;
}
const toMin = hm => +hm.slice(0, 2) * 60 + +hm.slice(3, 5);
