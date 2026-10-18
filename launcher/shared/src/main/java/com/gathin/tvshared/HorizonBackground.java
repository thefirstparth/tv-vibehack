package com.gathin.tvshared;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Horizon v2: the home screen's light. Eight moods timed by Bengaluru's sun (worked out on the TV, SunTimes), a faint
 * season, the sky of Bengaluru's weather word (rain, storm), a glow in the competition's colour while a match is live,
 * and personal days. Everything is decided on the TV with no network; the feed only lends its weather word and
 * live state. Drawn once per combination into a small image (soft gradients look the same scaled up) used as the
 * window's background, so the screen still draws one layer as with plain black. It never reacts to focus and is never
 * animated: a new image replaces the old one whole when the combination changes.
 */
public final class HorizonBackground {

    /** Where the sun is worked out for: config.xml horizon_lat and horizon_lon (read by locate; Bengaluru until then). */
    public static double LAT = 12.9716, LON = 77.5946;

    /** Reads the place from config.xml (horizon_lat, horizon_lon); a value that does not read stays as it was. */
    public static void locate(Resources res) {
        try {
            LAT = Double.parseDouble(res.getString(R.string.horizon_lat).trim());
            LON = Double.parseDouble(res.getString(R.string.horizon_lon).trim());
        } catch (RuntimeException ignored) {
        }
    }
    private static final int W = 480, H = 270;

    /** The four colours of a look: the top tint, the band, the core glow and the spot. */
    public static final class Palette {
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

    public enum Mood {
        // top / band / core / spot, the core's x and the spot's x (fractions of the width): the design's values
        LATE_NIGHT(rgba(14, 18, 48, .55), rgba(40, 50, 140, .22), rgba(70, 60, 170, .26), rgba(30, 90, 120, .16), .42f, .56f),
        DAWN(rgba(40, 24, 70, .50), rgba(120, 60, 140, .24), rgba(232, 120, 96, .32), rgba(255, 186, 128, .24), .22f, .30f),
        MORNING(rgba(34, 30, 60, .40), rgba(214, 128, 62, .22), rgba(250, 172, 92, .30), rgba(130, 176, 226, .18), .30f, .40f),
        MIDDAY(rgba(12, 36, 62, .45), rgba(40, 110, 170, .22), rgba(70, 170, 205, .28), rgba(150, 222, 232, .16), .50f, .60f),
        GOLDEN_HOUR(rgba(50, 30, 20, .45), rgba(210, 112, 40, .26), rgba(255, 172, 64, .32), rgba(255, 124, 84, .22), .66f, .56f),
        SUNSET(rgba(46, 20, 52, .50), rgba(200, 62, 92, .28), rgba(255, 112, 72, .32), rgba(150, 62, 176, .24), .76f, .64f),
        BLUE_HOUR(rgba(20, 22, 70, .55), rgba(80, 62, 170, .26), rgba(62, 112, 206, .28), rgba(206, 92, 144, .18), .80f, .66f),
        NIGHT(rgba(10, 16, 52, .55), rgba(40, 60, 160, .24), rgba(72, 82, 204, .28), rgba(92, 52, 154, .20), .50f, .62f);

        public final Palette palette;
        public final float x, spotX;

        Mood(int top, int band, int core, int spot, float x, float spotX) {
            this.palette = new Palette(top, band, core, spot);
            this.x = x;
            this.spotX = spotX;
        }

        /** By the minute of the local day, with sunrise and sunset (minutes) for that date (SunLine.Phase's timing). */
        public static Mood at(double minute, double rise, double set) {
            return valueOf(SunLine.Phase.at(minute, rise, set).name());
        }

        public static Mood named(String name) {
            for (Mood m : values()) if (m.name().equalsIgnoreCase(name)) return m;
            return null;
        }
    }

    public enum Season {
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
    public enum Sky {
        CLEAR, RAIN, STORM;

