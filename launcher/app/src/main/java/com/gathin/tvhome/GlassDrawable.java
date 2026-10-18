package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * Liquid-glass surface without blur (this TV runs Android 11, and blur is too heavy anyway):
 * an optional dark base, a translucent white sheen, a colour tint, a rim that is brighter along the top
 * edge and a faint inner highlight just inside it. Only rounded rects and gradients, no clip paths.
 * Base, sheen and tint are blended into one gradient up front, so the GPU fills the area once:
 * this TV redraws the whole screen on every animated frame and is limited by fill rate.
 */
final class GlassDrawable extends Drawable {

    private final float radius;
    private final float density;
    private final int base;
    private final int sheenTop, sheenBottom;
    private int tint = Palette.NEUTRAL.container;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private boolean innerHighlight = true;

    /**
     * @param base      opaque or translucent colour under the glass (0 for none)
     * @param sheenTop  white sheen at the top edge (ARGB)
     * @param sheenBottom white sheen at the bottom edge (ARGB)
     */
    GlassDrawable(float radius, float density, int base, int sheenTop, int sheenBottom) {
        this.radius = radius;
        this.density = density;
        this.base = base;
        this.sheenTop = sheenTop;
        this.sheenBottom = sheenBottom;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(density);
        inner.setStyle(Paint.Style.STROKE);
        inner.setStrokeWidth(density);
    }

    /** Leave out the inner highlight (worth it on small pills, not on large surfaces). */
    GlassDrawable withoutInnerHighlight() {
        innerHighlight = false;
        return this;
    }

    /** Sets the tint (a palette's container colour) and redraws. */
    void setGlassTint(int color) {
        if (tint == color) return;
        tint = color;
        updateFill();
        invalidateSelf();
    }

    private void updateFill() {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        int top = over(over(base, sheenTop), tint);
        int bottom = over(over(base, sheenBottom), tint);
        fill.setShader(new LinearGradient(0, b.top, 0, b.bottom, top, bottom, Shader.TileMode.CLAMP));
    }

    /** Source-over compositing of two non-premultiplied ARGB colours. */
    private static int over(int dst, int src) {
        float sa = (src >>> 24) / 255f, da = (dst >>> 24) / 255f;
        float oa = sa + da * (1f - sa);
        if (oa <= 0f) return 0;
        int r = Math.round((((src >> 16) & 0xFF) * sa + ((dst >> 16) & 0xFF) * da * (1f - sa)) / oa);
        int g = Math.round((((src >> 8) & 0xFF) * sa + ((dst >> 8) & 0xFF) * da * (1f - sa)) / oa);
        int bl = Math.round(((src & 0xFF) * sa + (dst & 0xFF) * da * (1f - sa)) / oa);
        return Math.round(oa * 255f) << 24 | r << 16 | g << 8 | bl;
    }

    @Override
    protected void onBoundsChange(Rect b) {
        super.onBoundsChange(b);
        float t = b.top, h = b.height();
        updateFill();
        // Rim: bright along the top, fading down the sides, faint at the bottom (light from above).
        rim.setShader(new LinearGradient(0, t, 0, t + h,
                new int[]{0x8CFFFFFF, 0x24FFFFFF, 0x14FFFFFF}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        // Inner highlight: only the top third, so it reads as thickness at the upper lip of the glass.
        inner.setShader(new LinearGradient(0, t, 0, t + h * 0.34f, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(@NonNull Canvas c) {
        Rect b = getBounds();
        float half = density / 2f;
        rect.set(b.left + half, b.top + half, b.right - half, b.bottom - half);
        float r = Math.min(radius, rect.height() / 2f);
        c.drawRoundRect(rect, r, r, fill);
        c.drawRoundRect(rect, r, r, rim);
        if (!innerHighlight) return;
        float in = 1.5f * density;
        rect.inset(in, in);
        c.drawRoundRect(rect, Math.max(0f, r - in), Math.max(0f, r - in), inner);
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
