package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;

/** A small drawing in a line of text, centred on the line's capital letters (the weekend's symbols). */
final class GlyphSpan extends ReplacementSpan {

    private final Drawable drawable;
    private final int size, gap;
    private final Rect caps = new Rect();

    GlyphSpan(Drawable drawable, int size, int gapBefore) {
        this.drawable = drawable;
        this.size = size;
        this.gap = gapBefore;
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        return gap + size;
    }

    @Override
    public void draw(@NonNull Canvas c, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        paint.getTextBounds("H", 0, 1, caps);
        int mid = y + caps.top / 2, left = (int) x + gap;
        drawable.setBounds(left, mid - size / 2, left + size, mid - size / 2 + size);
        drawable.draw(c);
    }
}
