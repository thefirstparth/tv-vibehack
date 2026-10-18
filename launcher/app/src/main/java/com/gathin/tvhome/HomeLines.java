package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.SuperscriptSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The home screen's sports and weather lines (design B3). One request to the feed each time the home screen appears
 * (TV on, Home, back from an app): never on a timer, never in the background. The last good answer is kept in a file;
 * when a request fails, that copy is shown with its time. Every minute, with the clock, the times are worded again from
 * what is already here (no network). The feed decides what to show; this class only words times and draws.
 */
final class HomeLines {

    private static final TimeZone IST = TimeZone.getTimeZone("Asia/Kolkata");
    private static final long MIN = 60_000L, HOUR = 60 * MIN, DAY = 24 * HOUR;
    /** A copy of a live line is believed for ten minutes after it was fetched, as on the site. */
    private static final long LIVE_FOR = 10 * MIN;
    private static final int TIMEOUT_MS = 5000;
    private static final int RED = 0xFFFF3B45, AMBER = 0xFFFFB347, DIM = 0x8CFFFFFF, WHITE = 0xFFFFFFFF, COMP = 0x42FFFFFF, ON_ACCENT = 0xFF170B05;
    /** The headline's size (design "Larger, centred"); a line with no shorter form left may shrink to 31 sp. */
    private static final float HEAD_SP = 44f;

    private final Activity act;
    private final String url;
    private final float density;
    private final File file;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final View lines, sport, weather;
    private final TextView label, hA, hScoreA, hV, hScoreB, hB, hLoc, weatherAsOf;
    private final View then1, then2;
    private final TextView then1Label, then1Text, then2Label, then2Text;
    private final LinearLayout weatherCols;
    private final Drawable dot;

    /** The competition's colour (res/values/config.xml, accent_colours): by competition, short form, then sport. */
    private final java.util.Map<String, Integer> accents = new java.util.HashMap<>();
    /** Where the lines go: centred between the top bar's bottom and the dock band's top (root coordinates). */
    private int frameTop = -1, frameBottom = -1;

    private JSONObject feed;     // the feed's value: { sport: [...], weather: [...] }
    private long fetchedAt;      // when that copy was fetched
    private boolean saved;       // showing the saved copy: the last request failed
    private boolean loading;
    /** An address is set, a request has failed and no good copy was ever received: say so instead of a blank. */
    private boolean neverAnswered;

