package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Outfit at fixed weights, one Typeface per weight, made once and shared by every view. A layout's
 * fontVariationSettings builds a separate font instance for each view that names it (on this TV about 170 ms of
 * startup and several MB for the home screen), so views name their weight in their tag instead ("w600") and get it
 * here. The look is the same font at the same weights.
 */
final class Fonts {

    private static final SparseArray<Typeface> BY_WEIGHT = new SparseArray<>();

    private Fonts() {
    }

    static Typeface weight(Context c, int w) {
        Typeface t = BY_WEIGHT.get(w);
        if (t == null) {
            Paint p = new Paint();
            p.setTypeface(c.getResources().getFont(R.font.outfit));
            p.setFontVariationSettings("'wght' " + w);
            t = p.getTypeface();
            BY_WEIGHT.put(w, t);
        }
        return t;
    }

    /** Gives every TextView under v with a "wNNN" tag its shared weight. */
    static void apply(View v) {
        if (v instanceof TextView && v.getTag() instanceof String) {
            String tag = (String) v.getTag();
            if (tag.length() > 1 && tag.charAt(0) == 'w') ((TextView) v).setTypeface(weight(v.getContext(), Integer.parseInt(tag.substring(1))));
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) apply(g.getChildAt(i));
        }
    }
}
