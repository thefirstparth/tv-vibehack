package com.gathin.tv1400;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** A rule along a view's top edge (and, for a table's last row, its bottom): the paper's 1px rules and 4px tab rules. */
final class TopRule extends Drawable {

    private final Paint paint = new Paint();
    private final int px;
    private final boolean bottom;

    TopRule(int colour, int px) {
        this(colour, px, false);
    }

    TopRule(int colour, int px, boolean bottom) {
        paint.setColor(colour);
        this.px = Math.max(1, px);
        this.bottom = bottom;
    }

    @Override
    public void draw(Canvas c) {
        android.graphics.Rect b = getBounds();
        c.drawRect(b.left, b.top, b.right, b.top + px, paint);
        if (bottom) c.drawRect(b.left, b.bottom - px, b.right, b.bottom, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter f) {
        paint.setColorFilter(f);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
