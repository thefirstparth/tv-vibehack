// Vercel Blob, private only: unlike the House of 1400's helper there is no public fallback, so if the store can't keep
// files private, saving fails instead of publishing the family's viewing. Files are read and written by path (no list
// calls: lists are the expensive operation on the free plan).
//
// Two kinds of file. days/<date>/<received>.json: every copy of every day as it arrived, never overwritten (the
// archive, so nothing the TV sent can be lost by a later write). months/<YYYY-MM>.json: the page's copy, one per month,
// written only after a clean read of the one before and only if nobody wrote it in between (its etag).

/** { month, etag } for a month file; month null when the file isn't there yet. Any other failure throws. */
export async function readMonth(path) {
  const { get } = await import("@vercel/blob");
  const r = await get(path, { access: "private", useCache: false });
  if (r === null) return { month: null, etag: null };
  if (!r?.stream) throw new Error("month file without a body");
  // the read can come back with a weak etag (W/"…"), which a conditional write refuses (23 Oct 2026: "ETag mismatch"
  // on a month that hadn't changed); the strong one is the same value without the W/
  return { month: await new Response(r.stream).json(), etag: String(r.blob?.etag || "").replace(/^W\//, "") || null };
}

/** Writes a month file only if it is still as read (etag), or only if it still doesn't exist (no etag). */
export async function writeMonth(path, obj, etag) {
  const { put } = await import("@vercel/blob");
  const opts = { access: "private", addRandomSuffix: false, contentType: "application/json" };
  return put(path, JSON.stringify(obj), etag ? { ...opts, allowOverwrite: true, ifMatch: etag } : { ...opts, allowOverwrite: false });
}

/** The archive copy of one arrival of one day: never overwritten. */
export async function archiveDay(day) {
  const { put } = await import("@vercel/blob");
  const stamp = day.received.replace(/[:.]/g, "-");
  return put(`days/${day.date}/${stamp}.json`, JSON.stringify(day), { access: "private", addRandomSuffix: true, allowOverwrite: false, contentType: "application/json" });
}

/** The last refused day (key fine, contents not): what the page shows as "the site refused a day". */
export async function writeStatus(obj) {
  const { put } = await import("@vercel/blob");
  return put("status.json", JSON.stringify(obj), { access: "private", addRandomSuffix: false, allowOverwrite: true, contentType: "application/json" });
}

export async function readStatus() {
  const { get } = await import("@vercel/blob");
  try {
    const r = await get("status.json", { access: "private", useCache: false });
    return r?.stream ? await new Response(r.stream).json() : null;
  } catch {
    return null;
  }
}

/** Parth's settings from the page (the electricity rate, the TV's draw): settings.json, or null. */
export async function readSettings() {
  const { get } = await import("@vercel/blob");
  const r = await get("settings.json", { access: "private", useCache: false });
  return r?.stream ? await new Response(r.stream).json() : null;
}

export async function writeSettings(obj) {
  const { put } = await import("@vercel/blob");
  return put("settings.json", JSON.stringify(obj), { access: "private", addRandomSuffix: false, allowOverwrite: true, contentType: "application/json" });
}
