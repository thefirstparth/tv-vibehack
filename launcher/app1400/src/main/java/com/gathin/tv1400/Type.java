package com.gathin.tv1400;

import android.content.Context;
import android.graphics.Typeface;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Horizon V3's type and colours. The paper's fonts as static subsets (res/font; one file per weight, so nothing is
 * built at run time), each loaded once and shared: Newsreader for the clock, temperatures, headlines, match names and
 * the day's lines; Libre Franklin for labels, dates and all figures (tabular, lining); Source Serif 4 italic for the
 * sport context; Titillium Web for F1; the nameplate's two only in the nameplate.
 */
final class Type {

    // the paper's night palette (house-of-1400 docs/DESIGN.md)
    static final int PAPER = 0xFF131210, INK = 0xFFEFE8D8, INK2 = 0xFFD4CCBA, MUTED = 0xFFACA390, RULE = 0xFF3A3730;
    static final int UP = 0xFF6FD49A, DOWN = 0xFFFF9585, AMBER = 0xFFFFB347;
    static final int NEWS = 0xFFA9C2EE, HOME = 0xFF86CFF3, SPORT = 0xFFF4A06A, TECH = 0xFFC0AAF4, MONEY = 0xFFF0A3C9, OFF = 0xFFE9C766;

    static final int LIGHT = R.font.newsreader_light, DISPLAY = R.font.newsreader_display, TEXT = R.font.newsreader_text,
            TEXT_BOLD = R.font.newsreader_text_bold, ITALIC = R.font.newsreader_italic, MEDIUM = R.font.franklin_medium,
            SEMI = R.font.franklin_semibold, BOLD = R.font.franklin_bold, CONTEXT = R.font.sourceserif_italic,
            F1 = R.font.titillium_semibold, FIGURE = R.font.nameplate_figure, FRAKTUR = R.font.nameplate_fraktur;

    private static final SparseArray<Typeface> FACES = new SparseArray<>();

    private Type() {
    }

    static Typeface face(Context c, int font) {
        Typeface t = FACES.get(font);
        if (t == null) FACES.put(font, t = c.getResources().getFont(font));
        return t;
    }

    /** The layout's text by its tag: the font and its colour ("light", "bold2", "semi3", ...). */
    static void apply(View v) {
        if (v instanceof TextView && v.getTag() instanceof String) {
            TextView t = (TextView) v;
            Context c = t.getContext();
            switch ((String) v.getTag()) {
                case "light": set(t, face(c, LIGHT), INK); t.setFontFeatureSettings("lnum"); break;
                case "bold2": set(t, face(c, BOLD), INK2); break;
                case "bold3": set(t, face(c, BOLD), MUTED); break;
                case "bold4": set(t, face(c, BOLD), AMBER); break;
                case "bold5": set(t, face(c, BOLD), SPORT); break;
                case "semi2": set(t, face(c, SEMI), INK2); break;
                case "semi3": set(t, face(c, SEMI), MUTED); break;
                case "fraktur": set(t, face(c, FRAKTUR), INK); break;
                case "figure": set(t, face(c, FIGURE), INK); t.setFontFeatureSettings("lnum"); break;
                default: break;
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) apply(g.getChildAt(i));
        }
    }

    private static void set(TextView t, Typeface f, int colour) {
        t.setTypeface(f);
        t.setTextColor(colour);
    }

    /** A text view: font, size in the design's pixels (halved to sp), colour; caps with the paper's 0.09em tracking. */
    static TextView text(Context c, int font, float px, int colour, boolean caps) {
        TextView t = new TextView(c);
        t.setTypeface(face(c, font));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, px / 2f);
        t.setTextColor(colour);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(null);
        if (caps) {
            t.setAllCaps(true);
            t.setLetterSpacing(0.09f);
        }
        return t;
    }

    /** Figures: tabular and lining, so columns of numbers line up. */
    static TextView figures(TextView t) {
        t.setFontFeatureSettings("tnum, lnum");
        return t;
    }

    /** A desk's colour by its id (the feed's minute lines carry it too). */
    static int desk(String id) {
        switch (id == null ? "" : id) {
            case "news": return NEWS;
            case "home": return HOME;
            case "sport": return SPORT;
            case "tech": return TECH;
            case "money": return MONEY;
            case "off": return OFF;
            default: return MUTED;
        }
    }
}
