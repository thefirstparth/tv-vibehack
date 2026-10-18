package com.gathin.tvshared;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * How the TV words the feed's sport, the same on the home screen and in the 1400 app: times in IST ("Tonight 12:30 am",
 * "Thu 7:30 am", "in 17h"), the moment's word ("Live 62'", "Started", "Full time", "Postponed"), and the longer and
 * shorter forms of names and competitions to step down through instead of cutting a word. Each screen draws these in
 * its own look and picks the longest form that fits.
 */
public final class Words {

    private static final TimeZone IST = TimeZone.getTimeZone("Asia/Kolkata");
    private static final long HOUR = Feed.HOUR, DAY = Feed.DAY;

    private Words() {
    }

    // ---- the label over a headline -----------------------------------------------------------------------------------

    /** What kind of moment: said plainly, live (red, with a dot), or off (amber: postponed, abandoned). */
    public static final int PLAIN = 0, LIVE = 1, OFF = 2;

    /**
     * The label over an event, longest form first: "La Liga · Tonight 12:30 am" (and the countdown "in 17h" apart, in
     * the competition's colour), "Live 62' · La Liga", "Full time · UCL". comps[i] is the competition text inside
     * forms[i] (to colour), null for the last form.
     */
    public static final class Label {
        public final String[] forms, comps;
        public final String countdown;
        public final int kind;

        Label(String[] forms, String[] comps, String countdown, int kind) {
            this.forms = forms;
            this.comps = comps;
            this.countdown = countdown;
            this.kind = kind;
        }
    }

    public static Label label(Feed f, JSONObject it, long now) {
        String sp = it.optString("sp");
        boolean f1 = "f1".equals(sp);
        String st = it.optString("state");
        int kind = PLAIN;
        String lead;
        String[] comps = f1 ? new String[]{it.optString("session"), ""} : compForms(it);
        if (f.liveNow(it, now)) {
            kind = LIVE;
            String pause = it.optString("pause", ""), clock = it.optString("clock", "");
            lead = !pause.isEmpty() ? pauseWord(pause) : clock.isEmpty() ? "Live" : "Live " + clock;
        } else if (f.started(it, now)) {
            lead = "Started";
        } else if ("done".equals(st)) {
            lead = f1 ? "Result" : resultWord(it);
            if (f1 && it.optBoolean("provisional")) comps = new String[]{it.optString("session") + " · Provisional", ""};
        } else if ("off".equals(st)) {
            if (it.has("offWhy")) {
                lead = it.optString("offWhy");
                kind = OFF;
            } else {
                lead = seenWord(it);
            }
        } else if (it.optBoolean("starting")) {
            lead = "Starting";
        } else if (it.optBoolean("late")) {
            lead = "tennis".equals(sp) ? "Not yet on court" : "Delayed";
        } else {
            // next: the competition first, then when ("LA LIGA · TONIGHT 12:30 AM   IN 17H")
            long start = Feed.startOf(it);
            String when = it.optBoolean("tbc") ? dayWord(start, now) + " · Time TBC" : timeLabel(start, now);
            String countdown = it.optBoolean("tbc") ? null : hoursLeft(start, now);
            return new Label(new String[]{join(comps[0], when), join(comps[1], when), when}, new String[]{comps[0], comps[1], null}, countdown, kind);
        }
        return new Label(new String[]{join(lead, comps[0]), join(lead, comps[1]), lead}, new String[]{comps[0], comps[1], null}, null, kind);
    }

    /** When, for a row under the headline: the clock while live, the moment's word, else the time and the hours left. */
    public static String when(Feed f, JSONObject it, long now) {
        String st = it.optString("state");
        if (f.liveNow(it, now)) return it.optString("clock", "");
        if (f.started(it, now)) return "Started";
        if ("done".equals(st)) return "f1".equals(it.optString("sp")) ? "Result" : resultWord(it);
        if ("off".equals(st)) return it.has("offWhy") ? it.optString("offWhy") : seenWord(it);
        if (it.optBoolean("starting")) return "Starting";
        if (it.optBoolean("late")) return "tennis".equals(it.optString("sp")) ? "Not yet on court" : "Delayed";
        long start = Feed.startOf(it);
        String left = it.optBoolean("tbc") ? null : hoursLeft(start, now);
        return it.optBoolean("tbc") ? dayWord(start, now) + ", time TBC" : timeLabel(start, now) + (left != null ? ", " + left : "");
    }

    // ---- names and competitions --------------------------------------------------------------------------------------

