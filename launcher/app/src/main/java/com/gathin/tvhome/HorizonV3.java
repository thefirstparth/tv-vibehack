package com.gathin.tvhome;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.Log;

import com.gathin.tvshared.SunTimes;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Horizon v3, the home screen's light, as in the approved mockup (examples/xiaomi-mitv-moor2/horizon-v3/mockup.html,
 * drawV3; its maths copied): v2's eight moods blended by the minute, the glow where the real sun is (at night where the
 * moon is), the moon as light only, 909 stars placed for the moment (a third as bright behind text), the Milky Way on
 * clear moonless nights, the feed's sky (cloud, haze, storm), fine grain, and a readability floor: the faintest text of
 * each block keeps 4.5:1 against its 90th-percentile background pixel, with a soft oval shadow under the block where it
 * would not. Not in this build: meteors, the match ribbon, the after-match glows. Drawn off the main thread.
 */
final class HorizonV3 {

    /** The picture's size (stretched to the screen). */
    static final int W = 960, H = 540;
    private static final float S = W / 960f, HOR = 0.66f;
    private static final double R = Astro.R;
    /** The floor: the faintest text keeps this contrast against its block's 90th-percentile background pixel. */
    static final double FLOOR = 4.5;
    private static final String TAG = "Horizon";

    private static float[] stars;   // RA°, Dec°, magnitude, B-V; read once from res/raw/stars.txt

    private HorizonV3() {
    }

    // ---- colours: {r, g, b, a} with a 0..1, as in the mockup ----------------------------------------------------

    private static float[] c(int argb) {
        return new float[]{Color.red(argb), Color.green(argb), Color.blue(argb), Color.alpha(argb) / 255f};
    }

