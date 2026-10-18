package com.gathin.tvhome;

import java.util.Calendar;

/**
 * The quiet weekend look: all of Saturday and Sunday (local time), never on a personal day. Its colours come from the
 * Horizon look in use, so they follow the time of day: tone 1 from the core, tone 2 from the spot, tone 3 from the
 * band, each kept at its hue as a light pastel. The gold (Settings) and the heart are fixed. In rain or a storm every
 * weekend colour is softened by Horizon's own factor; the football's off-white is not a weekend colour and stays.
 * Pure: worked out once whenever the Horizon state is looked at (the minute tick and when Home appears).
 */
final class Weekend {

    static final int GOLD = 0xFFF5D58B, HEART = 0xFFF2A9BA;
    /** The pastel the tones are made into, and Horizon's softening in rain or a storm. */
    static final float PASTEL_S = 0.40f, PASTEL_V = 0.95f, RAIN = 0.62f;

    final int tone1, tone2, tone3, gold, heart;

    private Weekend(int tone1, int tone2, int tone3, int gold, int heart) {
        this.tone1 = tone1;
        this.tone2 = tone2;
        this.tone3 = tone3;
        this.gold = gold;
        this.heart = heart;
    }

    /** The look's identity, to apply it only when it changes. */
    String key() {
        return tone1 + "|" + tone2 + "|" + tone3 + "|" + gold + "|" + heart;
    }

    /** Saturday or Sunday by this calendar's own clock. */
    static boolean isWeekend(Calendar now) {
        int d = now.get(Calendar.DAY_OF_WEEK);
        return d == Calendar.SATURDAY || d == Calendar.SUNDAY;
    }

    /** The weekend look for now and this Horizon state, or null (a weekday, or a personal day). */
    static Weekend of(Calendar now, HorizonBackground.State s) {
        HorizonBackground.Palette p = s.palette();
        return of(isWeekend(now), s.day != null, s.sky != HorizonBackground.Sky.CLEAR, p.core, p.spot, p.band);
    }

    static Weekend of(boolean weekend, boolean personalDay, boolean wet, int core, int spot, int band) {
        if (!weekend || personalDay) return null;
        float f = wet ? RAIN : 1f;
        return new Weekend(pastel(core, f), pastel(spot, f), pastel(band, f), soften(GOLD, f), soften(HEART, f));
    }

    /** The colour's hue as a light pastel (saturation 0.40 × f, value 0.95), opaque. */
    static int pastel(int argb, float f) {
        float[] hsv = hsv(argb);
        return rgb(hsv[0], PASTEL_S * f, PASTEL_V);
    }

    /** The colour with its saturation multiplied by f, opaque. */
    static int soften(int argb, float f) {
        if (f == 1f) return argb | 0xFF000000;
        float[] hsv = hsv(argb);
        return rgb(hsv[0], hsv[1] * f, hsv[2]);
    }

    static float[] hsv(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min, h = 0;
        if (d > 0) {
            if (max == r) h = ((g - b) / d) % 6;
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h *= 60;
            if (h < 0) h += 360;
        }
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    static int rgb(float h, float s, float v) {
        float c = v * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = v - c, r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }
}
