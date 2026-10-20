// Offline after the first visit: network first for everything, the last copy when the network fails or stalls (8 s).
// Sign-in is never cached. The days live only in this phone's cache and in the page's own storage.
const CACHE = "tv-insights-v2";
const SHELL = ["/", "/app.js", "/calc.js", "/app.css", "/manifest.webmanifest", "/icon-192.png"];

self.addEventListener("install", e => e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting())));
self.addEventListener("activate", e => e.waitUntil(
  caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim())));

self.addEventListener("fetch", e => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET" || url.pathname.startsWith("/api/login")) return;
  const fonts = url.host === "fonts.googleapis.com" || url.host === "fonts.gstatic.com";
  if (url.origin !== location.origin && !fonts) return;
  e.respondWith((async () => {
    const cache = await caches.open(CACHE);
    try {
      const r = await Promise.race([fetch(e.request, { cache: "no-cache" }), new Promise((_, no) => setTimeout(() => no(new Error("stalled")), 8000))]);
      if (r.ok) cache.put(e.request, r.clone());
      return r;
    } catch (err) {
      const hit = await cache.match(e.request, { ignoreSearch: url.pathname === "/" });
      if (hit) return hit;
      throw err;
    }
  })());
});
