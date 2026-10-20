package com.gathin.tvhome;

/**
 * Low-precision positions of the sun and moon (SunCalc-style), copied from Horizon v3's approved mockup
 * (examples/xiaomi-mitv-moor2/horizon-v3/mockup.html, ASTRO): good to about a minute for sunrise and a degree for
 * positions, which is all the light needs. Pure, so it is unit-tested. Times are epoch milliseconds; angles radians.
 */
final class Astro {

    static final double R = Math.PI / 180, E = R * 23.4397;

    final double lat, lng;

    Astro(double latDeg, double lngDeg) {
        lat = latDeg;
        lng = lngDeg;
    }

    /** Days since J2000.0. */
    static double days(long ms) {
        return ms / 86400000.0 - 0.5 + 2440588 - 2451545;
    }

    double sidereal(double d) {
        return R * (280.16 + 360.9856235 * d) + R * lng;
    }

    /** Right ascension and declination of an ecliptic longitude and latitude. */
    static double[] radec(double l, double b) {
        return new double[]{
                Math.atan2(Math.sin(l) * Math.cos(E) - Math.tan(b) * Math.sin(E), Math.cos(l)),
                Math.asin(Math.sin(b) * Math.cos(E) + Math.cos(b) * Math.sin(E) * Math.sin(l))};
    }

    /** {ra, dec, distance km}. */
    static double[] sun(double d) {
        double M = R * (357.5291 + 0.98560028 * d);
        double C = R * (1.9148 * Math.sin(M) + 0.02 * Math.sin(2 * M) + 0.0003 * Math.sin(3 * M));
        double L = M + C + R * 102.9372 + Math.PI;
        double[] c = radec(L, 0);
        return new double[]{c[0], c[1], 149598000};
    }

    /** {ra, dec, distance km}. */
    static double[] moon(double d) {
        double L = R * (218.316 + 13.176396 * d), M = R * (134.963 + 13.064993 * d), F = R * (93.272 + 13.229350 * d);
        double l = L + R * 6.289 * Math.sin(M), b = R * 5.128 * Math.sin(F);
        double[] c = radec(l, b);
        return new double[]{c[0], c[1], 385001 - 20905 * Math.cos(M)};
    }

    /** {hour angle (west positive, -pi..pi), altitude} of a position at time d. */
    double[] horiz(double ra, double dec, double d) {
        double H = sidereal(d) - ra, phi = R * lat;
        H = Math.atan2(Math.sin(H), Math.cos(H));
        double alt = Math.asin(Math.sin(phi) * Math.sin(dec) + Math.cos(phi) * Math.cos(dec) * Math.cos(H));
        return new double[]{H, alt};
    }

    /** The moon's lit fraction, 0 (new) to 1 (full). */
    static double moonLit(double d) {
        double[] s = sun(d), m = moon(d);
        double phi = Math.acos(Math.sin(s[1]) * Math.sin(m[1]) + Math.cos(s[1]) * Math.cos(m[1]) * Math.cos(s[0] - m[0]));
        double inc = Math.atan2(s[2] * Math.sin(phi), m[2] - s[2] * Math.cos(phi));
        return (1 + Math.cos(inc)) / 2;
    }

    /**
     * Sunrise and sunset (epoch ms) of the local day starting at dayStart (local midnight, ms), by scanning the sun's
     * altitude minute by minute against -0.833 degrees; -1 where there is none.
     */
    long[] sunTimes(long dayStart) {
        long rise = -1, set = -1;
        double prev = Double.NaN;
        for (int m = 0; m <= 1440; m++) {
            long t = dayStart + m * 60000L;
            double d = days(t);
            double[] s = sun(d);
            double a = horiz(s[0], s[1], d)[1] / R + 0.833;
            if (!Double.isNaN(prev) && prev < 0 && a >= 0) rise = t;
            if (!Double.isNaN(prev) && prev >= 0 && a < 0) set = t;
            prev = a;
        }
        return new long[]{rise, set};
    }
}
