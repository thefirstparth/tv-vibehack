import { ago, colourOf, rupees, dayName, freshness, hm, kwh, lastDays, monthOf, nameOf, OTHER, strip, topApps, totals } from "./calc.js";

const main = document.getElementById("main");
const tabs = document.getElementById("tabs");
const fresh = document.getElementById("fresh");
const say = document.getElementById("say");
const VIEWS = ["day", "week", "month", "speed"];
const TZ = "Asia/Kolkata";
const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
const store = {
  get(k) { try { return JSON.parse(localStorage.getItem(k)); } catch { return null; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} },
};

let data = store.get("tv-days");
let failed = false, signedOut = false;
let { view, date } = route();

/** "#day/2026-10-20", "#week", ...: the view, and for Day the date shown. */
function route() {
  const [v, d] = location.hash.slice(1).split("/");
  return { view: VIEWS.includes(v) ? v : "day", date: /^\d{4}-\d{2}-\d{2}$/.test(d ?? "") ? d : null };
}
/** Tabs and day steps replace the history entry, so Back is one step out of the app. */
function go(v, d = null) {
  view = v; date = d;
  history.replaceState(null, "", `#${v}${d ? "/" + d : ""}`);
  draw(true);
}

const todayIST = () => new Date().toLocaleDateString("en-CA", { timeZone: TZ });
const yesterdayIST = () => new Date(Date.parse(todayIST() + "T12:00:00Z") - 86400000).toISOString().slice(0, 10);
const longDay = d => new Date(d + "T12:00:00Z").toLocaleDateString("en-GB", { weekday: "long", day: "numeric", month: "long", timeZone: "UTC" });
/** "Wed 21 October": fits one line at phone width. */
const titleDay = d => new Date(d + "T12:00:00Z").toLocaleDateString("en-GB", { weekday: "short", day: "numeric", month: "long", timeZone: "UTC" }).replace(",", "");
const hoursHtml = minutes => {
  const m = Math.round(minutes || 0), h = Math.floor(m / 60), r = m % 60;
  return h ? `${h}<small>h</small>${String(r).padStart(2, "0")}<small>m</small>` : `${r}<small>m</small>`;
};
const hero = (numHtml, side) => `<div class="hero"><span class="num">${numHtml}</span><span class="side">${side}</span></div>`;
const ms = v => (v == null ? "—" : `<span class="num">${Math.round(v)}</span> ms`);
const count = (n, one, many) => (n ? `${n}${one ? " " + (n === 1 ? one : many) : ""}` : "none");

// ---- the views ------------------------------------------------------------------------------------------------

const stripWords = day => (day.on?.spans?.length ? `On ${day.on.spans.map(s => `${s[0]} to ${s[1]}`).join(", ")} IST` : "Off all day");
function stripHtml(day, cls = "") {
  return `<div class="strip ${cls}" role="img" aria-label="${esc(stripWords(day))}">${strip(day)
    .map(s => `<i style="left:${s.left.toFixed(3)}%;width:${s.width.toFixed(3)}%;background:${s.colour}"></i>`).join("")}</div>`;
}
const ticks = (style = "", few = false) => `<div class="ticks" aria-hidden="true" style="${style}">${(few ? ["12 AM", "12 PM", "12 AM"] : ["12 AM", "6 AM", "12 PM", "6 PM", "12 AM"]).map(t => `<span>${t}</span>`).join("")}</div>`;

function legend(day) {
  const shown = topApps([day]).slice(0, 4);
  const other = Math.max(0, (day.on?.minutes || 0) - shown.reduce((s, a) => s + a.minutes, 0));
  return `<ul class="legend">${shown.map(a => `<li style="--c:${colourOf(a.pkg)}">${esc(nameOf(a))} ${hm(a.minutes)}</li>`).join("")}${
    other >= 1 ? `<li style="--c:${OTHER}">Home and other ${hm(other)}</li>` : ""}</ul>`;
}

function appRows(apps, key) {
  if (!apps.length) return `<p class="note">No app use recorded. The TV needs "usage access" turned on for its home screen.</p>`;
  const row = a => `<li><span><span class="chip" style="--c:${colourOf(a.pkg)}"></span>${esc(nameOf(a))}</span><span><span class="num">${hm(a.minutes)}</span>${a.opens ? ` · opened ${a.opens}×` : ""}</span></li>`;
  const top = apps.slice(0, 5), rest = apps.slice(5);
  return `<ul class="rows">${top.map(row).join("")}</ul>${rest.length ? `<details data-key="${key}"${store.get("open-" + key) ? " open" : ""}>
    <summary><span>${rest.length} more app${rest.length === 1 ? "" : "s"}</span><span aria-hidden="true">›</span></summary><ul class="rows">${rest.map(row).join("")}</ul></details>` : ""}`;
}

