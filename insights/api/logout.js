// POST /api/logout   signs this phone out: the cookie is cleared.
export function POST() {
  return Response.json({ ok: true }, { headers: { "set-cookie": "tvs=; Max-Age=0; Path=/; HttpOnly; Secure; SameSite=Strict", "cache-control": "no-store" } });
}
