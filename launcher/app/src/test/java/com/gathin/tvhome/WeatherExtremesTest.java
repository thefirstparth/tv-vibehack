package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The weather corner's highlights: one city per item, never on a tie, never from fewer than two cities. */
public class WeatherExtremesTest {

    private static Integer[] v(Integer... xs) {
        return xs;
    }

    @Test
    public void normalCase() {
        // Bengaluru, Ranchi, Prayagraj (the mockup's numbers)
        WeatherExtremes e = WeatherExtremes.of(v(22, 16, 27), v(29, 24, 32), v(19, 11, 18), v(78, 54, 41));
        assertEquals(2, e.warmest);
        assertEquals(1, e.coolest);
        assertEquals(2, e.highestHigh);
        assertEquals(1, e.lowestLow);
        assertEquals(0, e.mostHumid);
    }

    @Test
    public void aTieAtTheTopPicksNothingForThatItemOnly() {
        WeatherExtremes e = WeatherExtremes.of(v(27, 16, 27), v(32, 24, 32), v(19, 11, 11), v(78, 54, 41));
        assertEquals(-1, e.warmest);       // 27 and 27
        assertEquals(1, e.coolest);        // the coolest is still clear
        assertEquals(-1, e.highestHigh);   // 32 and 32
        assertEquals(-1, e.lowestLow);     // 11 and 11
        assertEquals(0, e.mostHumid);
    }

    @Test
    public void aTieBelowTheTopDoesNotMatter() {
        assertEquals(0, WeatherExtremes.pick(v(30, 20, 20), true));
        assertEquals(0, WeatherExtremes.pick(v(10, 20, 20), false));
    }

    @Test
    public void missingValuesAreLeftOut() {
        WeatherExtremes e = WeatherExtremes.of(v(22, null, 27), v(null, null, 32), v(19, 11, null), v(null, null, null));
        assertEquals(2, e.warmest);        // only the two cities that have it
        assertEquals(0, e.coolest);
        assertEquals(-1, e.highestHigh);   // one city has it: nothing
        assertEquals(1, e.lowestLow);
        assertEquals(-1, e.mostHumid);     // none have it
    }

    @Test
    public void allTheSameOrOneCityPicksNothing() {
        assertEquals(-1, WeatherExtremes.pick(v(20, 20, 20), true));
        assertEquals(-1, WeatherExtremes.pick(v(20, 20, 20), false));
        assertEquals(-1, WeatherExtremes.pick(v(20), true));
        assertEquals(-1, WeatherExtremes.pick(v(), true));
    }
}
