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
 * Days missed (the TV off, the site down) are sent the next time, a week back at most. Runs on the diary's thread.
 */
final class DiarySend {

    static final long AFTER_HOME_MS = 5_000;
    static final int FROM_HOUR = 4, BACK_DAYS = 7;
    private static final int TIMEOUT_MS = 10_000;
    private static volatile boolean running;

    private DiarySend() {
    }

    static boolean configured(Context c) {
        return !c.getString(R.string.tv_diary_url).trim().isEmpty() && !c.getString(R.string.tv_diary_key).trim().isEmpty();
    }

    static void maybe(Context ctx) {
        Context c = ctx.getApplicationContext();
        if (!configured(c) || running) return;
        Diary diary = Diary.get(c);
        diary.io.execute(() -> {
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
        if (LocalTime.now(zone).getHour() < FROM_HOUR) return;
        SharedPreferences prefs = c.getSharedPreferences("diary", Context.MODE_PRIVATE);
        String last = prefs.getString("sent", "");
        LocalDate day = today.minusDays(BACK_DAYS);
        if (!last.isEmpty()) {
            LocalDate next = LocalDate.parse(last).plusDays(1);
            if (next.isAfter(day)) day = next;
        }
        for (; day.isBefore(today); day = day.plusDays(1)) {
            String date = day.toString();
            List<String> lines = diary.lines(date);
            UsageDay usage = usage(c, day, zone);
            if (lines.isEmpty() && (usage == null || (usage.onMs == 0 && usage.apps.isEmpty()))) {
                prefs.edit().putString("sent", date).apply();   // nothing to say for that day (the TV was off)
                continue;
            }
            String body = DaySummary.json(date, c.getString(R.string.tv_build), lines, usage, names(c, usage), tv(c), zone);
            if (!post(c, body)) return;
            prefs.edit().putString("sent", date).apply();
        }
        diary.prune(today);
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

    private static boolean post(Context c, String body) throws Exception {
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
        return code >= 200 && code < 300;
    }
}