const sticker = home => (!home?.opens ? "" : `<span class="sticker">${home.slow ? `${home.slow} slow open${home.slow === 1 ? "" : "s"}` : "All fast"}</span>`);

function dayView(days, watts, rate) {
  let i = date ? days.findIndex(d => d.date === date) : -1;
  if (i < 0) i = days.length - 1;
  const d = days[i], prev = days[i - 1], next = days[i + 1];
  const label = d.date === yesterdayIST() ? "Yesterday" : d.date === todayIST() ? "Today" : `${days.length - 1 - i} days before the latest`;
  return `<div class="dayNav">
      <button type="button" data-day="${prev?.date ?? ""}" ${prev ? "" : "disabled"} aria-label="The day before"><svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true"><path d="M15 5l-7 7 7 7" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg></button>
      <span>Day ${i + 1} of ${days.length} recorded</span>
      <button type="button" data-day="${next?.date ?? ""}" ${next ? "" : "disabled"} aria-label="The day after"><svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true"><path d="M9 5l7 7-7 7" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg></button></div>
    <section class="card" aria-labelledby="h">
      <p class="label">${label}</p><h1 id="h" tabindex="-1">${esc(titleDay(d.date))}</h1>
      ${hero(hoursHtml(d.on?.minutes), `on · about ${kwh(d.on?.minutes, watts)} kWh, ${rupees(kwh(d.on?.minutes, watts), rate)}`)}
      ${stripHtml(d)}${ticks()}${legend(d)}${sticker(d.home)}
    </section>
    <section class="card"><h2>Home screen</h2><ul class="rows">
      <li><span>Opened</span><span>${d.home?.opens ?? 0} times · ${ms(d.home?.median_ms)} median</span></li>
      <li><span>Slow opens (over 100 ms)</span><span>${count(d.home?.slow)}</span></li>
      <li><span>Sport and weather</span><span>${d.feed?.asks ?? 0} asks · ${count(d.feed?.old, "old copy", "old copies")}</span></li>
      <li><span>Restarts</span><span>${count(d.tv?.boots)}</span></li>
      ${d.tv?.net_drops ? `<li><span>Wi-Fi dropped</span><span>${count(d.tv.net_drops, "time", "times")}</span></li>` : ""}
    </ul></section>
    <section class="card"><h2>Apps</h2>${appRows(topApps([d]), "day")}</section>`;
}

function weekView(days, watts, rate) {
  const end = days[days.length - 1].date, week = lastDays(days, end, 7), t = totals(week, watts);
  const first = week[0].date;
  const range = first.slice(0, 7) === end.slice(0, 7)
    ? `${+first.slice(8)}–${longDay(end).replace(/^\w+ /, "")}`
    : `${longDay(first).replace(/^\w+ /, "")} – ${longDay(end).replace(/^\w+ /, "")}`;
  return `<section class="card" aria-labelledby="h"><p class="label">Last 7 days</p><h1 id="h" tabindex="-1">${esc(range)}</h1>
      ${hero(hoursHtml(t.on), `on · ${hm(t.perDay)} a day · about ${t.kwh} kWh, ${rupees(t.kwh, rate)}`)}
      <div class="week">${week.map(d => `<span class="d">${esc(dayName(d.date).replace(",", "").split(" ").slice(0, 2).join(" "))}</span>${stripHtml(d, "thin")}<span class="h num">${hm(d.on?.minutes)}</span>`).join("")}</div>
      ${ticks("margin:4px 74px 0 58px", true)}
    </section>
    <section class="card"><h2>Apps</h2>${appRows(topApps(week), "week")}</section>`;
}

