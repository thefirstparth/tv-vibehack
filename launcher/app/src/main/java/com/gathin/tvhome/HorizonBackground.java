package com.gathin.tvhome;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;

import com.gathin.tvshared.PersonalDays;
import com.gathin.tvshared.SunLine;
import com.gathin.tvshared.SunTimes;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The home screen's light: its state (what the picture depends on) and the image, drawn by HorizonV3 since Horizon v3
 * (20 Oct 2026). The notes below are v2's, whose moods, season, sky and personal days v3 still uses.
 * Horizon v2: the home screen's light. Eight moods timed by the sun at the place in config.xml (worked out on the TV:
 * SunTimes, with SunLine's timing, the same as the line above the clock), a faint
 * season, the sky of Bengaluru's weather word (rain, storm), a glow in the competition's colour while a match is live,
 * and personal days. Everything is decided on the TV with no network; the feed only lends its weather word and
 * live state. Drawn once per combination into a small image (soft gradients look the same scaled up) used as the
 * window's background, so the screen still draws one layer as with plain black. It never reacts to focus and is never
 * animated: a new image replaces the old one whole when the combination changes.
 */
final class HorizonBackground {

    /** The four colours of a look: the top tint, the band, the core glow and the spot. */
    static final class Palette {
        final int top, band, core, spot;

        Palette(int top, int band, int core, int spot) {
            this.top = top;
            this.band = band;
            this.core = core;
            this.spot = spot;
        }

        Palette desaturated(float factor) {
            return new Palette(sat(top, factor), sat(band, factor), sat(core, factor), sat(spot, factor));
        }

        private static int sat(int c, float f) {
            float[] hsv = new float[3];
            Color.colorToHSV(c, hsv);
            hsv[1] *= f;
            return Color.HSVToColor(Color.alpha(c), hsv);
        }
    }

    enum Mood {
        // top / band / core / spot, the core's x and the spot's x (fractions of the width): the design's values
        LATE_NIGHT(rgba(14, 18, 48, .55), rgba(40, 50, 140, .22), rgba(70, 60, 170, .26), rgba(30, 90, 120, .16), .42f, .56f),
        DAWN(rgba(40, 24, 70, .50), rgba(120, 60, 140, .24), rgba(232, 120, 96, .32), rgba(255, 186, 128, .24), .22f, .30f),
        MORNING(rgba(34, 30, 60, .40), rgba(214, 128, 62, .22), rgba(250, 172, 92, .30), rgba(130, 176, 226, .18), .30f, .40f),
        // two soft in-between moods for Horizon's day (colour review, 26 Oct): morning's orange and midday's teal, and
        // midday and golden hour, are nearly opposite colours, so going straight from one to the other turned grey or
        // invented pink and green; these keep each step small (only Horizon's palette uses them, never SunLine's phases)
        LATE_MORNING(rgba(26, 36, 64, .42), rgba(150, 140, 120, .18), rgba(235, 205, 160, .26), rgba(140, 180, 220, .16), .40f, .50f),
        MIDDAY(rgba(12, 36, 62, .45), rgba(40, 110, 170, .22), rgba(70, 170, 205, .28), rgba(150, 222, 232, .16), .50f, .60f),
        AFTERNOON(rgba(16, 34, 60, .44), rgba(120, 130, 140, .18), rgba(225, 200, 150, .26), rgba(150, 200, 225, .16), .60f, .55f),
        GOLDEN_HOUR(rgba(50, 30, 20, .45), rgba(210, 112, 40, .26), rgba(255, 172, 64, .32), rgba(255, 124, 84, .22), .66f, .56f),
        SUNSET(rgba(46, 20, 52, .50), rgba(200, 62, 92, .28), rgba(255, 112, 72, .32), rgba(150, 62, 176, .24), .76f, .64f),
        BLUE_HOUR(rgba(20, 22, 70, .55), rgba(80, 62, 170, .26), rgba(62, 112, 206, .28), rgba(206, 92, 144, .18), .80f, .66f),
        NIGHT(rgba(10, 16, 52, .55), rgba(40, 60, 160, .24), rgba(72, 82, 204, .28), rgba(92, 52, 154, .20), .50f, .62f);

        final Palette palette;
        final float x, spotX;

        Mood(int top, int band, int core, int spot, float x, float spotX) {
            this.palette = new Palette(top, band, core, spot);
            this.x = x;
            this.spotX = spotX;
        }

        /** The mood for a moment: SunLine.Phase's timing, shared with the line above the clock and the 1400 app. */
        static Mood at(Calendar now) {
            return valueOf(SunLine.phaseAt(now, SunTimes.LAT, SunTimes.LON).name());
        }

        static Mood named(String name) {
            for (Mood m : values()) if (m.name().equalsIgnoreCase(name)) return m;
            return null;
        }
    }

    enum Season {
        WINTER(rgba(120, 170, 255, .07)), SUMMER(rgba(255, 170, 90, .08)), MONSOON(rgba(80, 190, 170, .08)), POST_MONSOON(rgba(255, 200, 120, .05));

        final int colour;

        Season(int colour) {
            this.colour = colour;
        }

        /** December to February, March to May, June to September, October and November. */
        static Season of(int month) {
            return month == 12 || month <= 2 ? WINTER : month <= 5 ? SUMMER : month <= 9 ? MONSOON : POST_MONSOON;
        }
    }

    /** Bengaluru's sky from the feed's weather word: clear unless it says rain, or a storm or a warning. */
    enum Sky {
        CLEAR, RAIN, STORM;

