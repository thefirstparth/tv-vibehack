package com.gathin.tvhome;

import android.graphics.Color;

/**
 * A few tones derived from one app's seed colour (Material-style tonal palette, simplified to HSV maths).
 * Cheap to build, so only the seed is stored; see AppAdapter for the per-app cache.
 */
final class Palette {

    /** Neutral palette for the top bar before any tile has been focused, and for grey banners. */
    static final Palette NEUTRAL = new Palette(0xFF8E8E93);

    /** The vivid seed itself: the ambient glow behind the top shelf. */
    final int seed;
    /** Dark, translucent: tints glass surfaces (top bar, dock band, Inputs menu). */
    final int container;
    /** Light: the fill of a focused pill or menu row. */
    final int light;
    /** Text and icons on {@link #light}. */
    final int onLight;
    /** A soft tint for the glass highlight's rim. */
    final int rim;

    Palette(int seed) {
        this.seed = seed;
        float[] hsv = new float[3];
        Color.colorToHSV(seed, hsv);
        float h = hsv[0], s = hsv[1];
        if (s < 0.15f) {   // near grey (e.g. a white banner): stay neutral rather than inventing a hue
            container = 0x405A5A60;
            light = 0xFFF2F2F4;
            onLight = 0xFF111114;
            rim = 0xCCFFFFFF;
            return;
        }
        container = withAlpha(Color.HSVToColor(new float[]{h, Math.min(s, 0.75f) * 0.85f, 0.42f}), 0x48);
        light = Color.HSVToColor(new float[]{h, Math.min(0.12f + s * 0.22f, 0.32f), 0.98f});
        onLight = Color.HSVToColor(new float[]{h, Math.min(1f, s * 0.9f), 0.22f});
        rim = withAlpha(Color.HSVToColor(new float[]{h, s * 0.35f, 1f}), 0xCC);
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
