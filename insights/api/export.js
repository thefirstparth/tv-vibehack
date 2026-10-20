import { signedIn } from "../lib/auth.js";
import { lastMonths } from "../lib/day.js";
import { effective, FIELDS } from "../lib/settings.js";
import { readMonth, readSettings } from "../lib/store.js";

const FIRST = "2026-10";
const PROMPT = `Below is my living-room TV's daily usage, one summary per day, in JSON (the fields are explained under "fields").
Please look for: how much we watch and when; which apps take the most time and whether that is changing; days or habits
that stand out; how much electricity it uses and what it costs; and anything in the home-screen speed or the TV's health
(restarts, slow opens, Wi-Fi drops) that suggests something to fix. Say what the data shows and what it doesn't; keep it short.`;

// GET /api/export          every day kept, as one JSON file, with what each field means.
// GET /api/export?for=ai   the same as text, with a prompt on top, to paste into an AI.
export async function GET(request) {
  if (!signedIn(request.headers.get("cookie"), process.env)) return Response.json({ error: "sign in" }, { status: 401 });
  const ym = new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Kolkata" }).slice(0, 7);
  const names = lastMonths(ym, 600).filter(m => m >= FIRST).reverse();
  const months = await Promise.all(names.map(m => readMonth(`months/${m}.json`)));
  const days = months.flatMap(r => r.month?.days ?? []).sort((a, b) => (a.date < b.date ? -1 : 1));
  const today = new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Kolkata" });
  const file = {
    about: "TV Insights export: the daily summaries sent by the living-room TV (a Xiaomi Google TV), from 21 Oct 2026, 4 AM IST.",
    exported: new Date().toISOString(),
    energy: { ...effective(await readSettings().catch(() => null), process.env), note: "kWh = minutes on × watts ÷ 60,000; an estimate, not a meter." },
    fields: FIELDS,
    days,
  };
  const ai = new URL(request.url).searchParams.get("for") === "ai";
  const body = ai ? `${PROMPT}\n\n${JSON.stringify(file)}` : JSON.stringify(file, null, 2);
  return new Response(body, {
    headers: {
      "content-type": ai ? "text/plain; charset=utf-8" : "application/json",
      "content-disposition": `attachment; filename="tv-insights-${today}${ai ? "-for-ai.txt" : ".json"}"`,
      "cache-control": "private, no-store",
    },
  });
}
