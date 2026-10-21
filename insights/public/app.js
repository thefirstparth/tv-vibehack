import { ago, inputSplit, saverMinutes, busiestHours, change, clockWords, colourOf, hourHeat, hoursWords, periodBefore, periodOf, records, rupees, usualOnOff, weekdays, dayName, freshness, hm, kwh, lastDays, monthOf, nameOf, OTHER, strip, topApps, totals } from "./calc.js";

const fontsLink = document.getElementById("fonts");   // the web fonts load without holding up the page
if (fontsLink) fontsLink.media = "all";
const main = document.getElementById("main");
const tabs = document.getElementById("tabs");
const fresh = document.getElementById("fresh");
const say = document.getElementById("say");
const VIEWS = ["day", "week", "month", "insights", "speed"];
const PERIODS = { month: "This month", year: "This year", all: "All time" };
const TZ = "Asia/Kolkata";
const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
const store = {
  get(k) { try { return JSON.parse(localStorage.getItem(k)); } catch { return null; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} },
};

let data = store.get("tv-days");
let example = false;   // made-up days to show the screens before the first real one arrives
let failed = false, signedOut = false;
let { view, date } = route();
let period = route().period || store.get("tv-period") || "month";

/** "#day/2026-10-20", "#insights/year", "#week": the view, for Day the date shown, for Insights the period. */
function route() {
  const [v, d] = location.hash.slice(1).split("/");
  return { view: VIEWS.includes(v) ? v : "day", date: /^\d{4}-\d{2}-\d{2}$/.test(d ?? "") ? d : null, period: PERIODS[d] ? d : null };
}
/** Tabs and day steps replace the history entry, so Back is one step out of the app. */
function go(v, d = null, keep = false) {
  if (v === "insights") { period = d || period; store.set("tv-period", period); d = period; }
  view = v; date = v === "day" ? d : null;
  history.replaceState(null, "", `#${v}${d ? "/" + d : ""}`);
  draw(!keep);
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
  const shown = topApps([day]).slice(0, 4), saver = saverMinutes([day]);
  const other = Math.max(0, (day.on?.minutes || 0) - shown.reduce((s, a) => s + a.minutes, 0) - saver);
  return `<ul class="legend">${shown.map(a => `<li style="--c:${colourOf(a.pkg)}">${esc(nameOf(a))} ${hm(a.minutes)}</li>`).join("")}${
    saver >= 1 ? `<li style="--c:${colourOf("com.google.android.apps.tv.dreamx")}">Screensaver ${hm(saver)}</li>` : ""}${
    other >= 1 ? `<li style="--c:${OTHER}">Home screen and other apps ${hm(other)}</li>` : ""}</ul>`;
}

/** The screensaver, apart from the apps: counted in hours on and energy, not as watching. */
function saverLine(days) {
  const m = saverMinutes(days);
  return m >= 1 ? `<p class="note">Also ${hm(m)} of screensaver: counted in hours on and energy, not in the apps above.</p>` : "";
}

