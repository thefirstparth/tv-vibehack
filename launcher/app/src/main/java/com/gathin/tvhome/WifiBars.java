package com.gathin.tvhome;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * The Wi-Fi pill's icon: a dot and three arcs, as many lit as the signal is strong (0 to 3), the rest faint. It takes
 * the pill's text colour for its state (white, dark when focused). Redrawn only when the level or the focus changes.
 */
final class WifiBars extends Drawable {

    private final float dp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private ColorStateList tint;
    private int bars;

    WifiBars(float density) {
        dp = density;
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    /** Arcs lit: 0 (weak) to 3 (full). */
    void setBars(int n) {
        if (n == bars) return;
        bars = n;
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas c) {
        int colour = tint != null ? tint.getColorForState(getState(), 0xFFFFFFFF) : 0xFFFFFFFF;
        float cx = getBounds().exactCenterX(), base = getBounds().bottom - 3 * dp;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(colour);
        c.drawCircle(cx, base, 1.7f * dp, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f * dp);
        for (int i = 1; i <= 3; i++) {
            float r = (1.5f + 4.2f * i) * dp;
            arc.set(cx - r, base - r, cx + r, base + r);
            paint.setColor(i <= bars ? colour : (colour & 0x00FFFFFF) | 0x4D000000);
            c.drawArc(arc, 225, 90, false, paint);
        }
    }

    @Override
    public int getIntrinsicWidth() {
        return Math.round(20 * dp);
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(18 * dp);
    }

    @Override
    public void setTintList(ColorStateList t) {
        tint = t;
        invalidateSelf();
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        invalidateSelf();
        return true;
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
