package com.gathin.tv1400;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.gathin.tvshared.Feed;
import com.gathin.tvshared.PersonalDays;
import com.gathin.tvshared.SunLine;

/**
 * Horizon V3's sun: a strip of newsprint dots between the content and the foot (y 850 to 940 on a 1920 x 1080 screen),
 * never behind text, and the double rule under it at y 940. A 16 px grid, three dot sizes in the mood's three inks, the
 * biggest and densest at the sun's x and fading out to the sides and upwards. Drawn once per look into one small bitmap
 * (off the main thread); nothing moves.
 */
final class Halftone {

    /** One mood's inks (fine, mid, core), the sun's x (fraction of the width), the rule, and the dots' opacities. */
    static final class Inks {
        final int fine, mid, core, rule;
        final float x, fineAlpha, alpha;

        Inks(int fine, int mid, int core, float x, int rule, float fineAlpha, float alpha) {
            this.fine = fine;
            this.mid = mid;
            this.core = core;
            this.x = x;
            this.rule = rule;
            this.fineAlpha = fineAlpha;
            this.alpha = alpha;
        }
    }

    private Halftone() {
    }

    /** The design's eight moods (the same timing as Horizon v2, SunLine.Phase). */
    static Inks of(SunLine.Phase p) {
        switch (p) {
            case LATE_NIGHT: return new Inks(0xFF4B4F9E, 0xFF6A63C4, 0xFF8A7FE0, .42f, rgba(138, 127, 224, .45), .38f, .50f);
            case DAWN: return new Inks(0xFFB7608A, 0xFFE8907A, 0xFFFFC29A, .22f, rgba(232, 144, 122, .55), .42f, .62f);
            case MORNING: return new Inks(0xFFC9874A, 0xFFF0B070, 0xFFFFD59A, .30f, rgba(240, 176, 112, .55), .40f, .60f);
            case MIDDAY: return new Inks(0xFF3F86B8, 0xFF7FB8D8, 0xFFBFE3F0, .50f, rgba(127, 184, 216, .50), .40f, .58f);
            case GOLDEN_HOUR: return new Inks(0xFFC98A2E, 0xFFF2B04A, 0xFFFFD37A, .66f, rgba(242, 176, 74, .55), .42f, .64f);
            case SUNSET: return new Inks(0xFF9A3F7A, 0xFFEF7A5A, 0xFFFFB08A, .76f, rgba(239, 122, 90, .60), .44f, .66f);
            case BLUE_HOUR: return new Inks(0xFF3D4AA8, 0xFF6C7FD6, 0xFFC48AB8, .80f, rgba(108, 127, 214, .50), .40f, .58f);
            default: return new Inks(0xFF3A3F7A, 0xFF5A64B0, 0xFFCFC6B0, .50f, rgba(90, 100, 176, .45), .34f, .42f);
        }
    }

    /** On a personal day the inks take that day's Horizon v2 colours (band, spot, core); the sun stays where the mood puts it. */
    static Inks day(Inks m, PersonalDays.Day d) {
        return new Inks(opaque(d.band), opaque(d.spot), opaque(d.core), m.x, (d.core & 0x00FFFFFF) | (0x80 << 24), m.fineAlpha, m.alpha);
    }

    /** Rain or a storm: the inks lose about 40% of their saturation. */
    static Inks grey(Inks m) {
        return new Inks(sat(m.fine), sat(m.mid), sat(m.core), m.x, (sat(m.rule) & 0x00FFFFFF) | (m.rule & 0xFF000000), m.fineAlpha, m.alpha);
    }

    static Inks look(SunLine.Phase p, Feed.Sky sky, PersonalDays.Day d) {
        Inks m = of(p);
        if (d != null) m = day(m, d);
        return sky == Feed.Sky.CLEAR ? m : grey(m);
    }

    /** The strip, w pixels wide (the screen's): y 850 to 944 of a 1920 x 1080 screen, scaled. */
    static Bitmap draw(Inks m, int w) {
        float s = w / 1920f;
        int h = Math.round(94 * s);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float sx = m.x * w, bottom = 90 * s, bandH = 90 * s;
        // three layers on one grid (centred, bottom aligned), each masked by an ellipse at the sun's x on the bottom edge
        float[][] layers = {
                // dot radius px, mask radii (fraction of width, of height), mask stops (at, alpha) x2, opacity, which ink
                {1.3f, .62f, 1.20f, .40f, .40f, .72f, m.fineAlpha, 0},
                {2.8f, .34f, 1.00f, .45f, .50f, .80f, m.alpha, 1},
                {4.7f, .15f, .80f, .50f, .60f, .90f, m.alpha, 2}};
        float g = 16 * s;
        for (float[] L : layers) {
            int ink = L[7] == 0 ? m.fine : L[7] == 1 ? m.mid : m.core;
            int n = (int) Math.ceil(w / 2f / g) + 1;
            for (float y = bottom - g / 2; y > 0; y -= g) {
                for (int k = -n; k <= n; k++) dot(c, p, ink, w / 2f + k * g, y, L, sx, bottom, w, bandH, s);
            }
        }
        // the double rule: 1px, 2px gap, 1px, from x 96 to 1824
        p.setColor(m.rule);
        p.setStrokeWidth(Math.max(1, Math.round(s)));
        for (float y : new float[]{90 * s + .5f, 93 * s + .5f}) c.drawLine(96 * s, y, 1824 * s, y, p);
        return b;
    }

    private static void dot(Canvas c, Paint p, int ink, float x, float y, float[] L, float sx, float bottom, int w, float bandH, float s) {
        float dx = (x - sx) / (L[1] * w), dy = (y - bottom) / (L[2] * bandH), d = (float) Math.sqrt(dx * dx + dy * dy);
        // the mask's gradient: full at the centre, L[4] at L[3], none from L[5]
        float a = d <= 0 ? 1 : d < L[3] ? 1 - (1 - L[4]) * d / L[3] : d < L[5] ? L[4] * (L[5] - d) / (L[5] - L[3]) : 0;
        a *= L[6];
        if (a <= 0.01f) return;
        p.setColor(ink);
        p.setAlpha(Math.round(255 * a));
        c.drawCircle(x, y, (L[0] + .25f) * s, p);
    }

    private static int sat(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[1] *= 0.6f;
        return Color.HSVToColor(Color.alpha(c), hsv);
    }

    private static int opaque(int c) {
        return c | 0xFF000000;
    }

    static int rgba(int r, int g, int b, double a) {
        return Color.argb((int) Math.round(a * 255), r, g, b);
    }
}
