import { signedIn } from "../lib/auth.js";
import { lastMonths } from "../lib/day.js";
import { readMonth, readStatus } from "../lib/store.js";

const FIRST = "2026-10";   // the first month with any days: earlier months are never asked for

// GET /api/days?months=3   for the signed-in phone: the days of the last few months (at most 13), oldest first.
export async function GET(request) {
  if (!signedIn(request.headers.get("cookie"), process.env)) return Response.json({ error: "sign in" }, { status: 401 });
  const n = Math.max(1, Math.min(13, +new URL(request.url).searchParams.get("months") || 3));
  const ym = new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Kolkata" }).slice(0, 7);
  // one unreadable month doesn't take the page down: the others still show, and the page says one is missing
  const names = lastMonths(ym, n).filter(m => m >= FIRST);
  const [status, ...read] = await Promise.allSettled([readStatus(), ...names.map(m => readMonth(`months/${m}.json`))]);
  const missing = names.filter((m, i) => read[i].status === "rejected");
  const days = read.flatMap(r => (r.status === "fulfilled" ? r.value.month?.days ?? [] : [])).sort((a, b) => (a.date < b.date ? -1 : 1));
  // TV_RATE: rupees a unit (kWh), from the bill; 6.70 is BESCOM's 2025 flat ₹5.80 plus surcharges, an estimate (21 Oct)
  return Response.json({ days, missing, refused: status.status === "fulfilled" ? status.value : null, watts: +process.env.TV_WATTS || 95, rate: +process.env.TV_RATE || 6.7 }, { headers: { "cache-control": "private, no-store" } });
}
