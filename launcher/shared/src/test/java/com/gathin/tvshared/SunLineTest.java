package com.gathin.tvshared;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

/**
 * The line above the clock in Bengaluru (IST) on 18 Oct 2026: sunrise 6:10 AM and sunset 5:59 PM by NOAA's formula
 * (SunTimesTest checks it against the astral library). The personal days here are made up.
 */
public class SunLineTest {

    private static final double LAT = 12.9716, LON = 77.5946;
    private static final List<SunLine.Greeting> NONE = Collections.emptyList();
    private static final List<SunLine.Greeting> DAYS = SunLine.greetings("01-15|Happy birthday, Sam|1,2,3,1|1,2,3,1|1,2,3,1|1,2,3,1\n"
            + "10-18|Happy anniversary|1,2,3,1|1,2,3,1|1,2,3,1|1,2,3,1\n06-30|Happy birthday, Alexandra Montgomery|1,2,3,1|1,2,3,1|1,2,3,1|1,2,3,1\nnot a day");

    private static Calendar at(int y, int mo, int d, int h, int mi) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
        c.clear();
        c.set(y, mo - 1, d, h, mi);
        return c;
    }

    private static String line(int h, int mi) {
        return SunLine.text(at(2026, 10, 18, h, mi), LAT, LON, NONE);
    }

    @Test
    public void eachMoodWithTheSunsNextTurn() {
        assertEquals("LATE NIGHT · SUNRISE 6:10 AM", line(3, 0));
        assertEquals("DAWN · SUNRISE 6:10 AM", line(5, 40));
        assertEquals("MORNING · SUNSET 5:59 PM", line(8, 0));
        assertEquals("MIDDAY · SUNSET 5:59 PM", line(12, 0));
        assertEquals("GOLDEN HOUR · SUNSET 5:59 PM", line(16, 50));
        assertEquals("SUNSET 5:59 PM", line(17, 50));               // the Sunset mood: never "SUNSET · SUNSET"
        assertEquals("SUNSET · SUNRISE 6:10 AM", line(18, 10));     // the sun has set: tomorrow's sunrise
        assertEquals("BLUE HOUR · SUNRISE 6:10 AM", line(18, 40));
        assertEquals("NIGHT · SUNRISE 6:10 AM", line(22, 0));
    }

    @Test
    public void aPersonalDaysGreetingTakesTheMoodsPlace() {
        assertEquals("HAPPY ANNIVERSARY · SUNSET 5:59 PM", SunLine.text(at(2026, 10, 18, 12, 0), LAT, LON, DAYS));
        assertEquals("HAPPY BIRTHDAY, SAM · SUNRISE 6:46 AM", SunLine.text(at(2027, 1, 15, 3, 0), LAT, LON, DAYS));
        assertEquals("HAPPY BIRTHDAY, ALEXANDRA MONTGOMERY · SUNSET 6:50 PM", SunLine.text(at(2027, 6, 30, 17, 50), LAT, LON, DAYS));
        assertEquals(3, DAYS.size());
        assertEquals("MIDDAY · SUNSET 5:59 PM", SunLine.text(at(2026, 10, 18, 12, 0), LAT, LON, SunLine.greetings("")));
    }

    @Test
    public void nothingBeforeTheClockIsSet() {
        assertNull(SunLine.text(at(2024, 12, 31, 12, 0), LAT, LON, NONE));
        assertEquals("12:05 AM", SunLine.clock(5));
        assertEquals("12:30 PM", SunLine.clock(750));
    }
}
