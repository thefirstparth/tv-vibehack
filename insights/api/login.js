import { same, sessionCookie } from "../lib/auth.js";

// POST /api/login   {"password": "..."} from the page; on a match, a year-long signed cookie. A wrong password waits
// a second before answering, so guessing is slow.
export async function POST(request) {
  let password = "";
  try {
    password = (await request.json())?.password ?? "";
  } catch {}
  if (!process.env.TV_PASSWORD || !same(String(password), process.env.TV_PASSWORD)) {
    await new Promise(r => setTimeout(r, 1000));
    return Response.json({ error: "wrong password" }, { status: 401 });
  }
  return Response.json({ ok: true }, { headers: { "set-cookie": sessionCookie(process.env), "cache-control": "no-store" } });
}