    private static float[] c(float r, float g, float b, float a) {
        return new float[]{r, g, b, a};
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t, a[3] + (b[3] - a[3]) * t};
    }

    private static float[] sat(float[] x, float f) {
        float l = 0.299f * x[0] + 0.587f * x[1] + 0.114f * x[2];
        return new float[]{l + (x[0] - l) * f, l + (x[1] - l) * f, l + (x[2] - l) * f, x[3]};
    }

    private static int argb(float[] x, float k) {
        float a = Math.max(0, Math.min(1, x[3] * k));
        return Color.argb(Math.round(a * 255), clamp255(x[0]), clamp255(x[1]), clamp255(x[2]));
    }

    private static int clamp255(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    private static float sstep(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return (float) (t * t * (3 - 2 * t));
    }

    /** mulberry32, as the mockup's rng(seed). */
    private static final class Rng {
        private int seed;

        Rng(int seed) {
            this.seed = seed;
        }

        double next() {
            seed = seed + 0x6D2B79F5;
            int t = (seed ^ (seed >>> 15)) * (1 | seed);
            t = (t + ((t ^ (t >>> 7)) * (61 | t))) ^ t;
            return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
        }
    }

    // ---- the palette: v2's moods blended continuously by the minute -----------------------------------------------

    private static float[][] pal(HorizonBackground.Palette p) {
        return new float[][]{c(p.top), c(p.band), c(p.core), c(p.spot)};
    }

    /** The mockup's v3Palette: anchors relative to sunrise (r) and sunset (s), in minutes of the local day. */
    static float[][] palette(int min, int r, int s) {
        int n = (r + s) / 2;
        Object[][] A = {{90, "LATE_NIGHT"}, {r - 110, "LATE_NIGHT"}, {r - 35, "DAWN"}, {r + 70, "MORNING"}, {n - 60, "MIDDAY"},
                {n + 60, "MIDDAY"}, {s - 55, "GOLDEN_HOUR"}, {s - 5, "SUNSET"}, {s + 45, "BLUE_HOUR"}, {s + 130, "NIGHT"},
                {1410, "NIGHT"}, {90 + 1440, "LATE_NIGHT"}};
        int m = min < 90 ? min + 1440 : min;
        for (int i = 0; i < A.length - 1; i++) {
            int a0 = (Integer) A[i][0], a1 = (Integer) A[i + 1][0];
            if (m >= a0 && m <= a1) {
                float t = sstep(a0, a1, m);
                float[][] pa = pal(HorizonBackground.Mood.valueOf((String) A[i][1]).palette);
                float[][] pb = pal(HorizonBackground.Mood.valueOf((String) A[i + 1][1]).palette);
                return new float[][]{mix(pa[0], pb[0], t), mix(pa[1], pb[1], t), mix(pa[2], pb[2], t), mix(pa[3], pb[3], t)};
            }
        }
        return pal(HorizonBackground.Mood.NIGHT.palette);
    }

    // ---- drawing primitives (the mockup's shapes; a glow fills only its own box) ----------------------------------

    private static void vertical(Canvas g, Paint p, float[][] cols, float[] stops, float k) {
        int[] ci = new int[cols.length];
        for (int i = 0; i < cols.length; i++) ci[i] = argb(cols[i], k);
        p.setShader(new LinearGradient(0, 0, 0, H, ci, stops, Shader.TileMode.CLAMP));
        g.drawRect(0, 0, W, H, p);
        p.setShader(null);
    }

    private static float[] clr(float[] x) {
        return new float[]{x[0], x[1], x[2], 0};
    }

    private static void band(Canvas g, Paint p, float[] col, float from, float at, float to) {
        float[] n = clr(col);
        vertical(g, p, new float[][]{n, n, col, n, n}, new float[]{0, from, at, to, 1}, 1);
    }

    private static void glow(Canvas g, Paint p, float[] col, double x, double y, double rx, double ry, double k) {
        float rw = (float) (rx * W), rh = (float) (ry * H);
        if (rw <= 0 || k <= 0) return;
        int in = argb(col, (float) k), out = argb(clr(col), 1);
        RadialGradient rg = new RadialGradient(0, 0, rw, new int[]{in, out, out}, new float[]{0f, 0.7f, 1f}, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(1f, rh / rw);
        m.postTranslate((float) (x * W), (float) (y * H));
        rg.setLocalMatrix(m);
        p.setShader(rg);
        float cx = (float) (x * W), cy = (float) (y * H);
        g.drawRect(cx - rw, cy - rh, cx + rw, cy + rh, p);
        p.setShader(null);
    }

    /** The sky's projection: hour angle across, altitude up (the horizon at 66% of the height). */
    private static float[] proj(double hourAngle, double alt) {
        return new float[]{(float) (0.5 + hourAngle / Math.PI), (float) (HOR - (alt / (Math.PI / 2)) * (HOR - 0.02))};
    }

    private static int[] starColour(float bv) {
        if (bv < -0.1) return new int[]{170, 196, 255};
        if (bv < 0.2) return new int[]{205, 220, 255};
        if (bv < 0.6) return new int[]{246, 246, 255};
        if (bv < 1.0) return new int[]{255, 238, 210};
        if (bv < 1.4) return new int[]{255, 212, 160};
        return new int[]{255, 186, 128};
    }

    private static synchronized float[] stars(Resources res) {
        if (stars != null) return stars;
        List<Float> v = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(res.openRawResource(R_STARS), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) continue;
                for (String x : line.trim().split("\\s+")) v.add(Float.parseFloat(x));
            }
        } catch (Exception e) {
            Log.w(TAG, "stars not read", e);
        }
        float[] out = new float[v.size()];
        for (int i = 0; i < out.length; i++) out[i] = v.get(i);
        stars = out;
        return out;
    }

    private static final int R_STARS = com.gathin.tvhome.R.raw.stars;

    // ---- the picture ---------------------------------------------------------------------------------------------

    static Bitmap draw(Resources res, HorizonBackground.State st) {
        long t = st.at > 0 ? st.at : System.currentTimeMillis();
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        cal.setTimeInMillis(t);
        int min = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        int[] rs = riseSet(cal);
        Astro astro = new Astro(SunTimes.LAT, SunTimes.LON);
        double d = Astro.days(t);
        float[][] pal = st.day != null ? pal(st.day.palette) : st.fixedMood ? pal(st.mood.palette) : palette(min, rs[0], rs[1]);
        List<HorizonBackground.Zone> Z = st.zones;

        double[] sun = Astro.sun(d), sh = astro.horiz(sun[0], sun[1], d);
        double sAlt = sh[1] / R, sH = sh[0] / R;
        double[] moon = Astro.moon(d), mh = astro.horiz(moon[0], moon[1], d);
        double mAlt = mh[1] / R, lit = Astro.moonLit(d);
        float[] mp = proj(mh[0], mh[1]);
        boolean moonUp = mAlt > -0.5 && mp[0] > -0.02 && mp[0] < 1.02;
        String wx = weather(st.wx);
        double cloud = "overcast".equals(wx) ? 0.92 : "storm".equals(wx) ? 1 : "haze".equals(wx) ? 0.5 : 0;
        double kCore = 1, kSpot = 1;
        if ("overcast".equals(wx)) { pal = satAll(pal, 0.5f); kCore = 0.45; kSpot = 0.35; }
        if ("haze".equals(wx)) { pal = satAll(pal, 0.7f); kCore = 0.6; kSpot = 0.5; }
        if ("storm".equals(wx)) { pal = satAll(pal, 0.42f); kCore = 0.5; kSpot = 0.35; }

        // where the light sits: the sun by day, the moon (or the middle) by night
        float dfac = sstep(-12, -2, sAlt);
        double sx = Math.max(0.04, Math.min(0.96, 0.5 + 0.42 * (sH / 90)));
        double sy = 0.62 - 0.30 * Math.max(0, Math.sin(sAlt * R)) + 0.06 * Math.max(0, Math.min(1, -sAlt / 12));
        double nx = moonUp && mAlt > 5 ? Math.max(0.1, Math.min(0.9, mp[0])) : 0.5;
        double cx = nx + (sx - nx) * dfac, cy = 0.60 + (sy - 0.60) * dfac;
        double spx = cx + (cx < 0.5 ? 0.1 : -0.1), spy = cy - 0.05;

        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas g = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        g.drawColor(Color.BLACK);
        vertical(g, p, new float[][]{pal[0], clr(pal[0]), clr(pal[0])}, new float[]{0, 0.32f, 1}, 1);
        band(g, p, pal[1], 0.18f, 0.44f, 0.64f);
        band(g, p, c(st.season.colour), 0.10f, 0.48f, 0.72f);
        glow(g, p, pal[2], cx, cy, 0.72, 0.32, kCore);
        glow(g, p, pal[3], spx, spy, 0.30, 0.20, kSpot);

        // the night: moonlight, the Milky Way, stars (no moon disc, no planets)
        float night = 1 - sstep(-14, -4, sAlt);
        double moonWash = moonUp ? lit * sstep(0, 30, mAlt) : 0;
        double starVis = night * (1 - cloud);
        if (moonUp && !"storm".equals(wx)) {
            double mk = lit * sstep(-1, 12, mAlt) * (1 - 0.6 * cloud);
            float[] mc = c(70, 92, 150, (float) (.10 * moonWash * (1 - cloud))), m0 = c(70, 92, 150, 0);
            vertical(g, p, new float[][]{mc, m0, m0}, new float[]{0, 0.5f, 1}, 1);
            glow(g, p, c(190, 205, 255, .16f), mp[0], mp[1], 0.16, 0.24, mk * night + 0.25 * mk * (1 - night));
        }
        if (starVis > 0.3 && moonWash < 0.35) milkyWay(g, p, astro, d, starVis * (1 - moonWash / 0.35), Z);
        if (starVis > 0.01) starsOn(g, p, res, astro, d, starVis, 4.5 - 2.2 * moonWash, Z);

        // weather
        if ("overcast".equals(wx) || "storm".equals(wx)) {
            boolean storm = "storm".equals(wx);
            Rng rc = new Rng(storm ? 3 : 5);
            float[] cl = storm ? c(16, 20, 28, .5f) : c(66, 70, 84, .34f);
            if (storm) {
                float[] dk = c(6, 8, 14, .62f), d0 = c(6, 8, 14, 0);
                vertical(g, p, new float[][]{dk, d0, d0}, new float[]{0, 0.45f, 1}, 1);
            }
            for (int i = 0; i < 9; i++) {
                double x = rc.next(), y = 0.10 + rc.next() * 0.42, rx = 0.18 + rc.next() * 0.24, ry = 0.035 + rc.next() * 0.04;
                glow(g, p, cl, x, y, rx, ry, 1);
                if (!storm) glow(g, p, pal[2], x, y + ry * 0.7, rx * 0.9, ry * 0.6, 0.7 * Math.max(0, 1 - Math.abs(x - cx) * 1.6) * dfac);
            }
            if (storm) glow(g, p, c(110, 160, 210, .2f), 0.5, 0.67, 0.62, 0.12, 1);
        }
        if ("haze".equals(wx)) {
            band(g, p, c(190, 180, 168, .13f), 0.14f, 0.55f, 0.82f);
            g.drawColor(Color.argb(Math.round(0.05f * 255), 130, 124, 118));
        }
        // a match on: v2's faint glow in the competition's colour under the sport lines (no ribbon in this build)
        if (st.matchAccent != null) glow(g, p, c((st.matchAccent & 0x00FFFFFF) | (41 << 24)), 0.24, 0.63, 0.26, 0.20, 1);

        grain(b);
        floor(g, p, b, Z);
        return b;
    }

    /** v3's core colour at about 28%, for the dock band's top edge. */
    static int edge(HorizonBackground.State st) {
        long t = st.at > 0 ? st.at : System.currentTimeMillis();
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        cal.setTimeInMillis(t);
        int[] rs = riseSet(cal);
        int min = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        float[][] pal = st.day != null ? pal(st.day.palette) : st.fixedMood ? pal(st.mood.palette) : palette(min, rs[0], rs[1]);
        return Color.argb(71, clamp255(pal[2][0]), clamp255(pal[2][1]), clamp255(pal[2][2]));
    }

    /** The feed's sky in the mockup's terms: overcast and rain as cloud, fog as haze, storm; clear and partly clear. */
    static String weather(String sky) {
        if (sky == null) return "clear";
        switch (sky) {
            case "overcast":
            case "rain":
                return "overcast";
            case "fog":
                return "haze";
            case "storm":
                return "storm";
            default:
                return "clear";
        }
    }

    private static float[][] satAll(float[][] pal, float f) {
        return new float[][]{sat(pal[0], f), sat(pal[1], f), sat(pal[2], f), sat(pal[3], f)};
    }

    private static final java.util.Map<String, int[]> RISE_SET = new java.util.HashMap<>();

    /** Sunrise and sunset (minutes of the local day) for cal's date; computed once per day. */
    private static synchronized int[] riseSet(Calendar cal) {
        String k = cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.DAY_OF_YEAR);
        int[] v = RISE_SET.get(k);
        if (v != null) return v;
        Calendar day = (Calendar) cal.clone();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        long[] rs = new Astro(SunTimes.LAT, SunTimes.LON).sunTimes(day.getTimeInMillis());
        v = new int[]{rs[0] < 0 ? 6 * 60 : (int) ((rs[0] - day.getTimeInMillis()) / 60000), rs[1] < 0 ? 18 * 60 : (int) ((rs[1] - day.getTimeInMillis()) / 60000)};
        RISE_SET.put(k, v);
        return v;
    }

    /** 1 inside a text block, fading to 0 within 16 px (at 960 wide) outside it. */
    private static float cover(List<HorizonBackground.Zone> Z, float X, float Y) {
        float c = 0;
        for (HorizonBackground.Zone z : Z) {
            float zx = z.x * S, zy = z.y * S, zw = z.w * S, zh = z.h * S;
            float dx = Math.max(Math.max(zx - X, 0), X - zx - zw), dy = Math.max(Math.max(zy - Y, 0), Y - zy - zh);
            c = Math.max(c, 1 - sstep(0, 16 * S, Math.sqrt(dx * dx + dy * dy)));
        }
        return c;
    }

    private static void milkyWay(Canvas g, Paint p, Astro astro, double d, double mwk, List<HorizonBackground.Zone> Z) {
        double aG = 192.85948 * R, dG = 27.12825 * R, lN = 122.93192 * R;
        Rng rnd = new Rng(7);
        Paint dot = new Paint();
        for (double l = 0; l < 360; l += 1.5) {
            double q = lN - l * R, dec = Math.asin(Math.cos(dG) * Math.cos(q)), ra = aG + Math.atan2(Math.sin(q), -Math.sin(dG) * Math.cos(q));
            double[] hz = astro.horiz(ra, dec, d);
            if (hz[1] < 0) continue;
            float[] pp = proj(hz[0], hz[1]);
            if (pp[0] < -0.1 || pp[0] > 1.1) continue;
            double dl = Math.min(l, 360 - l), wl = 0.45 + 1.0 * Math.exp(-Math.pow(dl / 35, 2)) + 0.45 * Math.exp(-Math.pow((l - 75) / 25, 2));
            double a = 0.022 * wl * mwk * sstep(4, 25, hz[1] / R);
            glow(g, p, c(186, 192, 220, 1), pp[0], pp[1], 0.10, 0.10 * W / H * 0.9, a);
            for (int j = 0; j < 4; j++) {   // a little texture: faint grains of light along the band
                double ox = (rnd.next() - 0.5) * 0.09, oy = (rnd.next() - 0.5) * 0.12;
                float gx = (float) ((pp[0] + ox) * W), gy = (float) ((pp[1] + oy) * H);
                dot.setColor(argb(c(220, 225, 245, 1), (float) (0.10 * wl * mwk * (1 - 0.67 * cover(Z, gx, gy)))));
                g.drawRect(gx, gy, gx + 1, gy + 1, dot);
            }
        }
    }

    private static void starsOn(Canvas g, Paint p, Resources res, Astro astro, double d, double starVis, double lim, List<HorizonBackground.Zone> Z) {
        float[] S4 = stars(res);
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i + 3 < S4.length; i += 4) {
            float mag = S4[i + 2];
            if (mag > lim) continue;
            double[] hz = astro.horiz(S4[i] * R, S4[i + 1] * R, d);
            if (hz[1] <= 0) continue;
            float[] q = proj(hz[0], hz[1]);
            if (q[0] < 0 || q[0] > 1 || q[1] < 0) continue;
            double a2 = starVis * sstep(2, 14, hz[1] / R) * Math.max(0, Math.min(1, 0.16 + 0.84 * Math.pow((lim - mag) / (lim + 1.5), 0.9)));
            if (a2 < 0.02) continue;
            int[] col = starColour(S4[i + 3]);
            float rr = (float) ((0.55 + 0.38 * Math.max(0, 2.6 - mag)) * S * 1.15);
            float X = q[0] * W, Y = q[1] * H;
            a2 *= 1 - 0.67 * cover(Z, X, Y);   // a third as bright behind text
            if (mag < 1.6) {
                int in = Color.argb((int) Math.round(Math.min(1, a2 * 0.28) * 255), col[0], col[1], col[2]);
                p.setShader(new RadialGradient(X, Y, rr * 4.2f, in, Color.argb(0, col[0], col[1], col[2]), Shader.TileMode.CLAMP));
                g.drawRect(X - rr * 5, Y - rr * 5, X + rr * 5, Y + rr * 5, p);
                p.setShader(null);
            }
            dot.setColor(Color.argb((int) Math.round(Math.min(1, a2) * 255), col[0], col[1], col[2]));
            g.drawCircle(X, Y, Math.max(rr, 0.35f), dot);
        }
    }

    /** Fine noise baked into the picture (the mockup's grain: the same value added to R, G and B). */
    private static void grain(Bitmap b) {
        int[] px = new int[W * H];
        b.getPixels(px, 0, W, 0, 0, W, H);
        Rng rg = new Rng(99);
        for (int i = 0; i < px.length; i++) {
            int v = px[i], n = (int) Math.round((rg.next() + rg.next() - 1) * 5);
            px[i] = Color.argb(255, clampInt(((v >> 16) & 255) + n), clampInt(((v >> 8) & 255) + n), clampInt((v & 255) + n));
        }
        b.setPixels(px, 0, W, 0, 0, W, H);
    }

    private static int clampInt(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }

    // ---- the readability floor -----------------------------------------------------------------------------------

    /** sRGB to linear, for the 256 values a channel can take (worked out once: the floor reads many pixels). */
    private static final double[] LIN = new double[256];

    static {
        for (int i = 0; i < 256; i++) {
            double v = i / 255.0;
            LIN[i] = v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
    }

    static double lum(int r, int g, int b) {
        return 0.2126 * LIN[clampInt(r)] + 0.7152 * LIN[clampInt(g)] + 0.0722 * LIN[clampInt(b)];
    }

    /** The faintest text's contrast against a block's 90th-percentile background pixel (a luminance histogram). */
    static double ratio(Bitmap b, HorizonBackground.Zone z) {
        int x = Math.max(0, Math.round((z.x + 6) * S)), y = Math.max(0, Math.round((z.y + 6) * S));
        int w = Math.min(W - x, Math.round((z.w - 12) * S)), h = Math.min(H - y, Math.round((z.h - 12) * S));
        if (w < 1 || h < 1) return 99;
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, x, y, w, h);
        return ratioOf(px, z.alpha);
    }

    /** Pure, for the unit test: the contrast of white at `alpha` over the 90th-percentile pixel of `px`. */
    static double ratioOf(int[] px, float alpha) {
        final int BINS = 2048;
        int[] count = new int[BINS], sample = new int[BINS];
        for (int v : px) {
            double L = lum((v >> 16) & 255, (v >> 8) & 255, v & 255);
            int bin = Math.min(BINS - 1, (int) (Math.sqrt(L) * BINS));   // finer bins in the dark, where the sky is
            if (count[bin]++ == 0) sample[bin] = v;
        }
        int want = (int) Math.floor(px.length * 0.9), seen = 0, c = 0;
        for (int i = 0; i < BINS; i++) {
            seen += count[i];
            if (seen > want) {
                c = sample[i];
                break;
            }
        }
        int cr = (c >> 16) & 255, cg = (c >> 8) & 255, cb = c & 255;
        double lt = lum(Math.round(alpha * 255 + (1 - alpha) * cr), Math.round(alpha * 255 + (1 - alpha) * cg), Math.round(alpha * 255 + (1 - alpha) * cb));
        double lb = lum(cr, cg, cb);
        return (lt + 0.05) / (lb + 0.05);
    }

    /** Last of all: a soft oval shadow under any block whose faintest text is below the floor, until it is not. */
    private static void floor(Canvas g, Paint p, Bitmap b, List<HorizonBackground.Zone> Z) {
        for (HorizonBackground.Zone z : Z) {
            double before = ratio(b, z), r = before;
            int pass = 0;
            for (; pass < 30 && r < FLOOR; pass++) {
                glow(g, p, c(0, 0, 0, 1), (z.x + z.w / 2) * S / W, (z.y + z.h / 2) * S / H,
                        (z.w / 2 + 70) * S / W / 0.62, (z.h / 2 + 50) * S / H / 0.62, 0.05);
                r = ratio(b, z);
            }
            Log.i(TAG, String.format(java.util.Locale.ENGLISH, "floor %s: %.2f:1 -> %.2f:1 (%d shadow passes)", z.name, before, r, pass));
        }
    }
}
