// Offline after the first visit. The page's own files and the fonts: the copy kept here at once, refreshed in the
// background for next time (so a stalled connection never holds the page up). The days: from the network first, the
// kept copy if that fails or stalls for 8 s. Sign-in is never cached. The days live only in this phone's cache and in
// the page's own storage.
const CACHE = "tv-insights-v7";
const SHELL = ["/", "/app.js", "/calc.js", "/app.css", "/manifest.webmanifest", "/icon-192.png"];

self.addEventListener("install", e => e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting())));
self.addEventListener("activate", e => e.waitUntil(
  caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim())));

const stalls = ms => new Promise((_, no) => setTimeout(() => no(new Error("stalled")), ms));

self.addEventListener("fetch", e => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET" || url.pathname.startsWith("/api/login") || url.pathname.startsWith("/api/export")) return;
  const fonts = url.host === "fonts.googleapis.com" || url.host === "fonts.gstatic.com";
  if (url.origin !== location.origin && !fonts) return;
  const key = url.pathname === "/" || url.pathname === "/index.html" ? "/" : e.request;
  e.respondWith((async () => {
    const cache = await caches.open(CACHE);
    const fresh = fetch(e.request, { cache: "no-cache" }).then(r => {
      if (r.ok) cache.put(key, r.clone());
      return r;
    });
    if (url.pathname.startsWith("/api/")) {
      try {
        return await Promise.race([fresh, stalls(8000)]);
      } catch (err) {
        const hit = await cache.match(key);
        if (hit) return hit;
        throw err;
      }
    }
    const hit = await cache.match(key);
    if (hit) {
      e.waitUntil(fresh.catch(() => {}));
      return hit;
    }
    return fresh;
  })());
});