function monthView(days, watts, rate) {
  const end = days[days.length - 1].date, month = monthOf(days, end), t = totals(month, watts), apps = topApps(month);
  const name = new Date(end + "T12:00:00Z").toLocaleDateString("en-GB", { month: "long", year: "numeric", timeZone: "UTC" });
  const y = +end.slice(0, 4), m = +end.slice(5, 7), n = new Date(Date.UTC(y, m, 0)).getUTCDate();
  const lead = (new Date(Date.UTC(y, m - 1, 1)).getUTCDay() + 6) % 7;   // Monday first
  const by = Object.fromEntries(month.map(d => [d.date, d.on?.minutes || 0]));
  const max = Math.max(60, ...Object.values(by));
  const cells = Array.from({ length: lead }, () => `<span class="c none"></span>`);
  for (let dd = 1; dd <= n; dd++) {
    const key = `${end.slice(0, 7)}-${String(dd).padStart(2, "0")}`, v = by[key];
    const a = .3 + .7 * ((v || 0) / max);   // white text only on the darker blues, so it stays readable
    cells.push(v ? `<span class="c on${a < .62 ? " pale" : ""}" style="background:rgba(36,66,232,${a.toFixed(2)})">${dd}</span>` : `<span class="c">${dd}</span>`);
  }
  const top = apps[0];
  return `<section class="card" aria-labelledby="h"><p class="label">${+end.slice(8) === n ? "Wrapped" : "Wrapped so far"}</p><h1 id="h" tabindex="-1">${esc(name)}</h1>
      ${hero(hoursHtml(t.on), `on · ${count(t.days, "day", "days")} recorded`)}
      <div class="cal" role="img" aria-label="Hours on, each day of ${esc(name)}">${["M", "T", "W", "T", "F", "S", "S"].map(w => `<span class="w">${w}</span>`).join("")}${cells.join("")}</div>
      <p class="key">Key: darker blue, more hours on; plain, no summary that day.</p>
    </section>
    <section class="card"><ul class="rows">
      <li><span>Most watched</span><span>${top ? `<span class="chip" style="--c:${colourOf(top.pkg)}"></span>${esc(nameOf(top))} · <span class="num">${hm(top.minutes)}</span>` : "—"}</span></li>
      <li><span>Busiest day</span><span>${t.busiest ? `${esc(dayName(t.busiest.date))} · <span class="num">${hm(t.busiest.minutes)}</span>` : "—"}</span></li>
      <li><span>Home opened</span><span>${t.homeOpens} times</span></li>
      <li><span>Energy</span><span>about <span class="num">${t.kwh}</span> kWh · <span class="num">${rupees(t.kwh, rate)}</span></span></li>
      <li><span>Restarts</span><span>${count(t.boots)}</span></li>
    </ul></section>
    <section class="card"><h2>Apps</h2>${appRows(apps, "month")}</section>`;
}

function speedView(days) {
  const end = days[days.length - 1].date, recent = lastDays(days, end, 30), t = totals(recent, 0);
  const max = Math.max(50, ...recent.map(d => d.home?.median_ms || 0));
  const newest = [...recent].reverse();
  const mem = newest.find(d => d.tv?.mem_free_mb != null)?.tv?.mem_free_mb;
  return `<section class="card" aria-labelledby="h"><p class="label">Last 30 days</p><h1 id="h" tabindex="-1">Speed</h1>
      ${hero(t.homeMedian == null ? "—" : `${Math.round(t.homeMedian)}<small> ms</small>`, "Home, median time to draw")}
      <div class="bars" role="img" aria-label="Each day's median time for Home to draw">${recent.map(d => `<div><i class="${d.home?.slow ? "slow" : ""}" style="height:${((d.home?.median_ms || 0) / max) * 100}%"></i></div>`).join("")}</div>
      <div class="barDays" aria-hidden="true">${recent.map(d => `<span>${+d.date.slice(8) % 5 === 1 ? +d.date.slice(8) : ""}</span>`).join("")}</div>
      <p class="key">Key: each bar is one day's median, from Home starting to its first frame; orange, a day with a slow open (over 100 ms).</p>
    </section>
    <section class="card"><ul class="rows">
      <li><span>Slow opens (over 100 ms)</span><span>${count(t.slow)}</span></li>
      <li><span>Cold start, latest</span><span>${ms(newest.find(d => d.cold?.median_ms)?.cold?.median_ms)}</span></li>
      <li><span>Sport and weather, old copies</span><span>${count(recent.reduce((s, d) => s + (d.feed?.old || 0), 0))}</span></li>
      <li><span>Restarts</span><span>${count(t.boots)}</span></li>
      <li><span>Free memory, latest</span><span>${mem == null ? "—" : `<span class="num">${mem}</span> MB`}</span></li>
    </ul></section>`;
}

// ---- drawing, loading, the top bar -------------------------------------------------------------------------------

const skeleton = () => `<section class="card" aria-busy="true"><div class="skeleton" style="width:30%"></div><div class="skeleton" style="width:70%;height:30px"></div>
  <div class="skeleton" style="width:50%;height:56px"></div><div class="skeleton" style="height:46px"></div></section>
  <section class="card"><div class="skeleton"></div><div class="skeleton"></div><div class="skeleton" style="width:60%"></div></section>`;

