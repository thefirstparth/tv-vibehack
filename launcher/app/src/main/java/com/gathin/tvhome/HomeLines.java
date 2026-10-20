package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.drawable.Drawable;
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

import com.gathin.tvshared.Feed;
import com.gathin.tvshared.Words;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The home screen's sports and weather lines (design B3). One request to the feed each time the home screen appears
 * (TV on, Home, back from an app): never on a timer, never in the background. The last good answer is kept in a file;
 * when a request fails, that copy is shown with its time (the feed and its rules: Feed, shared with the 1400 app). Every
 * minute, with the clock, the times are worded again from what is already here (no network), as Words says them. The
 * feed decides what to show; this class only draws.
 */
final class HomeLines {

    private static final int RED = 0xFFFF3B45, AMBER = 0xFFFFB347, DIM = 0x8CFFFFFF, WHITE = 0xFFFFFFFF;
    /** The weather highlights (mockup option 5): the warmest and coolest temperature tinted, the extremes lit. */
    private static final int WARM_TINT = 0xFFFFDCBF, COOL_TINT = 0xFFCFE2FF, LIT = 0xE0FFFFFF;
    /**
     * The side Parth doesn't follow (the feed's "ours" names the other): 65% white in the headline; in the THEN/LATER
     * rows, already 70% white, 65% of that (the lines' own 45% dim), so the difference shows there too.
     */
    private static final int HEAD_OTHER = 0xA6FFFFFF, ROW_OTHER = 0x73FFFFFF;
    /** The headline's size (design "Larger, centred"); a line with no shorter form left may shrink to 31 sp. */
    private static final float HEAD_SP = 44f;

    private final Activity act;
    private final Feed data;
    private final float density;

    private final View lines, sport, weather;
    private final TextView label, hA, hScoreA, hV, hScoreB, hB, hLoc, weatherAsOf;
    private final View then1, then2;
    /** THEN and LATER rows: label, time, match, competition (columns lined up across the two rows). */
    private final TextView[][] thenCells;
    private final LinearLayout weatherCols;
    /** Which cities to pick out, worked out once per weather list from the feed (not on every redraw). */
    private JSONArray extremesFor;
    private WeatherExtremes extremes = WeatherExtremes.NONE;
    private final Drawable dot;

    /** The competition's colour (res/values/config.xml, accent_colours): by competition, short form, then sport. */
    private final java.util.Map<String, Integer> accents = new java.util.HashMap<>();
    /** Where the lines go: centred between the top bar's bottom and the dock band's top (root coordinates). */
    private int frameTop = -1, frameBottom = -1;


