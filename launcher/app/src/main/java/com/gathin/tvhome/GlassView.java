package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * A translucent glass slab: soft sheen fill, bright top specular, crisp rim and a faint outer glow.
 * One instance floats over the grid and is moved with spring animations between tiles.
 */
public class GlassView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specular = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private float radius;

    // Sheen: a diagonal band of light that crosses the glass once (see sweep()).
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix sheenMatrix = new Matrix();
    private float sheenBand;
    private float sheenProgress = -1f;   // < 0: not sweeping
    private ValueAnimator sheenAnim;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 20f * density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.5f * density);
        rim.setColor(0xCCFFFFFF);
        glow.setStyle(Paint.Style.STROKE);
    }

    void setRadius(float px) {
        radius = px;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fill.setShader(new LinearGradient(0, 0, 0, h, 0x3DFFFFFF, 0x12FFFFFF, Shader.TileMode.CLAMP));
        specular.setShader(new LinearGradient(0, 0, 0, h * 0.45f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        sheenBand = w * 0.32f;
        sheenPaint.setShader(new LinearGradient(0, 0, sheenBand, sheenBand * 0.6f,
                new int[]{0x00FFFFFF, 0x38FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
    }

    /** One pass of light across the glass. Finishes on its own; nothing keeps running. */
    void sweep() {
        cancelSweep();
        sheenAnim = ValueAnimator.ofFloat(0f, 1f);
        sheenAnim.setDuration(620);
        sheenAnim.setInterpolator(new DecelerateInterpolator(1.4f));
        sheenAnim.addUpdateListener(a -> {
            sheenProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        sheenAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                sheenProgress = -1f;
                invalidate();
            }
        });
        sheenAnim.start();
    }

    void cancelSweep() {
        if (sheenAnim != null) sheenAnim.cancel();
        sheenAnim = null;
        if (sheenProgress >= 0f) {
            sheenProgress = -1f;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = 6f * density;   // room for the glow
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);

        // Outer glow: three widening, fading strokes.
        for (int i = 1; i <= 3; i++) {
            glow.setStrokeWidth(i * 2f * density);
            glow.setColor((0x30 / i) << 24 | 0xFFFFFF);
            float o = i * density;
            canvas.drawRoundRect(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o, radius + o, radius + o, glow);
        }

        canvas.drawRoundRect(rect, radius, radius, fill);

        // Rounded shapes with fading shaders instead of a clip path: path clips are slow on this GPU.
        // The specular gradient is transparent below 45% of the height (CLAMP), so it stays at the top.
        canvas.drawRoundRect(rect, radius, radius, specular);
        if (sheenProgress >= 0f) {
            // Travels from fully left of the glass to fully right of it.
            float x = -sheenBand + sheenProgress * (getWidth() + sheenBand);
            sheenMatrix.setTranslate(x, 0);
            sheenPaint.getShader().setLocalMatrix(sheenMatrix);
            canvas.drawRoundRect(rect, radius, radius, sheenPaint);
        }

        canvas.drawRoundRect(rect, radius, radius, rim);
    }
}
