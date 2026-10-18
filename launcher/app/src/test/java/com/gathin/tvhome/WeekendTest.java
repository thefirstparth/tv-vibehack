package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/** When the weekend look is on, and its colours. */
public class WeekendTest {

    private static Calendar at(int y, int m, int d, int hh, int mm) {
        Calendar c = new GregorianCalendar(TimeZone.getTimeZone("Asia/Kolkata"));
        c.clear();
        c.set(y, m - 1, d, hh, mm);
        return c;
    }

    @Test
    public void allOfSaturdayAndSundayAndNothingElse() {
        assertFalse(Weekend.isWeekend(at(2026, 10, 23, 23, 59)));   // Friday 23:59
        assertTrue(Weekend.isWeekend(at(2026, 10, 24, 0, 0)));      // Saturday 00:00
        assertTrue(Weekend.isWeekend(at(2026, 10, 25, 23, 59)));    // Sunday 23:59
        assertFalse(Weekend.isWeekend(at(2026, 10, 26, 0, 0)));     // Monday 00:00
    }

    @Test
    public void aPersonalDayAlwaysWins() {
        assertNull(Weekend.of(true, true, false, 0xFF46AACD, 0xFF96DEE8, 0xFF286EAA));
        assertNull(Weekend.of(false, false, false, 0xFF46AACD, 0xFF96DEE8, 0xFF286EAA));
        assertNotNull(Weekend.of(true, false, false, 0xFF46AACD, 0xFF96DEE8, 0xFF286EAA));
    }

    @Test
    public void tonesKeepTheirHueAsALightPastel() {
        Weekend w = Weekend.of(true, false, false, 0xFF46AACD, 0xFF96DEE8, 0xFF286EAA);
        assertPastel(0xFF46AACD, w.tone1, 0.40f);   // core
        assertPastel(0xFF96DEE8, w.tone2, 0.40f);   // spot
        assertPastel(0xFF286EAA, w.tone3, 0.40f);   // band
        assertEquals(Weekend.GOLD, w.gold);
        assertEquals(Weekend.HEART, w.heart);
    }

    @Test
    public void rainSoftensEveryWeekendColourIncludingGoldAndHeart() {
        Weekend w = Weekend.of(true, false, true, 0xFF46AACD, 0xFF96DEE8, 0xFF286EAA);
        assertPastel(0xFF46AACD, w.tone1, 0.40f * 0.62f);
        assertEquals(Weekend.hsv(Weekend.GOLD)[1] * 0.62f, Weekend.hsv(w.gold)[1], 0.01f);
        assertEquals(Weekend.hsv(Weekend.HEART)[1] * 0.62f, Weekend.hsv(w.heart)[1], 0.01f);
        assertEquals(Weekend.hsv(Weekend.HEART)[0], Weekend.hsv(w.heart)[0], 2f);
    }

    @Test
    public void theStateKeyIsOffOnWeekdaysAndPersonalDaysAndFollowsMoodAndSky() {
        assertEquals(Weekend.OFF, Weekend.key(false, false, "MIDDAY", "CLEAR"));
        assertEquals(Weekend.OFF, Weekend.key(true, true, "MIDDAY", "CLEAR"));
        String midday = Weekend.key(true, false, "MIDDAY", "CLEAR");
        assertFalse(midday.equals(Weekend.key(true, false, "SUNSET", "CLEAR")));
        assertFalse(midday.equals(Weekend.key(true, false, "MIDDAY", "RAIN")));
        assertEquals(midday, Weekend.key(true, false, "MIDDAY", "CLEAR"));
    }

    @Test
    public void hsvRoundTrips() {
        for (int c : new int[]{0xFFF5D58B, 0xFFF2A9BA, 0xFF46AACD, 0xFF102030, 0xFFFFFFFF}) {
            float[] h = Weekend.hsv(c);
            assertEquals(c, Weekend.rgb(h[0], h[1], h[2]));
        }
    }

    private static void assertPastel(int from, int got, float s) {
        float[] a = Weekend.hsv(from), b = Weekend.hsv(got);
        assertEquals(a[0], b[0], 2f);
        assertEquals(s, b[1], 0.01f);
        assertEquals(0.95f, b[2], 0.01f);
    }
}
