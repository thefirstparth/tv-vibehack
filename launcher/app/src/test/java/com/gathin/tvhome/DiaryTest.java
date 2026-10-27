package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
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
        assertEquals(1, d.apps.get(0).spans.size());   // the second activity didn't split the stretch
        assertEquals(at(21, 19, 1), d.apps.get(0).spans.get(0)[0]);
        assertEquals(at(21, 21, 1), d.apps.get(0).spans.get(0)[1]);
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
        assertTrue(j, j.contains("{\"pkg\":\"com.sonyliv\",\"name\":\"Sony \\\"LIV\\\"\",\"minutes\":60,\"opens\":1,\"spans\":[[\"19:00\",\"19:59\"]]}"));
        assertTrue(j, j.contains("\"home\":{\"opens\":3,\"median_ms\":120,\"p90_ms\":400,\"max_ms\":400,\"slow\":2}"));
        assertTrue(j, j.contains("\"cold\":{\"count\":1,\"median_ms\":610}"));
        assertTrue(j, j.contains("\"feed\":{\"asks\":2,\"fresh\":1,\"old\":1}"));
        assertTrue(j, j.endsWith("\"tv\":{\"net_drops\":1,\"boots\":0,\"uptime_h\":30}}"));
    }

    @Test
    public void anOffDayIsSentAsZeroMinutesAndSendProblemsRideAlong() {
        String j = DaySummary.json("2026-10-21", "x", Collections.emptyList(), new UsageDay(), null, null, IST,
                "{\"failed\":3,\"last\":\"no internet\",\"at\":\"2026-10-22T19:42\"}");
        assertTrue(j, j.contains("\"on\":{\"minutes\":0,\"spans\":[]},\"apps\":[]"));
        assertTrue(j, j.endsWith(",\"send\":{\"failed\":3,\"last\":\"no internet\",\"at\":\"2026-10-22T19:42\"}}"));
    }

    @Test
    public void whichDayToSendFromAndWhatARefusalMeans() {
        java.time.LocalDate today = java.time.LocalDate.of(2026, 11, 30);
        assertEquals(today.minusDays(30), DiarySend.Plan.firstToSend(today, "", 30));
        assertEquals(java.time.LocalDate.of(2026, 11, 28), DiarySend.Plan.firstToSend(today, "2026-11-27", 30));
        assertEquals(today.minusDays(30), DiarySend.Plan.firstToSend(today, "2026-01-01", 30));   // long ago: 30 days back
        assertEquals(today.minusDays(30), DiarySend.Plan.firstToSend(today, "2027-05-01", 30));   // sent "in the future": the clock was wrong
        assertEquals(today.minusDays(30), DiarySend.Plan.firstToSend(today, "junk", 30));
        assertEquals(null, DiarySend.Plan.after(200));
        assertEquals("key refused (401)", DiarySend.Plan.after(401));
        assertEquals("site error (500)", DiarySend.Plan.after(500));
        assertTrue(DiarySend.Plan.skip(400));
        assertTrue(!DiarySend.Plan.skip(500) && !DiarySend.Plan.skip(401));
    }

    private static final String TVIN = "com.mitv.livetv", XBOX = "com.mediatek.tvinput/.hdmi.HDMIInputService/HW5";

    private static UsageDay inputDay(UsageDay.Ev... more) {
        List<UsageDay.Ev> evs = new java.util.ArrayList<>(Arrays.asList(
                ev(at(21, 19, 0), UsageDay.SCREEN_ON, "android"),
                ev(at(21, 19, 0), UsageDay.RESUMED, "com.gathin.tvhome")));
        evs.addAll(Arrays.asList(more));
        evs.add(ev(at(21, 23, 0), UsageDay.SCREEN_OFF, "android"));
        return UsageDay.of(evs, START, END);
    }

    @Test
    public void anInputChosenOnHomeNamesTheInputAppsNextStretch() {
        UsageDay u = inputDay(
                ev(at(21, 19, 1) + 1500, UsageDay.PAUSED, "com.gathin.tvhome"),
                ev(at(21, 19, 1) + 1500, UsageDay.RESUMED, TVIN),
                // a quick pause and back (the input app re-opening its player): the same input
                ev(at(21, 19, 30), UsageDay.PAUSED, TVIN),
                ev(at(21, 19, 30) + 3000, UsageDay.RESUMED, TVIN),
                ev(at(21, 20, 31), UsageDay.PAUSED, TVIN),
                ev(at(21, 20, 31), UsageDay.RESUMED, "com.gathin.tvhome"),
                // Home, then the input app again with no choice (the remote's source button): not named
                ev(at(21, 20, 32), UsageDay.PAUSED, "com.gathin.tvhome"),
                ev(at(21, 20, 32), UsageDay.RESUMED, TVIN),
                ev(at(21, 21, 2), UsageDay.PAUSED, TVIN),
                ev(at(21, 21, 2), UsageDay.RESUMED, "com.gathin.tvhome"));
        List<String> lines = Arrays.asList(at(21, 19, 1) + "\tI\t" + XBOX + "\tXbox", at(21, 19, 1) + "\tH\t80", "junk\tI\tx\ty");
        List<InputDay.Use> in = InputDay.of(InputDay.choices(lines), u.app(TVIN), u.homes);
        assertEquals(1, in.size());
        assertEquals(XBOX, in.get(0).id);
        assertEquals("Xbox", in.get(0).name);
        assertEquals(1, in.get(0).opens);
        assertEquals(90 * MIN - 1500 - 3000, in.get(0).ms);
        assertEquals(1, in.get(0).spans.size());   // back within a minute: one stretch
        assertEquals(120 * MIN - 1500 - 3000, u.app(TVIN).ms);   // the input app's own total keeps the unnamed half hour
    }

    @Test
    public void aChoiceWithNoStretchAfterItNamesNothing() {
        UsageDay u = inputDay(
                ev(at(21, 20, 0), UsageDay.PAUSED, "com.gathin.tvhome"),
                ev(at(21, 20, 0), UsageDay.RESUMED, TVIN),
                ev(at(21, 21, 0), UsageDay.PAUSED, TVIN));
        // chosen a minute before: too long before to be this stretch's
        List<InputDay.Use> in = InputDay.of(InputDay.choices(Arrays.asList(at(21, 19, 59) + "\tI\t" + XBOX + "\tXbox")), u.app(TVIN), u.homes);
        assertTrue(in.isEmpty());
        assertTrue(InputDay.of(InputDay.choices(Collections.emptyList()), u.app(TVIN), u.homes).isEmpty());
        assertTrue(InputDay.of(InputDay.choices(Arrays.asList(at(21, 20, 0) + "\tI\tlivetv\tLive TV")), null, u.homes).isEmpty());
    }

    @Test
    public void aStretchOverMidnightIsNamedByTheEveningsChoice() {
        List<UsageDay.Ev> evs = Arrays.asList(
                ev(at(20, 23, 30), UsageDay.SCREEN_ON, "android"),
                ev(at(20, 23, 30), UsageDay.RESUMED, TVIN),
                ev(at(21, 0, 45), UsageDay.PAUSED, TVIN),
                ev(at(21, 0, 45), UsageDay.SCREEN_OFF, "android"));
        UsageDay u = UsageDay.of(evs, START, END);
        List<InputDay.Use> in = InputDay.of(InputDay.choices(Arrays.asList((at(20, 23, 30) - 2000) + "\tI\t" + XBOX + "\tXbox")), u.app(TVIN), u.homes);
        assertEquals(45 * MIN, in.get(0).ms);   // only this day's part
        assertEquals(0, in.get(0).opens);   // opened the day before: counted on that day
    }

    @Test
    public void theInputsGoInTheJson() {
        UsageDay u = inputDay(
                ev(at(21, 19, 1), UsageDay.PAUSED, "com.gathin.tvhome"),
                ev(at(21, 19, 1), UsageDay.RESUMED, TVIN),
                ev(at(21, 20, 1), UsageDay.PAUSED, TVIN));
        List<InputDay.Use> in = InputDay.of(InputDay.choices(Arrays.asList(at(21, 19, 1) + "\tI\t" + XBOX + "\tXbox")), u.app(TVIN), u.homes);
        String j = DaySummary.json("2026-10-21", "x", Collections.emptyList(), u, null, null, IST, in);
        assertTrue(j, j.contains("],\"inputs\":[{\"id\":\"" + XBOX + "\",\"name\":\"Xbox\",\"minutes\":60,\"opens\":1,\"spans\":[[\"19:01\",\"20:00\"]]}],\"home\""));
        assertTrue(DaySummary.json("2026-10-21", "x", Collections.emptyList(), u, null, null, IST, Collections.emptyList()).indexOf("inputs") < 0);
    }

    @Test
    public void theTileOrAWakeOnHdmi2IsNamedByTheSignalWhenNothingWasChosen() {
        UsageDay u = inputDay(
                // the TV woke on HDMI 2 with the Xbox on: no choice, but the port has a picture
                ev(at(21, 19, 10), UsageDay.PAUSED, "com.gathin.tvhome"),
                ev(at(21, 19, 10), UsageDay.RESUMED, TVIN),
                ev(at(21, 20, 10), UsageDay.PAUSED, TVIN),
                ev(at(21, 20, 10), UsageDay.RESUMED, "com.gathin.tvhome"),
                // later the input app again, the port with no picture (the Xbox off): not named
                ev(at(21, 21, 0), UsageDay.PAUSED, "com.gathin.tvhome"),
                ev(at(21, 21, 0), UsageDay.RESUMED, TVIN),
                ev(at(21, 21, 30), UsageDay.PAUSED, TVIN),
                ev(at(21, 21, 30), UsageDay.RESUMED, "com.gathin.tvhome"));
        List<String> lines = Arrays.asList(at(21, 19, 0) + "\tX\t1", (at(21, 19, 10) + 4000) + "\tX\t0",
                at(21, 20, 10) + "\tX\t0", (at(21, 21, 0) + 3000) + "\tX\t1");
        List<InputDay.Use> in = InputDay.of(InputDay.choices(lines), u.app(TVIN), u.homes, InputDay.signals(lines), XBOX, "Xbox");
        assertEquals(1, in.size());
        assertEquals("Xbox", in.get(0).name);
        assertEquals(60 * MIN, in.get(0).ms);
        assertEquals(1, in.get(0).opens);
        // without the dock input the signal names nothing, as before
        assertTrue(InputDay.of(InputDay.choices(lines), u.app(TVIN), u.homes).isEmpty());
    }

    @Test
    public void aDaySaysWhichBuildsWroteIt() throws Exception {
        assertEquals("send", DaySummary.builds(Arrays.asList("1\tH\t80"), "send"));
        assertEquals("759be62 \u2192 d7d4c09", DaySummary.builds(Arrays.asList("1\tB\t759be62", "2\tH\t80", "3\tB\t759be62", "4\tB\td7d4c09"), "send"));
        java.io.File dir = java.nio.file.Files.createTempDirectory("diary").toFile();
        Diary d = new Diary(dir, "abc1234");
        d.netLost();
        d.netLost();
        d.io.submit(() -> { }).get();
        String today = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString();
        List<String> written = d.lines(today);
        assertEquals(3, written.size());   // the build once, then the two lines
        assertTrue(written.get(0).endsWith("\tB\tabc1234"));
        assertEquals("abc1234", DaySummary.builds(written, "send"));
    }

    @Test
    public void aFrozenDayIsSentAsFirstMadeWithTodaysSendProblems() throws Exception {
        java.io.File dir = java.nio.file.Files.createTempDirectory("diary").toFile();
        Diary d = new Diary(dir);
        String day = DaySummary.json("2026-10-21", "x", Collections.emptyList(), new UsageDay(), null, null, IST, (String) null);
        assertEquals(null, d.frozen("2026-10-21"));
        d.freeze("2026-10-21", day);
        assertEquals(day, d.frozen("2026-10-21"));
        assertEquals(day.substring(0, day.length() - 1) + ",\"send\":{\"failed\":12}}", DaySummary.withSend(d.frozen("2026-10-21"), "{\"failed\":12}"));
        assertEquals(day, DaySummary.withSend(day, null));
        // a copy cut short is made again, not sent
        java.nio.file.Files.write(new java.io.File(dir, "sent/2026-10-22.json").toPath(), "{\"v\":1,\"date\":\"2026-10-22\",\"on\":{".getBytes(StandardCharsets.UTF_8));
        assertEquals(null, d.frozen("2026-10-22"));
        // kept 35 days, like the diary
        d.freeze("2026-09-01", day.replace("2026-10-21", "2026-09-01"));
        d.prune(java.time.LocalDate.of(2026, 11, 25));   // 35 days after the 21st: still kept
        assertEquals(null, d.frozen("2026-09-01"));
        assertEquals(day, d.frozen("2026-10-21"));
        d.prune(java.time.LocalDate.of(2026, 11, 26));
        assertEquals(null, d.frozen("2026-10-21"));
    }

    @Test
    public void withoutUsageAccessOnlyTheLaunchersOwnNumbers() {
        String j = DaySummary.json("2026-10-21", "", Collections.emptyList(), null, null, null, IST);
        assertEquals("{\"v\":1,\"date\":\"2026-10-21\",\"build\":\"\",\"home\":{\"opens\":0},\"feed\":{\"asks\":0,\"fresh\":0,\"old\":0},\"tv\":{\"net_drops\":0}}", j);
    }
}
