// Vercel Blob, private only: unlike the House of 1400's helper there is no public fallback, so if the store can't keep
// files private, saving fails instead of publishing the family's viewing. One file per month, read and written by its
// path (no list calls: lists are the expensive operation on the free plan).

export async function readMonth(path) {
  const { get } = await import("@vercel/blob");
  try {
    const r = await get(path, { access: "private", useCache: false });
    return r?.stream ? await new Response(r.stream).json() : null;
  } catch (e) {
    if (/not.?found|404/i.test(String(e?.message ?? e))) return null;
    throw e;
  }
}

export async function writeMonth(path, obj) {
  const { put } = await import("@vercel/blob");
  return put(path, JSON.stringify(obj), { access: "private", addRandomSuffix: false, allowOverwrite: true, contentType: "application/json" });
}
