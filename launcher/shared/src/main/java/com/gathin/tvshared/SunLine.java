package com.gathin.tvshared;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * The line above the home screen's clock (and under the Today app's date): Horizon's mood and the sun's next turn,
 * "MIDDAY · SUNSET 5:59 PM". Worked out on the TV from its own time and time zone and the place in config.xml, with
 * the same timing as the light (Phase), so the name always matches what is on screen. On a personal day its greeting
 * takes the mood's place. Plain Java (no Android), so it is tested on the computer.
 */
public final class SunLine {

    private SunLine() {
    }

    /** Horizon's eight moods by the sun: the light's colours (HorizonBackground.Mood) follow this timing. */
    public enum Phase {
        LATE_NIGHT("Late night"), DAWN("Dawn"), MORNING("Morning"), MIDDAY("Midday"), GOLDEN_HOUR("Golden hour"),
        SUNSET("Sunset"), BLUE_HOUR("Blue hour"), NIGHT("Night");

        public final String word;

        Phase(String word) {
            this.word = word;
        }

        /** By the minute of the local day, with sunrise and sunset (minutes) for that date. */
        public static Phase at(double minute, double rise, double set) {
            if (minute < rise - 60) return LATE_NIGHT;
            if (minute < rise) return DAWN;
            if (minute < rise + 180) return MORNING;
            if (minute < set - 90) return MIDDAY;
            if (minute < set - 20) return GOLDEN_HOUR;
            if (minute < set + 25) return SUNSET;
            if (minute < set + 90) return BLUE_HOUR;
            return NIGHT;
        }
    }

    /**
     * The line for a moment: "MIDDAY · SUNSET 5:59 PM"; between sunrise and sunset today's sunset, otherwise the next
     * sunrise. In the Sunset mood with the sunset still to come, the sun time alone ("SUNSET 5:59 PM"). On a personal
     * day its greeting instead of the mood. Null when the clock is not set yet (a year before 2025).
     */
    public static String text(Calendar now, double lat, double lon, List<PersonalDays.Day> days) {
        if (now.get(Calendar.YEAR) < 2025) return null;
        double tz = now.getTimeZone().getOffset(now.getTimeInMillis()) / 3.6e6;
        double[] rs = riseSet(now, 0, lat, lon, tz);
        double minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        Phase phase = Phase.at(minute, rs[0], rs[1]);
        boolean beforeSet = minute >= rs[0] && minute < rs[1];
        double sun = minute < rs[0] ? rs[0] : beforeSet ? rs[1] : riseSet(now, 1, lat, lon, tz)[0];
        String event = Double.isNaN(sun) ? null : (beforeSet ? "Sunset " : "Sunrise ") + clock(sun);
        PersonalDays.Day day = PersonalDays.on(now, days);
        String greeting = day == null ? null : day.greeting;
        String lead = greeting != null ? greeting : phase == Phase.SUNSET && beforeSet ? null : phase.word;
        String line = lead == null ? event : event == null ? lead : lead + " · " + event;
        return line == null ? null : line.toUpperCase(Locale.ENGLISH);
    }

    /** The sun's next turn alone ("SUNSET 5:59 PM"), for the launcher's weekend line; null while the clock is not set. */
    public static String event(Calendar now, double lat, double lon) {
        if (now.get(Calendar.YEAR) < 2025) return null;
        double tz = now.getTimeZone().getOffset(now.getTimeInMillis()) / 3.6e6;
        double[] rs = riseSet(now, 0, lat, lon, tz);
        double minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        boolean beforeSet = minute >= rs[0] && minute < rs[1];
        double sun = minute < rs[0] ? rs[0] : beforeSet ? rs[1] : riseSet(now, 1, lat, lon, tz)[0];
        return Double.isNaN(sun) ? null : ((beforeSet ? "Sunset " : "Sunrise ") + clock(sun)).toUpperCase(Locale.ENGLISH);
    }

    /** Horizon's mood for a moment, at a place (the TV's own time zone). */
    public static Phase phaseAt(Calendar now, double lat, double lon) {
        double tz = now.getTimeZone().getOffset(now.getTimeInMillis()) / 3.6e6;
        double[] rs = riseSet(now, 0, lat, lon, tz);
        return Phase.at(now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE), rs[0], rs[1]);
    }

    private static double[] riseSet(Calendar now, int plusDays, double lat, double lon, double tz) {
        Calendar c = (Calendar) now.clone();
        c.add(Calendar.DAY_OF_MONTH, plusDays);
        return SunTimes.riseSet(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), lat, lon, tz);
    }

    /** Minutes after midnight as "5:59 PM", 12-hour like the home clock. */
    public static String clock(double minutes) {
        int t = (int) Math.round(minutes), h = Math.floorMod(t / 60, 24), mm = Math.floorMod(t, 60);
        return (h % 12 == 0 ? 12 : h % 12) + ":" + (mm < 10 ? "0" : "") + mm + (h < 12 ? " AM" : " PM");
    }
}
