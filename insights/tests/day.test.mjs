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
  assert.deepEqual(d.on, { minutes: 250, spans: [["19:40", "00:30"], ["07:00", "07:30"]] });
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
  m = withDay(m, cleanDay({ ...DAY, on: { minutes: 10 } }));
  assert.deepEqual(m.days.map(d => d.date), ["2026-10-21", "2026-10-22"]);
  assert.equal(m.days[0].on.minutes, 10);
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