    HomeLines(Activity act, float density) {
        this.act = act;
        this.density = density;
        this.url = act.getString(R.string.tv_feed_url).trim();
        this.file = new File(act.getFilesDir(), "home_lines.json");
        lines = act.findViewById(R.id.lines);
        sport = act.findViewById(R.id.sport);
        weather = act.findViewById(R.id.weather);
        label = act.findViewById(R.id.sportLabel);
        hA = act.findViewById(R.id.hA);
        hScoreA = act.findViewById(R.id.hScoreA);
        hV = act.findViewById(R.id.hV);
        hScoreB = act.findViewById(R.id.hScoreB);
        hB = act.findViewById(R.id.hB);
        hLoc = act.findViewById(R.id.hLoc);
        then1 = act.findViewById(R.id.then1);
        then2 = act.findViewById(R.id.then2);
        then1Label = act.findViewById(R.id.then1Label);
        then1Text = act.findViewById(R.id.then1Text);
        then2Label = act.findViewById(R.id.then2Label);
        then2Text = act.findViewById(R.id.then2Text);
        weatherAsOf = act.findViewById(R.id.weatherAsOf);
        weatherCols = act.findViewById(R.id.weatherCols);
        for (String pair : act.getResources().getStringArray(R.array.accent_colours)) {
            String[] kv = pair.split("\\|");
            if (kv.length == 2) accents.put(kv[0].trim().toLowerCase(Locale.ENGLISH), android.graphics.Color.parseColor(kv[1].trim()));
        }
        dot = act.getDrawable(R.drawable.live_dot);
        if (dot != null) dot.setBounds(0, 0, dot.getIntrinsicWidth(), dot.getIntrinsicHeight());
        // the lines' widths depend on the space left once laid out: word them again when that changes
        lines.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) v.post(this::render);
            place();
        });
        weather.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> { if (r - l != or - ol) v.post(this::render); });
    }

    /** The space to centre the lines in, from the activity's layout (the top bar and the dock band). */
    void frame(int top, int bottom) {
        frameTop = top;
        frameBottom = bottom;
        place();
    }

    private void place() {
        if (frameTop < 0 || lines.getHeight() == 0) return;
        lines.setTranslationY(frameTop + (frameBottom - frameTop - lines.getHeight()) / 2f - lines.getTop());
    }

    private Integer accentOf(JSONObject it) {
        for (String k : new String[]{it.optString("comp"), it.optString("comp_short"), it.optString("sp")}) {
            Integer c = k.isEmpty() ? null : accents.get(k.toLowerCase(Locale.ENGLISH));
            if (c != null) return c;
        }
        return null;
    }

    /** No feed address set (launcher/local.properties): the feature is off and nothing is ever requested. */
    boolean enabled() {
        return !url.isEmpty();
    }

    // ---- loading -------------------------------------------------------------------------------------------------

    /** Each time the home screen appears: one request, on a background thread. */
    void refresh() {
        if (!enabled() || loading) return;
        loading = true;
        io.execute(() -> {
            JSONObject value = null;
            long at = System.currentTimeMillis();
            boolean fromSaved = false;
            try {
                String body = get(url);
                JSONObject j = new JSONObject(body);
                value = j.optBoolean("ok") ? j.optJSONObject("value") : null;
                if (value == null) throw new IllegalStateException("no value");
                JSONObject keep = new JSONObject().put("at", at).put("value", value);
                try (FileOutputStream out = new FileOutputStream(file)) { out.write(keep.toString().getBytes(StandardCharsets.UTF_8)); }
            } catch (Exception e) {
                value = null;
                try {
                    JSONObject keep = new JSONObject(read(new FileInputStream(file)));
                    value = keep.optJSONObject("value");
                    at = keep.optLong("at", 0);
                    fromSaved = value != null;
                } catch (Exception ignored) {
                }
            }
            final JSONObject v = value;
            final long a = at;
            final boolean s = fromSaved;
            main.post(() -> {
                loading = false;
                feed = v;
                fetchedAt = a;
                saved = s;
                neverAnswered = v == null;
                render();
            });
        });
    }

    private static String get(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            return read(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws Exception {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = s.read(buf)) > 0 && out.size() < 256 * 1024) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    /** Every minute, with the clock: the times worded again from what is already here. */
    void tick() {
        render();
    }

    /** Out of the way while the grid below the dock is in use (it scrolls up into this space). */
    void setAway(boolean away) {
        if (!enabled()) return;
        lines.animate().alpha(away ? 0f : 1f).setDuration(away ? 120 : 200).start();
    }

    // ---- what to draw --------------------------------------------------------------------------------------------

    private void render() {
        if (!enabled() || feed == null) {
            // feature off, or the first request not back yet: nothing; never had an answer: one amber line
            if (enabled() && neverAnswered) {
                sport.setVisibility(View.GONE);
                weatherCols.setVisibility(View.GONE);
                weatherAsOf.setText(R.string.feed_not_answering);
                weatherAsOf.setVisibility(View.VISIBLE);
                weather.setVisibility(View.VISIBLE);
                lines.setVisibility(View.VISIBLE);
            } else {
                lines.setVisibility(View.GONE);
            }
            return;
        }
        long now = System.currentTimeMillis();
        List<JSONObject> items = usable(feed.optJSONArray("sport"), now);
        boolean hasSport = renderSport(items, now);
        boolean hasWeather = renderWeather(feed.optJSONArray("weather"));
        if (!hasWeather && saved) {
            // the saved copy's time still goes in its place under the weather, even with no weather to show
            weatherCols.setVisibility(View.GONE);
            weatherAsOf.setText("as of " + clock(fetchedAt));
            weatherAsOf.setVisibility(View.VISIBLE);
            hasWeather = true;
        }
        sport.setVisibility(hasSport ? View.VISIBLE : View.GONE);
        weather.setVisibility(hasWeather ? View.VISIBLE : View.GONE);
        lines.setVisibility(hasSport || hasWeather ? View.VISIBLE : View.GONE);
    }

    /** From a saved copy, a line whose start has passed is dropped, unless it is live and the copy under ten minutes old. */
    private List<JSONObject> usable(JSONArray list, long now) {
        List<JSONObject> out = new ArrayList<>();
        if (list == null) return out;
        for (int i = 0; i < list.length(); i++) {
            JSONObject it = list.optJSONObject(i);
            long start = startOf(it);
            if (it == null || start == 0) continue;
            if (saved && start <= now && !(isLive(it) && now - fetchedAt < LIVE_FOR)) continue;
            out.add(it);
        }
        return out;
    }

    private static long startOf(JSONObject it) {
        try {
            return it == null ? 0 : Instant.parse(it.optString("start")).toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean isLive(JSONObject it) {
        return "live".equals(it.optString("state"));
    }

    /** Live and believable now: said live by the feed, and that word is under ten minutes old. */
    private boolean liveNow(JSONObject it, long now) {
        return isLive(it) && now - fetchedAt < LIVE_FOR;
    }

    /** A start that has passed with no live word from the feed to go on: "Started" (no red dot, no score). */
    private boolean started(JSONObject it, long now) {
        String st = it.optString("state");
        if ("live".equals(st)) return !liveNow(it, now);
        return "next".equals(st) && startOf(it) <= now && !it.optBoolean("starting") && !it.optBoolean("late");
    }

    // ---- sport ---------------------------------------------------------------------------------------------------

    private boolean renderSport(List<JSONObject> items, long now) {
        if (items.isEmpty()) return false;
        int room = room();
        JSONObject h = items.get(0);
        headline(h, now, room);
        labelLine(h, now, room);
        TextView[][] rows = {{then1Label, then1Text}, {then2Label, then2Text}};
        View[] rowViews = {then1, then2};
        for (int i = 0; i < 2; i++) {
            JSONObject it = i + 1 < items.size() ? items.get(i + 1) : null;
            boolean shown = it != null && thenLine(it, i, rows[i][0], rows[i][1], now, room);
            rowViews[i].setVisibility(shown ? View.VISIBLE : View.GONE);
        }
        return true;
    }

    /** Width the sport side may use: what the weather side leaves, with a gap. */
    private int room() {
        int all = lines.getWidth();
        if (all <= 0) all = (int) (840 * density);
        int wx = weather.getVisibility() == View.VISIBLE ? weather.getWidth() : 0;
        return all - wx - (int) (40 * density);
    }

    // The label over the headline: what kind of moment this is, in caps
    private void labelLine(JSONObject it, long now, int room) {
        String sp = it.optString("sp");
        boolean f1 = "f1".equals(sp);
        String st = it.optString("state");
        int colour = DIM;
        boolean showDot = false;
        String lead, countdown = null;
        String[] comps = f1 ? new String[]{it.optString("session"), ""} : compForms(it);
        if (liveNow(it, now)) {
            colour = RED;
            showDot = true;
            String pause = it.optString("pause", "");
            String clock = it.optString("clock", "");
            lead = !pause.isEmpty() ? pauseWord(pause) : clock.isEmpty() ? "Live" : "Live " + clock;
        } else if (started(it, now)) {
            lead = "Started";
        } else if ("done".equals(st)) {
            lead = f1 ? "Result" : resultWord(it);
            if (f1 && it.optBoolean("provisional")) comps = new String[]{it.optString("session") + " · Provisional", ""};
        } else if ("off".equals(st)) {
            if (it.has("offWhy")) {
                lead = it.optString("offWhy");
                colour = AMBER;
            } else {
                lead = seenWord(it);
            }
        } else if (it.optBoolean("starting")) {
            lead = "Starting";
        } else if (it.optBoolean("late")) {
            lead = "tennis".equals(sp) ? "Not yet on court" : "Delayed";
        } else {
            // next: the competition first, then when ("LA LIGA · TONIGHT 12:30 AM   IN 17H")
            long start = startOf(it);
            String when = it.optBoolean("tbc") ? dayWord(start, now) + " · Time TBC" : timeLabel(start, now);
            countdown = it.optBoolean("tbc") ? null : hoursLeft(start, now);
            String[] forms = {join(comps[0], when), join(comps[1], when), when};
            String text = pickFits(label, forms, countdown, room);
            setLabel(text, countdown, colour, false, accentOf(it), text.equals(forms[0]) ? comps[0] : text.equals(forms[1]) ? comps[1] : null);
            return;
        }
        String[] forms = {join(lead, comps[0]), join(lead, comps[1]), lead};
        String text = pickFits(label, forms, null, room);
        setLabel(text, null, colour, showDot, accentOf(it), text.equals(forms[0]) ? comps[0] : text.equals(forms[1]) ? comps[1] : null);
    }

    /** The competition's name in its colour, and the countdown as a filled pill in it; the rest as the state says. */
    private void setLabel(String text, String countdown, int colour, boolean showDot, Integer accent, String comp) {
        SpannableStringBuilder b = new SpannableStringBuilder(text.toUpperCase(Locale.ENGLISH));
        if (accent != null && comp != null && !comp.isEmpty()) {
            int at = b.toString().indexOf(comp.toUpperCase(Locale.ENGLISH));
            if (at >= 0) b.setSpan(new ForegroundColorSpan(accent), at, at + comp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (countdown != null) {
            b.append("   ");
            int at = b.length();
            b.append(countdown.toUpperCase(Locale.ENGLISH));
            b.setSpan(accent != null ? new PillSpan(accent, ON_ACCENT, 6 * density) : new ForegroundColorSpan(WHITE), at, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        label.setText(b);
        label.setTextColor(colour);
        label.setCompoundDrawablesRelative(showDot ? dot : null, null, null, null);
    }

    // The big line: "Real Madrid v Sevilla", "Real Madrid 2–1 Sevilla", "United States GP  Austin", "VER · NOR · LEC"
    private void headline(JSONObject it, long now, int room) {
        boolean scores = (liveNow(it, now) || "done".equals(it.optString("state"))) && !started(it, now);
        hScoreA.setVisibility(View.GONE);
        hScoreB.setVisibility(View.GONE);
        hLoc.setVisibility(View.GONE);
        if ("f1".equals(it.optString("sp"))) {
            JSONArray top = it.optJSONArray("top");
            hV.setVisibility(View.GONE);
            hB.setVisibility(View.GONE);
            if (scores && top != null && top.length() >= 3) {
                hA.setText(top.optString(0) + " · " + top.optString(1) + " · " + top.optString(2));
            } else {
                hA.setText(it.optString("gp", ""));
                String city = it.optString("city", "");
                if (!city.isEmpty() && width(hA, hA.getText().toString()) + width(hLoc, city) + 14 * density <= room) {
                    hLoc.setText(city);
                    hLoc.setVisibility(View.VISIBLE);
                }
            }
            return;
        }
        String sp = it.optString("sp");
        boolean cricket = "cricket".equals(sp);
        String sa = scores ? it.optString("sa", "") : "", sb = scores ? it.optString("sb", "") : "";
        boolean oneScore = !cricket && !sa.isEmpty() && !sb.isEmpty();   // "2–1" between the names
        String[][] names = nameForms(it);
        setHeadSize(HEAD_SP);
        // try the full form first, then shorter names, then (cricket) scores without their overs; never cut a word
        String[] scoreForms = cricket ? new String[]{sa, noOvers(sa)} : new String[]{sa};
        String[] scoreFormsB = cricket ? new String[]{sb, noOvers(sb)} : new String[]{sb};
        for (int s = 0; s < scoreForms.length; s++) {
            for (String[] n : names) {
                String a = n[0], b = n[1];
                String scA = oneScore ? sa + "–" + sb : scoreForms[s], scB = oneScore ? "" : scoreFormsB[s];
                float w = width(hA, a) + (b.isEmpty() ? 0 : width(hB, b)) + (scA.isEmpty() ? 0 : width(hScoreA, scA) + 34 * density)
                        + (scB.isEmpty() ? 0 : width(hScoreB, scB) + 34 * density) + (oneScore || b.isEmpty() ? 0 : width(hV, "V") + 28 * density);
                boolean last = s == scoreForms.length - 1 && n == names[names.length - 1];
                // no shorter form left and still too wide: the names get smaller (down to 31 sp), never cut
                if (last && w > room) setHeadSize(Math.max(31f, HEAD_SP * room / w * 0.97f));
                if (w <= room || last) {
                    hA.setText(a);
                    hB.setText(b);
                    hB.setVisibility(b.isEmpty() ? View.GONE : View.VISIBLE);
                    hV.setVisibility(oneScore || b.isEmpty() ? View.GONE : View.VISIBLE);
                    if (!scA.isEmpty()) { hScoreA.setText(scA); hScoreA.setVisibility(View.VISIBLE); }
                    if (!scB.isEmpty()) { hScoreB.setText(scB); hScoreB.setVisibility(View.VISIBLE); }
                    // the score sits after its side's name ("India 245/3 v Australia"), the football one between them
                    LinearLayout row = (LinearLayout) hA.getParent();
                    row.removeView(hScoreA);
                    row.addView(hScoreA, row.indexOfChild(hA) + 1);
                    row.removeView(hScoreB);
                    row.addView(hScoreB, cricket ? row.indexOfChild(hB) + 1 : row.indexOfChild(hV) + 1);
                    return;
                }
            }
        }
    }

    private void setHeadSize(float sp) {
        for (TextView v : new TextView[]{hA, hB, hScoreA, hScoreB}) if (v.getTextSize() != sp * act.getResources().getDisplayMetrics().scaledDensity) v.setTextSize(sp);
    }

    private static String noOvers(String s) {
        return s.replaceAll("\\s*\\(.*\\)$", "");
    }

    // "THEN  Thu 12:30 am · Real Madrid v RB Leipzig   UCL"
    private boolean thenLine(JSONObject it, int index, TextView lbl, TextView txt, long now, int room) {
        boolean live = liveNow(it, now);
        String l = live ? "Also live" : index == 0 ? "Then" : "Later";
        lbl.setText(l.toUpperCase(Locale.ENGLISH));
        String st = it.optString("state");
        String when;
        if (live) when = null;
        else if (started(it, now)) when = "Started";
        else if ("done".equals(st)) when = "f1".equals(it.optString("sp")) ? "Result" : resultWord(it);
        else if ("off".equals(st)) when = it.has("offWhy") ? it.optString("offWhy") : seenWord(it);
        else if (it.optBoolean("starting")) when = "Starting";
        else if (it.optBoolean("late")) when = "tennis".equals(it.optString("sp")) ? "Not yet on court" : "Delayed";
        else {
            long start = startOf(it);
            String left = it.optBoolean("tbc") ? null : hoursLeft(start, now);
            when = it.optBoolean("tbc") ? dayWord(start, now) + ", time TBC" : timeLabel(start, now) + (left != null ? ", " + left : "");
        }
        boolean scores = (live || "done".equals(st)) && !started(it, now);
        List<String> whats = new ArrayList<>();
        if ("f1".equals(it.optString("sp"))) {
            JSONArray top = it.optJSONArray("top");
            whats.add(scores && top != null && top.length() >= 3 ? it.optString("session") + " " + top.optString(0) + " · " + top.optString(1) + " · " + top.optString(2) : it.optString("session"));
        } else {
            for (String[] n : nameForms(it)) whats.add(sides(it, n[0], n[1], scores));
        }
        String[] comps = "f1".equals(it.optString("sp")) ? new String[]{it.optString("gp", ""), ""} : compForms(it);
        float labelW = width(lbl, lbl.getText().toString()) + 10 * density;
        for (String c : new String[]{comps[0], comps[1], ""}) {
            for (String w : whats) {
                String main = when == null ? w : when + " · " + w;
                String full = c.isEmpty() ? main : main + "   " + c;
                if (labelW + width(txt, full) <= room) {
                    SpannableString s = new SpannableString(full);
                    if (!c.isEmpty()) s.setSpan(new ForegroundColorSpan(COMP), main.length(), full.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    txt.setText(s);
                    return true;
                }
            }
        }
        return false; // no form fits: the line is left out rather than cut
    }

    private static String sides(JSONObject it, String a, String b, boolean scores) {
        String sa = scores ? it.optString("sa", "") : "", sb = scores ? it.optString("sb", "") : "";
        if (b.isEmpty()) return a;
        if ("cricket".equals(it.optString("sp"))) return (a + (sa.isEmpty() ? "" : " " + sa)) + " v " + (b + (sb.isEmpty() ? "" : " " + sb));
        if (!sa.isEmpty() && !sb.isEmpty()) return a + " " + sa + "–" + sb + " " + b;
        return a + " v " + b;
    }

    /** Name pairs from longest to shortest: as sent, then each side's short form where the feed gives one. */
    private static String[][] nameForms(JSONObject it) {
        String a = it.optString("a", ""), b = it.optString("b", "");
        String as = it.optString("a_short", a), bs = it.optString("b_short", b);
        List<String[]> out = new ArrayList<>();
        out.add(new String[]{a, b});
        if (!as.equals(a)) out.add(new String[]{as, b});
        if (!bs.equals(b)) out.add(new String[]{a, bs});
        if (!as.equals(a) && !bs.equals(b)) out.add(new String[]{as, bs});
        return out.toArray(new String[0][]);
    }

    private static String[] compForms(JSONObject it) {
        String c = it.optString("comp", "");
        return new String[]{c, it.optString("comp_short", c)};
    }

    /** The result in the label: FULL TIME, FINAL, INDIA WON BY 14 RUNS, ALCARAZ WON. */
    private static String resultWord(JSONObject it) {
        if (it.has("result")) return it.optString("result");
        switch (it.optString("sp")) {
            case "nba": return "Final";
            case "football":
            case "intl": return "Full time";
            default: return "Result";
        }
    }

    /** A live line the feed lost sight of: when it was last seen live, from the feed's own time. */
    private static String seenWord(JSONObject it) {
        try {
            return "Last seen live " + clock(Instant.parse(it.optString("seen")).toEpochMilli());
        } catch (Exception e) {
            return "Not live";
        }
    }

    private static String pauseWord(String p) {
        return "HT".equalsIgnoreCase(p) ? "Half-time" : p;
    }

    private static String join(String a, String b) {
        return a == null || a.isEmpty() ? b : b == null || b.isEmpty() ? a : a + " · " + b;
    }

    /** The first form that fits the room (the label's countdown counted in); the last one if none does. */
    private String pickFits(TextView v, String[] forms, String extra, int room) {
        float more = extra == null ? 0 : width(v, "   " + extra.toUpperCase(Locale.ENGLISH)) + 12 * density;
        for (String f : forms) if (!f.isEmpty() && width(v, f.toUpperCase(Locale.ENGLISH)) + more <= room) return f;
        return forms[forms.length - 1];
    }

    private static float width(TextView v, String s) {
        return v.getPaint().measureText(s);
    }

    /** A filled, rounded pill behind a run of text, drawn in one go (no extra view, nothing animated). */
    private static final class PillSpan extends android.text.style.ReplacementSpan {
        private final int fill, ink;
        private final float pad;
        private final android.graphics.RectF r = new android.graphics.RectF();

        PillSpan(int fill, int ink, float pad) {
            this.fill = fill;
            this.ink = ink;
            this.pad = pad;
        }

        @Override
        public int getSize(android.graphics.Paint p, CharSequence t, int start, int end, android.graphics.Paint.FontMetricsInt fm) {
            return Math.round(p.measureText(t, start, end) + 2 * pad);
        }

        @Override
        public void draw(android.graphics.Canvas c, CharSequence t, int start, int end, float x, int top, int y, int bottom, android.graphics.Paint p) {
            int was = p.getColor();
            android.graphics.Paint.FontMetrics m = p.getFontMetrics();
            r.set(x, y + m.ascent - pad * 0.35f, x + p.measureText(t, start, end) + 2 * pad, y + m.descent + pad * 0.35f);
            p.setColor(fill);
            c.drawRoundRect(r, r.height() / 2, r.height() / 2, p);
            p.setColor(ink);
            c.drawText(t, start, end, x + pad, y, p);
            p.setColor(was);
        }
    }

    // ---- times, in IST -------------------------------------------------------------------------------------------

    private static long istDay(long ms) {
        return Math.floorDiv(ms + IST.getOffset(ms), DAY);
    }

    private static int istHour(long ms) {
        return (int) (Math.floorMod(ms + IST.getOffset(ms), DAY) / HOUR);
    }

    private static String fmt(String pattern, long ms) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.ENGLISH);
        f.setTimeZone(IST);
        return f.format(new Date(ms));
    }

    /** "12:30 am": 12-hour, lowercase am/pm, always the minutes. */
    static String clock(long ms) {
        return fmt("h:mm a", ms).replace("AM", "am").replace("PM", "pm");
    }

    /**
     * Within a day: "Today 12:30 pm", "Tonight 12:30 am" (a start after midnight counts as tonight), "Tomorrow 7:30 am".
     * One to six days: "Thu 12:30 am". Seven to fifteen: "Sat 31 Oct, 1:30 am".
     */
    static String timeLabel(long start, long now) {
        long d = istDay(start) - istDay(now);
        if (start > now && start - now <= DAY) {
            String w = istHour(start) < 5 && (d == 1 || istHour(now) < 5) ? "Tonight" : d == 0 ? "Today" : "Tomorrow";
            return w + " " + clock(start);
        }
        if (d <= 6) return fmt("EEE", start) + " " + clock(start);
        return fmt("EEE d MMM", start) + ", " + clock(start);
    }

    /** The day alone, for a time not yet announced. */
    static String dayWord(long start, long now) {
        long d = istDay(start) - istDay(now);
        return d == 0 ? "Today" : d == 1 ? "Tomorrow" : d <= 6 ? fmt("EEE", start) : fmt("EEE d MMM", start);
    }

    /** "in 17h", "in under 1h" (whole hours, rounded down), within a day only. */
    static String hoursLeft(long start, long now) {
        long left = start - now;
        if (left <= 0 || left > DAY) return null;
        return left < HOUR ? "in under 1h" : "in " + (left / HOUR) + "h";
    }

    // ---- weather -------------------------------------------------------------------------------------------------

    private boolean renderWeather(JSONArray list) {
        if (list == null || list.length() == 0) return false;
        weatherCols.setVisibility(View.VISIBLE);
        LayoutInflater inf = act.getLayoutInflater();
        while (weatherCols.getChildCount() < list.length()) inf.inflate(R.layout.item_city, weatherCols, true);
        while (weatherCols.getChildCount() > list.length()) weatherCols.removeViewAt(weatherCols.getChildCount() - 1);
        String asOf = null;
        int widest = 0;
        for (int i = 0; i < list.length(); i++) {
            JSONObject c = list.optJSONObject(i);
            View col = weatherCols.getChildAt(i);
            ((ViewGroup.MarginLayoutParams) col.getLayoutParams()).setMarginStart(i == 0 ? 0 : (int) (42 * density));
            ((TextView) col.findViewById(R.id.city)).setText(c.optString("city").toUpperCase(Locale.ENGLISH));
            TextView temp = col.findViewById(R.id.temp), feels = col.findViewById(R.id.feels), hilo = col.findViewById(R.id.hilo), word = col.findViewById(R.id.word);
            temp.setText(c.has("now") ? degrees(c.optInt("now")) : "");
            temp.setVisibility(c.has("now") ? View.VISIBLE : View.GONE);
            feels.setText(c.has("feels") ? "Feels " + c.optInt("feels") + "°" : "");
            feels.setVisibility(c.has("feels") ? View.VISIBLE : View.GONE);
            String hl = c.has("hi") && c.has("lo") ? "H " + c.optInt("hi") + "° · L " + c.optInt("lo") + "°" : c.has("hi") ? "H " + c.optInt("hi") + "°" : c.has("lo") ? "L " + c.optInt("lo") + "°" : "";
            hilo.setText(hl);
            hilo.setVisibility(hl.isEmpty() ? View.GONE : View.VISIBLE);
            word.setVisibility(View.GONE);
            if (c.has("as_of")) asOf = c.optString("as_of");
            col.setMinimumWidth(0);
            col.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            widest = Math.max(widest, col.getMeasuredWidth());
        }
        // three equal columns; a word wider than its column steps down to its short form ("Orange alert")
        for (int i = 0; i < list.length(); i++) {
            JSONObject c = list.optJSONObject(i);
            View col = weatherCols.getChildAt(i);
            col.setMinimumWidth(widest);
            TextView word = col.findViewById(R.id.word);
            String w = c.optString("word", "");
            if (w.isEmpty()) continue;
            String ws = c.optString("word_short", w);
            float limit = widest + 36 * density;
            int cut = w.indexOf(" · ");
            word.setSingleLine(false);
            word.setMaxLines(2);
            if (width(word, w) <= limit || ws.equals(w)) word.setText(w);
            else if (cut > 0 && width(word, w.substring(0, cut)) <= limit && width(word, w.substring(cut + 3)) <= limit) word.setText(w.substring(0, cut) + "\n" + w.substring(cut + 3));
            else word.setText(ws);
            word.setVisibility(View.VISIBLE);
        }
        String note = saved ? "as of " + clock(fetchedAt) : asOf != null ? asOfClock(asOf) : null;
        weatherAsOf.setText(note == null ? "" : note);
        weatherAsOf.setVisibility(note == null ? View.GONE : View.VISIBLE);
        return true;
    }

    private static String asOfClock(String iso) {
        try {
            return "as of " + clock(Instant.parse(iso).toEpochMilli());
        } catch (Exception e) {
            return null;
        }
    }

    /** "21°" with a small, raised degree mark. */
    private static CharSequence degrees(int t) {
        SpannableString s = new SpannableString(t + "°");
        int at = s.length() - 1;
        s.setSpan(new RelativeSizeSpan(0.5f), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new SuperscriptSpan(), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(DIM), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }
}
