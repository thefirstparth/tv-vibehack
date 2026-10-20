import assert from "node:assert/strict";
import { test } from "node:test";
import { hm, kwh, lastDays, monthOf, topApps, totals } from "../public/calc.js";

const days = [
  { date: "2026-09-30", on: { minutes: 60 }, apps: [{ pkg: "a", name: "A", minutes: 60 }] },
  { date: "2026-10-20", on: { minutes: 120 }, apps: [{ pkg: "a", name: "A", minutes: 30 }, { pkg: "b", name: "B", minutes: 90 }], home: { median_ms: 90, opens: 10, slow: 1 }, tv: { boots: 1 } },
  { date: "2026-10-21", on: { minutes: 240 }, apps: [{ pkg: "a", name: "A", minutes: 200 }], home: { median_ms: 110, opens: 20, slow: 0 }, feed: { failed: 2 } },
];

test("hours and minutes", () => {
  assert.equal(hm(250), "4 h 10 m");
  assert.equal(hm(45), "45 m");
  assert.equal(hm(undefined), "0 m");
});

test("energy is minutes times watts", () => {
  assert.equal(kwh(240, 95), 0.38);
  assert.equal(kwh(0, 95), 0);
});

test("apps add up across days, most watched first", () => {
  assert.deepEqual(topApps(days.slice(1)).map(a => [a.pkg, a.minutes]), [["a", 230], ["b", 90]]);
});

test("a month's totals and its busiest day", () => {
  const t = totals(monthOf(days, "2026-10-21"), 95);
  assert.equal(t.days, 2);
  assert.equal(t.on, 360);
  assert.equal(t.busiest.date, "2026-10-21");
  assert.equal(t.homeMedian, 100);
  assert.equal(t.slow, 1);
  assert.equal(t.feedFailed, 2);
  assert.equal(t.boots, 1);
});

test("the last seven days reach back into the month before", () => {
  assert.deepEqual(lastDays(days, "2026-10-21", 7).map(d => d.date), ["2026-10-20", "2026-10-21"]);
  assert.deepEqual(lastDays(days, "2026-10-21", 22).map(d => d.date), ["2026-09-30", "2026-10-20", "2026-10-21"]);
});
