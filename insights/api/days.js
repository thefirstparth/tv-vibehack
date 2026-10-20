import { signedIn } from "../lib/auth.js";
import { lastMonths } from "../lib/day.js";
import { readMonth } from "../lib/store.js";

// GET /api/days?months=3   for the signed-in phone: the days of the last few months (at most 13), oldest first.
export async function GET(request) {
  if (!signedIn(request.headers.get("cookie"), process.env)) return Response.json({ error: "sign in" }, { status: 401 });
  const n = Math.max(1, Math.min(13, +new URL(request.url).searchParams.get("months") || 3));
  const ym = new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Kolkata" }).slice(0, 7);
  const months = await Promise.all(lastMonths(ym, n).map(m => readMonth(`months/${m}.json`)));
  const days = months.filter(Boolean).flatMap(m => m.days ?? []).sort((a, b) => (a.date < b.date ? -1 : 1));
  return Response.json({ days, watts: +process.env.TV_WATTS || 95 }, { headers: { "cache-control": "private, no-store" } });
}
