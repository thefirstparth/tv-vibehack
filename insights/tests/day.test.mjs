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

test("the first copy of a day is kept; a different later copy is only noted; days stay in order", () => {
  let m = withDay(null, cleanDay({ ...DAY, date: "2026-10-22" }));
  m = withDay(m, cleanDay(DAY));
  m = withDay(m, { ...cleanDay({ ...DAY, on: { minutes: 1226 }, build: "d145324" }), received: "2026-10-23T00:51:21Z" });
  assert.deepEqual(m.days.map(d => d.date), ["2026-10-21", "2026-10-22"]);
  assert.equal(m.days[0].on.minutes, 250, "the first copy stays");
  assert.deepEqual(m.days[0].ignored, { at: "2026-10-23T00:51:21Z", build: "d145324", count: 1 });
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
  // the same copy again (a resend after a failed send) updates only the send notes
  const m = withDay(withDay(null, full), { ...full, send: thin.send, received: "2026-10-22T09:00:00Z" });
  assert.equal(m.days[0].on.minutes, 250);
  assert.equal(m.days[0].send.last, "no internet");
  assert.equal(m.days[0].resent, "2026-10-22T09:00:00Z");
  assert.equal(m.days[0].ignored, undefined);
  // a different copy, fuller or thinner, never replaces the first
  assert.equal(withDay(withDay(null, thin), full).days[0].on, undefined, "a fuller copy doesn't replace the first");
  assert.equal(withDay(withDay(null, full), thin).days[0].on.minutes, 250, "nor does a thinner one");
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

test("per-input minutes are kept when sent, and a day without them (build 1bb593d) is as before", () => {
  const XBOX = "com.mediatek.tvinput/.hdmi.HDMIInputService/HW5";
  const d = cleanDay({ ...DAY, inputs: [
    { id: XBOX, name: "Xbox", minutes: 95, opens: 2, spans: [["19:40", "21:15"]], extra: 1 },
    { id: "livetv", name: "Live TV", minutes: 10 },
    { id: "bad id with spaces", minutes: 5 },
    { id: XBOX, name: "no minutes" }] });
  assert.deepEqual(d.inputs, [{ id: XBOX, name: "Xbox", minutes: 95, opens: 2, spans: [["19:40", "21:15"]] }, { id: "livetv", name: "Live TV", minutes: 10 }]);
  assert.equal("inputs" in cleanDay(DAY), false);
  assert.deepEqual(Object.keys(cleanDay(DAY)).sort(), ["apps", "build", "date", "feed", "home", "on", "tv", "v"]);
});

test("test stretches are cut out of a day, with its home-screen numbers", async () => {
  const { withoutTests } = await import("../lib/day.js");
  const tests = [{ date: "2026-10-22", from: "03:45", to: "04:50" }];
  const d = withoutTests({ date: "2026-10-22", on: { minutes: 999, spans: [["02:06", "02:37"], ["03:47", "05:10"]] },
    apps: [{ pkg: "com.sonyliv", minutes: 22, spans: [["02:06", "02:27"]] }, { pkg: "x.media", minutes: 30, spans: [["03:50", "04:20"]] }], home: { opens: 80 }, tv: { boots: 3 } }, tests);
  assert.deepEqual(d.on.spans, [["02:06", "02:37"], ["04:50", "05:10"]]);
  assert.equal(d.on.minutes, 32 + 21);
  assert.deepEqual(d.apps.map(a => a.pkg), ["com.sonyliv"]);
  assert.equal(d.home, undefined);
  assert.equal(d.tv.boots, 0);
  assert.equal(withoutTests({ date: "2026-10-23", home: { opens: 5 } }, tests).home.opens, 5);
  // an app without stretches can't be placed outside the tests: left out on a test day, kept on other days
  const noSpans = { pkg: "com.xiaomi.mitv.mediaexplorer", minutes: 10, opens: 143 };
  assert.deepEqual(withoutTests({ date: "2026-10-22", apps: [noSpans] }, tests).apps, []);
  assert.deepEqual(withoutTests({ date: "2026-10-23", apps: [noSpans] }, tests).apps, [noSpans]);
});
