package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * The banner of the dock's input tile (config dock_input, the Xbox on HDMI 2), Parth's pick "peek from the left"
 * (22 Oct): a big controller shaped like the Xbox one peeks in from the left edge, tilted, with the name bottom right.
 * On (the device on that port is on): Xbox green, the controller white, its A, B, X and Y in their colours. Off: all
 * greys. Drawn, not an image, so the two looks are one small object; nothing moves.
 */
final class InputTile extends Drawable {

    private static final int[] ON = {0xFF107C10, 0xFFFFFFFF, 0xFF0F6E0F, 0xFF2FB52F, 0xFFE5323B, 0xFF1F6FE5, 0xFFF2C200, 0xFFFFFFFF};
    private static final int[] OFF = {0xFF26282F, 0xFF3A3D46, 0xFF2C2F36, 0xFF545964, 0xFF545964, 0xFF545964, 0xFF545964, 0xFF8A8F9C};
    private static final int BG = 0, BODY = 1, KEY = 2, A = 3, B = 4, X = 5, Y = 6, NAME = 7;

    /** The controller, in a 200 x 140 box: two grips, offset sticks, a D-pad, the guide button, ABXY. */
    private static final Path BODY_PATH = new Path(), DPAD = new Path();

    static {
        Path p = BODY_PATH;
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
        DPAD.addRect(74, 72, 98, 80, Path.Direction.CW);
        DPAD.addRect(82, 64, 90, 88, Path.Direction.CW);
        DPAD.setFillType(Path.FillType.WINDING);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final String name;
    private boolean on;

    InputTile(Context c, String name) {
        this.name = name;
        text.setTypeface(Fonts.weight(c, 700));
        text.setTextAlign(Paint.Align.RIGHT);
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

    @Override
    public void draw(Canvas c) {
        int[] k = on ? ON : OFF;
        Rect b = getBounds();
        float w = b.width(), h = b.height();
        c.save();
        c.clipRect(b);
        c.translate(b.left, b.top);
        paint.setColor(k[BG]);
        c.drawRect(0, 0, w, h, paint);

        // the controller: 0.83 of the tile's width, pushed 0.31 of it past the left edge, tilted 10 degrees
        float s = w * 0.83f / 200f, bw = 200 * s, bh = 140 * s, x0 = -0.305f * w, y0 = 0.11f * h;
        c.save();
        c.rotate(10, x0 + bw / 2, y0 + bh / 2);
        c.translate(x0, y0);
        c.scale(s, s);
        paint.setColor(k[BODY]);
        c.drawPath(BODY_PATH, paint);
        paint.setColor(k[KEY]);
        c.drawCircle(55, 52, 15, paint);
        c.drawCircle(123, 80, 13, paint);
        c.drawCircle(100, 36, 8, paint);
        c.drawPath(DPAD, paint);
        paint.setColor(k[BODY]);
        c.drawCircle(55, 52, 9, paint);
        c.drawCircle(123, 80, 8, paint);
        button(c, 148, 38, k[Y]);
        button(c, 134, 52, k[X]);
        button(c, 162, 52, k[B]);
        button(c, 148, 66, k[A]);
        c.restore();

        text.setColor(k[NAME]);
        text.setTextSize(h * 0.16f);
        c.drawText(name, w - w * 0.061f, h - h * 0.08f - text.descent(), text);
        c.restore();
    }

    private void button(Canvas c, float x, float y, int colour) {
        paint.setColor(colour);
        c.drawCircle(x, y, 7, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        text.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
        text.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