    /** "Real Madrid 2–1 Sevilla", "India 245/3 v Australia", "Lakers v Warriors". */
    public static String sides(JSONObject it, String a, String b, boolean scores) {
        String sa = scores ? it.optString("sa", "") : "", sb = scores ? it.optString("sb", "") : "";
        if (b.isEmpty()) return a;
        if ("cricket".equals(it.optString("sp"))) return (a + (sa.isEmpty() ? "" : " " + sa)) + " v " + (b + (sb.isEmpty() ? "" : " " + sb));
        if (!sa.isEmpty() && !sb.isEmpty()) return a + " " + sa + "–" + sb + " " + b;
        return a + " v " + b;
    }

    /** Name pairs from longest to shortest: as sent, then each side's short form where the feed gives one. */
    public static String[][] nameForms(JSONObject it) {
        String a = it.optString("a", ""), b = it.optString("b", "");
        String as = it.optString("a_short", a), bs = it.optString("b_short", b);
        List<String[]> out = new ArrayList<>();
        out.add(new String[]{a, b});
        if (!as.equals(a)) out.add(new String[]{as, b});
        if (!bs.equals(b)) out.add(new String[]{a, bs});
        if (!as.equals(a) && !bs.equals(b)) out.add(new String[]{as, bs});
        return out.toArray(new String[0][]);
    }

    public static String[] compForms(JSONObject it) {
        String c = it.optString("comp", "");
        return new String[]{c, it.optString("comp_short", c)};
    }

    /** The result in the label: FULL TIME, FINAL, INDIA WON BY 14 RUNS, ALCARAZ WON. */
    public static String resultWord(JSONObject it) {
        if (it.has("result")) return it.optString("result");
        switch (it.optString("sp")) {
            case "nba": return "Final";
            case "football":
            case "intl": return "Full time";
            default: return "Result";
        }
    }

    /** A live line the feed lost sight of: when it was last seen live, from the feed's own time. */
    public static String seenWord(JSONObject it) {
        try {
            return "Last seen live " + clock(Instant.parse(it.optString("seen")).toEpochMilli());
        } catch (Exception e) {
            return "Not live";
        }
    }

    public static String pauseWord(String p) {
        return "HT".equalsIgnoreCase(p) ? "Half-time" : p;
    }

    public static String join(String a, String b) {
        return a == null || a.isEmpty() ? b : b == null || b.isEmpty() ? a : a + " · " + b;
    }

    /** A cricket score without its overs, the shorter form. */
    public static String noOvers(String s) {
        return s.replaceAll("\\s*\\(.*\\)$", "");
    }

    // ---- times, in IST -----------------------------------------------------------------------------------------------

    public static long istDay(long ms) {
        return Math.floorDiv(ms + IST.getOffset(ms), DAY);
    }

    public static int istHour(long ms) {
        return (int) (Math.floorMod(ms + IST.getOffset(ms), DAY) / HOUR);
    }

    public static String fmt(String pattern, long ms) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.ENGLISH);
        f.setTimeZone(IST);
        return f.format(new Date(ms));
    }

    /** "12:30 am": 12-hour, lowercase am/pm, always the minutes. */
    public static String clock(long ms) {
        return fmt("h:mm a", ms).replace("AM", "am").replace("PM", "pm");
    }

    /**
     * Within a day: "Today 12:30 pm", "Tonight 12:30 am" (a start after midnight counts as tonight), "Tomorrow 7:30 am".
     * One to six days: "Thu 12:30 am". Seven to fifteen: "Sat 31 Oct, 1:30 am".
     */
    public static String timeLabel(long start, long now) {
        long d = istDay(start) - istDay(now);
        if (start > now && start - now <= DAY) {
            String w = istHour(start) < 5 && (d == 1 || istHour(now) < 5) ? "Tonight" : d == 0 ? "Today" : "Tomorrow";
            return w + " " + clock(start);
        }
        if (d <= 6) return fmt("EEE", start) + " " + clock(start);
        return fmt("EEE d MMM", start) + ", " + clock(start);
    }

    /** The day alone, for a time not yet announced. */
    public static String dayWord(long start, long now) {
        long d = istDay(start) - istDay(now);
        return d == 0 ? "Today" : d == 1 ? "Tomorrow" : d <= 6 ? fmt("EEE", start) : fmt("EEE d MMM", start);
    }

    /** "in 17h", "in under 1h" (whole hours, rounded down), within a day only. */
    public static String hoursLeft(long start, long now) {
        long left = start - now;
        if (left <= 0 || left > DAY) return null;
        return left < HOUR ? "in under 1h" : "in " + (left / HOUR) + "h";
    }
}