    HomeLines(Activity act, float density) {
        this.act = act;
        this.density = density;
        this.data = new Feed(act, Feed.address(act, null), "home_lines.json");
        lines = act.findViewById(R.id.lines);
        // its texts never overlap: fading the block (setAway) fades each text alone, with no off-screen buffer
        lines.forceHasOverlappingRendering(false);
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
        thenCells = new TextView[][]{
                {act.findViewById(R.id.then1Label), act.findViewById(R.id.then1Time), act.findViewById(R.id.then1Match), act.findViewById(R.id.then1Comp)},
                {act.findViewById(R.id.then2Label), act.findViewById(R.id.then2Time), act.findViewById(R.id.then2Match), act.findViewById(R.id.then2Comp)}};
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
        weather.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) v.post(this::render);
            alignBaselines();
        });
        sport.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> alignBaselines());
    }

    private Runnable changed;

    /** Called after each redraw of the lines (the Horizon light follows the weather word and a live match). */
    void onChange(Runnable r) {
        changed = r;
    }

    /** Bengaluru's sky for the Horizon light, from the feed's weather word or warning (clear without a feed). */
    HorizonBackground.Sky sky() {
        return HorizonBackground.Sky.valueOf(data.sky().name());
    }

    /** The competition's colour of a match live now (believed: under ten minutes old), or null. */
    Integer liveAccent() {
        JSONObject it = data.liveItem(System.currentTimeMillis());
        return it == null ? null : accentOf(it);
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
        alignBaselines();
    }

    /** The headline sits on the same baseline as the big temperatures: the weather side moves to meet it. */
    private void alignBaselines() {
        View temp = weatherCols.getChildCount() > 0 ? weatherCols.getChildAt(0).findViewById(R.id.temp) : null;
        boolean both = sport.getVisibility() == View.VISIBLE && weatherCols.getVisibility() == View.VISIBLE && temp != null && temp.getVisibility() == View.VISIBLE;
        weather.setTranslationY(both ? baselineIn(hA) - baselineIn(temp) : 0);
    }

    private int baselineIn(View v) {
        int y = v.getBaseline();
        for (View p = v; p != lines && p != null; p = (View) p.getParent()) y += p.getTop();
        return y;
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
        return data.enabled();
    }

    // ---- loading -------------------------------------------------------------------------------------------------

    /** Each time the home screen appears: one request, on a background thread. */
    void refresh() {
        data.refresh(this::render);
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
        JSONObject feed = data.value();
        if (!enabled() || feed == null) {
            // feature off, or the first request not back yet: nothing; never had an answer: one amber line
            if (enabled() && data.neverAnswered()) {
                sport.setVisibility(View.GONE);
                weatherCols.setVisibility(View.GONE);
                weatherAsOf.setText(com.gathin.tvshared.R.string.feed_not_answering);
                weatherAsOf.setVisibility(View.VISIBLE);
                weather.setVisibility(View.VISIBLE);
                lines.setVisibility(View.VISIBLE);
            } else {
                lines.setVisibility(View.GONE);
            }
            return;
        }
        long now = System.currentTimeMillis();
        List<JSONObject> items = data.usable(feed.optJSONArray("sport"), now);
        boolean hasSport = renderSport(items, now);
        boolean hasWeather = renderWeather(feed.optJSONArray("weather"));
        if (!hasWeather && data.saved()) {
            // the saved copy's time still goes in its place under the weather, even with no weather to show
            weatherCols.setVisibility(View.GONE);
            weatherAsOf.setText("as of " + Words.clock(data.fetchedAt()));
            weatherAsOf.setVisibility(View.VISIBLE);
            hasWeather = true;
        }
        sport.setVisibility(hasSport ? View.VISIBLE : View.GONE);
        weather.setVisibility(hasWeather ? View.VISIBLE : View.GONE);
        lines.setVisibility(hasSport || hasWeather ? View.VISIBLE : View.GONE);
        if (changed != null) changed.run();
    }

    // ---- sport ---------------------------------------------------------------------------------------------------

    private boolean renderSport(List<JSONObject> items, long now) {
        if (items.isEmpty()) return false;
        int room = room();
        JSONObject h = items.get(0);
        headline(h, now, room);
        labelLine(h, now, room);
        View[] rowViews = {then1, then2};
        boolean[] shown = new boolean[2];
        for (int i = 0; i < 2; i++) {
            JSONObject it = i + 1 < items.size() ? items.get(i + 1) : null;
            shown[i] = it != null && thenLine(it, i, thenCells[i], now, room);
            rowViews[i].setVisibility(shown[i] ? View.VISIBLE : View.GONE);
        }
        // line the columns up: each column as wide as its widest cell in the rows shown (a gap after each)
        float[] gaps = {12 * density, 14 * density, 18 * density, 0};
        for (int c = 0; c < 4; c++) {
            int w = 0;
            for (int i = 0; i < 2; i++) if (shown[i]) w = Math.max(w, Math.round(width(thenCells[i][c], thenCells[i][c].getText().toString()) + gaps[c]));
            for (int i = 0; i < 2; i++) thenCells[i][c].setMinWidth(c == 3 ? 0 : w);
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

    // The label over the headline: what kind of moment this is, in caps (Words.label)
    private void labelLine(JSONObject it, long now, int room) {
        Words.Label l = Words.label(data, it, now);
        String text = pickFits(label, l.forms, l.countdown, room);
        String comp = text.equals(l.forms[0]) ? l.comps[0] : text.equals(l.forms[1]) ? l.comps[1] : null;
        int colour = l.kind == Words.LIVE ? RED : l.kind == Words.OFF ? AMBER : DIM;
        setLabel(text, l.countdown, colour, l.kind == Words.LIVE, accentOf(it), comp);
    }

    /** The competition's name and the countdown in its colour (plain text); the rest as the state says. */
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
            b.setSpan(new ForegroundColorSpan(accent != null ? accent : WHITE), at, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        label.setText(b);
        label.setTextColor(colour);
        label.setCompoundDrawablesRelative(showDot ? dot : null, null, null, null);
    }

    // The big line: "Real Madrid v Sevilla", "Real Madrid 2–1 Sevilla", "United States GP  Austin", "VER · NOR · LEC"
    private void headline(JSONObject it, long now, int room) {
        boolean scores = data.scores(it, now);
        hScoreA.setVisibility(View.GONE);
        hScoreB.setVisibility(View.GONE);
        hLoc.setVisibility(View.GONE);
        for (TextView v : new TextView[]{hA, hB, hScoreA, hScoreB}) v.setTextColor(WHITE);
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
        String[][] names = Words.nameForms(it);
        setHeadSize(HEAD_SP);
        // try the full form first, then shorter names, then (cricket) scores without their overs; never cut a word
        String[] scoreForms = cricket ? new String[]{sa, Words.noOvers(sa)} : new String[]{sa};
        String[] scoreFormsB = cricket ? new String[]{sb, Words.noOvers(sb)} : new String[]{sb};
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
                    // the side Parth follows stands out: the other side's name and score at 65% white
                    String ours = b.isEmpty() ? "" : it.optString("ours", "");
                    if ("b".equals(ours)) hA.setTextColor(HEAD_OTHER);
                    if ("a".equals(ours)) hB.setTextColor(HEAD_OTHER);
                    int[] other = oneScore ? Opponent.inScore(sa, sb, ours) : null;
                    if (other != null) {
                        SpannableString sc = new SpannableString(scA);
                        sc.setSpan(new ForegroundColorSpan(HEAD_OTHER), other[0], other[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        hScoreA.setText(sc);
                    } else if (!oneScore) {
                        if ("b".equals(ours)) hScoreA.setTextColor(HEAD_OTHER);
                        if ("a".equals(ours)) hScoreB.setTextColor(HEAD_OTHER);
                    }
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

    // "THEN   Thu 12:30 am   Real Madrid v RB Leipzig   UCL": label, time, match, competition in four cells
    private boolean thenLine(JSONObject it, int index, TextView[] cells, long now, int room) {
        boolean live = data.liveNow(it, now);
        String l = live ? "Also live" : index == 0 ? "Then" : "Later";
        cells[0].setText(l.toUpperCase(Locale.ENGLISH));
        String when = Words.when(data, it, now);
        boolean scores = data.scores(it, now);
        List<String> whats = new ArrayList<>();
        List<String[]> pairs = new ArrayList<>();   // the names behind each form (none for F1)
        if ("f1".equals(it.optString("sp"))) {
            JSONArray top = it.optJSONArray("top");
            whats.add(scores && top != null && top.length() >= 3 ? it.optString("session") + " " + top.optString(0) + " · " + top.optString(1) + " · " + top.optString(2) : it.optString("session"));
        } else {
            for (String[] n : Words.nameForms(it)) {
                whats.add(Words.sides(it, n[0], n[1], scores));
                pairs.add(n);
            }
        }
        String[] comps = "f1".equals(it.optString("sp")) ? new String[]{it.optString("gp", ""), ""} : Words.compForms(it);
        float fixed = width(cells[0], cells[0].getText().toString()) + 12 * density + width(cells[1], when) + 14 * density;
        for (String c : new String[]{comps[0], comps[1], ""}) {
            for (int k = 0; k < whats.size(); k++) {
                String w = whats.get(k);
                if (fixed + width(cells[2], w) + (c.isEmpty() ? 0 : 18 * density + width(cells[3], c)) <= room) {
                    cells[1].setText(when);
                    cells[2].setText(k < pairs.size() ? withOther(it, w, pairs.get(k), scores) : w);
                    cells[3].setText(c);
                    return true;
                }
            }
        }
        return false; // no form fits: the line is left out rather than cut
    }

    /** A THEN/LATER match with the side Parth doesn't follow dimmed (the feed's "ours"); as it was when not set. */
    private static CharSequence withOther(JSONObject it, String line, String[] names, boolean scores) {
        String sa = scores ? it.optString("sa", "") : "", sb = scores ? it.optString("sb", "") : "";
        int[][] other = Opponent.inLine(it.optString("sp"), names[0], names[1], sa, sb, it.optString("ours", ""));
        if (other.length == 0) return line;
        SpannableString s = new SpannableString(line);
        for (int[] r : other) s.setSpan(new ForegroundColorSpan(ROW_OTHER), r[0], r[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    /** The first form that fits the room (the label's countdown counted in); the last one if none does. */
    private String pickFits(TextView v, String[] forms, String extra, int room) {
        float more = extra == null ? 0 : width(v, "   " + extra.toUpperCase(Locale.ENGLISH));
        for (String f : forms) if (!f.isEmpty() && width(v, f.toUpperCase(Locale.ENGLISH)) + more <= room) return f;
        return forms[forms.length - 1];
    }

    private static float width(TextView v, String s) {
        return v.getPaint().measureText(s);
    }

    // ---- weather -------------------------------------------------------------------------------------------------

    private boolean renderWeather(JSONArray list) {
        if (list == null || list.length() == 0) return false;
        weatherCols.setVisibility(View.VISIBLE);
        LayoutInflater inf = act.getLayoutInflater();
        while (weatherCols.getChildCount() < list.length()) {
            View col = inf.inflate(R.layout.item_city, weatherCols, false);
            Fonts.apply(col);
            weatherCols.addView(col);
        }
        while (weatherCols.getChildCount() > list.length()) weatherCols.removeViewAt(weatherCols.getChildCount() - 1);
        if (list != extremesFor) {
            extremesFor = list;
            extremes = extremesOf(list);
        }
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
            temp.setTextColor(i == extremes.warmest ? WARM_TINT : i == extremes.coolest ? COOL_TINT : WHITE);
            feels.setText(c.has("feels") ? "Feels " + c.optInt("feels") + "°" : "");
            feels.setVisibility(c.has("feels") ? View.VISIBLE : View.GONE);
            SpannableStringBuilder hl = new SpannableStringBuilder();
            if (c.has("hi")) lit(hl, "H " + c.optInt("hi") + "°", i == extremes.highestHigh);
            if (c.has("hi") && c.has("lo")) hl.append(" · ");
            if (c.has("lo")) lit(hl, "L " + c.optInt("lo") + "°", i == extremes.lowestLow);
            hilo.setText(hl);
            hilo.setVisibility(hl.length() == 0 ? View.GONE : View.VISIBLE);
            TextView hum = col.findViewById(R.id.hum);
            hum.setText(c.has("hum") ? lit(new SpannableStringBuilder(), "Humidity " + c.optInt("hum") + "%", i == extremes.mostHumid) : "");
            hum.setVisibility(c.has("hum") ? View.VISIBLE : View.GONE);
            // measured without the word: an invisible view still takes its width, and a long word ("Storms likely
            // tomorrow") left from the last update would widen all three columns
            word.setText("");
            word.setVisibility(View.GONE);
            if (c.has("as_of")) asOf = c.optString("as_of");
            col.setMinimumWidth(0);
            col.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            widest = Math.max(widest, col.getMeasuredWidth());
        }
        // three equal columns; a word wider than its column steps down to its short form ("Orange alert"), or else
        // wraps onto two lines inside it, never widening it. Every column keeps the word's slot (as many lines as the
        // longest word), so all three are the same height.
        int slot = 1;
        for (int i = 0; i < list.length(); i++) {
            JSONObject c = list.optJSONObject(i);
            View col = weatherCols.getChildAt(i);
            col.setMinimumWidth(widest);
            TextView word = col.findViewById(R.id.word);
            word.setSingleLine(false);
            word.setMaxLines(2);
            word.setText(" ");
            word.setVisibility(View.INVISIBLE);
            String w = c.optString("word", "");
            if (w.isEmpty()) continue;
            String ws = c.optString("word_short", w);
            float limit = widest + 36 * density;
            int cut = w.indexOf(" · ");
            word.setMaxWidth((int) limit);
            String shown;
            if (width(word, w) <= limit) shown = w;
            else if (cut > 0 && width(word, w.substring(0, cut)) <= limit && width(word, w.substring(cut + 3)) <= limit) shown = w.substring(0, cut) + "\n" + w.substring(cut + 3);
            else if (!ws.equals(w)) shown = ws;
            else shown = w;   // wraps at a space within the column ("Storms likely" / "tomorrow")
            word.setText(shown);
            word.setVisibility(View.VISIBLE);
            if (shown.contains("\n") || width(word, shown) > limit) slot = 2;
        }
        for (int i = 0; i < list.length(); i++) ((TextView) weatherCols.getChildAt(i).findViewById(R.id.word)).setMinLines(slot);
        String note = data.saved() ? "as of " + Words.clock(data.fetchedAt()) : asOf != null ? asOfClock(asOf) : null;
        weatherAsOf.setText(note == null ? "" : note);
        weatherAsOf.setVisibility(note == null ? View.GONE : View.VISIBLE);
        return true;
    }

    private static WeatherExtremes extremesOf(JSONArray list) {
        int n = list.length();
        Integer[] now = new Integer[n], hi = new Integer[n], lo = new Integer[n], hum = new Integer[n];
        for (int i = 0; i < n; i++) {
            JSONObject c = list.optJSONObject(i);
            if (c == null) continue;
            if (c.has("now")) now[i] = c.optInt("now");
            if (c.has("hi")) hi[i] = c.optInt("hi");
            if (c.has("lo")) lo[i] = c.optInt("lo");
            if (c.has("hum")) hum[i] = c.optInt("hum");
        }
        return WeatherExtremes.of(now, hi, lo, hum);
    }

    /** Appends a value; when it is the extreme, only the value is lit (the line around it stays dim). */
    private static SpannableStringBuilder lit(SpannableStringBuilder b, String value, boolean on) {
        int at = b.length();
        b.append(value);
        if (on) b.setSpan(new ForegroundColorSpan(LIT), at, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return b;
    }

    private static String asOfClock(String iso) {
        try {
            return "as of " + Words.clock(Instant.parse(iso).toEpochMilli());
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