/** The TV input's time by what was picked on Home's Inputs menu; shown only once the TV has named some. */
function inputLine(days) {
  const { named, unnamed } = inputSplit(days);
  if (!named.length) return "";
  return `<p class="note">Xbox (TV input), by what was picked on the home screen's Inputs menu: ${named.map(i => `${esc(i.name)} ${hm(i.minutes)}`).join(", ")}${
    unnamed >= 1 ? `; ${hm(unnamed)} not known (switched with the remote's source button, or already on)` : ""}.</p>`;
}

/** "4:49–5:14 AM" for a stretch ["04:49", "05:14"]; the end is the last minute on. */
const rangeWords = s => {
  const a = clockWords(+s[0].slice(0, 2) * 60 + +s[0].slice(3)), b = clockWords(+s[1].slice(0, 2) * 60 + +s[1].slice(3) + 1);
  return a.slice(-2) === b.slice(-2) ? `${a.slice(0, -3)}–${b}` : `${a}–${b}`;
};
const spanMinutes = s => +s[1].slice(0, 2) * 60 + +s[1].slice(3) + 1 - (+s[0].slice(0, 2) * 60 + +s[0].slice(3));

/** Every stretch of the day in time order (each app's), then when the screen was on, as words. */
function timesList(d) {
  const rows = [];
  for (const a of d.apps ?? []) for (const s of a.spans ?? []) rows.push({ s, name: nameOf(a), c: colourOf(a.pkg) });
  rows.sort((x, y) => (x.s[0] < y.s[0] ? -1 : 1));
  return `<ul class="rows times">${rows.map(r => `<li><span><span class="chip" style="--c:${r.c}"></span>${esc(rangeWords(r.s))}</span><span>${esc(r.name)} · <span class="num">${hm(spanMinutes(r.s))}</span></span></li>`).join("")}
    ${(d.on?.spans ?? []).length ? `<li class="note"><span>Screen on</span><span>${esc(d.on.spans.map(rangeWords).join(", "))}</span></li>` : ""}</ul>`;
}

function appRows(apps, key, day = null) {
  if (!apps.length) return `<p class="note">No app use recorded. The TV needs "usage access" turned on for its home screen.</p>`;
  const spansOf = a => (day?.apps ?? []).filter(x => x.pkg === a.pkg).flatMap(x => x.spans ?? []);
  const line = a => `<span><span class="chip" style="--c:${colourOf(a.pkg)}"></span>${esc(nameOf(a))}</span><span><span class="num">${hm(a.minutes)}</span>${a.opens ? ` · opened ${a.opens}×` : ""}</span>`;
  // in Day, an app with times opens to show them
  const row = a => (day && spansOf(a).length
    ? `<li class="app"><details><summary>${line(a)}</summary><p class="note">${esc(spansOf(a).map(rangeWords).join(", "))} IST</p></details></li>`
    : `<li>${line(a)}</li>`);
  const top = apps.slice(0, 5), rest = apps.slice(5);
  return `<ul class="rows">${top.map(row).join("")}</ul>${rest.length ? `<details class="more" data-key="${key}"${store.get("open-" + key) ? " open" : ""}>
    <summary><span>${rest.length} more app${rest.length === 1 ? "" : "s"}</span><span aria-hidden="true">›</span></summary><ul class="rows">${rest.map(row).join("")}</ul></details>` : ""}`;
}

/**
 * When the newest summary is late, or the TV or the site reported a problem: one plain card above the day, so a gap
 * reads as "the summary didn't arrive" and says what is known about why.
 */
function trouble(days) {
  const last = days[days.length - 1], f = freshness(days, Date.now()), lines = [];
  if (f?.late) lines.push(`No summary since ${esc(dayName(last.date))}. If the TV was on since then, its summaries will arrive the next time it can reach this site; it keeps the last 30 days.`);
  if (last.send?.failed) lines.push(`Before its last summary got through, the TV failed to send ${count(last.send.failed, "time", "times")}. Last problem: ${esc(last.send.last || "unknown")}${last.send.at ? `, at ${esc(clockWords(+last.send.at.slice(11, 13) * 60 + +last.send.at.slice(14, 16)))} on ${esc(dayName(last.send.at.slice(0, 10)))}` : ""}.`);
  if (data?.refused && Date.parse(data.refused.at) > Date.parse(last.received ?? 0)) lines.push(`On ${esc(dayName(data.refused.at.slice(0, 10)))} this site refused a summary from the TV: ${esc(data.refused.why)} (${data.refused.status}).`);
  if (data?.missing?.length) lines.push(`Couldn't read ${data.missing.map(m => esc(m)).join(", ")} just now; those days are missing until the next refresh.`);
  return lines.length ? `<section class="card warn" role="status"><h2>Something to know</h2>${lines.map(l => `<p>${l}</p>`).join("")}</section>` : "";
}


function dayView(days, watts, rate) {
  let i = date ? days.findIndex(d => d.date === date) : -1;
  if (i < 0) i = days.length - 1;
  const d = days[i], prev = days[i - 1], next = days[i + 1];
  const back = Math.round((Date.parse(todayIST() + "T12:00:00Z") - Date.parse(d.date + "T12:00:00Z")) / 86400000);
  const label = back === 0 ? "Today" : back === 1 ? "Yesterday" : `${back} days ago`;
  const off = d.on && d.on.minutes === 0 && !(d.apps ?? []).length;
  return `${i === days.length - 1 ? trouble(days) : ""}<div class="dayNav">
      <button type="button" data-day="${prev?.date ?? ""}" ${prev ? "" : "disabled"} aria-label="The day before"><svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true"><path d="M15 5l-7 7 7 7" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg></button>
      <span>Day ${i + 1} of ${days.length} recorded</span>
      <button type="button" data-day="${next?.date ?? ""}" ${next ? "" : "disabled"} aria-label="The day after"><svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true"><path d="M9 5l7 7-7 7" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg></button></div>
    <section class="card" aria-labelledby="h">
      <p class="label">${label}</p><h1 id="h" tabindex="-1">${esc(titleDay(d.date))}</h1>
      ${off ? `<div class="hero"><span class="num">Off</span><span class="side">all day</span></div>` : hero(hoursHtml(d.on?.minutes), `on · about ${kwh(d.on?.minutes, watts)} kWh, ${rupees(kwh(d.on?.minutes, watts), rate)}`)}
      ${off ? stripHtml(d) : `<details class="when" data-key="when"${store.get("open-when") ? " open" : ""}><summary aria-label="Show the times">${stripHtml(d)}${ticks()}</summary>${timesList(d)}</details>`}${off ? ticks() : ""}${off ? "" : legend(d)}
      <p class="key">Times are India time (IST).</p>
    </section>
    <section class="card"><h2>Apps</h2>${appRows(topApps([d]), "day", d)}${saverLine([d])}${inputLine([d])}</section>`;
}

function weekView(days, watts, rate) {
  const end = days[days.length - 1].date, week = lastDays(days, end, 7), t = totals(week, watts);
  const first = week[0].date;
  const range = first.slice(0, 7) === end.slice(0, 7)
    ? `${+first.slice(8)}–${longDay(end).replace(/^\w+ /, "")}`
    : `${longDay(first).replace(/^\w+ /, "")} – ${longDay(end).replace(/^\w+ /, "")}`;
  return `<section class="card" aria-labelledby="h"><p class="label">Last 7 days</p><h1 id="h" tabindex="-1">${esc(range)}</h1>
      ${hero(hoursHtml(t.on), `on · ${t.days} of 7 days recorded · ${hm(t.perDay)} a day · about ${t.kwh} kWh, ${rupees(t.kwh, rate)}`)}
      <div class="week">${week.map(d => `<span class="d">${esc(dayName(d.date).replace(",", "").split(" ").slice(0, 2).join(" "))}</span>${stripHtml(d, "thin")}<span class="h num">${hm(d.on?.minutes)}</span>`).join("")}</div>
      ${ticks("margin:4px 74px 0 58px", true)}
    </section>
    <section class="card"><h2>Apps</h2>${appRows(topApps(week), "week")}${saverLine(week)}${inputLine(week)}</section>`;
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
    const off = key in by && !v;
    const h = v >= 60 ? `${Math.round(v / 60)}h` : `${v}m`;
    cells.push(v ? `<span class="c on${a < .78 ? " pale" : ""}" style="background:rgba(36,66,232,${a.toFixed(2)})"><b>${dd}</b><small>${h}</small></span>` : off ? `<span class="c off"><b>${dd}</b><small>off</small></span>` : `<span class="c"><b>${dd}</b></span>`);
  }
  const top = apps[0];
  return `<section class="card" aria-labelledby="h"><p class="label">${+end.slice(8) === n ? "The whole month" : "The month so far"}</p><h1 id="h" tabindex="-1">${esc(name)}</h1>
      ${hero(hoursHtml(t.on), `on · ${count(t.days, "day", "days")} recorded`)}
      <div class="cal" role="img" aria-label="Hours on, each day of ${esc(name)}">${["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"].map(w => `<span class="w">${w}</span>`).join("")}${cells.join("")}</div>
      <p class="key">Key: each day shows its hours on (h, rounded) or minutes (m); darker blue, more hours; striped, the TV was off all day; plain, no summary that day.</p>
    </section>
    <section class="card"><ul class="rows">
      <li><span>Most watched</span><span>${top ? `<span class="chip" style="--c:${colourOf(top.pkg)}"></span>${esc(nameOf(top))} · <span class="num">${hm(top.minutes)}</span>` : "—"}</span></li>
      <li><span>Busiest day</span><span>${t.busiest ? `${esc(dayName(t.busiest.date))} · <span class="num">${hm(t.busiest.minutes)}</span>` : "—"}</span></li>
      <li><span>Home opened</span><span>${t.homeOpens} times</span></li>
      <li><span>Energy</span><span>about <span class="num">${t.kwh}</span> kWh · <span class="num">${rupees(t.kwh, rate)}</span></span></li>
      <li><span>Restarts</span><span>${count(t.boots)}</span></li>
    </ul></section>
    <section class="card"><h2>Apps</h2>${appRows(apps, "month")}${saverLine(month)}${inputLine(month)}</section>`;
}

