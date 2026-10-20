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

test("a day's strip: on time first, apps on top in their colours", async () => {
  const { strip, colourOf, OTHER } = await import("../public/calc.js");
  const s = strip({ on: { spans: [["19:40", "23:59"]] }, apps: [{ pkg: "com.sonyliv", spans: [["19:40", "20:39"]] }] });
  assert.equal(s.length, 2);
  assert.equal(s[0].colour, OTHER);
  assert.ok(Math.abs(s[0].left - (1180 / 1440) * 100) < 1e-9);
  assert.ok(Math.abs(s[0].width - (260 / 1440) * 100) < 1e-9, "to the end of the day");
  assert.equal(s[1].colour, "#1d1d1d");
  assert.ok(Math.abs(s[1].width - (60 / 1440) * 100) < 1e-9);
  assert.equal(colourOf("com.example.x"), colourOf("com.example.x"), "the same colour every time");
});

test("ages are never rounded up, and a day over 30 h old is late", async () => {
  const { ago, freshness } = await import("../public/calc.js");
  assert.equal(ago(26_900), "26 s");
  assert.equal(ago(5 * 60_000 + 59_000), "5 min");
  assert.equal(ago(3 * 3600_000 + 3599_000), "3 h");
  assert.equal(ago(50 * 3600_000), "2 days");
  const now = Date.parse("2026-10-22T10:00:00Z");
  assert.equal(freshness([{ received: "2026-10-22T04:00:00Z" }], now).late, false);
  assert.equal(freshness([{ received: "2026-10-21T03:00:00Z" }], now).late, true);
  assert.equal(freshness([{}], now), null);
});

test("the cost in rupees", async () => {
  const { rupees } = await import("../public/calc.js");
  assert.equal(rupees(0.4, 6.7), "₹2.68");
  assert.equal(rupees(0, 6.7), "₹0.00");
});

test("the screensaver is left out of the apps but kept apart", async () => {
  const { topApps, saverMinutes, SAVER } = await import("../public/calc.js");
  const ds = [{ apps: [{ pkg: SAVER, minutes: 40 }, { pkg: "com.sonyliv", minutes: 30 }] }];
  assert.deepEqual(topApps(ds).map(a => a.pkg), ["com.sonyliv"]);
  assert.equal(saverMinutes(ds), 40);
});

test("the TV's input app is shown as the Xbox", async () => {
  const { nameOf, colourOf } = await import("../public/calc.js");
  assert.equal(nameOf({ pkg: "com.mitv.livetv", name: "Live TV" }), "Xbox (TV input)");
  assert.equal(colourOf("com.mitv.livetv"), "#107c10");
});

test("the TV input's time split by what was picked on the Inputs menu", async () => {
  const { inputSplit } = await import("../public/calc.js");
  const XBOX = "com.mediatek.tvinput/.hdmi.HDMIInputService/HW5";
  const days = [
    { date: "2026-10-28", apps: [{ pkg: "com.mitv.livetv", minutes: 120 }], inputs: [{ id: XBOX, name: "Xbox", minutes: 90 }, { id: "livetv", minutes: 10 }] },
    { date: "2026-10-29", apps: [{ pkg: "com.mitv.livetv", minutes: 30 }, { pkg: "com.sonyliv", minutes: 60 }] },   // a day from 1bb593d: no inputs
    { date: "2026-10-30", apps: [{ pkg: "com.mitv.livetv", minutes: 40 }], inputs: [{ id: XBOX, name: "Xbox", minutes: 41 }] },   // rounding: over by a minute
  ];
  assert.deepEqual(inputSplit(days), { named: [{ id: XBOX, name: "Xbox", minutes: 131 }, { id: "livetv", name: "Live TV", minutes: 10 }], unnamed: 49 });
  assert.deepEqual(inputSplit([days[1]]), { named: [], unnamed: 30 });
  assert.deepEqual(inputSplit([]), { named: [], unnamed: 0 });
});
