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
 * The banner of the dock's input tile (config dock_input, the Xbox on HDMI 2), Parth's pick "starry rise" (22 Oct): a
 * fun, simple controller with a few Xbox tells (bumper bumps, offset sticks, the guide button, ABXY in colour) rises out
 * of the tile's bottom edge into a starry green night. On (the device on that port is on): a bright green glow from
 * below, white stars, the controller white; no words for it. Off: greys, and "Off" under the name. Drawn, not an image;
 * nothing moves.
 */
final class InputTile extends Drawable {

    /** The controller, in a 200 x 140 box. */
    private static final Path BODY = new Path(), DPAD = new Path();

    static {
        Path p = BODY;
        p.moveTo(40, 24);
        p.cubicTo(46, 14, 72, 12, 84, 18);
        p.lineTo(116, 18);
        p.cubicTo(128, 12, 154, 14, 160, 24);
        p.cubicTo(186, 34, 197, 64, 198, 96);
        p.cubicTo(200, 126, 181, 139, 165, 129);
        p.cubicTo(150, 119, 141, 101, 121, 99);
        p.lineTo(79, 99);
        p.cubicTo(59, 101, 50, 119, 35, 129);
        p.cubicTo(19, 139, 0, 126, 2, 96);
        p.cubicTo(3, 64, 14, 34, 40, 24);
        p.close();
        DPAD.addRect(75, 76, 99, 84, Path.Direction.CW);
        DPAD.addRect(83, 68, 91, 92, Path.Direction.CW);
    }

    /** Stars on the "on" sky: x, y as fractions of the tile, radius as a fraction of its height, opacity. */
    private static final float[][] STARS = {{0.417f, 0.14f, 0.005f, 0.8f}, {0.833f, 0.18f, 0.0075f, 0.9f}, {0.694f, 0.35f, 0.005f, 0.6f},
            {0.917f, 0.48f, 0.005f, 0.6f}, {0.556f, 0.28f, 0.005f, 0.7f}, {0.333f, 0.32f, 0.005f, 0.5f}, {0.75f, 0.10f, 0.005f, 0.7f}};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG), sky = new Paint(), text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final String name;
    private boolean on;
    private int skyW, skyH;
    private boolean skyOn;

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
        if (skyW == (int) w && skyH == (int) h && skyOn == on && sky.getShader() != null) return;
        skyW = (int) w;
        skyH = (int) h;
        skyOn = on;
        if (on) {
            // a glow from below the bottom edge: an ellipse 75% of the width by 85% of the height, centred just under it
            float rx = 0.75f * w, ry = 0.85f * h;
            RadialGradient g = new RadialGradient(0, 0, rx, new int[]{0xFF6FDC3F, 0xFF2AA52A, 0xFF11741A, 0xFF0B4A1A, 0xFF072F12},
                    new float[]{0f, 0.30f, 0.58f, 0.82f, 1f}, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            m.setScale(1f, ry / rx);
            m.postTranslate(0.5f * w, 1.05f * h);
            g.setLocalMatrix(m);
            sky.setShader(g);
        } else {
            sky.setShader(new LinearGradient(0, 0, 0, h, 0xFF2A2C34, 0xFF1E2026, Shader.TileMode.CLAMP));
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
            for (float[] s : STARS) {
                paint.setColor(0xE9FFE0 | (Math.round(s[3] * 255) << 24));
                c.drawCircle(s[0] * w, s[1] * h, Math.max(1f, s[2] * h * 2), paint);
            }
        }

        // the controller: 0.61 of the tile's width, from 0.19 of it in, its top at 0.46 of the height; the bottom edge cuts it
        float s = w * 0.611f / 200f;
        int body = on ? 0xFFFFFFFF : 0xFF3A3D46, key = on ? 0xFF0F5F0F : 0xFF262832, dim = 0xFF4E535E;
        c.save();
        c.translate(0.194f * w, 0.46f * h);
        c.scale(s, s);
        paint.setColor(body);
        c.drawPath(BODY, paint);
        paint.setColor(key);
        c.drawCircle(56, 54, 15, paint);
        c.drawCircle(124, 82, 13, paint);
        c.drawPath(DPAD, paint);
        paint.setColor(body);
        c.drawCircle(56, 54, 8, paint);
        c.drawCircle(124, 82, 7, paint);
        dot(c, 100, 38, 9, on ? 0xFF3EC43E : dim);
        dot(c, 146, 40, 8, on ? 0xFFF2C200 : dim);
        dot(c, 131, 54, 8, on ? 0xFF1F6FE5 : dim);
        dot(c, 161, 54, 8, on ? 0xFFE5323B : dim);
        dot(c, 146, 68, 8, on ? 0xFF2FB52F : dim);
        c.restore();

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

    private void dot(Canvas c, float x, float y, float r, int colour) {
        paint.setColor(colour);
        c.drawCircle(x, y, r, paint);
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