function insightsView(days, watts, rate) {
  const end = days[days.length - 1].date, these = periodOf(days, end, period), before = periodBefore(days, end, period);
  const apps = topApps(these), total = apps.reduce((s, a) => s + a.minutes, 0) || 1, t = totals(these, watts);
  const heat = hourHeat(these), hmax = Math.max(1, ...heat), busy = busiestHours(heat);
  const wk = weekdays(these), wmax = Math.max(1, ...wk.map(v => v || 0));
  const best = wk.reduce((b, v, i) => (v != null && (b < 0 || v > wk[b]) ? i : b), -1);
  const names = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
  const usual = usualOnOff(these), rec = records(these), ch = change(these, before);
  const label = period === "all" ? `Since ${dayName(days[0].date)}` : period === "year" ? end.slice(0, 4) : new Date(end + "T12:00:00Z").toLocaleDateString("en-GB", { month: "long", year: "numeric", timeZone: "UTC" });
  const versus = period === "year" ? "last year" : new Date(Date.UTC(+end.slice(0, 4), +end.slice(5, 7) - 2, 15)).toLocaleDateString("en-GB", { month: "long", timeZone: "UTC" });
  const top3 = apps.slice(0, 3), others = Math.max(0, total - top3.reduce((s, a) => s + a.minutes, 0));
  const pct = m => Math.round((m / total) * 100);
  return `<div class="seg" role="group" aria-label="Period">${Object.entries(PERIODS).map(([k, v]) => `<button type="button" data-period="${k}" aria-pressed="${k === period}">${v}</button>`).join("")}</div>
    <section class="card" aria-labelledby="h"><p class="label">${esc(label)} · ${count(t.days, "day", "days")}</p><h1 id="h" tabindex="-1">Insights</h1>
      <h2 style="margin-top:12px">Most watched</h2><p class="key" style="margin:0 0 2px">Share of time in apps; the screensaver is left out.</p>
      <div class="share" role="img" aria-label="${esc(top3.map(a => `${nameOf(a)} ${pct(a.minutes)}%`).join(", "))}">${top3.map(a => `<i style="width:${pct(a.minutes)}%;background:${colourOf(a.pkg)}"></i>`).join("")}${others ? `<i style="width:${pct(others)}%;background:${OTHER}"></i>` : ""}</div>
      ${apps.length ? `<ul class="rows" style="margin-top:6px">${top3.map(a => `<li><span><span class="chip" style="--c:${colourOf(a.pkg)}"></span>${esc(nameOf(a))}</span><span><span class="num">${hm(a.minutes)}</span> · ${pct(a.minutes)}%</span></li>`).join("")}</ul>` : appRows([], "")}
    </section>
    <section class="card"><h2>When it's on</h2>
      <div class="heat" role="img" aria-label="Time on in each hour of the day">${heat.map(v => `<i style="--a:${(v / hmax).toFixed(2)}"></i>`).join("")}</div>${ticks()}
      <p class="note">Busiest hours: <b class="ink">${busy ? `${hoursWords(busy[0])} IST` : "—"}</b></p>
      <div class="wk" role="img" aria-label="Average time on for each weekday">${wk.map((v, i) => `<i class="${i === best ? "best" : ""}" style="height:${((v || 0) / wmax) * 100}%"></i>`).join("")}</div>
      <div class="wkl" aria-hidden="true">${names.map(n => `<span>${n.slice(0, 3)}</span>`).join("")}</div>
      <p class="note">Busiest day: <b class="ink">${best < 0 ? "—" : names[best]}</b>${best < 0 ? "" : `, ${hm(wk[best])} on average`}</p>
    </section>
    <section class="card"><h2>Habits and records</h2><ul class="rows">
      <li><span>Usually on</span><span>${usual.on == null ? "—" : `${clockWords(usual.on)} – ${clockWords(usual.off)}`}</span></li>
      <li><span>An average day</span><span class="num">${hm(t.perDay)}</span></li>
      <li><span>Longest stretch on</span><span>${rec.on ? `${esc(dayName(rec.on.date))} · <span class="num">${hm(rec.on.minutes)}</span>` : "—"}</span></li>
      <li><span>Longest in one app</span><span>${rec.app ? `${esc(nameOf(rec.app))} · <span class="num">${hm(rec.app.minutes)}</span>` : "—"}</span></li>
      ${period === "all" ? "" : `<li><span>Against ${esc(versus)}</span><span>${ch == null ? "not enough days yet" : `${Math.abs(ch)}% ${ch >= 0 ? "more" : "less"} time on each day`}</span></li>`}
      <li><span>Energy</span><span>about ${t.kwh} kWh · <span class="num">${rupees(t.kwh, rate)}</span></span></li>
    </ul><p class="key">Times are India time (IST).</p></section>`;
}

