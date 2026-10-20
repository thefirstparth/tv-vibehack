import assert from "node:assert/strict";
import { test } from "node:test";
import { busiestHours, change, clockWords, hourHeat, hoursWords, periodBefore, periodOf, records, usualOnOff, weekdays } from "../public/calc.js";

const day = (date, spans, apps = []) => ({ date, on: { minutes: spans.reduce((s, x) => s + 60, 0), spans }, apps });

test("periods: this month, this year, all time, and the one before", () => {
  const days = [day("2025-12-31", []), day("2026-09-30", []), day("2026-10-01", []), day("2026-10-21", [])];
  assert.deepEqual(periodOf(days, "2026-10-21", "month").map(d => d.date), ["2026-10-01", "2026-10-21"]);
  assert.deepEqual(periodOf(days, "2026-10-21", "year").map(d => d.date), ["2026-09-30", "2026-10-01", "2026-10-21"]);
  assert.equal(periodOf(days, "2026-10-21", "all").length, 4);
  assert.deepEqual(periodBefore(days, "2026-10-21", "month").map(d => d.date), ["2026-09-30"]);
  assert.deepEqual(periodBefore(days, "2026-10-21", "year").map(d => d.date), ["2025-12-31"]);
  assert.equal(periodBefore(days, "2026-10-21", "all"), null);
});

test("the hours: minutes on split across the hours, busiest two in a row", () => {
  const h = hourHeat([day("2026-10-20", [["19:30", "21:59"]]), day("2026-10-21", [["20:00", "20:59"]])]);
  assert.equal(h[19], 30);
  assert.equal(h[20], 120);
  assert.equal(h[21], 60);
  assert.deepEqual(busiestHours(h), [20, 180]);
  assert.equal(hoursWords(20), "8 to 10 PM");
  assert.equal(hoursWords(23), "11 PM to 1 AM");
  assert.equal(busiestHours(new Array(24).fill(0)), null);
});

test("weekdays are averaged over the days recorded, Monday first", () => {
  const w = weekdays([{ date: "2026-10-18", on: { minutes: 300 } }, { date: "2026-10-11", on: { minutes: 100 } }, { date: "2026-10-19", on: { minutes: 60 } }]);
  assert.equal(w[6], 200);   // Sundays
  assert.equal(w[0], 60);    // Monday
  assert.equal(w[1], null);
});

test("usually on and off, following a night past midnight", () => {
  const days = [day("2026-10-20", [["19:40", "23:59"]]), day("2026-10-21", [["00:00", "00:29"], ["19:50", "23:30"]])];
  const u = usualOnOff(days);
  assert.equal(clockWords(u.on), "7:45 PM");
  assert.equal(u.off, Math.round((1470 + 1411) / 2));
  assert.equal(clockWords(1470), "12:30 AM");
});

test("records and the change against the period before", () => {
  const r = records([day("2026-10-18", [["18:00", "23:49"]], [{ pkg: "com.sonyliv", name: "SonyLIV", spans: [["19:00", "21:54"]] }])]);
  assert.deepEqual(r.on, { date: "2026-10-18", minutes: 350 });
  assert.equal(r.app.minutes, 175);
  assert.equal(change([{ on: { minutes: 112 } }], [{ on: { minutes: 100 } }]), 12);
  assert.equal(change([{ on: { minutes: 112 } }], []), null);
});
