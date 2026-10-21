package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * The banner of the dock's input tile (config dock_input, the Xbox on HDMI 2): the name in the console's green while
 * the device on that port is on, grey while it is off. Drawn, not an image, so the two looks are one small object.
 */
final class InputTile extends Drawable {

    static final int ON_BG = 0xFF107C10, ON_TEXT = Color.WHITE, OFF_BG = 0xFF26282F, OFF_TEXT = 0xFF8A8F9C;

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final String name;
    private boolean on;

    InputTile(Context c, String name) {
        this.name = name.toUpperCase(java.util.Locale.ROOT);
        text.setTypeface(Fonts.weight(c, 700));
        text.setTextAlign(Paint.Align.CENTER);
        text.setLetterSpacing(0.12f);
        setOn(false);
    }

    /** True: the device is on (the port reads "connected"). Returns whether the look changed. */
    boolean setOn(boolean now) {
        boolean changed = now != on;
        on = now;
        bg.setColor(on ? ON_BG : OFF_BG);
        text.setColor(on ? ON_TEXT : OFF_TEXT);
        if (changed) invalidateSelf();
        return changed;
    }

    boolean isOn() {
        return on;
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        c.drawRect(b, bg);
        text.setTextSize(b.height() * 0.24f);
        float y = b.exactCenterY() - (text.descent() + text.ascent()) / 2f;
        c.drawText(name, b.exactCenterX(), y, text);
    }

    @Override
    public void setAlpha(int alpha) {
        bg.setAlpha(alpha);
        text.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        bg.setColorFilter(cf);
        text.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