function speedView(days) {
  const end = days[days.length - 1].date, recent = lastDays(days, end, 30), t = totals(recent, 0);
  const max = Math.max(50, ...recent.map(d => d.home?.median_ms || 0));
  const newest = [...recent].reverse();
  const mem = newest.find(d => d.tv?.mem_free_mb != null)?.tv?.mem_free_mb;
  const d = days[days.length - 1];
  const latest = `<section class="card"><h2>The latest day, ${esc(dayName(d.date))}</h2><ul class="rows">
      <li><span>Home screen opened</span><span>${d.home?.opens ?? 0} times · ${ms(d.home?.median_ms)} usually</span></li>
      <li><span>Slow opens (over 100 ms)</span><span>${count(d.home?.slow)}</span></li>
      <li><span>Sport and weather checks</span><span>${d.feed?.asks ?? 0} · ${count(d.feed?.old, "came in out of date", "came in out of date")}</span></li>
      <li><span>Restarts</span><span>${count(d.tv?.boots)}</span></li>
      <li><span>Wi-Fi dropped</span><span>${count(d.tv?.net_drops, "time", "times")}</span></li>
    </ul></section>`;
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
    </ul></section>${latest}`;
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
      ? `<section class="card"><p class="label">Nothing here yet</p><h1 id="h" tabindex="-1">The first day is on its way</h1>
          <p class="note">The TV sends each day's summary the next morning, the first time its home screen opens after 4 AM IST.
          The record starts at 4 AM on Wednesday 21 October, so that day should arrive on the morning of Thursday 22 October.</p>
          <button class="primary" id="ex" type="button" style="margin-top:14px">Show an example with made-up data</button></section>`
      : skeleton();
    document.getElementById("ex")?.addEventListener("click", () => { example = true; data = { ...data, days: exampleDays() }; draw(true); });
    topLine();
    return;
  }
  tabs.hidden = false;
  const watts = data.watts || 95, rate = data.rate || 6.7;
  document.body.classList.toggle("example", example);
  const aboutEnergy = document.getElementById("aboutEnergy");
  if (aboutEnergy) aboutEnergy.textContent = `Energy is an estimate: hours on times a typical ${watts} W, not a meter reading. The cost uses ₹${rate.toFixed(2)} a unit (1 unit = 1 kWh).`;
  main.innerHTML = (example ? `<p class="exampleBar" role="status">Example with made-up data, not your TV. <button type="button" id="exOff">Back to real</button></p>` : "") + (view === "week" ? weekView(days, watts, rate) : view === "month" ? monthView(days, watts, rate) : view === "insights" ? insightsView(days, watts, rate) : view === "speed" ? speedView(days) : dayView(days, watts, rate));
  document.getElementById("exOff")?.addEventListener("click", () => { example = false; load(true); });
  for (const b of tabs.querySelectorAll("button")) b.setAttribute("aria-current", b.dataset.view === view ? "page" : "false");
  for (const d of main.querySelectorAll("details[data-key]")) d.ontoggle = () => store.set("open-" + d.dataset.key, d.open);
  for (const b of main.querySelectorAll("[data-day]")) b.onclick = () => { if (b.dataset.day) { const side = b.getAttribute("aria-label"); go("day", b.dataset.day, true); main.querySelector(`[aria-label="${side}"]:not([disabled])`)?.focus(); } };
  for (const b of main.querySelectorAll("[data-period]")) b.onclick = () => { go("insights", b.dataset.period, true); main.querySelector(`[data-period="${b.dataset.period}"]`)?.focus(); };
  if (view === "day" && date && !days.some(d => d.date === date)) history.replaceState(null, "", "#day");
  if (moved) { document.getElementById("h")?.focus({ preventScroll: true }); window.scrollTo(0, 0); }
  topLine();
}

/** The quiet freshness line, tap to refresh: amber when the last day is late or offline, red when a refresh failed. */
function topLine() {
  if (signedOut) return;
  if (example) { fresh.className = "fresh"; fresh.textContent = "Example data"; return; }
  const f = freshness(data?.days ?? [], Date.now());
  fresh.className = "fresh";
  if (!navigator.onLine) {
    fresh.textContent = f ? `Offline · last summary arrived ${ago(f.age)} ago` : "Offline";
    fresh.classList.add("late");
  } else if (failed) {
    fresh.textContent = f ? `Refresh failed · ${ago(f.age)} old ↻` : "Refresh failed · tap to try again";
    fresh.classList.add("bad");
  } else if (!f) {
    fresh.textContent = data ? "No days yet" : "Loading…";
  } else {
    fresh.textContent = f.late ? `Last summary arrived ${ago(f.age)} ago ↻` : `Updated ${ago(f.age)} ago ↻`;
    if (f.late) fresh.classList.add("late");
  }
}

function signin(message) {
  signedOut = true;
  tabs.hidden = true;
  fresh.textContent = "";
  main.innerHTML = `<form class="signin" id="f"><h1 id="h" tabindex="-1">TV Insights</h1>
    <input type="password" name="p" placeholder="Password" aria-label="Password" autocomplete="current-password" required>
    <button class="primary">Sign in</button><p class="note ${message ? "bad" : ""}" role="status">${esc(message || "For this TV's household only.")}</p></form>`;
  const f = document.getElementById("f");
  f.p.focus();
  f.onsubmit = async e => {
    e.preventDefault();
    const btn = f.querySelector("button");
    btn.disabled = true;
    btn.textContent = "Checking…";
    const ctl = new AbortController(), timer = setTimeout(() => ctl.abort(), 10000);
    const r = await fetch("/api/login", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ password: f.p.value }), signal: ctl.signal }).catch(() => null);
    clearTimeout(timer);
    if (r?.ok) { signedOut = false; load(true); }
    else signin(!r ? "Couldn't reach the server. Check the connection and try again." : r.status === 401 ? "Wrong password." : `The server had a problem (${r.status}). Try again in a minute.`);
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
    if (example) return;   // looking at the example: a refresh doesn't take it away
    data = j;
    failed = false;
    if (!example) store.set("tv-days", data);
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

/** Three weeks of made-up days in the shape the TV sends, for the example. */
function exampleDays() {
  const hm = m => `${String(Math.floor(m / 60)).padStart(2, "0")}:${String(m % 60).padStart(2, "0")}`;
  const out = [], end = Date.parse(todayIST() + "T12:00:00Z");
  for (let i = 21; i >= 1; i--) {
    const date = new Date(end - i * 86400000).toISOString().slice(0, 10), k = 22 - i;
    const start = 1140 + (k * 17) % 60, a = 60 + (k * 13) % 70, b = 30 + (k * 7) % 50, c = 20 + (k * 5) % 30;
    out.push({ v: 1, date, received: new Date(end - (i - 1) * 86400000 - 7 * 3600000).toISOString(),
      on: { minutes: a + b + c + 15, spans: [[hm(start), hm(Math.min(1439, start + a + b + c + 15))]] },
      apps: [
        { pkg: "com.sonyliv", name: "SonyLIV", minutes: a, opens: 2, spans: [[hm(start + 5), hm(start + 4 + a)]] },
        { pkg: "com.google.android.youtube.tv", name: "YouTube", minutes: b, opens: 3, spans: [[hm(start + 6 + a), hm(start + 5 + a + b)]] },
        { pkg: "in.startv.hotstar", name: "JioHotstar", minutes: c, opens: 1, spans: [[hm(start + 7 + a + b), hm(Math.min(1439, start + 6 + a + b + c))]] },
        { pkg: "com.mitv.livetv", name: "Live TV", minutes: 15, opens: 1, spans: [[hm(start - 20), hm(start - 6)]] },
        { pkg: "com.google.android.apps.tv.dreamx", name: "Backdrop", minutes: 8 }],
      inputs: [{ id: "com.mediatek.tvinput/.hdmi.HDMIInputService/HW5", name: "Xbox", minutes: 10 + k % 5, opens: 1 }],
      home: { opens: 30 + k, median_ms: 40 + (k * 11) % 30, slow: k % 6 === 0 ? 1 : 0 }, feed: { asks: 40, fresh: 38, old: 2 }, tv: { boots: 0, mem_free_mb: 420 } });
  }
  return out;
}

tabs.onclick = e => { const v = e.target.closest("button")?.dataset.view; if (v) go(v); };
fresh.onclick = () => { fresh.textContent = "Refreshing…"; load(false); };
// ---- settings ----------------------------------------------------------------------------------------------------
const sheet = document.getElementById("settings"), energyForm = document.getElementById("energyForm");
function toast(text) {
  const t = document.getElementById("toast");
  t.textContent = text;
  t.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => (t.hidden = true), 4000);
}
document.getElementById("settingsBtn").onclick = () => {
  energyForm.rate.value = (data?.rate ?? 6.7).toFixed(2);
  energyForm.watts.value = data?.watts ?? 95;
  sheet.showModal();
};
energyForm.onsubmit = async e => {
  e.preventDefault();
  const body = { rate: energyForm.rate.value, watts: energyForm.watts.value };
  const r = await fetch("/api/settings", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body) }).catch(() => null);
  const j = r ? await r.json().catch(() => ({})) : {};
  if (!r?.ok) return toast(j.error || "Couldn't save: no connection. Try again.");
  if (data) { data.rate = j.rate; data.watts = j.watts; if (!example) store.set("tv-days", data); }
  sheet.close();
  draw(false);
  toast(`Saved: ₹${j.rate.toFixed(2)} a unit, ${j.watts} W.`);
};
document.getElementById("signOut").onclick = async () => {
  await fetch("/api/logout", { method: "POST" }).catch(() => {});
  try { localStorage.removeItem("tv-days"); } catch {}
  data = null;
  sheet.close();
  signin("Signed out of this phone.");
};
window.addEventListener("hashchange", () => { const r = route(); view = r.view; date = r.date; if (r.period) period = r.period; draw(true); });
window.addEventListener("online", () => load(false));
window.addEventListener("offline", topLine);
window.addEventListener("scroll", () => document.getElementById("top").classList.toggle("solid", scrollY > 4), { passive: true });
setInterval(topLine, 30000);
if ("serviceWorker" in navigator) navigator.serviceWorker.register("/sw.js").catch(() => {});
draw(false);
load(false);
