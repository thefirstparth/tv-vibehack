package com.gathin.tvtoday;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvshared.Fonts;
import com.gathin.tvshared.HomeLines;
import com.gathin.tvshared.HorizonBackground;
import com.gathin.tvshared.SunLine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Today app's screen (TodayActivity). Two modes:
 * Today (a big clock, the three cities' weather and a band turning through sport, the day in a minute and Money) and
 * Sport (the next event in big type, then this week, results and where you stand). Sport takes over from an hour before
 * any followed event until 30 minutes after it (the feed's windows, kept by the TV's own clock).
 *
 * The feed is the home screen's (HomeLines, with ?saver=1): fetched when the app opens, then every 15 minutes
 * while it is open (every minute while anything is live), never after it closes; its last good copy is shown with its
 * time when a request fails. The sport and weather are drawn by HomeLines' own rules. Nothing animates continuously:
 * a 1-second cross-fade every 25 seconds, and once a minute, with the clock, the content jumps to the next of nine
 * positions (burn-in); the Horizon light behind it is a still image.
 */
final class TodayScreen {

    private static final long MIN = HomeLines.MIN, STOP_MS = 25_000L, FADE_MS = 1000L;
    private static final long FETCH_MS = 15 * MIN, FETCH_LIVE_MS = MIN;
    private static final int UP = 0xFF6FD49A, DOWN = 0xFFFF9585, EVEN = 0x8CFFFFFF;
    private static final int TEXT = 0xEBFFFFFF, SOFT = 0xB3FFFFFF, DIM = 0x8CFFFFFF, FAINT = 0x57FFFFFF, KEY = 0x66FFFFFF;
    /** Burn-in: nine positions, as steps of SHIFT_PX (so within about 10 px end to end). */
    private static final int[][] SHIFTS = {{0, 0}, {1, -1}, {-1, 1}, {1, 1}, {-1, -1}, {0, 1}, {1, 0}, {0, -1}, {-1, 0}};
    private static final int SHIFT_PX = 5;
    /** Lines on a page of the day in a minute, and rows in a Sport stop. */
    private static final int PAGE = 5;

    enum Mode { TODAY, SPORT }

    private final Context ctx;
    private final Window window;
    private final float density;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService painter = Executors.newSingleThreadExecutor();
    private final HorizonBackground horizon = new HorizonBackground();
    private final List<HorizonBackground.Day> days;
    private final List<SunLine.Greeting> greetings;

    private final View content, today, sportMode, sportStop, minute1, minute2, money, week, results, standings;
    private final TextView sunLine, heroContext, sportAsOf;
    private final View hero;
    private final LinearLayout dots;
    private final HomeLines lines;
    private final HomeLines.SportViews heroViews;

    private Mode mode = Mode.TODAY;
    /** Set by Left/Right in the activity: the mode no longer follows the feed's windows. */
    private boolean manual;
    private boolean running;
    private int shift;
    private long nextFetchAt;

    /** The band's turn: the stops of the current mode, the one showing, and the dots' stops. */
    private final List<View> cycle = new ArrayList<>(), dotStops = new ArrayList<>();
    private int at = -1;
    private View showing;
    /** The event the Sport mode's headline shows (left out of This week). */
    private JSONObject heroItem;

    /** For screenshots only (the activity's launch extras): the light's time, mood and sky. */
    Calendar fakeAt;
    HorizonBackground.Mood fakeMood;
    HorizonBackground.Sky fakeSky;

    TodayScreen(Context ctx, Window window, View root) {
        this.ctx = ctx;
        this.window = window;
        this.density = ctx.getResources().getDisplayMetrics().density;
        String dayText = ctx.getString(com.gathin.tvshared.R.string.tv_days);
        days = HorizonBackground.Day.parse(dayText);
        greetings = SunLine.greetings(dayText);
        content = root.findViewById(R.id.content);
        today = root.findViewById(R.id.today);
        sportMode = root.findViewById(R.id.sportMode);
        sportStop = today.findViewById(R.id.sport);
        minute1 = root.findViewById(R.id.minute1);
        minute2 = root.findViewById(R.id.minute2);
        money = root.findViewById(R.id.money);
        week = root.findViewById(R.id.week);
        results = root.findViewById(R.id.results);
        standings = root.findViewById(R.id.standings);
        sunLine = root.findViewById(R.id.sunLine);
        hero = root.findViewById(R.id.hero);
        heroContext = root.findViewById(R.id.heroContext);
        sportAsOf = root.findViewById(R.id.sportAsOf);
        dots = root.findViewById(R.id.dots);
        // texts in a stop never overlap: a fade draws each text alone, with no off-screen buffer
        for (View v : new View[]{content, today, sportMode, sportStop, minute1, minute2, money, week, results, standings, hero, dots}) v.forceHasOverlappingRendering(false);

        String url = ctx.getString(com.gathin.tvshared.R.string.tv_feed_url).trim();
        HomeLines.Look look = new HomeLines.Look();
        look.url = url.isEmpty() ? "" : url + (url.contains("?") ? "&" : "?") + "saver=1";
        look.file = "today_lines.json";
        look.headSp = 58f;
        look.minHeadSp = 40f;
        look.cityLayout = R.layout.today_city;
        look.cityGap = 40;
        look.home = false;
        look.bright = TEXT;
        lines = new HomeLines(ctx, today, density, look);
        heroViews = lines.viewsIn(sportMode, 79f, 48f);
        lines.onChange(this::onFeed);
    }

    // ---- start and stop ------------------------------------------------------------------------------------------

    void start() {
        if (running) return;
        running = true;
        lines.tick();       // the saved copy, worded for now, while the request is out
        fetch.run();
        onFeed();
        scheduleMinute();
        handler.postDelayed(turn, STOP_MS);
    }

    void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
    }

    /** Left or Right: the other mode, and it stays until the app closes. */
    void switchMode() {
        manual = true;
        setMode(mode == Mode.TODAY ? Mode.SPORT : Mode.TODAY);
    }

    private final Runnable fetch = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            lines.refresh();
            long wait = soon() ? FETCH_LIVE_MS : FETCH_MS;
            nextFetchAt = System.currentTimeMillis() + wait;
            handler.removeCallbacks(this);
            handler.postDelayed(this, wait);
        }
    };

    private void scheduleMinute() {
        long now = System.currentTimeMillis();
        handler.postDelayed(minute, MIN - now % MIN + 200);
    }

    /** Once a minute, at the clock's tick: times worded again, the mode window, the light, and the burn-in jump. */
    private final Runnable minute = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            shift = (shift + 1) % SHIFTS.length;
            content.setTranslationX(SHIFTS[shift][0] * SHIFT_PX);
            content.setTranslationY(SHIFTS[shift][1] * SHIFT_PX);
            lines.tick();   // redraws, then onFeed
            scheduleMinute();
        }
    };

    /** Up or Down: the previous or next stop; the 25-second turn carries on from there. */
    void step(int dir) {
        if (!running || cycle.isEmpty()) return;
        show(Math.floorMod((at < 0 ? 0 : at) + dir, cycle.size()), true);
        handler.removeCallbacks(turn);
        handler.postDelayed(turn, STOP_MS);
    }

    /** Every 25 seconds: the next stop of the band, with a one-second cross-fade. */
    private final Runnable turn = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (!cycle.isEmpty()) show((at + 1) % cycle.size(), true);
            handler.postDelayed(this, STOP_MS);
        }
    };

    // ---- the feed ------------------------------------------------------------------------------------------------

    /** After each redraw of the lines (a new copy, or the minute): everything else follows. */
    private void onFeed() {
        if (!running) return;
        long now = System.currentTimeMillis();
        JSONObject f = lines.feed();
        // a live line now, or the site's old cached copy, and the next request more than a minute away: ask sooner
        if (soon() && nextFetchAt - now > FETCH_LIVE_MS + 5000) {
            handler.removeCallbacks(fetch);
            handler.postDelayed(fetch, FETCH_LIVE_MS);
            nextFetchAt = now + FETCH_LIVE_MS;
        }
        sunLine.setText(sunWords());
        fillMinute(f);
        fillMoney(f, now);
        JSONObject more = f == null ? null : f.optJSONObject("sport_more");
        fillHero(more, now);
        fillList(week, "This week", weekRows(more, now));
        fillList(results, "Results", resultRows(more));
        fillList(standings, "Where you stand", standRows(more));
        // the feed's state where the Sport mode can show it, as the home screen does under its weather
        String state = lines.neverAnswered() ? ctx.getString(com.gathin.tvshared.R.string.feed_not_answering) : lines.saved() && f != null ? "as of " + HomeLines.clock(lines.fetchedAt()) : null;
        sportAsOf.setText(state == null ? "" : state);
        sportAsOf.setVisibility(state == null ? View.GONE : View.VISIBLE);
        if (!manual) setMode(sportWanted(f, now) ? Mode.SPORT : Mode.TODAY);
        else rebuildCycle();
        updateHorizon();
    }

    /** Ask again in a minute: something is live, or the last answer was the site's old copy (a fresh one is on its way). */
    private boolean soon() {
        return anyLive() || lines.old();
    }

    /** Anything live now (believed: under ten minutes old) among the home screen's lines or the headline. */
    private boolean anyLive() {
        JSONObject f = lines.feed();
        if (f == null) return false;
        long now = System.currentTimeMillis();
        JSONArray s = f.optJSONArray("sport");
        for (int i = 0; s != null && i < s.length(); i++) if (lines.liveNow(s.optJSONObject(i), now)) return true;
        JSONObject more = f.optJSONObject("sport_more"), h = more == null ? null : more.optJSONObject("headline");
        return h != null && lines.liveNow(h, now);
    }

    /** Sport mode: inside one of the feed's windows by this TV's clock, or something live. */
    static boolean inWindow(JSONObject f, long now) {
        JSONArray w = f == null ? null : f.optJSONArray("sport_windows");
        if (w == null) return f != null && f.optBoolean("sport_mode");
        for (int i = 0; i < w.length(); i++) {
            JSONObject x = w.optJSONObject(i);
            try {
                if (x != null && Instant.parse(x.optString("from")).toEpochMilli() <= now && now <= Instant.parse(x.optString("to")).toEpochMilli()) return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private boolean sportWanted(JSONObject f, long now) {
        return f != null && (inWindow(f, now) || anyLive());
    }

    // ---- modes and the band --------------------------------------------------------------------------------------

    private void setMode(Mode m) {
        boolean changed = m != mode;
        mode = m;
        if (changed) {
            View in = m == Mode.SPORT ? sportMode : today, out = m == Mode.SPORT ? today : sportMode;
            fade(out, false);
            fade(in, true);
            at = -1;
            showing = null;
            for (View v : new View[]{sportStop, minute1, minute2, money, week, results, standings}) v.setAlpha(0f);
        }
        rebuildCycle();
        if (changed) updateHorizon();
    }

    /**
     * The stops for this mode, those with something to show. Today: sport, the day in a minute (a second page only past
     * five lines), Money; while a match is live, sport again after every other stop. Sport: this week, results, standings.
     */
    private void rebuildCycle() {
        List<View> base = new ArrayList<>();
        if (mode == Mode.TODAY) {
            if (lines.hasSport()) base.add(sportStop);
            if (((ViewGroup) minute1).getChildCount() > 0) base.add(minute1);
            if (((ViewGroup) minute2).getChildCount() > 0) base.add(minute2);
            if (((ViewGroup) money).getChildCount() > 0) base.add(money);
        } else {
            for (View v : new View[]{week, results, standings}) if (((ViewGroup) v).getChildCount() > 0) base.add(v);
        }
        List<View> c = new ArrayList<>();
        boolean live = mode == Mode.TODAY && anyLive() && base.contains(sportStop) && base.size() > 1;
        for (View v : base) {
            if (live && v == sportStop) continue;
            if (live) c.add(sportStop);
            c.add(v);
        }
        if (!live) c = base;
        boolean same = c.equals(cycle);
        cycle.clear();
        cycle.addAll(c);
        dotStops.clear();
        dotStops.addAll(base);
        if (!same || showing == null || !cycle.contains(showing)) {
            int keep = showing == null ? -1 : cycle.indexOf(showing);
            show(keep >= 0 ? keep : 0, showing != null && keep < 0);
        } else {
            at = cycle.indexOf(showing) >= 0 && cycle.get(at < 0 || at >= cycle.size() ? 0 : at) == showing ? at : cycle.indexOf(showing);
            paintDots();
        }
    }

    private void show(int index, boolean fadeOld) {
        if (cycle.isEmpty()) {
            if (showing != null) fade(showing, false);
            showing = null;
            at = -1;
            paintDots();
            return;
        }
        View next = cycle.get(index);
        if (next != showing) {
            if (showing != null) {
                if (fadeOld) fade(showing, false);
                else showing.setAlpha(0f);
            }
            fade(next, true);
        }
        showing = next;
        at = index;
        paintDots();
    }

    private void fade(View v, boolean in) {
        v.animate().cancel();
        if (in) {
            v.setVisibility(View.VISIBLE);
            v.animate().alpha(1f).setDuration(FADE_MS).withEndAction(null).start();
        } else {
            v.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
                if (v == today || v == sportMode) v.setVisibility(View.INVISIBLE);
            }).start();
        }
    }

    /** Small dots bottom right, one per stop (sport once, however often it comes back): the current one brighter. */
    private void paintDots() {
        int n = dotStops.size() > 1 ? dotStops.size() : 0;
        while (dots.getChildCount() < n) {
            View d = new View(ctx);
            d.setBackgroundResource(R.drawable.today_dot);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(5), dp(5));
            lp.setMarginStart(dp(7));
            dots.addView(d, lp);
        }
        while (dots.getChildCount() > n) dots.removeViewAt(dots.getChildCount() - 1);
        for (int i = 0; i < n; i++) dots.getChildAt(i).setAlpha(dotStops.get(i) == showing ? 0.70f : 0.18f);
    }

    // ---- Today: the day in a minute ------------------------------------------------------------------------------

    /**
     * Five lines a page, each with a short dash in its desk's colour; a line may wrap to two (never cut). The edition's
     * date and the page in the label. A page that would overflow the band gives its last lines to the next.
     */
    private void fillMinute(JSONObject f) {
        ((ViewGroup) minute1).removeAllViews();
        ((ViewGroup) minute2).removeAllViews();
        JSONObject m = f == null ? null : f.optJSONObject("minute");
        JSONArray ls = m == null ? null : m.optJSONArray("lines");
        if (ls == null || ls.length() == 0) return;
        boolean edition = "edition".equals(m.optString("kind"));
        int n = Math.min(ls.length(), 2 * PAGE);
        int first = Math.min(n, PAGE);
        int width = bandWidth(), room = dp(540 - 260 - 44) - dp(26);
        // what fits on the first page, at most five
        int used = 0, fit = 0;
        for (int i = 0; i < first; i++) {
            int h = lineHeight(ls.optJSONObject(i).optString("text"), width) + (i > 0 ? dp(10) : 0);
            if (used + h > room && fit > 0) break;
            used += h;
            fit++;
        }
        String date = edition ? editionDate(m.optString("date")) : null;
        page(minute1, ls, 0, fit, n, edition, date);
        if (n > fit) page(minute2, ls, fit, Math.min(n, fit + PAGE), n, edition, date);
    }

    private void page(View stop, JSONArray ls, int from, int to, int total, boolean edition, String date) {
        LinearLayout box = (LinearLayout) stop;
        String head = edition ? "The day in a minute" : "Headlines";
        String rest = edition ? join(date == null ? null : date + " edition", total > PAGE ? (from + 1) + "–" + to + " of " + total : null) : feedSource();
        box.addView(label(head, rest));
        for (int i = from; i < to; i++) {
            JSONObject l = ls.optJSONObject(i);
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.topMargin = dp(i == from ? 12 : 10);
            if (edition) {
                View dash = new View(ctx);
                int colour = 0x8CFFFFFF;
                try { colour = android.graphics.Color.parseColor(l.optString("colour", "#8CFFFFFF")); } catch (Exception ignored) { }
                dash.setBackgroundColor(colour);
                LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(dp(9), dp(2));
                dp.topMargin = dp(13);
                dp.setMarginEnd(dp(9));
                row.addView(dash, dp);
            }
            TextView t = text(20, 300, TEXT, false);
            t.setSingleLine(false);
            t.setLineSpacing(0, 1.12f);
            t.setText(l.optString("text"));
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            box.addView(row, rp);
        }
    }

    private String feedSource() {
        JSONObject f = lines.feed(), m = f == null ? null : f.optJSONObject("minute");
        return m == null ? null : m.optString("source", null);
    }

    private int lineHeight(String text, int width) {
        TextView t = text(20, 300, TEXT, false);
        t.setSingleLine(false);
        t.setLineSpacing(0, 1.12f);
        t.setText(text);
        t.measure(View.MeasureSpec.makeMeasureSpec(width - dp(18), View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        return t.getMeasuredHeight();
    }

    /** "17 Oct" from "2026-10-17". */
    private static String editionDate(String ymd) {
        String[] p = ymd.split("-");
        if (p.length != 3) return null;
        String[] mon = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        try {
            return Integer.parseInt(p[2]) + " " + mon[Integer.parseInt(p[1]) - 1];
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- Today: Money --------------------------------------------------------------------------------------------

    /**
     * A table: name, price, then the day, 30 days and a year as amount and percent. Green up, red down; the rupee's
     * row by the rupee (a dearer dollar is red). A figure the feed does not have is an empty cell.
     */
    private void fillMoney(JSONObject f, long now) {
        LinearLayout box = (LinearLayout) money;
        box.removeAllViews();
        JSONObject m = f == null ? null : f.optJSONObject("money");
        JSONArray rows = m == null ? null : m.optJSONArray("rows");
        if (rows == null || rows.length() == 0) return;
        String rest = m.optString("label", "");
        if (m.optBoolean("stale") && m.has("as_of")) {
            try { rest = join(rest, "as of " + HomeLines.clock(Instant.parse(m.optString("as_of")).toEpochMilli())); } catch (Exception ignored) { }
        }
        box.addView(label("Money", rest.isEmpty() ? null : rest));
        int n = rows.length();
        TextView[][] cells = new TextView[n + 1][5];
        String[] heads = {"", "", "1 day", "30 days", "1 year"};
        for (int c = 0; c < 5; c++) {
            cells[0][c] = text(12, 600, FAINT, true);
            cells[0][c].setText(heads[c]);
        }
        for (int r = 0; r < n; r++) {
            JSONObject row = rows.optJSONObject(r);
            String kind = row.optString("kind");
            boolean inverse = "usdinr".equals(kind);
            cells[r + 1][0] = text(12, 600, DIM, true);
            cells[r + 1][0].setText(row.optString("name"));
            cells[r + 1][1] = text(19, 300, TEXT, false);
            cells[r + 1][1].setFontFeatureSettings("tnum");
            cells[r + 1][1].setText(price(row.optDouble("price"), kind));
            String[] keys = {"d1", "d30", "y1"};
            for (int k = 0; k < 3; k++) {
                TextView t = text(15, 400, TEXT, false);
                t.setFontFeatureSettings("tnum");
                JSONObject mv = row.optJSONObject(keys[k]);
                if (mv != null && mv.has("amt") && mv.has("pct")) {
                    double amt = mv.optDouble("amt"), pct = mv.optDouble("pct");
                    boolean up = inverse ? amt < 0 : amt > 0, flat = Math.abs(amt) < 1e-9;
                    t.setTextColor(flat ? EVEN : up ? UP : DOWN);
                    SpannableStringBuilder b = new SpannableStringBuilder(signed(amt, kind));
                    int s = b.length();
                    b.append("  ").append(signedPct(pct));
                    b.setSpan(new RelativeSizeSpan(0.8f), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    b.setSpan(new ForegroundColorSpan(((flat ? EVEN : up ? UP : DOWN) & 0x00FFFFFF) | (0x9E << 24)), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    t.setText(b);
                }
                cells[r + 1][2 + k] = t;
            }
        }
        // columns as wide as their widest cell; the changes right-aligned, tightly spaced so all three fit
        int[] w = new int[5];
        for (TextView[] row : cells) for (int c = 0; c < 5; c++) w[c] = Math.max(w[c], (int) Math.ceil(measure(row[c])));
        int gapName = dp(30), gapCol = dp(34);
        for (int r = 0; r <= n; r++) {
            LinearLayout line = new LinearLayout(ctx);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            for (int c = 0; c < 5; c++) {
                TextView t = cells[r][c];
                t.setGravity(c >= 2 ? Gravity.END : Gravity.START);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w[c], ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMarginStart(c == 0 ? 0 : c == 1 ? gapName : gapCol);
                line.addView(t, lp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = r == 0 ? dp(12) : 0;
            line.setPadding(0, r == 0 ? 0 : dp(4), 0, dp(r == 0 ? 6 : 4));
            box.addView(line, lp);
            if (r < n) box.addView(rule(r == 0 ? 0x1FFFFFFF : 0x12FFFFFF, w, gapName, gapCol));
        }
    }

    private View rule(int colour, int[] w, int gapName, int gapCol) {
        View v = new View(ctx);
        v.setBackgroundColor(colour);
        int total = w[0] + gapName + w[1] + 3 * gapCol + w[2] + w[3] + w[4];
        v.setLayoutParams(new LinearLayout.LayoutParams(total, 1));
        return v;
    }

    /** Index and gold amounts in whole numbers, the rupee to two decimals; rupees in Indian grouping (₹1,46,430). */
    static String price(double v, String kind) {
        if ("usdinr".equals(kind)) return "₹" + String.format(Locale.ENGLISH, "%.2f", v);
        if ("gold".equals(kind)) return "₹" + indian(Math.round(v));
        return String.format(Locale.ENGLISH, "%,d", Math.round(v));
    }

    static String signed(double amt, String kind) {
        String sign = amt > 0 ? "+" : amt < 0 ? "−" : "";
        double a = Math.abs(amt);
        String n = "usdinr".equals(kind) ? String.format(Locale.ENGLISH, "%.2f", a) : "gold".equals(kind) ? indian(Math.round(a)) : String.format(Locale.ENGLISH, "%,d", Math.round(a));
        return sign + n;
    }

    static String signedPct(double pct) {
        return (pct > 0 ? "+" : pct < 0 ? "−" : "") + String.format(Locale.ENGLISH, "%.2f%%", Math.abs(pct));
    }

    /** 146430 as "1,46,430". */
    static String indian(long n) {
        String s = Long.toString(Math.abs(n));
        if (s.length() <= 3) return (n < 0 ? "-" : "") + s;
        String last = s.substring(s.length() - 3), head = s.substring(0, s.length() - 3);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < head.length(); i++) {
            if (i > 0 && (head.length() - i) % 2 == 0) b.append(',');
            b.append(head.charAt(i));
        }
        return (n < 0 ? "-" : "") + b + "," + last;
    }

    // ---- Sport: the headline -------------------------------------------------------------------------------------

    /**
     * The next event in big type (a live scoreboard once it starts; the latest result when nothing is coming), drawn by
     * the home screen's rules, and one line of context from the feed. From a saved copy, a passed start is dropped.
     */
    private void fillHero(JSONObject more, long now) {
        JSONObject h = more == null ? null : more.optJSONObject("headline");
        JSONObject item = null;
        String context = null;
        if (h != null && !lines.usable(new JSONArray().put(h), now).isEmpty()) {
            item = h;
            context = h.optString("context", null);
        } else {
            List<JSONObject> wk = lines.usable(more == null ? null : more.optJSONArray("week"), now);
            JSONArray res = more == null ? null : more.optJSONArray("results");
            item = !wk.isEmpty() ? wk.get(0) : res != null && res.length() > 0 ? res.optJSONObject(0) : null;
        }
        heroItem = item;
        hero.setVisibility(item == null ? View.INVISIBLE : View.VISIBLE);
        if (item == null) return;
        lines.paintHeadline(heroViews, item, bandWidth());
        heroContext.setText(context == null ? "" : context);
        heroContext.setVisibility(context == null || context.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ---- Sport: this week, results, where you stand --------------------------------------------------------------

    /** One row: when (caps), what (with its figures bolder), and the competition or a note. */
    private static final class Row {
        final String when;
        final CharSequence what;
        final String key;

        Row(String when, CharSequence what, String key) {
            this.when = when;
            this.what = what;
            this.key = key;
        }
    }

    private List<Row> weekRows(JSONObject more, long now) {
        List<Row> out = new ArrayList<>();
        for (JSONObject it : lines.usable(more == null ? null : more.optJSONArray("week"), now)) {
            if (out.size() >= PAGE) break;
            long start = HomeLines.startOf(it);
            if (start <= now || it == heroItem) continue;
            String when = it.optBoolean("tbc") ? HomeLines.dayWord(start, now) + ", time TBC" : HomeLines.timeLabel(start, now);
            boolean f1 = "f1".equals(it.optString("sp"));
            String what = f1 ? HomeLines.join(it.optString("session"), it.optString("gp")) : HomeLines.sides(it, it.optString("a"), it.optString("b"), false);
            String key = f1 ? it.optString("city", "F1") : it.optString("comp", "");
            out.add(new Row(when, what, key));
        }
        return out;
    }

    private List<Row> resultRows(JSONObject more) {
        List<Row> out = new ArrayList<>();
        JSONArray rs = more == null ? null : more.optJSONArray("results");
        for (int i = 0; rs != null && i < rs.length() && out.size() < PAGE; i++) {
            JSONObject it = rs.optJSONObject(i);
            String sp = it.optString("sp");
            String when = HomeLines.fmt("EEE d MMM", HomeLines.startOf(it));
            SpannableStringBuilder b = new SpannableStringBuilder();
            String key = it.optString("comp", "");
            if ("f1".equals(sp)) {
                JSONArray top = it.optJSONArray("top");
                b.append(it.optString("gp"));
                if (top != null && top.length() >= 3) bold(b.append(" "), top.optString(0) + " · " + top.optString(1) + " · " + top.optString(2), SOFT);
                key = "F1";
            } else if ("tennis".equals(sp) && it.has("winner")) {
                b.append(it.optString("winner")).append(" bt ").append(it.optString("loser"));
                if (it.has("sets")) bold(b.append(" "), it.optString("sets") + (it.optBoolean("retired") ? " ret." : ""), SOFT);
            } else if ("cricket".equals(sp)) {
                b.append(it.optString("a")).append(" v ").append(it.optString("b"));
                if (it.has("result")) bold(b.append(" "), it.optString("result"), FAINT);
            } else {
                String sa = it.optString("sa", ""), sb = it.optString("sb", "");
                b.append(it.optString("a"));
                if (!sa.isEmpty() && !sb.isEmpty()) bold(b.append(" "), sa + "–" + sb, SOFT).append(" ");
                else b.append(" v ");
                b.append(it.optString("b"));
            }
            out.add(new Row(when, b, key));
        }
        return out;
    }

    private List<Row> standRows(JSONObject more) {
        List<Row> out = new ArrayList<>();
        JSONArray st = more == null ? null : more.optJSONArray("standings");
        for (int i = 0; st != null && i < st.length() && out.size() < PAGE; i++) {
            JSONObject s = st.optJSONObject(i);
            JSONArray runs = s.optJSONArray("runs");
            SpannableStringBuilder b = new SpannableStringBuilder();
            for (int k = 0; runs != null && k < runs.length(); k++) {
                JSONObject r = runs.optJSONObject(k);
                if (r.optBoolean("b")) bold(b, r.optString("t"), SOFT);
                else b.append(r.optString("t"));
            }
            out.add(new Row(s.optString("label"), b, s.optString("note", "")));
        }
        return out;
    }

    private SpannableStringBuilder bold(SpannableStringBuilder b, String s, int colour) {
        int at = b.length();
        b.append(s);
        b.setSpan(new WeightSpan(Fonts.weight(ctx, 500)), at, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (colour != SOFT) b.setSpan(new ForegroundColorSpan(colour), at, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return b;
    }

    /** A stop's list: up to five rows in three lined-up columns; nothing to show, no stop. */
    private void fillList(View stop, String title, List<Row> rows) {
        LinearLayout box = (LinearLayout) stop;
        box.removeAllViews();
        if (rows.isEmpty()) return;
        box.addView(label(title, null));
        TextView[][] cells = new TextView[rows.size()][3];
        int w0 = 0, w1 = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            cells[i][0] = text(12, 600, DIM, true);
            cells[i][0].setText(r.when);
            cells[i][1] = text(18, 300, SOFT, false);
            cells[i][1].setText(r.what);
            cells[i][2] = text(13, 400, KEY, false);
            cells[i][2].setText(r.key);
            w0 = Math.max(w0, (int) Math.ceil(measure(cells[i][0])));
            w1 = Math.max(w1, (int) Math.ceil(measure(cells[i][1])));
        }
        int gap = dp(25), room = bandWidth();
        // a list wider than the band: the competition column goes first, then the times stay and the rest is cut nowhere
        boolean keys = w0 + gap + w1 + gap + dp(60) <= room;
        for (int i = 0; i < rows.size(); i++) {
            LinearLayout line = new LinearLayout(ctx);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setBaselineAligned(true);
            line.addView(cells[i][0], new LinearLayout.LayoutParams(w0, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.min(w1, room - w0 - gap), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(gap);
            line.addView(cells[i][1], lp);
            if (keys) {
                LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                kp.setMarginStart(gap);
                line.addView(cells[i][2], kp);
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.topMargin = dp(i == 0 ? 12 : 7);
            box.addView(line, rp);
        }
    }

    // ---- the light -----------------------------------------------------------------------------------------------

    /** Horizon v2 as on the home screen, its glow centred on the information: 62% of the height in Today, 44% in Sport. */
    private void updateHorizon() {
        Calendar now = fakeAt != null ? fakeAt : HorizonBackground.now();
        HorizonBackground.Sky sky = fakeSky != null ? fakeSky : lines.sky();
        HorizonBackground.State st = HorizonBackground.stateAt(now, sky, lines.liveAccent(), days);
        if (fakeMood != null) st = new HorizonBackground.State(fakeMood, st.season, st.sky, st.matchAccent, st.day);
        float centre = mode == Mode.SPORT ? 0.44f : 0.62f;
        if (!horizon.claim(st.key() + "|" + centre)) return;
        final HorizonBackground.State state = st;
        Resources res = ctx.getResources();
        painter.execute(() -> {
            BitmapDrawable image = HorizonBackground.draw(res, state, centre);
            handler.post(() -> window.setBackgroundDrawable(image));
        });
    }

    /** Under the date: the home screen's line above its clock ("MIDDAY · SUNSET 5:59 PM"), from the same helper. */
    private String sunWords() {
        Calendar now = fakeAt != null ? fakeAt : HorizonBackground.now();
        String line = SunLine.text(now, HorizonBackground.LAT, HorizonBackground.LON, greetings);
        return line == null ? "" : line;
    }

    // ---- small helpers -------------------------------------------------------------------------------------------

    /** A stop's label in small caps: its name, then (fainter) what it is of. */
    private TextView label(String head, String rest) {
        TextView t = text(13, 600, DIM, true);
        SpannableStringBuilder b = new SpannableStringBuilder(head);
        if (rest != null && !rest.isEmpty()) {
            b.append(" · ");
            int at = b.length();
            b.append(rest);
            b.setSpan(new ForegroundColorSpan(FAINT), at, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        t.setText(b);
        return t;
    }

    private TextView text(float sp, int weight, int colour, boolean caps) {
        TextView t = new TextView(ctx);
        t.setTypeface(Fonts.weight(ctx, weight));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(null);
        if (caps) {
            t.setAllCaps(true);
            t.setLetterSpacing(0.14f);
        }
        return t;
    }

    private static float measure(TextView t) {
        CharSequence s = t.getText();
        if (t.isAllCaps() || !(s instanceof Spanned)) return t.getPaint().measureText(t.isAllCaps() ? s.toString().toUpperCase(Locale.ENGLISH) : s.toString());
        t.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        return t.getMeasuredWidth();
    }

    private int bandWidth() {
        int w = ctx.getResources().getDisplayMetrics().widthPixels;
        return w - dp(124);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    private static String join(String a, String b) {
        return HomeLines.join(a, b);
    }

    /** A run of text in another of Outfit's weights (the figures in a row). */
    static final class WeightSpan extends MetricAffectingSpan {
        private final Typeface face;

        WeightSpan(Typeface face) {
            this.face = face;
        }

        @Override
        public void updateDrawState(TextPaint p) {
            p.setTypeface(face);
        }

        @Override
        public void updateMeasureState(TextPaint p) {
            p.setTypeface(face);
        }
    }
}
