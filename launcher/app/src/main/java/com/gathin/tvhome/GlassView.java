package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * The focus highlight: a crisp rim around the focused tile and a faint glow outside it. Nothing is drawn over the
 * tile itself.
 * One instance floats over the grid and is moved with spring animations between tiles.
 */
public class GlassView extends View {

    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private float radius;
    /**
     * The layer sits on whole pixels (a layer drawn at a fractional position is filtered, which softens the rim); the
     * fraction left over is drawn inside the layer instead, so at rest every pixel is where it was without the layer.
     * While the spring moves it, the fraction is left out, so the layer is not redrawn on every frame.
     */
    private float fracX, fracY;
    private boolean moving;
    /** The layer is this many pixels wider and taller than the highlight, so the fraction never pushes it off the edge. */
    static final int SPARE = 1;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 20f * density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.5f * density);
        rim.setColor(0xCCFFFFFF);
        glow.setStyle(Paint.Style.STROKE);
        // Cache the highlight as a texture: the spring only changes its translation (and the fade its alpha), so the
        // GPU moves the texture instead of replaying the rim and the three glow strokes on every frame. It is redrawn
        // only when its size or radius changes. The strokes themselves are unchanged, so it looks the same.
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    public void setTranslationX(float x) {
        float whole = (float) Math.floor(x);
        super.setTranslationX(whole);
        setFraction(x - whole, fracY);
    }

    @Override
    public void setTranslationY(float y) {
        float whole = (float) Math.floor(y);
        super.setTranslationY(whole);
        setFraction(fracX, y - whole);
    }

    /** True while the spring moves it (the fraction is left out), false once it has come to rest. */
    void setMoving(boolean on) {
        if (moving == on) return;
        moving = on;
        if (fracX != 0f || fracY != 0f) invalidate();
    }

    private void setFraction(float x, float y) {
        if (x == fracX && y == fracY) return;
        fracX = x;
        fracY = y;
        if (!moving) invalidate();
    }

    void setRadius(float px) {
        radius = px;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!moving) canvas.translate(fracX, fracY);
        int w = getWidth() - SPARE, h = getHeight() - SPARE;
        canvas.clipRect(0, 0, w, h);   // the highlight's own edges, as when it was drawn without a layer
        float inset = 6f * density;   // room for the glow
        rect.set(inset, inset, w - inset, h - inset);

        // Outer glow: three widening, fading strokes.
        for (int i = 1; i <= 3; i++) {
            glow.setStrokeWidth(i * 2f * density);
            glow.setColor((0x30 / i) << 24 | 0xFFFFFF);
            float o = i * density;
            canvas.drawRoundRect(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o, radius + o, radius + o, glow);
        }

        // Only the rim and the glow outside it: nothing is drawn over the tile, so its logo shows as its app made it
        // (a sheen across the tile washed out the top of every banner, worst on coloured ones).
        canvas.drawRoundRect(rect, radius, radius, rim);
    }
}
