import assert from "node:assert/strict";
import { test } from "node:test";
import { cleanSettings, effective } from "../lib/settings.js";

test("settings: stored first, then the environment, then the defaults", () => {
  assert.deepEqual(effective(null, {}), { rate: 6.7, watts: 95 });
  assert.deepEqual(effective(null, { TV_RATE: "7.1" }), { rate: 7.1, watts: 95 });
  assert.deepEqual(effective({ rate: 7.25, watts: 110 }, { TV_RATE: "7.1" }), { rate: 7.25, watts: 110 });
});

test("settings: only sensible numbers are saved", () => {
  assert.deepEqual(cleanSettings({ rate: "7.204", watts: "95.4" }), { rate: 7.2, watts: 95 });
  assert.throws(() => cleanSettings({ rate: -1, watts: 95 }), /rate/);
  assert.throws(() => cleanSettings({ rate: 7, watts: 5000 }), /power/);
  assert.throws(() => cleanSettings({ rate: "", watts: 95 }), /rate/);
});
