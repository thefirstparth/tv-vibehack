import { signedIn } from "../lib/auth.js";
import { lastMonths } from "../lib/day.js";
import { effective } from "../lib/settings.js";
import { readMonth, readSettings, readStatus } from "../lib/store.js";

const FIRST = "2026-10";   // the first month with any days: earlier months are never asked for

// GET /api/days?months=3   for the signed-in phone: the days of the last few months (at most 13), oldest first.
export async function GET(request) {
  if (!signedIn(request.headers.get("cookie"), process.env)) return Response.json({ error: "sign in" }, { status: 401 });
  const n = Math.max(1, Math.min(13, +new URL(request.url).searchParams.get("months") || 3));
  const ym = new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Kolkata" }).slice(0, 7);
  // one unreadable month doesn't take the page down: the others still show, and the page says one is missing
  const names = lastMonths(ym, n).filter(m => m >= FIRST);
  const [status, settings, ...read] = await Promise.allSettled([readStatus(), readSettings(), ...names.map(m => readMonth(`months/${m}.json`))]);
  const missing = names.filter((m, i) => read[i].status === "rejected");
  const days = read.flatMap(r => (r.status === "fulfilled" ? r.value.month?.days ?? [] : [])).sort((a, b) => (a.date < b.date ? -1 : 1));
  // the rate (₹ a unit) and the TV's draw: from the settings sheet, else the environment, else the defaults (lib/settings.js)
  const energy = effective(settings.status === "fulfilled" ? settings.value : null, process.env);
  return Response.json({ days, missing, refused: status.status === "fulfilled" ? status.value : null, ...energy }, { headers: { "cache-control": "private, no-store" } });
}
