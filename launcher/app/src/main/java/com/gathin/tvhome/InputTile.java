package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * The banner of the dock's input tile (config dock_input, the Xbox on HDMI 2), Parth's pick "3B" (22 Oct): a controller
 * shaped like the Xbox one rises half-hidden over a horizon, like the home screen's sun. On (the device on that port is
 * on): a deep-green night sky with a few stars and a green glow, the controller white with A, B, X and Y in their
 * colours, and no words for it. Off: greys, and "Off" under the name. Only whole controls show above the horizon (left stick, guide, ABXY); the D-pad and right stick
 * are below it. The name top left. Drawn, not an image; nothing moves.
 */
final class InputTile extends Drawable {

    /** The controller's outline, in a 200 x 140 box. */
    private static final Path BODY = new Path();

    static {
        Path p = BODY;
        p.moveTo(42, 22);
        p.cubicTo(62, 12, 138, 12, 158, 22);
        p.cubicTo(184, 33, 196, 62, 198, 96);
        p.cubicTo(200, 126, 181, 139, 165, 129);
        p.cubicTo(150, 119, 141, 101, 121, 99);
        p.lineTo(79, 99);
        p.cubicTo(59, 101, 50, 119, 35, 129);
        p.cubicTo(19, 139, 0, 126, 2, 96);
        p.cubicTo(4, 62, 16, 33, 42, 22);
        p.close();
    }

    /** Stars on the "on" sky, as fractions of the tile: x, y, radius in tile heights. */
    private static final float[][] STARS = {{0.11f, 0.35f, 0.005f}, {0.83f, 0.20f, 0.0075f}, {0.69f, 0.45f, 0.005f},
            {0.31f, 0.22f, 0.005f}, {0.92f, 0.55f, 0.005f}, {0.53f, 0.15f, 0.005f}};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG), sky = new Paint(), text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final String name;
    private boolean on;
    private int shaderW, shaderH;
    private boolean shaderOn;

    InputTile(Context c, String name) {
        this.name = name;
        text.setTypeface(Fonts.weight(c, 700));
    }

    /** True: the device is on (the port reads "connected"). Returns whether the look changed. */
    boolean setOn(boolean now) {
        boolean changed = now != on;
        on = now;
        if (changed) invalidateSelf();
        return changed;
    }

    boolean isOn() {
        return on;
    }

    /** The sky, made again only when the size or the state changes. */
    private void sky(float w, float h) {
        if (shaderW == (int) w && shaderH == (int) h && shaderOn == on && sky.getShader() != null) return;
        shaderW = (int) w;
        shaderH = (int) h;
        shaderOn = on;
        if (on) {
            // CSS radial-gradient(ellipse 60% 55% at 50% 82%): a circle of the width's 60%, squashed to 55% of the height
            float rx = 0.6f * w, ry = 0.55f * h;
            RadialGradient g = new RadialGradient(0, 0, rx, new int[]{0xFF3EC43E, 0xFF0F5F0F, 0xFF062B10, 0xFF031A0A},
                    new float[]{0f, 0.45f, 0.8f, 1f}, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            m.setScale(1f, ry / rx);
            m.postTranslate(0.5f * w, 0.82f * h);
            g.setLocalMatrix(m);
            sky.setShader(g);
        } else {
            sky.setShader(new LinearGradient(0, 0, 0, h, 0xFF262832, 0xFF1B1C22, Shader.TileMode.CLAMP));
        }
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float w = b.width(), h = b.height();
        c.save();
        c.clipRect(b);
        c.translate(b.left, b.top);
        sky(w, h);
        c.drawRect(0, 0, w, h, sky);
        if (on) {
            paint.setColor(0xB3D9FFD9);
            for (float[] s : STARS) c.drawCircle(s[0] * w, s[1] * h, Math.max(1f, s[2] * h * 2), paint);
        }

        // the controller: 0.58 of the tile's width, centred, its top at 0.35 of the height; the horizon at 0.76
        float s = w * 0.583f / 200f, x0 = (w - 200 * s) / 2f, y0 = 0.35f * h, horizon = 0.76f * h;
        int body = on ? 0xFFFFFFFF : 0xFF3A3D46, key = on ? 0xFF0F5F0F : 0xFF262832, off = 0xFF545964;
        c.save();
        c.clipRect(0, 0, w, horizon);
        c.translate(x0, y0);
        c.scale(s, s);
        paint.setColor(body);
        c.drawPath(BODY, paint);
        paint.setColor(key);
        c.drawCircle(55, 52, 15, paint);
        c.drawCircle(100, 36, 8, paint);
        paint.setColor(body);
        c.drawCircle(55, 52, 9, paint);
        button(c, 148, 38, on ? 0xFFF2C200 : off);
        button(c, 134, 52, on ? 0xFF1F6FE5 : off);
        button(c, 162, 52, on ? 0xFFE5323B : off);
        button(c, 148, 66, on ? 0xFF2FB52F : off);
        c.restore();

        paint.setColor(on ? 0xFF020F06 : 0xFF16171C);
        c.drawRect(0, horizon, w, h, paint);

        text.setColor(on ? 0xFFFFFFFF : 0xFF8A8F9C);
        text.setTextSize(h * 0.14f);
        float base = h * 0.07f - text.ascent();
        c.drawText(name, w * 0.061f, base, text);
        if (!on) {   // off says so in words; on is said by the colour alone (Parth, 22 Oct)
            text.setColor(0xFF6F7480);
            text.setTextSize(h * 0.085f);
            c.drawText("Off", w * 0.061f, base + text.getTextSize() * 1.35f, text);
        }
        c.restore();
    }

    private void button(Canvas c, float x, float y, int colour) {
        paint.setColor(colour);
        c.drawCircle(x, y, 7, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        sky.setAlpha(alpha);
        text.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
        sky.setColorFilter(cf);
        text.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
