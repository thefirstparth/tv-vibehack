package com.gathin.tvhome;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;

import java.util.Calendar;

/**
 * The "Horizon" background: a soft band of light behind the sport and weather lines, fading to black well before the
 * dock, in one of four colours by time of day. Drawn once into a small bitmap (soft gradients look the same scaled up)
 * and used as the window's background, so the screen still draws one layer, as with plain black. It never reacts to
 * focus; it is redrawn only when the time of day moves to the next look, with no animation.
 */
final class HorizonBackground {

    enum Look {
        // glow (the ellipse) / band (the vertical stripe), the design's values
        MORNING(5, Color.argb(77, 214, 128, 62), Color.argb(46, 150, 70, 110)),
        DAY(10, Color.argb(71, 46, 140, 170), Color.argb(41, 40, 90, 170)),
        EVENING(17, Color.argb(77, 200, 80, 110), Color.argb(46, 120, 50, 150)),
        NIGHT(20, Color.argb(77, 60, 80, 190), Color.argb(46, 60, 40, 150));

        final int from, glow, band;

        Look(int from, int glow, int band) {
            this.from = from;
            this.glow = glow;
            this.band = band;
        }

        /** Morning 05:00 to 10:00, day to 17:00, evening to 20:00, night to 05:00. */
        static Look at(int hour) {
            return hour >= NIGHT.from || hour < MORNING.from ? NIGHT : hour >= EVENING.from ? EVENING : hour >= DAY.from ? DAY : MORNING;
        }

        static Look named(String name) {
            for (Look l : values()) if (l.name().equalsIgnoreCase(name)) return l;
            return null;
        }
    }

    private static final int W = 480, H = 270;

    private Look shown;

    /** The look for now (or a forced one, for screenshots), or null when the one on screen is still right. */
    Look due(Look forced) {
        Look l = forced != null ? forced : Look.at(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        return l == shown ? null : l;
    }

    BitmapDrawable draw(Resources res, Look look) {
        shown = look;
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.BLACK);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        // the vertical band: clear at 22% of the height, the colour at 44%, clear again at 62%
        int clearBand = look.band & 0x00FFFFFF;
        p.setShader(new LinearGradient(0, 0, 0, H, new int[]{clearBand, clearBand, look.band, clearBand, clearBand},
                new float[]{0f, 0.22f, 0.44f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        // the glow on top: an ellipse at 50% x 60% (low, so it fills the space above the dock and the small text sits on
        // darker ground), radii 80% of the width by 34% of the height, clear at 70%
        float rx = 0.80f * W, ry = 0.34f * H;
        RadialGradient g = new RadialGradient(0, 0, rx, new int[]{look.glow, look.glow & 0x00FFFFFF, look.glow & 0x00FFFFFF},
                new float[]{0f, 0.70f, 1f}, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(1f, ry / rx);
        m.postTranslate(0.50f * W, 0.60f * H);
        g.setLocalMatrix(m);
        p.setShader(g);
        c.drawRect(0, 0, W, H, p);
        BitmapDrawable d = new BitmapDrawable(res, b);
        d.setFilterBitmap(true);   // stretched to the screen, smoothly
        d.setDither(true);
        return d;
    }

    /** The dock band's top edge: the glow's colour at about 28%. */
    static int edge(Look look) {
        return (look.glow & 0x00FFFFFF) | (71 << 24);
    }
}
