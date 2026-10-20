package com.gathin.tvhome;

/**
 * Which city to pick out in the weather corner (mockup option 5): the warmest and the coolest temperature now, the
 * highest high, the lowest low and the most humid. Only cities that have the value take part; with fewer than two of
 * them, or a tie at the top, nothing is picked for that item. "Feels" is never picked. Pure, so it is unit-tested.
 * Each field is a city's index in the feed's list, or -1.
 */
final class WeatherExtremes {

    static final WeatherExtremes NONE = new WeatherExtremes(-1, -1, -1, -1, -1);

    final int warmest, coolest, highestHigh, lowestLow, mostHumid;

    WeatherExtremes(int warmest, int coolest, int highestHigh, int lowestLow, int mostHumid) {
        this.warmest = warmest;
        this.coolest = coolest;
        this.highestHigh = highestHigh;
        this.lowestLow = lowestLow;
        this.mostHumid = mostHumid;
    }

    /** One array per value, one entry per city (null where the city doesn't have it). */
    static WeatherExtremes of(Integer[] now, Integer[] hi, Integer[] lo, Integer[] hum) {
        return new WeatherExtremes(pick(now, true), pick(now, false), pick(hi, true), pick(lo, false), pick(hum, true));
    }

    /** The index of the single largest (or smallest) value, or -1 with fewer than two values or a tie. */
    static int pick(Integer[] values, boolean largest) {
        int best = -1, count = 0;
        boolean tie = false;
        for (int i = 0; i < values.length; i++) {
            Integer v = values[i];
            if (v == null) continue;
            count++;
            if (best < 0) {
                best = i;
                continue;
            }
            int cmp = Integer.compare(v, values[best]);
            if (cmp == 0) {
                tie = true;
            } else if (largest ? cmp > 0 : cmp < 0) {
                best = i;
                tie = false;
            }
        }
        return count < 2 || tie ? -1 : best;
    }
}
