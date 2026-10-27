package com.gathin.tvhome;

import android.app.ActivityManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.SystemClock;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Once a day, the day before's summary to the insights site (insights/, a separate Vercel project): only when its address
 * and key are set in launcher/local.properties (tv.diaryUrl, tv.diaryKey), only from 4 am, and only after Home has been
 * on screen a few seconds (MainActivity posts it and takes it back on pause), so never while something is watched.
 * Days missed (the site down, no internet) are sent the next time, 30 days back at most. A day the TV was off is sent
 * as "on 0 minutes", so on the page a missing day means the summary never arrived. What went wrong with a send (no
 * internet, a timeout, the site's answer) is remembered and sent with the next day that gets through. Each day's summary
 * is made once, at its first try, and kept (Diary.freeze): a day that fails for weeks is still sent whole. Runs on a thread
 * of its own, so the diary never waits for the network.
 */
final class DiarySend {

    static final long AFTER_HOME_MS = 5_000;
    static final int FROM_HOUR = 4, BACK_DAYS = 30;
    /** Before this the TV's clock isn't set (this feature didn't exist): nothing is sent and the diary isn't pruned. */
    static final LocalDate CLOCK_FLOOR = LocalDate.of(2026, 10, 21);
    private static final int TIMEOUT_MS = 10_000;
    private static volatile boolean running;
    private static final java.util.concurrent.ExecutorService NET = java.util.concurrent.Executors.newSingleThreadExecutor();

    private DiarySend() {
    }

    /** The TV the diary describes: only it sends. The emulator runs the same build, address and key, and on 23 Oct sent
     *  its own made-up 22 Oct to the site (on all day, no apps), which replaced the real one. */
    static final String TV_MODEL = "MiTV-MOOR2";

    static boolean configured(Context c) {
        return TV_MODEL.equals(android.os.Build.MODEL)
                && !c.getString(R.string.tv_diary_url).trim().isEmpty() && !c.getString(R.string.tv_diary_key).trim().isEmpty();
    }

    static void maybe(Context ctx) {
        Context c = ctx.getApplicationContext();
        if (!configured(c) || running) return;
        Diary diary = Diary.get(c);
        NET.execute(() -> {
            running = true;
            try {
                send(c, diary);
            } catch (Exception ignored) {
                // tried again the next time Home appears
            } finally {
                running = false;
            }
        });
    }

    private static void send(Context c, Diary diary) throws Exception {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        if (today.isBefore(CLOCK_FLOOR) || LocalTime.now(zone).getHour() < FROM_HOUR) return;
        SharedPreferences prefs = c.getSharedPreferences("diary", Context.MODE_PRIVATE);
        // the first day this launcher kept a diary: before it, a day with no usage is unknown, not "off"
        String since = prefs.getString("since", "");
        if (since.isEmpty()) {
            since = today.toString();
            prefs.edit().putString("since", since).apply();
        }
        LocalDate day = Plan.firstToSend(today, prefs.getString("sent", ""), BACK_DAYS);
        for (; day.isBefore(today); day = day.plusDays(1)) {
            String date = day.toString();
            // made once, at the day's first try, and sent as it was: Android keeps a day's app detail only ~10 days
            String summary = diary.frozen(date);
            if (summary == null) {
                List<String> lines = diary.lines(date);
                UsageDay usage = usage(c, day, zone);
                boolean empty = lines.isEmpty() && (usage == null || (usage.onMs == 0 && usage.apps.isEmpty()));
                if (empty && date.compareTo(since) < 0) {   // before the diary: nothing known, nothing sent
                    prefs.edit().putString("sent", date).apply();
                    continue;
                }
                if (empty && usage == null) usage = new UsageDay();   // the TV was off all day: "on 0 minutes"
                summary = DaySummary.json(date, c.getString(R.string.tv_build), lines, usage, names(c, usage), tv(c), zone, inputs(c, diary, day, lines, usage));
                diary.freeze(date, summary);
            }
            String body = DaySummary.withSend(summary, failures(prefs));
            int code;
            try {
                code = post(c, body);
            } catch (java.net.SocketTimeoutException e) {
                failed(prefs, "timed out");
                return;
            } catch (java.io.IOException e) {
                failed(prefs, "no internet");
                return;
            }
            String why = Plan.after(code);
            if (why == null) {
                prefs.edit().putString("sent", date).remove("failed").remove("fail_why").remove("fail_at").apply();
            } else if (Plan.skip(code)) {
                // the site refused this day for good: keep it on the TV and go on with the next day
                failed(prefs, why);
                keep(c, date, body);
                prefs.edit().putString("sent", date).apply();
            } else {
                failed(prefs, why);   // the key, or the site: try again next time
                return;
            }
        }
        Diary.get(c).io.execute(() -> diary.prune(today));
    }

    /** Which day to send from, and what a reply means. Pure, so it is unit-tested. */
    static final class Plan {
        private Plan() {
        }

        /** The day after the last sent one, but never more than `back` days ago; a "sent" in the future (the clock was wrong) is ignored. */
        static LocalDate firstToSend(LocalDate today, String sent, int back) {
            LocalDate from = today.minusDays(back);
            if (sent == null || sent.isEmpty()) return from;
            LocalDate next;
            try {
                next = LocalDate.parse(sent).plusDays(1);
            } catch (Exception e) {
                return from;
            }
            if (next.isAfter(today)) return from;
            return next.isAfter(from) ? next : from;
        }

        /** null when the site kept the day; otherwise what went wrong, in words for the page. */
        static String after(int code) {
            if (code >= 200 && code < 300) return null;
            if (code == 401 || code == 403) return "key refused (" + code + ")";
            if (code == 400 || code == 413 || code == 422) return "day refused (" + code + ")";
            return "site error (" + code + ")";
        }

        /** A day the site will never take as it is: skipped, so it doesn't hold up the days after it. */
        static boolean skip(int code) {
            return code == 400 || code == 413 || code == 422;
        }
    }

    private static void failed(SharedPreferences prefs, String why) {
        prefs.edit().putInt("failed", prefs.getInt("failed", 0) + 1).putString("fail_why", why)
                .putString("fail_at", java.time.LocalDateTime.now().withNano(0).toString()).apply();
    }

    /** The send problems since the last good send, as JSON for the summary, or null when there were none. */
    private static String failures(SharedPreferences prefs) {
        int n = prefs.getInt("failed", 0);
        if (n == 0) return null;
        return "{\"failed\":" + n + ",\"last\":" + DaySummary.q(prefs.getString("fail_why", "")) + ",\"at\":" + DaySummary.q(prefs.getString("fail_at", "")) + "}";
    }

    /** A refused day stays on the TV (files/refused/), so it can be looked at and sent again by hand. */
    private static void keep(Context c, String date, String body) {
        try {
            java.io.File dir = new java.io.File(c.getFilesDir(), "refused");
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            java.nio.file.Files.write(new java.io.File(dir, date + ".json").toPath(), body.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // best effort
        }
    }

    /** The input app's time by Inputs-menu choice: the day before's late choices too, for a stretch over midnight. */
    private static List<InputDay.Use> inputs(Context c, Diary diary, LocalDate day, List<String> lines, UsageDay usage) {
        if (usage == null) return null;
        UsageDay.App app = usage.app(c.getString(R.string.live_tv_package).trim());
        if (app == null) return null;
        List<String> both = new ArrayList<>(diary.lines(day.minusDays(1).toString()));
        both.addAll(lines);
        String dock = c.getString(R.string.dock_input).trim();
        return InputDay.of(InputDay.choices(both), app, usage.homes, InputDay.signals(both), dock.isEmpty() ? null : dock,
                c.getString(R.string.dock_input_name).trim());
    }

    /** null without usage access (granted once over adb; MOOR2 README). */
    private static UsageDay usage(Context c, LocalDate day, ZoneId zone) {
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return null;
        long start = day.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        UsageEvents events = usm.queryEvents(start - 12 * 3_600_000L, end);
        if (events == null) return null;
        List<UsageDay.Ev> list = new ArrayList<>();
        UsageEvents.Event e = new UsageEvents.Event();
        while (events.getNextEvent(e)) list.add(new UsageDay.Ev(e.getTimeStamp(), e.getEventType(), e.getPackageName()));
        if (list.isEmpty()) return null;
        return UsageDay.of(list, start, end);
    }

    private static Map<String, String> names(Context c, UsageDay usage) {
        Map<String, String> m = new HashMap<>();
        if (usage == null) return m;
        PackageManager pm = c.getPackageManager();
        for (UsageDay.App a : usage.apps) {
            try {
                m.put(a.pkg, String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(a.pkg, 0))));
            } catch (Exception ignored) {
                // an app since removed: its package name only
            }
        }
        return m;
    }

    private static DaySummary.Tv tv(Context c) {
        DaySummary.Tv t = new DaySummary.Tv();
        t.uptimeH = SystemClock.elapsedRealtime() / 3_600_000L;
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            t.memFreeMb = mi.availMem >> 20;
        }
        t.storageFreeMb = c.getFilesDir().getUsableSpace() >> 20;
        return t;
    }

    private static int post(Context c, String body) throws java.io.IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(c.getString(R.string.tv_diary_url).trim()).openConnection();
        h.setConnectTimeout(TIMEOUT_MS);
        h.setReadTimeout(TIMEOUT_MS);
        h.setRequestMethod("POST");
        h.setDoOutput(true);
        h.setRequestProperty("content-type", "application/json");
        h.setRequestProperty("x-tv-key", c.getString(R.string.tv_diary_key).trim());
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        h.setFixedLengthStreamingMode(b.length);
        try (OutputStream out = h.getOutputStream()) {
            out.write(b);
        }
        int code = h.getResponseCode();
        h.disconnect();
        return code;
    }
}