        public static Sky of(String word, boolean warning) {
            if (warning || "Storms likely".equals(word)) return STORM;
            if ("Rain likely".equals(word) || "Heavy rain".equals(word) || "Very heavy rain".equals(word)) return RAIN;
            return CLEAR;
        }
    }

    /** A personal day (launcher/local.properties): its date, greeting and four colours. */
    public static final class Day {
        public final int month, day;
        public final String greeting;
        public final Palette palette;

        Day(int month, int day, String greeting, Palette palette) {
            this.month = month;
            this.day = day;
            this.greeting = greeting;
            this.palette = palette;
        }

        /**
         * One per line, as built from tv.day.N in local.properties: "MM-DD|greeting|core|spot|band|top", each colour
         * "r,g,b,a" (a from 0 to 1). A line that does not read cleanly is skipped.
         */
        public static List<Day> parse(String text) {
            List<Day> out = new ArrayList<>();
            for (String line : text.split("\n")) {
                String[] f = line.trim().split("\\|");
                if (f.length != 6) continue;
                try {
                    String[] md = f[0].trim().split("-");
                    int[] c = new int[4];
                    for (int i = 0; i < 4; i++) {
                        String[] v = f[2 + i].trim().split(",");
                        c[i] = rgba(Integer.parseInt(v[0].trim()), Integer.parseInt(v[1].trim()), Integer.parseInt(v[2].trim()), Double.parseDouble(v[3].trim()));
                    }
                    out.add(new Day(Integer.parseInt(md[0]), Integer.parseInt(md[1]), f[1].trim(), new Palette(c[3], c[2], c[0], c[1])));
                } catch (RuntimeException ignored) {
                }
            }
            return out;
        }
    }

    /** Everything the image depends on; a new image only when this changes. */
    public static final class State {
        public final Mood mood;
        public final Season season;
        public final Sky sky;
        public final Integer matchAccent;   // the live competition's colour, or null
        public final Day day;               // today's personal day, or null

        public State(Mood mood, Season season, Sky sky, Integer matchAccent, Day day) {
            this.mood = mood;
            this.season = season;
            this.sky = sky;
            this.matchAccent = matchAccent;
            this.day = day;
        }

        public String key() {
            return mood + "|" + season + "|" + sky + "|" + matchAccent + "|" + (day == null ? "" : day.month + "-" + day.day);
        }

        /** The colours in use: the personal day's, else the mood's; muted in rain or a storm. */
        Palette palette() {
            Palette p = day != null ? day.palette : mood.palette;
            return sky == Sky.CLEAR ? p : p.desaturated(0.62f);
        }
    }

    /** The state for a moment (the TV's local time), the feed's sky and live colour, and the personal days. */
    public static State stateAt(Calendar now, Sky sky, Integer matchAccent, List<Day> days) {
        int y = now.get(Calendar.YEAR), m = now.get(Calendar.MONTH) + 1, d = now.get(Calendar.DAY_OF_MONTH);
        double tz = now.getTimeZone().getOffset(now.getTimeInMillis()) / 3.6e6;
        double[] rs = SunTimes.riseSet(y, m, d, LAT, LON, tz);
        double minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        return new State(Mood.at(minute, rs[0], rs[1]), Season.of(m), sky, matchAccent, dayOf(now, days));
    }

    /** Today's personal day, if any (the whole day, local time). */
    public static Day dayOf(Calendar now, List<Day> days) {
        int m = now.get(Calendar.MONTH) + 1, d = now.get(Calendar.DAY_OF_MONTH);
        for (Day x : days) if (x.month == m && x.day == d) return x;
        return null;
    }

    public static Calendar now() {
        return Calendar.getInstance(TimeZone.getDefault(), Locale.ENGLISH);
    }

    private String shown;

    /** True when this state needs a new image (and marks it as on its way; call on the main thread). */
    public boolean claim(State s) {
        return claim(s.key());
    }

    /** The same for a state drawn somewhere else too (the screensaver's light, centred on its own information). */
    public boolean claim(String key) {
        if (key.equals(shown)) return false;
        shown = key;
        return true;
    }

