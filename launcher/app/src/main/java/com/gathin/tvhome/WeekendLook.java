package com.gathin.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

/**
 * The weekend look, built once per weekend state (off the main thread, after the first frame) and then only reused:
 * the start of the line above the clock ("WEEKEND" and the four symbols), the pills' icon tints and the dock edge's
 * colours. Nothing here is made on a weekday.
 */
final class WeekendLook {

    final CharSequence lead;
    final ColorStateList[] pillTints;   // newspaper, Inputs, Wi-Fi, Settings
    final int[] edge;                   // the dock's top edge, left to right

    private WeekendLook(CharSequence lead, ColorStateList[] pillTints, int[] edge) {
        this.lead = lead;
        this.pillTints = pillTints;
        this.edge = edge;
    }

    static WeekendLook build(Context ctx, Weekend w, float density) {
        SpannableStringBuilder b = new SpannableStringBuilder("WEEKEND");
        b.setSpan(new ForegroundColorSpan(w.tone1), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int[] icons = {R.drawable.wk_football, R.drawable.wk_note, R.drawable.wk_cocktail, R.drawable.wk_heart};
        int[] tints = {0, w.tone3, w.tone2, w.heart};   // the football is never tinted
        for (int i = 0; i < icons.length; i++) {
            Drawable d = ctx.getDrawable(icons[i]).mutate();
            if (tints[i] != 0) d.setTint(tints[i]);
            int at = b.length();
            b.append('￼');
            b.setSpan(new GlyphSpan(d, (int) (13 * density), (int) ((i == 0 ? 6 : 5) * density)), at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        int[][] states = {{android.R.attr.state_focused}, {}};
        int[] colours = {w.tone1, w.tone2, w.tone3, w.gold};
        ColorStateList[] pills = new ColorStateList[colours.length];
        for (int i = 0; i < colours.length; i++) pills[i] = new ColorStateList(states, new int[]{0xFF111111, colours[i]});   // focused: as always
        int a = 0x99000000;   // about 60%
        int[] edge = {(w.tone1 & 0xFFFFFF) | a, (w.gold & 0xFFFFFF) | a, (w.tone2 & 0xFFFFFF) | a, (w.tone3 & 0xFFFFFF) | a};
        return new WeekendLook(b, pills, edge);
    }
}
