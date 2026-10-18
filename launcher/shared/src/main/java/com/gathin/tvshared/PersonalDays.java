package com.gathin.tvshared;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * The personal days (launcher/local.properties tv.day.N, built into this module as R.string.tv_days, one per line):
 * "MM-DD|greeting|core|spot|band|top", each colour "r,g,b,a" with a from 0 to 1. A line that does not read cleanly is
 * skipped; none set, no personal days. Plain Java, so it is tested on the computer.
 */
public final class PersonalDays {

    /** One day: its date, greeting and four colours as ARGB. */
    public static final class Day {
        public final int month, day;
        public final String greeting;
        public final int core, spot, band, top;

        Day(int month, int day, String greeting, int core, int spot, int band, int top) {
            this.month = month;
            this.day = day;
            this.greeting = greeting;
            this.core = core;
            this.spot = spot;
            this.band = band;
            this.top = top;
        }
    }

    private PersonalDays() {
    }

    public static List<Day> parse(String text) {
        List<Day> out = new ArrayList<>();
        for (String line : (text == null ? "" : text).split("\n")) {
            String[] f = line.trim().split("\\|");
            if (f.length != 6) continue;
            try {
                String[] md = f[0].trim().split("-");
                int[] c = new int[4];
                for (int i = 0; i < 4; i++) {
                    String[] v = f[2 + i].trim().split(",");
                    c[i] = argb(Double.parseDouble(v[3].trim()), Integer.parseInt(v[0].trim()), Integer.parseInt(v[1].trim()), Integer.parseInt(v[2].trim()));
                }
                out.add(new Day(Integer.parseInt(md[0]), Integer.parseInt(md[1]), f[1].trim(), c[0], c[1], c[2], c[3]));
            } catch (RuntimeException ignored) {
            }
        }
        return out;
    }

    /** Today's personal day, if any (the whole day, local time). */
    public static Day on(Calendar now, List<Day> days) {
        int m = now.get(Calendar.MONTH) + 1, d = now.get(Calendar.DAY_OF_MONTH);
        for (Day x : days) if (x.month == m && x.day == d) return x;
        return null;
    }

    /** Alpha 0 to 1 and red, green, blue 0 to 255 as one ARGB colour. */
    public static int argb(double a, int r, int g, int b) {
        return ((int) Math.round(a * 255) & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }
}
