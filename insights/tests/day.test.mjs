import assert from "node:assert/strict";
import { test } from "node:test";
import { same, sessionCookie, signedIn, tvAllowed } from "../lib/auth.js";
import { cleanDay, lastMonths, monthPath, withDay } from "../lib/day.js";

const DAY = {
  v: 1, date: "2026-10-21", build: "e1cc510",
  on: { minutes: 250, spans: [["19:40", "00:30"], ["bad"], ["07:00", "07:30"]] },
  apps: [{ pkg: "com.sonyliv", name: "SonyLIV", minutes: 125, opens: 3 }, { pkg: "bad pkg!", minutes: 5 }, { pkg: "com.netflix.ninja", name: "Netflix" }],
  home: { opens: 38, median_ms: 92.4, p90_ms: 140, slow: 0, extra: "x" },
  feed: { asks: 40, fresh: 35, old: 4, failed: 1 },
  tv: { boots: 1, uptime_h: 30, net_drops: 0 },
  secret: "never kept",
};

test("a day keeps only the known fields, cleaned", () => {
  const d = cleanDay(DAY);
  assert.equal(d.secret, undefined);
  assert.deepEqual(d.on, { minutes: 250, spans: [["07:00", "07:30"]] });   // ["19:40", "00:30"] runs backwards: left out
  assert.deepEqual(d.apps, [{ pkg: "com.sonyliv", name: "SonyLIV", minutes: 125, opens: 3 }]);
  assert.deepEqual(d.home, { opens: 38, median_ms: 92, p90_ms: 140, slow: 0 });
  assert.equal(d.tv.boots, 1);
});

test("a day without a version or with a bad date is refused", () => {
  assert.throws(() => cleanDay({ ...DAY, v: 2 }), /version/);
  assert.throws(() => cleanDay({ ...DAY, date: "21-10-2026" }), /date/);
  assert.throws(() => cleanDay(null), /object/);
});

test("numbers are clamped and control characters dropped", () => {
  const d = cleanDay({ ...DAY, on: { minutes: 99999 }, apps: [{ pkg: "a.b", name: "X\u0007Y", minutes: -5 }] });
  assert.equal(d.on.minutes, 1440);
  assert.deepEqual(d.apps, [{ pkg: "a.b", name: "XY", minutes: 0 }]);
});

test("a resent day replaces its earlier copy, days stay in order", () => {
  let m = withDay(null, cleanDay({ ...DAY, date: "2026-10-22" }));
  m = withDay(m, cleanDay(DAY));
  m = withDay(m, cleanDay({ ...DAY, on: { minutes: 300 } }));
  assert.deepEqual(m.days.map(d => d.date), ["2026-10-21", "2026-10-22"]);
  assert.equal(m.days[0].on.minutes, 300, "a fuller resend replaces");
  assert.equal(monthPath("2026-10-21"), "months/2026-10.json");
});

test("an app's stretches on screen are kept when well formed", () => {
  const d = cleanDay({ ...DAY, apps: [{ pkg: "com.sonyliv", minutes: 85, spans: [["19:40", "21:05"], ["9pm", "10pm"]] }] });
  assert.deepEqual(d.apps[0].spans, [["19:40", "21:05"]]);
});

test("the last months go back across a year", () => {
  assert.deepEqual(lastMonths("2026-02", 3), ["2026-02", "2026-01", "2025-12"]);
});

test("the TV's key and the phone's cookie", () => {
  const env = { TV_KEY: "k-123", TV_PASSWORD: "pw" };
  assert.ok(tvAllowed("k-123", env));
  assert.ok(!tvAllowed("k-124", env));
  assert.ok(!tvAllowed("", { TV_KEY: "" }), "no key set lets nothing in");
  assert.ok(!same(undefined, "x"));
  const now = 1_800_000_000_000;
  const c = sessionCookie(env, now).split(";")[0];
  assert.ok(signedIn(c, env, now));
  assert.ok(!signedIn(c, { ...env, TV_PASSWORD: "changed" }, now), "a new password signs everyone out");
  assert.ok(!signedIn(c, env, now + 400 * 86400000), "it expires after a year");
  assert.ok(!signedIn(c.replace(/.$/, c.endsWith("A") ? "B" : "A"), env, now));
  assert.ok(!signedIn(c, {}, now), "no password set: nobody is signed in");
});

test("only real dates, nothing after tomorrow in India, only real times in order", () => {
  const now = new Date("2026-10-21T10:00:00Z");
  assert.throws(() => cleanDay({ ...DAY, date: "2026-13-45" }, now), /bad date/);
  assert.throws(() => cleanDay({ ...DAY, date: "2026-02-30" }, now), /bad date/);
  assert.throws(() => cleanDay({ ...DAY, date: "2099-01-01" }, now), /range/);
  assert.equal(cleanDay({ ...DAY, date: "2026-10-22" }, now).date, "2026-10-22");
  const d = cleanDay({ ...DAY, on: { minutes: 60, spans: [["25:99", "26:00"], ["23:00", "01:00"], ["19:00", "19:59"]] } }, now);
  assert.deepEqual(d.on.spans, [["19:00", "19:59"]]);
});

test("a missing number is left out, not stored as 0", () => {
  const d = cleanDay({ ...DAY, apps: [{ pkg: "a.b", minutes: null }], home: { opens: 3, median_ms: null } });
  assert.equal(d.apps.length, 0);
  assert.deepEqual(d.home, { opens: 3 });
});

test("a resend with less never replaces a fuller copy, but its send problems are kept", () => {
  const full = cleanDay(DAY);
  const thin = cleanDay({ v: 1, date: DAY.date, home: { opens: 0 }, send: { failed: 2, last: "no internet", at: "2026-10-22T08:00" } });
  const m = withDay(withDay(null, full), { ...thin, received: "2026-10-22T09:00:00Z" });
  assert.equal(m.days[0].on.minutes, 250);
  assert.equal(m.days[0].send.last, "no internet");
  assert.equal(m.days[0].resent, "2026-10-22T09:00:00Z");
  const fuller = withDay(withDay(null, thin), full);
  assert.equal(fuller.days[0].on.minutes, 250, "a fuller copy does replace a thin one");
});

test("the first day is kept only from 04:00, without its home-screen numbers", async () => {
  const { fromStart } = await import("../lib/day.js");
  const start = { date: "2026-10-21", time: "04:00" };
  const d = fromStart(cleanDay({ v: 1, date: "2026-10-21", on: { minutes: 300, spans: [["00:10", "02:59"], ["03:30", "05:59"], ["19:00", "19:59"]] },
    apps: [{ pkg: "com.sonyliv", minutes: 170, spans: [["00:10", "02:59"]] }, { pkg: "a.b", minutes: 60, spans: [["19:00", "19:59"]] }], home: { opens: 40 } }), start);
  assert.deepEqual(d.on.spans, [["04:00", "05:59"], ["19:00", "19:59"]]);
  assert.equal(d.on.minutes, 180);
  assert.deepEqual(d.apps.map(a => [a.pkg, a.minutes]), [["a.b", 60]]);
  assert.equal(d.home, undefined);
  assert.equal(fromStart({ date: "2026-10-22", home: { opens: 3 } }, start).home.opens, 3, "later days as sent");
});
