package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One day of Android's usage events (UsageStatsManager) worked into what the day's summary needs: minutes on screen and
 * opens per app, when the screen was on, and restarts. Events from before the day are passed too, so an app or a screen
 * that was already on at midnight counts from midnight. Pure, so it is unit-tested.
 */
final class UsageDay {

    // UsageEvents.Event types used here (public constants; numbers so this class needs no Android)
    static final int RESUMED = 1, PAUSED = 2, SCREEN_ON = 15, SCREEN_OFF = 16, SHUTDOWN = 26, STARTUP = 27;

    static final class Ev {
        final long t;
        final int type;
        final String pkg;

        Ev(long t, int type, String pkg) {
            this.t = t;
            this.type = type;
            this.pkg = pkg;
        }
    }

    static final class App {
        final String pkg;
        long ms;
        int opens;
        /** When it was on screen, [start, end] in ms; stretches under a minute are left out (ms still counts them). */
        final List<long[]> spans = new ArrayList<>();

        App(String pkg) {
            this.pkg = pkg;
        }
    }

    /** Apps by time on screen, most first; the screen's on spans as [start, end] in ms; restarts during the day. */
    final List<App> apps = new ArrayList<>();
    final List<long[]> on = new ArrayList<>();
    long onMs;
    int boots;

    /** Not counted as apps: the launcher itself (its own numbers are separate) and the system. */
    static boolean counted(String pkg) {
        return pkg != null && !pkg.equals("com.gathin.tvhome") && !pkg.equals("android") && !pkg.equals("com.android.systemui");
    }

    /** events in time order; the day is [start, end). */
    static UsageDay of(List<Ev> events, long start, long end) {
        UsageDay d = new UsageDay();
        Map<String, App> apps = new HashMap<>();
        Map<String, Integer> open = new HashMap<>();   // resumed activities per app
        Map<String, Long> since = new HashMap<>();
        String front = null;
        long screenSince = -1;
        for (Ev e : events) {
            if (e.t >= end) break;
            switch (e.type) {
                case RESUMED: {
                    int n = open.getOrDefault(e.pkg, 0);
                    if (n == 0) {
                        since.put(e.pkg, e.t);
                        if (e.t >= start && counted(e.pkg) && !e.pkg.equals(front)) apps.computeIfAbsent(e.pkg, App::new).opens++;
                    }
                    open.put(e.pkg, n + 1);
                    front = e.pkg;
                    break;
                }
                case PAUSED: {
                    int n = open.getOrDefault(e.pkg, 0);
                    if (n <= 0) break;
                    if (n == 1) close(apps, since, e.pkg, e.t, start);
                    open.put(e.pkg, n - 1);
                    break;
                }
                case SCREEN_ON:
                    if (screenSince < 0) screenSince = e.t;
                    break;
                case SHUTDOWN:
                case SCREEN_OFF:
                    if (screenSince >= 0) d.span(screenSince, e.t, start);
                    screenSince = -1;
                    for (String p : new ArrayList<>(since.keySet())) close(apps, since, p, e.t, start);
                    open.clear();
                    front = null;
                    break;
                case STARTUP:
                    if (e.t >= start) d.boots++;
                    break;
                default:
                    break;
            }
        }
        // still on at the end of the day
        if (screenSince >= 0) d.span(screenSince, end, start);
        for (String p : new ArrayList<>(since.keySet())) close(apps, since, p, end, start);
        d.apps.addAll(apps.values());
        d.apps.removeIf(a -> a.ms <= 0 && a.opens == 0);
        d.apps.sort((a, b) -> Long.compare(b.ms, a.ms));
        return d;
    }

    private static void close(Map<String, App> apps, Map<String, Long> since, String pkg, long t, long start) {
        Long s = since.remove(pkg);
        if (s == null || !counted(pkg)) return;
        long from = Math.max(s, start);
        if (t <= from) return;
        App a = apps.computeIfAbsent(pkg, App::new);
        a.ms += t - from;
        if (t - from < 60_000) return;
        long[] last = a.spans.isEmpty() ? null : a.spans.get(a.spans.size() - 1);
        if (last != null && from - last[1] < 60_000) last[1] = t;   // back within a minute: one stretch
        else a.spans.add(new long[]{from, t});
    }

    private void span(long from, long to, long start) {
        from = Math.max(from, start);
        if (to <= from) return;
        onMs += to - from;
        if (to - from >= 60_000) on.add(new long[]{from, to});   // the list skips blips under a minute; the total keeps them
    }
}
