package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Horizon v3's sky: the sun's times, the moon's phase and a star's place, for Bengaluru. */
public class AstroTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Astro BLR = new Astro(12.9716, 77.5946);

    private static long at(int y, int mo, int d, int h, int mi) {
        return LocalDateTime.of(y, mo, d, h, mi).atZone(IST).toInstant().toEpochMilli();
    }

    private static int minuteOfDay(long ms) {
        LocalDateTime t = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), IST);
        return t.getHour() * 60 + t.getMinute();
    }

    @Test
    public void bengaluruSunrise611AndSunset559On20October2026() {
        long day = LocalDate.of(2026, 10, 20).atStartOfDay(IST).toInstant().toEpochMilli();
        long[] rs = BLR.sunTimes(day);
        assertEquals(6 * 60 + 11, minuteOfDay(rs[0]), 2);
        assertEquals(17 * 60 + 59, minuteOfDay(rs[1]), 2);
    }

    @Test
    public void fullMoon26OctoberNewMoon10October() {
        double full = Astro.moonLit(Astro.days(at(2026, 10, 26, 12, 0)));
        double nu = Astro.moonLit(Astro.days(at(2026, 10, 10, 12, 0)));
        assertTrue("full moon lit " + full, full > 0.99);
        assertTrue("new moon lit " + nu, nu < 0.01);
    }

    @Test
    public void betelgeuseAbout84DegreesUpAt3amOn9November2026() {
        // Betelgeuse: RA 88.79°, Dec +7.41°
        double d = Astro.days(at(2026, 11, 9, 3, 0));
        double alt = BLR.horiz(88.79 * Astro.R, 7.41 * Astro.R, d)[1] / Astro.R;
        assertEquals(84, alt, 2);
    }
}
