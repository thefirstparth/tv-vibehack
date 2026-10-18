package com.gathin.tv1400;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvshared.Feed;
import com.gathin.tvshared.PersonalDays;
import com.gathin.tvshared.SunLine;
import com.gathin.tvshared.SunTimes;
import com.gathin.tvshared.Words;

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
 * The 1400 app's screen, in Horizon V3. One row of sections, named as tabs in the foot: Sport, The day in a minute and
 * Money (under a big clock and the three cities' weather), then This week, Results and Where you stand (under the next
 * event in big type). It opens on Sport; Left and Right go along the tabs, Up and Down turn the pages of a section that
 * has more than one (its tab says "2 / 3"). Nothing moves by itself.
 *
 * The feed is /api/live/tv?saver=1 (Feed, shared with the home screen, with its rules for what is still believable and
 * Words for how times and moments are said): fetched when the app opens, then every 15 minutes while it is open (every
 * minute while anything is live, or after the site's old cached copy), never once it closes. Nothing animates
 * continuously: a short cross-fade on a key press, and once a minute, with the clock, the content jumps to the next of
 * nine positions (burn-in). The halftone sun is a still image, drawn once per look.
 */
final class Screen {

    private static final long MIN = Feed.MIN, FADE_MS = 250L;
    private static final long FETCH_MS = 15 * MIN, FETCH_LIVE_MS = MIN;
    /** Burn-in: nine positions, as steps of SHIFT_PX (so within about 10 px end to end). */
    private static final int[][] SHIFTS = {{0, 0}, {1, -1}, {-1, 1}, {1, 1}, {-1, -1}, {0, 1}, {1, 0}, {0, -1}, {-1, 0}};
    private static final int SHIFT_PX = 5;
    /** Rows on a page of the day in a minute, and in a Sport list. */
    private static final int PAGE = 5;
    /** Every band ends above y 840 (420 dp): the halftone strip starts at 850. */
    private static final int BAND_END_DP = 420;

    enum Mode { TODAY, SPORT }

    /** A stop in the band: its view, and its tab in the foot (name and rule colour). */
    private static final class Stop {
        final LinearLayout view;
        final String tab;
        final int rule;

        Stop(LinearLayout view, String tab, int rule) {
            this.view = view;
            this.tab = tab;
            this.rule = rule;
        }
    }

    private final Context ctx;
    private final float density;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService painter = Executors.newSingleThreadExecutor();
    private final List<PersonalDays.Day> days;
    private final Feed feed;

    private final View content, today, sportMode;
    private final ImageView sun;
    private final TextView sunLine, todayState, sportState;
    private final LinearLayout cities, hero, tabs;
    private final FrameLayout todayBand, sportBand;
    private final Stop sportStop, money, week, results, standings;
    private final List<Stop> minutePages = new ArrayList<>();

    /** Which top shows: the section's (Today's clock and weather, or Sport's headline). */
    private Mode mode = Mode.TODAY;
    private boolean running;
    private int shift;
    private long nextFetchAt;
    private String sunKey;

    /** The pages with something to show, in the tabs' order (the day in a minute once a page), and the one showing. */
    private final List<Stop> order = new ArrayList<>();
    private Stop showing;
    /** The event the Sport mode's headline shows (left out of This week). */
    private JSONObject heroItem;

    /** For screenshots only (the activity's launch extras): the light's time, mood and sky. */
    Calendar fakeAt;
    SunLine.Phase fakeMood;
    Feed.Sky fakeSky;

    Screen(Context ctx, Window window, View root) {
        this.ctx = ctx;
        this.density = ctx.getResources().getDisplayMetrics().density;
        days = PersonalDays.parse(ctx.getString(com.gathin.tvshared.R.string.tv_days));
        feed = new Feed(ctx, Feed.address(ctx, "saver=1"), "screen_1400.json");
        content = root.findViewById(R.id.content);
        today = root.findViewById(R.id.today);
        sportMode = root.findViewById(R.id.sportMode);
        sun = root.findViewById(R.id.sun);
        sunLine = root.findViewById(R.id.sunLine);
        todayState = root.findViewById(R.id.todayState);
        sportState = root.findViewById(R.id.sportState);
        cities = root.findViewById(R.id.cities);
        hero = root.findViewById(R.id.hero);
        tabs = root.findViewById(R.id.tabs);
        todayBand = root.findViewById(R.id.todayBand);
        sportBand = root.findViewById(R.id.sportBand);
        sportStop = stop(todayBand, "Sport", Type.SPORT);
        money = stop(todayBand, "Money", Type.MONEY);
        week = stop(sportBand, "This week", Type.SPORT);
        results = stop(sportBand, "Results", Type.RULE);
        standings = stop(sportBand, "Where you stand", Type.RULE);
        // the Sport tab top right: an orange rule over orange caps
        TextView tab = root.findViewById(R.id.sportTab);
        tab.setBackground(new TopRule(Type.SPORT, dp(2)));
        // texts never overlap: a fade draws each text alone, with no off-screen buffer
        for (View v : new View[]{content, today, sportMode, hero, tabs}) v.forceHasOverlappingRendering(false);
    }

    private Stop stop(FrameLayout band, String tab, int rule) {
        LinearLayout v = new LinearLayout(ctx);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setAlpha(0f);
        v.forceHasOverlappingRendering(false);
        band.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new Stop(v, tab, rule);
    }

    // ---- start and stop ------------------------------------------------------------------------------------------

    void start() {
        if (running) return;
        running = true;
        fetch.run();
        onFeed();
        scheduleMinute();
    }

    void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
    }

    /** Left or Right: the previous or next section (its first page); at either end it stays. */
    void side(int dir) {
        if (!running || showing == null) return;
        show(order.get(sideOf(tabNames(), order.indexOf(showing), dir)));
    }

    /** Up or Down: the previous or next page of this section; past its first or last page it stays. */
    void page(int dir) {
        if (!running || showing == null) return;
        show(order.get(pageOf(tabNames(), order.indexOf(showing), dir)));
    }

    private List<String> tabNames() {
        List<String> n = new ArrayList<>();
        for (Stop s : order) n.add(s.tab);
        return n;
    }

    /** From page at of tabs (one name a page, a section's pages together): the first page of the section dir away, or at. */
    static int sideOf(List<String> tabs, int at, int dir) {
        int i = at;
        while (i >= 0 && i < tabs.size() && tabs.get(i).equals(tabs.get(at))) i += dir;
        if (i < 0 || i >= tabs.size()) return at;
        while (i > 0 && tabs.get(i - 1).equals(tabs.get(i))) i--;
        return i;
    }

    /** The page dir away within the same section, or at. */
    static int pageOf(List<String> tabs, int at, int dir) {
        int i = at + dir;
        return i >= 0 && i < tabs.size() && tabs.get(i).equals(tabs.get(at)) ? i : at;
    }

    /** "2 / 3" for a section with more than one page, else null. */
    static String pageMark(List<String> tabs, int at) {
        int first = at, n = 0;
        while (first > 0 && tabs.get(first - 1).equals(tabs.get(at))) first--;
        for (int i = first; i < tabs.size() && tabs.get(i).equals(tabs.get(at)); i++) n++;
        return n > 1 ? (at - first + 1) + " / " + n : null;
    }

    private final Runnable fetch = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            feed.refresh(Screen.this::onFeed);
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

    /** Once a minute, at the clock's tick: times worded again, the light, and the burn-in jump. */
    private final Runnable minute = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            shift = (shift + 1) % SHIFTS.length;
            content.setTranslationX(SHIFTS[shift][0] * SHIFT_PX);
            content.setTranslationY(SHIFTS[shift][1] * SHIFT_PX);
            onFeed();
            scheduleMinute();
        }
    };

    // ---- the feed ------------------------------------------------------------------------------------------------

    /** After each answer (or saved copy), and each minute: everything is drawn again from the copy in hand. */
    private void onFeed() {
        if (!running) return;
        long now = System.currentTimeMillis();
        JSONObject f = feed.value();
        // a live line now, or the site's old cached copy, and the next request more than a minute away: ask sooner
        if (soon() && nextFetchAt - now > FETCH_LIVE_MS + 5000) {
            handler.removeCallbacks(fetch);
            handler.postDelayed(fetch, FETCH_LIVE_MS);
            nextFetchAt = now + FETCH_LIVE_MS;
        }
        Calendar cal = fakeAt != null ? fakeAt : Calendar.getInstance();
        String line = SunLine.text(cal, SunTimes.LAT, SunTimes.LON, days);
        sunLine.setText(line == null ? "" : line);
        fillCities(f);
        // the feed's state, as the home screen says it under its weather
        String state = feed.neverAnswered() ? ctx.getString(com.gathin.tvshared.R.string.feed_not_answering) : feed.saved() && f != null ? "as of " + Words.clock(feed.fetchedAt()) : null;
        for (TextView t : new TextView[]{todayState, sportState}) {
            t.setText(state == null ? "" : state);
            t.setVisibility(state == null ? View.GONE : View.VISIBLE);
        }
        JSONObject more = f == null ? null : f.optJSONObject("sport_more");
        fillTodaySport(f, more, now);
        fillMinute(f);
        fillMoney(f);
        fillHero(more, now);
        fillList(week, "This week", "Next 5 · within 15 days", weekRows(more, now));
        fillList(results, "Results", "The latest of each · last 10 days", resultRows(more));
        fillList(standings, "Where you stand", null, standRows(more));
        rebuild();
        updateSun(cal);
    }

    private boolean soon() {
        return anyLive() || feed.old();
    }

    /** Anything live now (believed: under ten minutes old) among the home screen's lines or the headline. */
    private boolean anyLive() {
        JSONObject f = feed.value();
        if (f == null) return false;
        long now = System.currentTimeMillis();
        if (feed.liveItem(now) != null) return true;
        JSONObject more = f.optJSONObject("sport_more"), h = more == null ? null : more.optJSONObject("headline");
        return h != null && feed.liveNow(h, now);
    }

    // ---- modes, the band and the tabs ----------------------------------------------------------------------------

    private List<Stop> all() {
        List<Stop> a = new ArrayList<>(List.of(sportStop));
        a.addAll(minutePages);
        a.addAll(List.of(money, week, results, standings));
        return a;
    }

    /**
     * The pages with something to show, after each answer and each minute. The page showing stays; if it has emptied,
     * the nearest earlier page that has not; before anything has shown, Sport (or the first section there is).
     */
    private void rebuild() {
        List<Stop> a = all();
        order.clear();
        for (Stop s : a) if (s.view.getChildCount() > 0) order.add(s);
        Stop next = null;
        if (showing != null) for (int i = a.indexOf(showing); i >= 0 && next == null; i--) if (order.contains(a.get(i))) next = a.get(i);
        if (next == null && !order.isEmpty()) next = order.contains(sportStop) ? sportStop : order.get(0);
        show(next);
    }

    private static Mode modeOf(Stop s, Stop week, Stop results, Stop standings) {
        return s == week || s == results || s == standings ? Mode.SPORT : Mode.TODAY;
    }

    /** A page in its section's top (the tops cross-fade when the section changes), and the tabs. */
    private void show(Stop next) {
        if (next != showing) {
            Mode m = next == null ? mode : modeOf(next, week, results, standings);
            if (m != mode) {
                mode = m;
                fade(m == Mode.SPORT ? today : sportMode, false);
                fade(m == Mode.SPORT ? sportMode : today, true);
            }
            if (showing != null) fade(showing.view, false);
            if (next != null) fade(next.view, true);
            showing = next;
        }
        paintTabs();
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

    /**
     * The sections as the paper's tabs in the foot: caps under a rule in each section's colour (the day in a minute's
     * pages share one tab); the one showing in cream under a 6px rule, with "2 / 3" when it has pages, the others dimmed.
     */
    private void paintTabs() {
        List<String> names = new ArrayList<>();
        List<Integer> rules = new ArrayList<>();
        for (Stop s : order) if (!names.contains(s.tab)) { names.add(s.tab); rules.add(s.rule); }
        if (names.size() < 2) names.clear();
        String mark = showing == null ? null : pageMark(tabNames(), order.indexOf(showing));
        tabs.removeAllViews();
        for (int i = 0; i < names.size(); i++) {
            boolean on = showing != null && names.get(i).equals(showing.tab);
            TextView t = Type.text(ctx, Type.BOLD, 15, on ? Type.INK : Type.MUTED, true);
            t.setText(on && mark != null ? names.get(i) + "  " + mark : names.get(i));
            t.setPadding(0, dp(5), 0, 0);
            t.setBackground(new TopRule(rules.get(i), dp(on ? 3 : 2)));
            t.setAlpha(on ? 1f : 0.45f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.setMarginStart(dp(18));
            tabs.addView(t, lp);
        }
    }

    // ---- Today: the cities ---------------------------------------------------------------------------------------

    /** Three columns under 1px rules: city (Bengaluru in Close to Home's blue), temperature, word or warning, feels and high / low. */
    private void fillCities(JSONObject f) {
        cities.removeAllViews();
        JSONArray wx = f == null ? null : f.optJSONArray("weather");
        for (int i = 0; wx != null && i < wx.length() && i < 3; i++) {
            JSONObject c = wx.optJSONObject(i);
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(0, dp(7), 0, 0);
            col.setBackground(new TopRule(Type.RULE, 1));
            TextView city = Type.text(ctx, Type.BOLD, 15, i == 0 ? Type.HOME : Type.MUTED, true);
            city.setText(c.optString("city"));
            col.addView(city);
            if (c.has("now")) {
                TextView t = Type.text(ctx, Type.LIGHT, 76, Type.INK, false);
                t.setFontFeatureSettings("lnum");
                t.setText(c.optInt("now") + "°");
                col.addView(t, margins(0, 3, 0, 0));
            }
            String word = c.optString("word", "");
            if (!word.isEmpty()) {
                TextView w = Type.text(ctx, Type.SEMI, 16, c.optBoolean("warn") ? Type.DOWN : Type.INK2, true);
                w.setSingleLine(false);
                w.setText(word);
                col.addView(w, margins(0, 3, 0, 0));
            }
            List<String> bits = new ArrayList<>();
            if (c.has("feels")) bits.add("Feels " + c.optInt("feels"));
            if (c.has("hi") && c.has("lo")) bits.add(c.optInt("hi") + " / " + c.optInt("lo"));
            else if (c.has("hi")) bits.add("High " + c.optInt("hi"));
            if (!bits.isEmpty()) {
                TextView s = Type.figures(Type.text(ctx, Type.MEDIUM, 17, Type.MUTED, false));
                s.setText(String.join(" · ", bits));
                col.addView(s, margins(0, 3, 0, 0));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.setMarginStart(dp(28));
            cities.addView(col, lp);
        }
    }

    // ---- Today: sport ----------------------------------------------------------------------------------------------

    /** The home screen's list: the first event as a headline (with its context when it is the Sport mode's), then two rows. */
    private void fillTodaySport(JSONObject f, JSONObject more, long now) {
        LinearLayout box = sportStop.view;
        box.removeAllViews();
        List<JSONObject> items = feed.usable(f == null ? null : f.optJSONArray("sport"), now);
        if (items.isEmpty()) return;
        JSONObject h = items.get(0), mh = more == null ? null : more.optJSONObject("headline");
        box.addView(head("Sport", Type.SPORT, Type.SPORT, labelText(h, now, bandWidth() / 2)));
        box.addView(matchLine(h, now, 80, 52, bandWidth()), margins(0, 6, 0, 0));
        if (mh != null && mh.optString("start").equals(h.optString("start")) && mh.has("context")) {
            TextView c = Type.text(ctx, Type.CONTEXT, 26, Type.MUTED, false);
            c.setText(mh.optString("context"));
            box.addView(c, margins(0, 6, 0, 0));
        }
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < items.size() && rows.size() < 2; i++) {
            JSONObject it = items.get(i);
            boolean live = feed.liveNow(it, now);
            rows.add(new Row(live ? "Live " + it.optString("clock", "") : Words.when(feed, it, now), whatOf(it, now), keyOf(it), live ? Type.DOWN : Type.INK2, isF1(it)));
        }
        addRows(box, rows, dp(BAND_END_DP - 200));
    }

    // ---- Today: the day in a minute --------------------------------------------------------------------------------

    /**
     * Rows under 1px rules: the line's section in its desk's colour (or the headline's source), then the line in
     * Newsreader with its subject in bold. Up to five a page; a row that would cross y 840 starts the next page, never
     * shrunk or cut.
     */
    private void fillMinute(JSONObject f) {
        for (Stop p : minutePages) p.view.removeAllViews();
        JSONObject m = f == null ? null : f.optJSONObject("minute");
        JSONArray ls = m == null ? null : m.optJSONArray("lines");
        if (ls == null || ls.length() == 0) return;
        boolean edition = "edition".equals(m.optString("kind"));
        int width = bandWidth(), room = dp(BAND_END_DP - 200);
        // build every row once, then deal them out to pages
        List<View> rows = new ArrayList<>();
        for (int i = 0; i < ls.length(); i++) rows.add(dayRow(ls.optJSONObject(i), edition, width));
        int headH = measure(head("x", Type.INK, Type.INK, "x"), width);
        List<int[]> pages = new ArrayList<>();
        int from = 0;
        while (from < rows.size()) {
            int used = headH, to = from;
            while (to < rows.size() && to - from < PAGE) {
                int h = measure(rows.get(to), width);
                if (used + h > room && to > from) break;
                used += h;
                to++;
            }
            pages.add(new int[]{from, to});
            from = to;
        }
        String date = edition ? editionDate(m.optString("date")) : null;
        for (int p = 0; p < pages.size(); p++) {
            if (minutePages.size() <= p) minutePages.add(stop(todayBand, "The day in a minute", Type.INK));
            int[] r = pages.get(p);
            String kicker = edition ? Words.join(date == null ? null : date + " edition", rows.size() > PAGE || pages.size() > 1 ? (r[0] + 1) + "–" + r[1] + " of " + rows.size() : null)
                    : Words.join("Headlines", m.optString("source", null));
            LinearLayout box = minutePages.get(p).view;
            box.addView(head("The day in a minute", Type.INK, Type.INK, kicker));
            for (int i = r[0]; i < r[1]; i++) box.addView(rows.get(i));
        }
    }

    private View dayRow(JSONObject l, boolean edition, int width) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(true);
        row.setPadding(0, dp(8.5f), 0, dp(8.5f));
        row.setBackground(new TopRule(Type.RULE, 1));
        String tag = edition ? l.optString("section", "") : l.optString("source", "");
        TextView t = Type.text(ctx, Type.BOLD, 15, edition ? deskColour(l) : Type.MUTED, true);
        t.setText(tag);
        row.addView(t, new LinearLayout.LayoutParams(dp(170), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView line = Type.text(ctx, Type.TEXT, 33, Type.INK, false);
        line.setSingleLine(false);
        line.setLineSpacing(0, 1.12f);
        String text = l.optString("text"), subject = l.optString("subject", "");
        SpannableStringBuilder b = new SpannableStringBuilder(text);
        if (!subject.isEmpty() && text.startsWith(subject)) b.setSpan(new Face(Type.face(ctx, Type.TEXT_BOLD)), 0, subject.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        line.setText(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(dp(20));
        row.addView(line, lp);
        return row;
    }

    private static int deskColour(JSONObject l) {
        return Type.desk(l.optString("desk", null));
    }

    /** "17 October" from "2026-10-17". */
    static String editionDate(String ymd) {
        String[] p = ymd.split("-");
        if (p.length != 3) return null;
        String[] mon = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
        try {
            return Integer.parseInt(p[2]) + " " + mon[Integer.parseInt(p[1]) - 1];
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- Today: Money ----------------------------------------------------------------------------------------------

    /**
     * A table in Libre Franklin: name, price, then the day, 30 days and a year as amount and a smaller percent. Green up,
     * red down; the rupee's row by the rupee (a dearer dollar is red). A figure the feed does not have is an empty cell.
     */
    private void fillMoney(JSONObject f) {
        LinearLayout box = money.view;
        box.removeAllViews();
        JSONObject m = f == null ? null : f.optJSONObject("money");
        JSONArray rows = m == null ? null : m.optJSONArray("rows");
        if (rows == null || rows.length() == 0) return;
        String kicker = m.optString("label", "");
        if (m.optBoolean("stale") && m.has("as_of")) {
            try { kicker = Words.join(kicker, "as of " + Words.clock(Instant.parse(m.optString("as_of")).toEpochMilli())); } catch (Exception ignored) { }
        }
        box.addView(head("Money", Type.MONEY, Type.MONEY, kicker));
        String[] heads = {"", "Price", "1 day", "30 days", "1 year"};
        LinearLayout hr = moneyRow(false);
        for (int c = 0; c < 5; c++) {
            TextView t = Type.text(ctx, Type.BOLD, 14, Type.MUTED, true);
            t.setText(heads[c]);
            addCell(hr, t, c);
        }
        hr.setPadding(0, dp(3), 0, dp(4));
        box.addView(hr);
        for (int r = 0; r < rows.length(); r++) {
            JSONObject row = rows.optJSONObject(r);
            String kind = row.optString("kind");
            LinearLayout line = moneyRow(true);
            if (r == rows.length() - 1) line.setBackground(new TopRule(Type.RULE, 1, true));
            TextView name = Type.text(ctx, Type.BOLD, 16, Type.INK, true);
            name.setText(row.optString("name"));
            addCell(line, name, 0);
            TextView price = Type.figures(Type.text(ctx, Type.MEDIUM, 30, Type.INK, false));
            price.setText(price(row.optDouble("price"), kind));
            addCell(line, price, 1);
            String[] keys = {"d1", "d30", "y1"};
            for (int k = 0; k < 3; k++) {
                TextView t = Type.figures(Type.text(ctx, Type.SEMI, 25, Type.INK, false));
                JSONObject mv = row.optJSONObject(keys[k]);
                if (mv != null && mv.has("amt") && mv.has("pct")) {
                    double amt = mv.optDouble("amt"), pct = mv.optDouble("pct");
                    boolean up = "usdinr".equals(kind) ? amt < 0 : amt > 0, flat = Math.abs(amt) < 1e-9;
                    int colour = flat ? Type.MUTED : up ? Type.UP : Type.DOWN;
                    t.setTextColor(colour);
                    SpannableStringBuilder b = new SpannableStringBuilder(signed(amt, kind));
                    int s = b.length();
                    b.append(" ").append(signedPct(pct));
                    b.setSpan(new RelativeSizeSpan(18f / 25f), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    b.setSpan(new ForegroundColorSpan((colour & 0x00FFFFFF) | (0xBF << 24)), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    t.setText(b);
                }
                addCell(line, t, 2 + k);
            }
            box.addView(line);
        }
    }

    private LinearLayout moneyRow(boolean ruled) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setBaselineAligned(true);
        l.setPadding(0, dp(3), 0, dp(3));
        if (ruled) l.setBackground(new TopRule(Type.RULE, 1));
        return l;
    }

    /** The money table's columns: name 300px, price 260px, then three equal columns, 32px apart. */
    private void addCell(LinearLayout row, TextView t, int c) {
        t.setGravity(c == 0 ? Gravity.START : Gravity.END);
        LinearLayout.LayoutParams lp = c == 0 ? new LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.WRAP_CONTENT)
                : c == 1 ? new LinearLayout.LayoutParams(dp(130), ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (c > 0) lp.setMarginStart(dp(16));
        row.addView(t, lp);
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

    // ---- Sport: the headline ---------------------------------------------------------------------------------------

    /**
     * The next event in big type (a live scoreboard once it starts; the latest result when nothing is coming): its
     * label, the match in Newsreader with an italic "v", and the context line. From a saved copy, a passed start is
     * dropped.
     */
    private void fillHero(JSONObject more, long now) {
        hero.removeAllViews();
        JSONObject h = more == null ? null : more.optJSONObject("headline");
        JSONObject item;
        String context = null;
        if (h != null && !feed.usable(new JSONArray().put(h), now).isEmpty()) {
            item = h;
            context = h.optString("context", null);
        } else {
            List<JSONObject> wk = feed.usable(more == null ? null : more.optJSONArray("week"), now);
            JSONArray res = more == null ? null : more.optJSONArray("results");
            item = !wk.isEmpty() ? wk.get(0) : res != null && res.length() > 0 ? res.optJSONObject(0) : null;
        }
        heroItem = item;
        if (item == null) return;
        TextView label = Type.text(ctx, Type.BOLD, 20, Type.INK2, true);
        label.setText(labelText(item, now, bandWidth()));
        hero.addView(label);
        hero.addView(matchLine(item, now, 112, 72, bandWidth()), margins(0, 6, 0, 0));
        if (context != null && !context.isEmpty()) {
            TextView c = Type.text(ctx, Type.CONTEXT, 28, Type.MUTED, false);
            c.setText(context);
            hero.addView(c, margins(0, 7, 0, 0));
        }
    }

    /**
     * The label as Words says it, in caps: the competition and the countdown in sport orange ("La Liga · Tonight 12:30 am
     * · in 14h"), or "● Live 62' · La Liga" in red; the longest form that fits.
     */
    private CharSequence labelText(JSONObject it, long now, int room) {
        Words.Label l = Words.label(feed, it, now);
        TextPaint p = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        p.setTypeface(Type.face(ctx, Type.BOLD));
        p.setTextSize(sp(10));
        p.setLetterSpacing(0.09f);
        int pick = l.forms.length - 1;
        for (int i = 0; i < l.forms.length; i++) {
            String s = l.forms[i] + (l.countdown == null ? "" : " · " + l.countdown);
            if (!l.forms[i].isEmpty() && p.measureText(s.toUpperCase(Locale.ENGLISH)) <= room) { pick = i; break; }
        }
        String text = l.forms[pick], comp = l.comps[pick];
        SpannableStringBuilder b = new SpannableStringBuilder();
        if (l.kind == Words.LIVE) {
            b.append("● ").append(text);
            b.setSpan(new ForegroundColorSpan(Type.DOWN), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return b;
        }
        b.append(text);
        if (l.kind == Words.OFF) b.setSpan(new ForegroundColorSpan(Type.AMBER), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        else if (comp != null && !comp.isEmpty() && text.startsWith(comp)) b.setSpan(new ForegroundColorSpan(Type.SPORT), 0, comp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (l.countdown != null) {
            b.append(" · ");
            int s = b.length();
            b.append(l.countdown);
            b.setSpan(new ForegroundColorSpan(Type.SPORT), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return b;
    }

    /**
     * The match in big type: "Real Madrid v Sevilla" (an italic, muted, smaller "v"), "Real Madrid 2–1 Sevilla" (the
     * score in Libre Franklin), cricket "India 245/3 v Australia"; F1 in Titillium Web: "United States GP Austin" or the
     * top three. The longest names that fit, else smaller type (down to minPx), never cut.
     */
    private TextView matchLine(JSONObject it, long now, float px, float minPx, int room) {
        boolean f1 = isF1(it), scores = feed.scores(it, now);
        TextView t = Type.text(ctx, f1 ? Type.F1 : Type.DISPLAY, px, Type.INK, false);
        t.setLetterSpacing(f1 ? 0f : -0.02f);
        t.setFontFeatureSettings("lnum");
        List<CharSequence> forms = new ArrayList<>();
        if (f1) {
            JSONArray top = it.optJSONArray("top");
            if (scores && top != null && top.length() >= 3) forms.add(top.optString(0) + " · " + top.optString(1) + " · " + top.optString(2));
            else {
                SpannableStringBuilder b = new SpannableStringBuilder(it.optString("gp", ""));
                String city = it.optString("city", "");
                if (!city.isEmpty()) {
                    int s = b.length();
                    b.append("  ").append(city);
                    b.setSpan(new RelativeSizeSpan(0.5f), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    b.setSpan(new ForegroundColorSpan(Type.MUTED), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                forms.add(b);
                forms.add(it.optString("gp", ""));
            }
        } else {
            boolean cricket = "cricket".equals(it.optString("sp"));
            String sa = scores ? it.optString("sa", "") : "", sb = scores ? it.optString("sb", "") : "";
            for (String[] n : Words.nameForms(it)) {
                forms.add(sides(n[0], n[1], sa, sb, cricket, false));
                if (cricket && (!sa.isEmpty() || !sb.isEmpty())) forms.add(sides(n[0], n[1], Words.noOvers(sa), Words.noOvers(sb), true, false));
            }
        }
        TextPaint p = new TextPaint(t.getPaint());
        CharSequence pick = forms.get(forms.size() - 1);
        for (CharSequence f : forms) if (StaticLayout.getDesiredWidth(f, p) <= room) { pick = f; break; }
        float w = StaticLayout.getDesiredWidth(pick, p);
        if (w > room) t.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(minPx, px * room / w * 0.97f) / 2f);
        t.setText(pick);
        return t;
    }

    /** Two sides with an italic, muted "v" or the score between (scores in Libre Franklin; cricket's after each side). */
    private CharSequence sides(String a, String b, String sa, String sb, boolean cricket, boolean small) {
        SpannableStringBuilder s = new SpannableStringBuilder(a);
        if (b.isEmpty()) return s;
        if (cricket) {
            if (!sa.isEmpty()) score(s.append(" "), sa);
            v(s.append(" "));
            s.append(" ").append(b);
            if (!sb.isEmpty()) score(s.append(" "), sb);
        } else if (!sa.isEmpty() && !sb.isEmpty()) {
            score(s.append(" "), sa + "–" + sb);
            s.append(" ").append(b);
        } else {
            v(s.append(" "));
            s.append(" ").append(b);
        }
        return s;
    }

    private void v(SpannableStringBuilder s) {
        int at = s.length();
        s.append("v");
        s.setSpan(new Face(Type.face(ctx, Type.ITALIC)), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new RelativeSizeSpan(0.68f), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(Type.MUTED), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void score(SpannableStringBuilder s, String score) {
        int at = s.length();
        s.append(score);
        s.setSpan(new Face(Type.face(ctx, Type.SEMI)), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new TabularSpan(), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    // ---- Sport: this week, results, where you stand ------------------------------------------------------------------

    /** One row: when (caps), what (Newsreader, F1 in Titillium Web), and the competition or a note (caps, right). */
    private static final class Row {
        final String when;
        final CharSequence what;
        final String key;
        final int whenColour;
        final boolean f1;

        Row(String when, CharSequence what, String key, int whenColour, boolean f1) {
            this.when = when;
            this.what = what;
            this.key = key;
            this.whenColour = whenColour;
            this.f1 = f1;
        }
    }

    private static boolean isF1(JSONObject it) {
        return "f1".equals(it.optString("sp"));
    }

    private CharSequence whatOf(JSONObject it, long now) {
        if (isF1(it)) {
            JSONArray top = it.optJSONArray("top");
            if (feed.scores(it, now) && top != null && top.length() >= 3) return it.optString("session") + " " + top.optString(0) + " · " + top.optString(1) + " · " + top.optString(2);
            return Words.join(it.optString("gp"), it.optString("session"));
        }
        boolean sc = feed.scores(it, now);
        return sides(it.optString("a"), it.optString("b"), sc ? it.optString("sa", "") : "", sc ? it.optString("sb", "") : "", "cricket".equals(it.optString("sp")), true);
    }

    private static String keyOf(JSONObject it) {
        if (isF1(it)) return Words.join("F1", it.optString("city", ""));
        return it.optString("comp_short", it.optString("comp", ""));
    }

    private List<Row> weekRows(JSONObject more, long now) {
        List<Row> out = new ArrayList<>();
        for (JSONObject it : feed.usable(more == null ? null : more.optJSONArray("week"), now)) {
            if (out.size() >= PAGE) break;
            long start = Feed.startOf(it);
            if (start <= now || it == heroItem) continue;
            String when = it.optBoolean("tbc") ? Words.dayWord(start, now) + ", time TBC" : Words.timeLabel(start, now);
            out.add(new Row(when, whatOf(it, now), keyOf(it), Type.INK2, isF1(it)));
        }
        return out;
    }

    private List<Row> resultRows(JSONObject more) {
        List<Row> out = new ArrayList<>();
        JSONArray rs = more == null ? null : more.optJSONArray("results");
        for (int i = 0; rs != null && i < rs.length() && out.size() < PAGE; i++) {
            JSONObject it = rs.optJSONObject(i);
            String sp = it.optString("sp");
            String when = Words.fmt("EEE d MMM", Feed.startOf(it));
            SpannableStringBuilder b = new SpannableStringBuilder();
            String key = it.optString("comp_short", it.optString("comp", ""));
            if ("f1".equals(sp)) {
                JSONArray top = it.optJSONArray("top");
                b.append(it.optString("gp"));
                if (top != null && top.length() >= 3) bold(b.append(" "), top.optString(0) + " · " + top.optString(1) + " · " + top.optString(2), Type.INK);
                key = "F1";
            } else if ("tennis".equals(sp) && it.has("winner")) {
                b.append(it.optString("winner")).append(" bt ").append(it.optString("loser"));
                if (it.has("sets")) score(b.append(" "), it.optString("sets") + (it.optBoolean("retired") ? " ret." : ""));
            } else if ("cricket".equals(sp)) {
                b.append(it.optString("a"));
                v(b.append(" "));
                b.append(" ").append(it.optString("b"));
                if (it.has("result")) bold(b.append(" · "), it.optString("result"), Type.MUTED);
            } else {
                b.append(sides(it.optString("a"), it.optString("b"), it.optString("sa", ""), it.optString("sb", ""), false, true));
            }
            out.add(new Row(when, b, key, Type.INK2, "f1".equals(sp)));
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
                if (r.optBoolean("b")) bold(b, r.optString("t"), Type.INK);
                else b.append(r.optString("t"));
            }
            out.add(new Row(s.optString("label"), b, s.optString("note", ""), Type.INK2, "F1".equals(s.optString("label"))));
        }
        return out;
    }

    private void bold(SpannableStringBuilder b, String s, int colour) {
        int at = b.length();
        b.append(s);
        b.setSpan(new Face(Type.face(ctx, Type.TEXT_BOLD)), at, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (colour != Type.INK) b.setSpan(new ForegroundColorSpan(colour), at, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** A Sport list: its section head, then up to five rows that end above y 840; nothing to show, no stop. */
    private void fillList(Stop stop, String title, String kicker, List<Row> rows) {
        LinearLayout box = stop.view;
        box.removeAllViews();
        if (rows.isEmpty()) return;
        box.addView(head(title, Type.SPORT, Type.SPORT, kicker));
        addRows(box, rows, dp(BAND_END_DP - 235));
    }

    /** Rows in three columns (when 300px, what, competition 320px right-aligned, 40px apart) under 1px rules. */
    private void addRows(LinearLayout box, List<Row> rows, int room) {
        int width = bandWidth(), used = measure(box, width);
        for (Row r : rows) {
            LinearLayout line = new LinearLayout(ctx);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setBaselineAligned(true);
            line.setPadding(0, dp(5), 0, dp(5));
            line.setBackground(new TopRule(Type.RULE, 1));
            TextView when = Type.text(ctx, Type.BOLD, 16, r.whenColour, true);
            when.setText(r.when);
            line.addView(when, new LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView what = Type.text(ctx, r.f1 ? Type.F1 : Type.TEXT, r.f1 ? 30 : 32, Type.INK, false);
            what.setFontFeatureSettings("lnum");
            what.setText(r.what);
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            wp.setMarginStart(dp(20));
            line.addView(what, wp);
            TextView key = Type.text(ctx, Type.BOLD, 15, Type.MUTED, true);
            key.setGravity(Gravity.END);
            key.setText(r.key);
            // 320px wide, or as wide as a longer note needs (the middle column gives way)
            key.setMinWidth(dp(160));
            LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            kp.setMarginStart(dp(20));
            line.addView(key, kp);
            int h = measure(line, width);
            if (used + h > room && room > 0 && box.getChildCount() > 1) break;
            used += h;
            box.addView(line);
        }
    }

    // ---- a section head --------------------------------------------------------------------------------------------

    /** A 2px rule in the stop's colour, the name in Newsreader 600, and a kicker in muted caps on the right. */
    private View head(String name, int ruleColour, int nameColour, CharSequence kicker) {
        LinearLayout h = new LinearLayout(ctx);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setBaselineAligned(true);
        h.setPadding(0, dp(7), 0, dp(6));
        h.setBackground(new TopRule(ruleColour, dp(1)));
        TextView n = Type.text(ctx, Type.DISPLAY, 46, nameColour, false);
        n.setLetterSpacing(-0.015f);
        n.setText(name);
        h.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (kicker != null && kicker.length() > 0) {
            TextView k = Type.text(ctx, Type.BOLD, 16, Type.MUTED, true);
            k.setText(kicker);
            h.addView(k);
        }
        return h;
    }

    // ---- the sun ---------------------------------------------------------------------------------------------------

    /** The halftone strip for now: the mood's inks (a personal day's colours; greyer in rain), drawn when the look changes. */
    private void updateSun(Calendar cal) {
        SunLine.Phase phase = fakeMood != null ? fakeMood : SunLine.phaseAt(cal, SunTimes.LAT, SunTimes.LON);
        Feed.Sky sky = fakeSky != null ? fakeSky : feed.sky();
        PersonalDays.Day day = PersonalDays.on(cal, days);
        String key = phase + "|" + sky + "|" + (day == null ? "" : day.month + "-" + day.day);
        if (key.equals(sunKey)) return;
        sunKey = key;
        Halftone.Inks inks = Halftone.look(phase, sky, day);
        int w = ctx.getResources().getDisplayMetrics().widthPixels;
        painter.execute(() -> {
            Bitmap b = Halftone.draw(inks, w);
            handler.post(() -> sun.setImageBitmap(b));
        });
    }

    // ---- small helpers -----------------------------------------------------------------------------------------------

    private LinearLayout.LayoutParams margins(float l, float t, float r, float b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        return lp;
    }

    private static int measure(View v, int width) {
        v.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        return v.getMeasuredHeight();
    }

    private int bandWidth() {
        return ctx.getResources().getDisplayMetrics().widthPixels - dp(96);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, ctx.getResources().getDisplayMetrics());
    }

    /** A run of text in another of the paper's faces. */
    static final class Face extends MetricAffectingSpan {
        private final Typeface face;

        Face(Typeface face) {
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

    /** Figures in a run: tabular and lining. */
    static final class TabularSpan extends MetricAffectingSpan {
        @Override
        public void updateDrawState(TextPaint p) {
            p.setFontFeatureSettings("tnum, lnum");
        }

        @Override
        public void updateMeasureState(TextPaint p) {
            p.setFontFeatureSettings("tnum, lnum");
        }
    }
}
