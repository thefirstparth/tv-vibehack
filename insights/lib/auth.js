import { createHash, createHmac, timingSafeEqual } from "node:crypto";

// Two secrets, both only in Vercel's environment: TV_KEY (the TV sends it with each day) and TV_PASSWORD (Parth types
// it once on his phone). The phone then keeps a signed cookie for a year; signing uses SESSION_SECRET, or the password
// itself when that is not set, so changing the password signs everyone out.

const digest = s => createHash("sha256").update(String(s)).digest();
/** Same length compare, so the time taken says nothing about how much matched. */
export const same = (a, b) => typeof a === "string" && typeof b === "string" && a.length > 0 && timingSafeEqual(digest(a), digest(b));

const COOKIE = "tvs";
export const YEAR_S = 365 * 24 * 3600;
const secret = env => env.SESSION_SECRET || env.TV_PASSWORD || "";
const sign = (exp, env) => createHmac("sha256", secret(env)).update(`tv:${exp}`).digest("base64url");

export function sessionCookie(env, now = Date.now()) {
  const exp = Math.floor(now / 1000) + YEAR_S;
  return `${COOKIE}=${exp}.${sign(exp, env)}; Max-Age=${YEAR_S}; Path=/; HttpOnly; Secure; SameSite=Strict`;
}

export function signedIn(cookieHeader, env, now = Date.now()) {
  if (!secret(env)) return false;
  const m = new RegExp(`(?:^|;\\s*)${COOKIE}=(\\d+)\\.([A-Za-z0-9_-]+)`).exec(cookieHeader || "");
  if (!m) return false;
  const exp = +m[1];
  return exp * 1000 > now && same(m[2], sign(exp, env));
}

export const tvAllowed = (key, env) => same(key, env.TV_KEY || "");
