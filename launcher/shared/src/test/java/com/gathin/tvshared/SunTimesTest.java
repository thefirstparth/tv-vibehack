package com.gathin.tvshared;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Bengaluru's sunrise and sunset against an independent reference (the astral library, NOAA's method), within 2 min. */
public class SunTimesTest {

    private static final double LAT = 12.9716, LON = 77.5946, IST = 5.5;

    private static void check(int y, int m, int d, String rise, String set) {
        double[] rs = SunTimes.riseSet(y, m, d, LAT, LON, IST);
        assertEquals(y + "-" + m + "-" + d + " sunrise", minutes(rise), rs[0], 2.0);
        assertEquals(y + "-" + m + "-" + d + " sunset", minutes(set), rs[1], 2.0);
    }

    private static double minutes(String hms) {
        String[] p = hms.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]) + Integer.parseInt(p[2]) / 60.0;
    }

    @Test
    public void bengaluruThroughTheYear() {
        check(2026, 10, 18, "06:10:26", "17:58:58"); // also checked by hand: about 06:10 / 17:59
        check(2026, 1, 1, "06:41:54", "18:04:18");
        check(2026, 3, 20, "06:24:09", "18:30:15");
        check(2026, 6, 21, "05:54:51", "18:47:55");
        check(2026, 9, 22, "06:08:56", "18:15:45");
        check(2026, 12, 21, "06:36:51", "17:58:19");
        check(2027, 4, 10, "06:10:36", "18:31:38");
    }
}
