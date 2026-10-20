import { dayName, hm, kwh, lastDays, monthOf, topApps, totals } from "./calc.js";

const app = document.getElementById("app");
const nav = document.getElementById("nav");
const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
const store = {
  get(k) { try { return JSON.parse(localStorage.getItem(k)); } catch { return null; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} },
};

let data = store.get("tv-days");
let view = store.get("tv-view") || "day";

function login(message) {
  nav.hidden = true;
  app.innerHTML = `<form id="f"><h1>TV Insights</h1><input type="password" name="p" placeholder="Password" autocomplete="current-password" required autofocus>
    <button>Open</button><p class="note ${message ? "bad" : ""}">${esc(message || "Only for this TV's household.")}</p></form>`;
  document.getElementById("f").onsubmit = async e => {
    e.preventDefault();
    const r = await fetch("/api/login", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ password: e.target.p.value }) });
    if (r.ok) load(); else login("Wrong password.");
  };
}

async function load() {
  try {
    const r = await fetch("/api/days?months=13", { credentials: "same-origin" });
    if (r.status === 401) return login();
    if (!r.ok) throw new Error(r.status);
    data = await r.json();
    store.set("tv-days", data);
  } catch {
    if (!data) { app.innerHTML = `<p class="empty">Couldn't reach the server. Try again later.</p>`; return; }
  }
  draw();
}

const appsList = (apps, n = 6) => {
  const top = apps.slice(0, n), max = top[0]?.minutes || 1;
  return top.length
    ? top.map(a => `<div class="app"><div><span>${esc(a.name)}</span><span>${hm(a.minutes)}</span></div><div class="bar" style="width:${Math.max(2, (a.minutes / max) * 100)}%"></div></div>`).join("")
    : `<p class="note">No app use recorded (the launcher needs usage access).</p>`;
};
const bars = (days, value, label) => {
  const max = Math.max(1, ...days.map(value));
  return `<div class="chart">${days.map(d => `<div><i style="height:${(value(d) / max) * 100}%"></i>${esc(label(d))}</div>`).join("")}</div>`;
};
const ms = v => (v == null ? "—" : `${Math.round(v)} ms`);

function dayView(d, watts) {
  const on = d.on?.minutes;
  return `<h1>${esc(dayName(d.date))}</h1><p class="sub">${d.on?.spans?.length ? esc(d.on.spans.map(s => `${s[0]}–${s[1]}`).join(", ")) : "&nbsp;"}</p>
  <div class="card"><h2>On</h2><div class="big">${on == null ? "—" : hm(on)}</div>
    <p class="note">About ${kwh(on, watts)} kWh at ${watts} W (an estimate, not a meter).</p></div>
  <div class="card"><h2>Apps</h2>${appsList(topApps([d]))}</div>
  <div class="card"><h2>Home screen</h2><div class="stats">
    <div class="stat"><b>${d.home?.opens ?? "—"}</b><small>opened</small></div>
    <div class="stat"><b>${ms(d.home?.median_ms)}</b><small>median to draw</small></div>
    <div class="stat"><b class="${d.home?.slow ? "warm" : "good"}">${d.home?.slow ?? "—"}</b><small>slow (over 300 ms)</small></div>
  </div></div>`;
}

function weekView(days, end, watts) {
  const week = lastDays(days, end, 7), t = totals(week, watts);
  return `<h1>Last 7 days</h1><p class="sub">to ${esc(dayName(end))}</p>
  <div class="card"><h2>Hours on</h2><div class="big">${hm(t.on)}</div><p class="note">${hm(t.perDay)} a day · about ${t.kwh} kWh</p>
    ${bars(week, d => d.on?.minutes || 0, d => dayName(d.date).slice(0, 2))}</div>
  <div class="card"><h2>Apps</h2>${appsList(topApps(week), 8)}</div>`;
}

function monthView(days, end, watts) {
  const month = monthOf(days, end), t = totals(month, watts), apps = topApps(month);
  const name = new Date(end + "T12:00:00Z").toLocaleDateString("en-GB", { month: "long", year: "numeric", timeZone: "UTC" });
  return `<h1>${esc(name)}</h1><p class="sub">${t.days} day${t.days === 1 ? "" : "s"} recorded</p>
  <div class="card"><h2>Wrapped so far</h2>
    <div class="row"><span>Hours on</span><span>${hm(t.on)}</span></div>
    <div class="row"><span>Most watched</span><span>${esc(apps[0]?.name ?? "—")}</span></div>
    <div class="row"><span>Busiest day</span><span>${t.busiest ? `${esc(dayName(t.busiest.date))} · ${hm(t.busiest.minutes)}` : "—"}</span></div>
    <div class="row"><span>Home opened</span><span>${t.homeOpens} times</span></div>
    <div class="row"><span>Energy</span><span>about ${t.kwh} kWh</span></div>
    <div class="row"><span>Restarts</span><span>${t.boots}</span></div></div>
  <div class="card"><h2>Apps</h2>${appsList(apps, 10)}</div>`;
}

function speedView(days, end) {
  const recent = lastDays(days, end, 30);
  const t = totals(recent, 0);
  return `<h1>Speed</h1><p class="sub">last 30 days</p>
  <div class="card"><h2>Home, median time to draw</h2><div class="big">${ms(t.homeMedian)}</div>
    ${bars(recent, d => d.home?.median_ms || 0, d => (+d.date.slice(8) % 5 === 1 ? d.date.slice(8) : ""))}</div>
  <div class="card"><h2>Slow opens and problems</h2>
    <div class="row"><span>Home opens over 300 ms</span><span class="${t.slow ? "warm" : ""}">${t.slow}</span></div>
    <div class="row"><span>Feed failed</span><span>${t.feedFailed}</span></div>
    <div class="row"><span>Restarts</span><span>${t.boots}</span></div>
    <div class="row"><span>Cold starts, latest median</span><span>${ms([...recent].reverse().find(d => d.cold?.median_ms)?.cold?.median_ms)}</span></div></div>
  <p class="note">Times are the launcher's own, from coming back to Home to its first drawn frame.</p>`;
}

function draw() {
  const days = data?.days ?? [], watts = data?.watts || 95;
  if (!days.length) { nav.hidden = true; app.innerHTML = `<p class="empty">No days yet. The TV sends yesterday each morning, the first time Home opens after 4 am.</p>`; return; }
  nav.hidden = false;
  const last = days[days.length - 1];
  app.innerHTML = view === "week" ? weekView(days, last.date, watts) : view === "month" ? monthView(days, last.date, watts) : view === "speed" ? speedView(days, last.date) : dayView(last, watts);
  for (const b of nav.querySelectorAll("button")) b.setAttribute("aria-current", b.dataset.view === view ? "page" : "false");
}

nav.onclick = e => {
  const v = e.target.closest("button")?.dataset.view;
  if (v) { view = v; store.set("tv-view", v); draw(); window.scrollTo(0, 0); }
};
if (data) draw();
load();
