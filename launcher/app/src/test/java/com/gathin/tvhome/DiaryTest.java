package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The daily summary: Android's usage events worked into a day (UsageDay), and the day's JSON (DaySummary). */
public class DiaryTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final long START = at(21, 0, 0), END = at(22, 0, 0), MIN = 60_000L;

    private static long at(int day, int h, int m) {
        return LocalDateTime.of(2026, 10, day, h, m).atZone(IST).toInstant().toEpochMilli();
    }

    private static UsageDay.Ev ev(long t, int type, String pkg) {
        return new UsageDay.Ev(t, type, pkg);
    }

    @Test
    public void appsAndScreenTimeForADay() {
        List<UsageDay.Ev> evs = Arrays.asList(
                ev(at(21, 19, 0), UsageDay.SCREEN_ON, "android"),
                ev(at(21, 19, 0), UsageDay.RESUMED, "com.gathin.tvhome"),
                ev(at(21, 19, 1), UsageDay.PAUSED, "com.gathin.tvhome"),
                ev(at(21, 19, 1), UsageDay.RESUMED, "com.sonyliv"),
                // a second activity of the same app: still one open, no extra minutes
                ev(at(21, 19, 30), UsageDay.RESUMED, "com.sonyliv"),
                ev(at(21, 19, 30), UsageDay.PAUSED, "com.sonyliv"),
                ev(at(21, 21, 1), UsageDay.PAUSED, "com.sonyliv"),
                ev(at(21, 21, 1), UsageDay.RESUMED, "com.netflix.ninja"),
                ev(at(21, 21, 31), UsageDay.SCREEN_OFF, "android"));
        UsageDay d = UsageDay.of(evs, START, END);
        assertEquals(2, d.apps.size());
        assertEquals("com.sonyliv", d.apps.get(0).pkg);
        assertEquals(120 * MIN, d.apps.get(0).ms);
        assertEquals(1, d.apps.get(0).opens);
        assertEquals(30 * MIN, d.apps.get(1).ms);   // closed by the screen going off
        assertEquals(151 * MIN, d.onMs);
        assertEquals(1, d.on.size());
    }

    @Test
    public void onAtMidnightCountsFromMidnightAndRestartsAreCounted() {
        List<UsageDay.Ev> evs = Arrays.asList(
                ev(at(20, 23, 0), UsageDay.SCREEN_ON, "android"),
                ev(at(20, 23, 0), UsageDay.RESUMED, "com.sonyliv"),
                ev(at(21, 0, 40), UsageDay.SHUTDOWN, "android"),
                ev(at(21, 0, 42), UsageDay.STARTUP, "android"),
                ev(at(21, 0, 42), UsageDay.SCREEN_ON, "android"),
                ev(at(21, 0, 50), UsageDay.SCREEN_OFF, "android"));
        UsageDay d = UsageDay.of(evs, START, END);
        assertEquals(48 * MIN, d.onMs);
        assertEquals(1, d.boots);
        assertEquals(40 * MIN, d.apps.get(0).ms);
        assertEquals(0, d.apps.get(0).opens);   // opened the day before
    }

    @Test
    public void stillOnAtTheEndOfTheDay() {
        UsageDay d = UsageDay.of(Arrays.asList(ev(at(21, 23, 0), UsageDay.SCREEN_ON, "android"),
                ev(at(21, 23, 0), UsageDay.RESUMED, "in.startv.hotstar")), START, END);
        assertEquals(60 * MIN, d.onMs);
        assertEquals(60 * MIN, d.apps.get(0).ms);
    }

    @Test
    public void theDaysJson() {
        UsageDay u = UsageDay.of(Arrays.asList(ev(at(21, 19, 0), UsageDay.SCREEN_ON, "android"),
                ev(at(21, 19, 0), UsageDay.RESUMED, "com.sonyliv"), ev(at(21, 20, 0), UsageDay.SCREEN_OFF, "android")), START, END);
        Map<String, String> names = new HashMap<>();
        names.put("com.sonyliv", "Sony \"LIV\"");
        List<String> diary = Arrays.asList("1\tH\t80", "2\tH\t120", "3\tH\t400", "4\tC\t610", "5\tF\t1", "6\tF\t0", "7\tN\t0", "bad line");
        DaySummary.Tv tv = new DaySummary.Tv();
        tv.uptimeH = 30;
        String j = DaySummary.json("2026-10-21", "e1cc510", diary, u, names, tv, IST);
        assertTrue(j, j.startsWith("{\"v\":1,\"date\":\"2026-10-21\",\"build\":\"e1cc510\",\"on\":{\"minutes\":60,\"spans\":[[\"19:00\",\"19:59\"]]}"));
        assertTrue(j, j.contains("{\"pkg\":\"com.sonyliv\",\"name\":\"Sony \\\"LIV\\\"\",\"minutes\":60,\"opens\":1}"));
        assertTrue(j, j.contains("\"home\":{\"opens\":3,\"median_ms\":120,\"p90_ms\":400,\"max_ms\":400,\"slow\":2}"));
        assertTrue(j, j.contains("\"cold\":{\"count\":1,\"median_ms\":610}"));
        assertTrue(j, j.contains("\"feed\":{\"asks\":2,\"fresh\":1,\"old\":1}"));
        assertTrue(j, j.endsWith("\"tv\":{\"net_drops\":1,\"boots\":0,\"uptime_h\":30}}"));
    }

    @Test
    public void withoutUsageAccessOnlyTheLaunchersOwnNumbers() {
        String j = DaySummary.json("2026-10-21", "", Collections.emptyList(), null, null, null, IST);
        assertEquals("{\"v\":1,\"date\":\"2026-10-21\",\"build\":\"\",\"home\":{\"opens\":0},\"feed\":{\"asks\":0,\"fresh\":0,\"old\":0},\"tv\":{\"net_drops\":0}}", j);
    }
}