        static Sky of(String word, boolean warning) {
            if (warning || "Storms likely".equals(word)) return STORM;
            if ("Rain likely".equals(word) || "Heavy rain".equals(word) || "Very heavy rain".equals(word)) return RAIN;
            return CLEAR;
        }
    }

    /** A personal day (launcher/local.properties): its date, greeting and four colours. */
    static final class Day {
        final int month, day;
        final String greeting;
        final Palette palette;

        Day(int month, int day, String greeting, Palette palette) {
            this.month = month;
            this.day = day;
            this.greeting = greeting;
            this.palette = palette;
        }

        /** The personal days (local.properties tv.day.N, read by PersonalDays) with their light. */
        static List<Day> parse(String text) {
            List<Day> out = new ArrayList<>();
            for (PersonalDays.Day d : PersonalDays.parse(text)) out.add(new Day(d.month, d.day, d.greeting, new Palette(d.top, d.band, d.core, d.spot)));
            return out;
        }
    }

    /** A block of text on the home screen, in the picture's 960-wide units: where it is and its faintest text's alpha. */
    static final class Zone {
        final float x, y, w, h, alpha;
        final String name;

        Zone(String name, float x, float y, float w, float h, float alpha) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.alpha = alpha;
        }

        String key() {
            return name + Math.round(x) + "," + Math.round(y) + "," + Math.round(w) + "," + Math.round(h);
        }
    }

    /** A new picture at most every five minutes while Home shows (and when the sky, a match, the day or the text moves). */
    static final long SLOT_MS = 5 * 60_000L;

    /** Everything the image depends on; a new image only when this changes. */
    static final class State {
        final Mood mood;
        final Season season;
        final Sky sky;
        final Integer matchAccent;   // the live competition's colour, or null
        final Day day;               // today's personal day, or null
        final long at;               // the moment drawn: the start of its five-minute slot (epoch ms), 0 when unknown
        final String wx;             // the feed's sky for Bengaluru: clear, partly, overcast, fog, rain, storm
        final List<Zone> zones;      // the text blocks, for the readability floor
        final boolean fixedMood;     // the "mood" test extra: that mood's colours instead of the day's blend

        State(Mood mood, Season season, Sky sky, Integer matchAccent, Day day) {
            this(mood, season, sky, matchAccent, day, 0, "clear", new ArrayList<>(), true);
        }

        State(Mood mood, Season season, Sky sky, Integer matchAccent, Day day, long at, String wx, List<Zone> zones, boolean fixedMood) {
            this.mood = mood;
            this.season = season;
            this.sky = sky;
            this.matchAccent = matchAccent;
            this.day = day;
            this.at = at;
            this.wx = wx == null || wx.isEmpty() ? "clear" : wx;
            this.zones = zones;
            this.fixedMood = fixedMood;
        }

        String key() {
            StringBuilder z = new StringBuilder();
            for (Zone x : zones) z.append(x.key()).append(';');
            return mood + "|" + season + "|" + sky + "|" + matchAccent + "|" + (day == null ? "" : day.month + "-" + day.day)
                    + "|" + HorizonV3.lookAt(at) + "|" + HorizonV3.sky(wx) + "|" + HorizonV3.stars + "|" + fixedMood + "|" + z;   // v5: redrawn only when the look, the weather or the text changes
        }

        /** The phase's colours (the weekend look's tones come from these): the personal day's, else the mood's. */
        Palette palette() {
            Palette p = day != null ? day.palette : mood.palette;
            return sky == Sky.CLEAR ? p : p.desaturated(0.62f);
        }
    }

    /** The state for a moment (the TV's local time), the feed's sky and live colour, and the personal days. */
    static State stateAt(Calendar now, Sky sky, Integer matchAccent, List<Day> days) {
        return new State(Mood.at(now), Season.of(now.get(Calendar.MONTH) + 1), sky, matchAccent, dayOf(now, days));
    }

    /** Horizon v3's state: as above, with the five-minute slot, the feed's sky word and the text blocks. */
    static State stateAt(Calendar now, Sky sky, String wx, Integer matchAccent, List<Day> days, List<Zone> zones) {
        long slot = now.getTimeInMillis() / SLOT_MS * SLOT_MS;
        return new State(Mood.at(now), Season.of(now.get(Calendar.MONTH) + 1), sky, matchAccent, dayOf(now, days), slot, wx, zones, false);
    }

    /** Today's personal day, if any (the whole day, local time). */
    static Day dayOf(Calendar now, List<Day> days) {
        int m = now.get(Calendar.MONTH) + 1, d = now.get(Calendar.DAY_OF_MONTH);
        for (Day x : days) if (x.month == m && x.day == d) return x;
        return null;
    }

    static Calendar now() {
        return Calendar.getInstance(TimeZone.getDefault(), Locale.ENGLISH);
    }

    private String shown;

    /** True when this state needs a new image (and marks it as on its way; call on the main thread). */
    boolean claim(State s) {
        if (s.key().equals(shown)) return false;
        shown = s.key();
        return true;
    }

    /** Draws a state's image (Horizon v3, HorizonV3); safe off the main thread (touches nothing shared). */
    static BitmapDrawable draw(Resources res, State s) {
        Bitmap b = HorizonV3.draw(res, s);
        BitmapDrawable d = new BitmapDrawable(res, b);
        d.setFilterBitmap(true);   // stretched to the screen, smoothly
        d.setDither(true);
        return d;
    }

    /** The dock band's top edge: the core's colour at about 28%. */
    static int edge(State s) {
        return HorizonV3.edge(s);
    }

    private static int clear(int c) {
        return c & 0x00FFFFFF;
    }

    static int rgba(int r, int g, int b, double a) {
        return Color.argb((int) Math.round(a * 255), r, g, b);
    }
}
