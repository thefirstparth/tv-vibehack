import { tvAllowed } from "../lib/auth.js";
import { cleanDay, MAX_BYTES, monthPath, withDay } from "../lib/day.js";
import { archiveDay, readMonth, writeMonth, writeStatus } from "../lib/store.js";

export const START = "2026-10-21";

// POST /api/day   from the TV, once a day: one day's summary as JSON, with its key in the X-TV-Key header.
// First the day is archived as it arrived (never overwritten), then merged into its month file. A resend never
// replaces a fuller copy with an emptier one (withDay). If the month can't be read cleanly, nothing is written and
// the TV gets a 503, so it tries again later rather than marking the day sent.
export async function POST(request) {
  if (!tvAllowed(request.headers.get("x-tv-key"), process.env)) return Response.json({ error: "key" }, { status: 401 });
  const body = await request.text();
  const refuse = async (status, why) => {
    await writeStatus({ at: new Date().toISOString(), status, why }).catch(() => {});
    return Response.json({ error: why }, { status });
  };
  if (body.length > MAX_BYTES) return refuse(413, "too big");
  let day;
  try {
    day = cleanDay(JSON.parse(body), new Date());
  } catch (e) {
    return refuse(400, String(e.message ?? e).slice(0, 60));
  }
  // The record starts on 21 Oct 2026 (Parth, 21 Oct): earlier days the TV still has (Android's usage stats) are full of
  // the A/B tests that night and are taken as sent but not kept, so the TV doesn't try them again or call it a failure.
  if (day.date < START) return Response.json({ ok: true, date: day.date, kept: false }, { headers: { "cache-control": "no-store" } });
  day.received = new Date().toISOString();   // the page's "Updated" line
  try {
    await archiveDay(day);
  } catch (e) {
    return Response.json({ error: "archive failed, try again" }, { status: 503 });
  }
  const path = monthPath(day.date);
  for (let attempt = 0; attempt < 3; attempt++) {
    let read;
    try {
      read = await readMonth(path);
    } catch {
      return Response.json({ error: "month unreadable, try again" }, { status: 503 });
    }
    try {
      await writeMonth(path, withDay(read.month, day), read.etag);
      return Response.json({ ok: true, date: day.date }, { headers: { "cache-control": "no-store" } });
    } catch (e) {
      if (!/precondition|already exists|412|409/i.test(String(e?.message ?? e) + (e?.name ?? ""))) break;
      // someone wrote the month in between: read it again and merge again
    }
  }
  return Response.json({ error: "month busy, try again" }, { status: 503 });
}
