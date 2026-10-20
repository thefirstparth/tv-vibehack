import { tvAllowed } from "../lib/auth.js";
import { cleanDay, MAX_BYTES, monthPath, withDay } from "../lib/day.js";
import { readMonth, writeMonth } from "../lib/store.js";

// POST /api/day   from the TV, once a day: one day's summary as JSON, with its key in the X-TV-Key header.
// The day replaces any earlier copy of the same date, so a resend changes nothing.
export async function POST(request) {
  if (!tvAllowed(request.headers.get("x-tv-key"), process.env)) return Response.json({ error: "key" }, { status: 401 });
  const body = await request.text();
  if (body.length > MAX_BYTES) return Response.json({ error: "too big" }, { status: 413 });
  let day;
  try {
    day = cleanDay(JSON.parse(body));
  } catch (e) {
    return Response.json({ error: String(e.message ?? e).slice(0, 60) }, { status: 400 });
  }
  const path = monthPath(day.date);
  await writeMonth(path, withDay(await readMonth(path), day));
  return Response.json({ ok: true, date: day.date }, { headers: { "cache-control": "no-store" } });
}
