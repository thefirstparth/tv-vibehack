import { signedIn } from "../lib/auth.js";
import { cleanSettings, effective } from "../lib/settings.js";
import { readSettings, writeSettings } from "../lib/store.js";

// GET  /api/settings   the rate (₹ a unit) and the TV's draw (W) the page uses.
// POST /api/settings   {"rate": 7.2, "watts": 95} from the settings sheet; the signed-in phone only.
export async function GET(request) {
  if (!signedIn(request.headers.get("cookie"), process.env)) return Response.json({ error: "sign in" }, { status: 401 });
  return Response.json(effective(await readSettings().catch(() => null), process.env), { headers: { "cache-control": "private, no-store" } });
}

export async function POST(request) {
  if (!signedIn(request.headers.get("cookie"), process.env)) return Response.json({ error: "sign in" }, { status: 401 });
  let s;
  try {
    s = cleanSettings(await request.json());
  } catch (e) {
    return Response.json({ error: String(e.message ?? e).slice(0, 120) }, { status: 400 });
  }
  await writeSettings({ ...s, saved: new Date().toISOString() });
  return Response.json(s, { headers: { "cache-control": "no-store" } });
}
