package com.gathin.tvhome;

/**
 * Sunrise and sunset on the TV itself (no network): NOAA's solar calculator (the equations behind
 * gml.noaa.gov/grad/solcalc), with the sun's centre at zenith 90.833 degrees (refraction and the sun's radius). Used for
 * Horizon's sun-timed moods in Bengaluru. Plain Java, so it is unit-tested on the computer.
 */
final class SunTimes {

    static final double ZENITH = 90.833;

    private SunTimes() {
    }

    /** Minutes after local midnight of sunrise and sunset ({rise, set}); NaN when the sun does not rise or set. */
    static double[] riseSet(int year, int month, int day, double lat, double lon, double tzHours) {
        double jd = julianDay(year, month, day);
        return new double[]{local(true, jd, lat, lon, tzHours), local(false, jd, lat, lon, tzHours)};
    }

    private static double local(boolean rise, double jd, double lat, double lon, double tz) {
        double utc = utcMinutes(rise, jd, lat, lon);
        utc = utcMinutes(rise, jd + utc / 1440.0, lat, lon); // once more at the time found
        return utc + tz * 60;
    }

    // minutes after 0h UT on the Julian day jd (longitude east positive)
    private static double utcMinutes(boolean rise, double jd, double lat, double lon) {
        double t = (jd - 2451545.0) / 36525.0;
        double ha = hourAngle(lat, declination(t));
        if (!rise) ha = -ha;
        return 720 - 4 * (lon + Math.toDegrees(ha)) - equationOfTime(t);
    }

    static double julianDay(int y, int m, int d) {
        if (m <= 2) {
            y -= 1;
            m += 12;
        }
        int a = y / 100, b = 2 - a + a / 4;
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5;
    }

    private static double meanLongitude(double t) {
        double l = 280.46646 + t * (36000.76983 + 0.0003032 * t);
        return ((l % 360) + 360) % 360;
    }

    private static double meanAnomaly(double t) {
        return 357.52911 + t * (35999.05029 - 0.0001537 * t);
    }

    private static double eccentricity(double t) {
        return 0.016708634 - t * (0.000042037 + 0.0000001267 * t);
    }

    private static double obliquity(double t) {
        double e0 = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60;
        return e0 + 0.00256 * Math.cos(Math.toRadians(125.04 - 1934.136 * t));
    }

    private static double declination(double t) {
        double m = Math.toRadians(meanAnomaly(t));
        double centre = Math.sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t)) + Math.sin(2 * m) * (0.019993 - 0.000101 * t) + Math.sin(3 * m) * 0.000289;
        double apparent = meanLongitude(t) + centre - 0.00569 - 0.00478 * Math.sin(Math.toRadians(125.04 - 1934.136 * t));
        return Math.asin(Math.sin(Math.toRadians(obliquity(t))) * Math.sin(Math.toRadians(apparent)));
    }

    private static double equationOfTime(double t) {
        double e = Math.toRadians(obliquity(t)), l0 = Math.toRadians(meanLongitude(t)), m = Math.toRadians(meanAnomaly(t)), ecc = eccentricity(t);
        double y = Math.tan(e / 2) * Math.tan(e / 2);
        double eq = y * Math.sin(2 * l0) - 2 * ecc * Math.sin(m) + 4 * ecc * y * Math.sin(m) * Math.cos(2 * l0)
                - 0.5 * y * y * Math.sin(4 * l0) - 1.25 * ecc * ecc * Math.sin(2 * m);
        return Math.toDegrees(eq) * 4;
    }

    private static double hourAngle(double lat, double decl) {
        double la = Math.toRadians(lat);
        double c = Math.cos(Math.toRadians(ZENITH)) / (Math.cos(la) * Math.cos(decl)) - Math.tan(la) * Math.tan(decl);
        return c < -1 || c > 1 ? Double.NaN : Math.acos(c);
    }
}