function draw(moved) {
  if (signedOut) return;
  const days = data?.days ?? [];
  if (!days.length) {
    tabs.hidden = true;
    main.innerHTML = data
      ? `<section class="card"><h1 id="h" tabindex="-1">No days yet</h1><p class="note">The TV sends the day before each morning, the first time its home screen opens after 4 AM IST.</p></section>`
      : skeleton();
    topLine();
    return;
  }
  tabs.hidden = false;
  const watts = data.watts || 95, rate = data.rate || 6.7;
  main.innerHTML = view === "week" ? weekView(days, watts, rate) : view === "month" ? monthView(days, watts, rate) : view === "speed" ? speedView(days) : dayView(days, watts, rate);
  for (const b of tabs.querySelectorAll("button")) b.setAttribute("aria-current", b.dataset.view === view ? "page" : "false");
  for (const d of main.querySelectorAll("details[data-key]")) d.ontoggle = () => store.set("open-" + d.dataset.key, d.open);
  for (const b of main.querySelectorAll("[data-day]")) b.onclick = () => b.dataset.day && go("day", b.dataset.day);
  if (moved) { document.getElementById("h")?.focus({ preventScroll: true }); window.scrollTo(0, 0); }
  topLine();
}

/** The quiet freshness line, tap to refresh: amber when the last day is late or offline, red when a refresh failed. */
function topLine() {
  if (signedOut) return;
  const f = freshness(data?.days ?? [], Date.now());
  fresh.className = "fresh";
  if (!navigator.onLine) {
    fresh.textContent = f ? `Offline · last day came ${ago(f.age)} ago` : "Offline";
    fresh.classList.add("late");
  } else if (failed) {
    fresh.textContent = f ? `Refresh failed · ${ago(f.age)} old` : "Refresh failed · tap to try again";
    fresh.classList.add("bad");
  } else if (!f) {
    fresh.textContent = data ? "No days yet" : "Loading…";
  } else {
    fresh.textContent = f.late ? `Last day came ${ago(f.age)} ago` : `Updated ${ago(f.age)} ago`;
    if (f.late) fresh.classList.add("late");
  }
}

function signin(message) {
  signedOut = true;
  tabs.hidden = true;
  fresh.textContent = "";
  main.innerHTML = `<form class="signin" id="f"><h1 id="h" tabindex="-1">TV Insights</h1>
    <input type="password" name="p" placeholder="Password" aria-label="Password" autocomplete="current-password" required>
    <button class="primary">Open</button><p class="note ${message ? "bad" : ""}" role="status">${esc(message || "For this TV's household only.")}</p></form>`;
  const f = document.getElementById("f");
  f.p.focus();
  f.onsubmit = async e => {
    e.preventDefault();
    const btn = f.querySelector("button");
    btn.disabled = true;
    btn.textContent = "Checking…";
    const r = await fetch("/api/login", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ password: f.p.value }) }).catch(() => null);
    if (r?.ok) { signedOut = false; load(true); } else signin(r ? "Wrong password." : "Couldn't reach the server. Check the connection and try again.");
  };
}

async function load(moved) {
  const ctl = new AbortController(), timer = setTimeout(() => ctl.abort(), 8000);
  try {
    const r = await fetch("/api/days?months=13", { credentials: "same-origin", signal: ctl.signal });
    if (r.status === 401) return signin();
    const j = r.ok ? await r.json().catch(() => null) : null;
    if (!j || !Array.isArray(j.days)) throw new Error("bad answer");
    const before = data?.days?.length ?? 0;
    data = j;
    failed = false;
    store.set("tv-days", data);
    if (before && j.days.length > before) say.textContent = "A new day has arrived.";
  } catch {
    failed = true;
    if (!data) {
      tabs.hidden = true;
      main.innerHTML = `<section class="card"><h1 id="h" tabindex="-1">Couldn't load</h1><p class="note bad">The server didn't answer${navigator.onLine ? "" : ": this phone is offline"}.</p>
        <button class="primary" id="again" type="button" style="margin-top:12px">Try again now</button></section>`;
      document.getElementById("again").onclick = () => load(true);
      topLine();
      return;
    }
  } finally {
    clearTimeout(timer);
  }
  draw(moved);
}

tabs.onclick = e => { const v = e.target.closest("button")?.dataset.view; if (v) go(v); };
fresh.onclick = () => load(false);
document.getElementById("aboutBtn").onclick = () => document.getElementById("about").showModal();
window.addEventListener("hashchange", () => { ({ view, date } = route()); draw(true); });
window.addEventListener("online", () => load(false));
window.addEventListener("offline", topLine);
window.addEventListener("scroll", () => document.getElementById("top").classList.toggle("solid", scrollY > 4), { passive: true });
setInterval(topLine, 30000);
if ("serviceWorker" in navigator) navigator.serviceWorker.register("/sw.js").catch(() => {});
draw(false);
load(false);