    /** The home screen's light: the core glow at 60% of the height. */
    public static final float HOME_CENTRE = 0.60f;

    /** Draws a state's image; safe off the main thread (touches nothing shared). */
    public static BitmapDrawable draw(Resources res, State s) {
        return draw(res, s, HOME_CENTRE);
    }

    /**
     * The same light with its glow centred at another height (the screensaver: on its band of information): the
     * band, season, haze, core, spot and match glow all move with it; the top tint stays at the top.
     */
    public static BitmapDrawable draw(Resources res, State s, float centre) {
        float dy = centre - HOME_CENTRE;
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.BLACK);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        Palette pal = s.palette();
        // a) the top tint: the colour at the top, clear by 32% of the height
        vertical(c, p, new int[]{pal.top, clear(pal.top), clear(pal.top)}, new float[]{0f, 0.32f, 1f});
        // b) the band: clear at 18%, the colour at 44%, clear at 64%
        band(c, p, pal.band, 0.18f + dy, 0.44f + dy, 0.64f + dy);
        // the season, faint, on top of the mood: clear at 10%, the colour at 48%, clear at 72%
        band(c, p, s.season.colour, 0.10f + dy, 0.48f + dy, 0.72f + dy);
        // rain or a storm: a grey haze at 46%, gone by 12% and 70%
        if (s.sky != Sky.CLEAR) band(c, p, rgba(120, 140, 165, s.sky == Sky.STORM ? .14 : .11), 0.12f + dy, 0.46f + dy, 0.70f + dy);
        // c) the core: an ellipse at (x, 60%), radii 72% x 32%, clear at 70%
        glow(c, p, pal.core, s.mood.x, 0.60f + dy, 0.72f, 0.32f);
        // d) the spot: at (spot x, 55%), radii 30% x 20%, clear at 70%
        glow(c, p, pal.spot, s.mood.spotX, 0.55f + dy, 0.30f, 0.20f);
        // a match on: a faint glow in the competition's colour under the sport lines
        if (s.matchAccent != null) glow(c, p, (s.matchAccent & 0x00FFFFFF) | (41 << 24), 0.24f, 0.63f + dy, 0.26f, 0.20f);
        BitmapDrawable d = new BitmapDrawable(res, b);
        d.setFilterBitmap(true);   // stretched to the screen, smoothly
        d.setDither(true);
        return d;
    }

    /** The dock band's top edge: the core's colour at about 28%. */
    public static int edge(State s) {
        return (s.palette().core & 0x00FFFFFF) | (71 << 24);
    }

    private static void vertical(Canvas c, Paint p, int[] colours, float[] stops) {
        p.setShader(new LinearGradient(0, 0, 0, H, colours, stops, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
    }

    private static void band(Canvas c, Paint p, int colour, float from, float at, float to) {
        int none = clear(colour);
        from = Math.max(0.001f, from);   // stops must stay in order inside 0..1 when the band moves up or down
        at = Math.max(from + 0.001f, at);
        to = Math.min(0.999f, Math.max(at + 0.001f, to));
        vertical(c, p, new int[]{none, none, colour, none, none}, new float[]{0f, from, at, to, 1f});
    }

    private static void glow(Canvas c, Paint p, int colour, float x, float y, float rx, float ry) {
        float rw = rx * W, rh = ry * H;
        RadialGradient g = new RadialGradient(0, 0, rw, new int[]{colour, clear(colour), clear(colour)}, new float[]{0f, 0.70f, 1f}, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(1f, rh / rw);
        m.postTranslate(x * W, y * H);
        g.setLocalMatrix(m);
        p.setShader(g);
        c.drawRect(0, 0, W, H, p);
    }

    private static int clear(int c) {
        return c & 0x00FFFFFF;
    }

    public static int rgba(int r, int g, int b, double a) {
        return Color.argb((int) Math.round(a * 255), r, g, b);
    }
}
