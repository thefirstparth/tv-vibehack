package com.gathin.tvhome;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Yesterday's summary as the JSON the insights site takes (insights/lib/day.js, version 1): the launcher's own diary
 * lines (Diary) and Android's usage (UsageDay) for one day. Written by hand, so it needs no org.json and is
 * unit-tested on the computer.
 */
final class DaySummary {

    /** A Home return slower than this counts as slow. */
    static final long SLOW_MS = 300;

    private DaySummary() {
    }

    /** What is read at sending time, not from the day. */
    static final class Tv {
        long uptimeH = -1, memFreeMb = -1, storageFreeMb = -1;
    }

    static String json(String date, String build, List<String> diary, UsageDay usage, Map<String, String> names, Tv tv, ZoneId zone) {
        List<Long> home = new ArrayList<>(), cold = new ArrayList<>();
        int asks = 0, fresh = 0, drops = 0;
        for (String line : diary) {
            String[] f = line.split("\t");
            if (f.length < 3) continue;
            long v;
            try {
                v = Long.parseLong(f[2].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            switch (f[1]) {
                case Diary.HOME: home.add(v); break;
                case Diary.COLD: cold.add(v); break;
                case Diary.FEED: asks++; if (v == 1) fresh++; break;
                case Diary.NET_LOST: drops++; break;
                default: break;
            }
        }
        StringBuilder s = new StringBuilder(2048);
        s.append("{\"v\":1,\"date\":").append(q(date)).append(",\"build\":").append(q(build));
        if (usage != null) {
            DateTimeFormatter hm = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH).withZone(zone);
            s.append(",\"on\":{\"minutes\":").append(Math.round(usage.onMs / 60000.0)).append(",\"spans\":[");
            for (int i = 0; i < usage.on.size() && i < 48; i++) {
                long[] p = usage.on.get(i);
                if (i > 0) s.append(',');
                s.append('[').append(q(hm.format(Instant.ofEpochMilli(p[0])))).append(',').append(q(hm.format(Instant.ofEpochMilli(p[1] - 1)))).append(']');
            }
            s.append("]},\"apps\":[");
            int n = 0;
            for (UsageDay.App a : usage.apps) {
                if (n == 40) break;
                if (n++ > 0) s.append(',');
                String name = names == null ? null : names.get(a.pkg);
                s.append("{\"pkg\":").append(q(a.pkg));
                if (name != null) s.append(",\"name\":").append(q(name));
                s.append(",\"minutes\":").append(Math.round(a.ms / 60000.0)).append(",\"opens\":").append(a.opens).append('}');
            }
            s.append(']');
        }
        s.append(",\"home\":{\"opens\":").append(home.size());
        if (!home.isEmpty()) {
            s.append(",\"median_ms\":").append(pct(home, 50)).append(",\"p90_ms\":").append(pct(home, 90))
                    .append(",\"max_ms\":").append(Collections.max(home)).append(",\"slow\":").append(home.stream().filter(x -> x > SLOW_MS).count());
        }
        s.append('}');
        if (!cold.isEmpty()) s.append(",\"cold\":{\"count\":").append(cold.size()).append(",\"median_ms\":").append(pct(cold, 50)).append('}');
        s.append(",\"feed\":{\"asks\":").append(asks).append(",\"fresh\":").append(fresh).append(",\"old\":").append(asks - fresh).append('}');
        s.append(",\"tv\":{\"net_drops\":").append(drops);
        if (usage != null) s.append(",\"boots\":").append(usage.boots);
        if (tv != null) {
            if (tv.uptimeH >= 0) s.append(",\"uptime_h\":").append(tv.uptimeH);
            if (tv.memFreeMb >= 0) s.append(",\"mem_free_mb\":").append(tv.memFreeMb);
            if (tv.storageFreeMb >= 0) s.append(",\"storage_free_mb\":").append(tv.storageFreeMb);
        }
        return s.append("}}").toString();
    }

    /** The nearest-rank percentile. */
    static long pct(List<Long> xs, int p) {
        List<Long> s = new ArrayList<>(xs);
        Collections.sort(s);
        int i = (int) Math.ceil(p / 100.0 * s.size()) - 1;
        return s.get(Math.max(0, Math.min(s.size() - 1, i)));
    }

    static String q(String v) {
        if (v == null) return "null";
        StringBuilder b = new StringBuilder(v.length() + 2).append('"');
        for (char c : v.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20) b.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }
}
