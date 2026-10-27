package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which input the TV's input app (com.mitv.livetv) was showing, as far as the launcher knows: Android only sees that app,
 * never the HDMI port inside it. A choice on the launcher's own Inputs menu (a diary "I" line) names the input app's
 * stretch that opens within a few seconds of it, and the stretches straight after that one (back within a minute, Home
 * not opened in between). A stretch with no choice before it (the remote's source button, the TV waking on its last
 * input) stays unnamed. Pure, so it is unit-tested.
 */
final class InputDay {

    /** A stretch opening this long after a choice is the choice's; a little before allows for two clocks' rounding. */
    static final long MATCH_MS = 20_000, EARLY_MS = 2_000;
    /** Back in the input app within this, with no Home in between: still the same input. */
    static final long CHAIN_MS = 60_000;

    static final class Choice {
        final long t;
        final String id, name;

        Choice(long t, String id, String name) {
            this.t = t;
            this.id = id;
            this.name = name;
        }
    }

    static final class Use {
        final String id, name;
        long ms;
        int opens;
        /** [start, end] in ms, as UsageDay.App.spans: under a minute left out, back within a minute joined. */
        final List<long[]> spans = new ArrayList<>();

        Use(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private InputDay() {
    }

    /** The Inputs-menu choices among some diary lines ("wall-ms TAB I TAB id TAB name"), in time order. */
    static List<Choice> choices(List<String> lines) {
        List<Choice> out = new ArrayList<>();
        for (String line : lines) {
            String[] f = line.split("\t");
            if (f.length < 3 || !Diary.INPUT.equals(f[1]) || f[2].trim().isEmpty()) continue;
            try {
                out.add(new Choice(Long.parseLong(f[0].trim()), f[2].trim(), f.length > 3 ? f[3].trim() : null));
            } catch (NumberFormatException ignored) {
                // a broken line: skipped
            }
        }
        out.sort((a, b) -> Long.compare(a.t, b.t));
        return out;
    }

    /** Minutes per chosen input, most first; empty when the input app wasn't used or nothing was chosen. */
    static List<Use> of(List<Choice> choices, UsageDay.App app, List<Long> homes) {
        return of(choices, app, homes, Collections.emptyList(), null, null);
    }

    /** The dock input's port states among some diary lines ("wall-ms TAB X TAB state"): [time, state], in time order. */
    static List<long[]> signals(List<String> lines) {
        List<long[]> out = new ArrayList<>();
        for (String line : lines) {
            String[] f = line.split("\t");
            if (f.length < 3 || !Diary.SIGNAL.equals(f[1])) continue;
            try {
                out.add(new long[]{Long.parseLong(f[0].trim()), Long.parseLong(f[2].trim())});
            } catch (NumberFormatException ignored) {
                // a broken line: skipped
            }
        }
        out.sort((a, b) -> Long.compare(a[0], b[0]));
        return out;
    }

    /**
     * As above, and a stretch no choice names is the dock input's (dockId, the Xbox) when its port said connected
     * SETTLE_MS into the stretch (or at its end, if shorter): on this TV a port reads connected only while it is on
     * screen with a picture, so the TV waking on HDMI 2 or the remote's source button still counts as the Xbox.
     */
    static List<Use> of(List<Choice> choices, UsageDay.App app, List<Long> homes, List<long[]> signal, String dockId, String dockName) {
        boolean bySignal = dockId != null && !signal.isEmpty();
        if (app == null || (choices.isEmpty() && !bySignal)) return Collections.emptyList();
        List<long[]> runs = new ArrayList<>(app.runs);
        runs.sort((a, b) -> Long.compare(a[0], b[0]));
        Map<String, Use> uses = new LinkedHashMap<>();
        boolean[] taken = new boolean[choices.size()];
        Choice now = null;
        long lastEnd = Long.MIN_VALUE;
        for (long[] r : runs) {
            int c = latest(choices, taken, r[0] - MATCH_MS, r[0] + EARLY_MS);
            if (c >= 0) {
                taken[c] = true;
                now = choices.get(c);
                if (r[0] == r[1]) use(uses, choices, now.id).opens++;   // opened the day before: counted that day
            } else if (now == null || r[0] - lastEnd > CHAIN_MS || between(homes, lastEnd - EARLY_MS, r[0])
                    || chosenBetween(choices, lastEnd, r[0])) {
                now = null;
            }
            lastEnd = r[2];
            Use u;
            if (now != null) {
                u = use(uses, choices, now.id);
            } else if (bySignal && stateAt(signal, Math.min(r[1] + SETTLE_MS, r[2])) == CONNECTED) {
                u = uses.get(dockId);
                if (u == null) uses.put(dockId, u = new Use(dockId, dockName));
                if (r[0] == r[1]) u.opens++;
            } else {
                continue;
            }
            u.ms += r[2] - r[1];
            if (r[2] - r[1] < 60_000) continue;
            long[] last = u.spans.isEmpty() ? null : u.spans.get(u.spans.size() - 1);
            if (last != null && r[1] - last[1] < 60_000) last[1] = r[2];
            else u.spans.add(new long[]{r[1], r[2]});
        }
        List<Use> out = new ArrayList<>(uses.values());
        out.removeIf(u -> u.ms <= 0);
        out.sort((a, b) -> Long.compare(b.ms, a.ms));
        return out;
    }

    /** The TV re-reads a port's picture when it comes on screen: its state is read this long into a stretch. */
    static final long SETTLE_MS = 20_000;
    /** TvInputManager.INPUT_STATE_CONNECTED. */
    private static final long CONNECTED = 0;

    /** The last state at or before t, or -1 when none is known. */
    private static long stateAt(List<long[]> signal, long t) {
        long s = -1;
        for (long[] x : signal) {
            if (x[0] > t) break;
            s = x[1];
        }
        return s;
    }

    /** The latest choice not yet used in [from, to], or -1. */
    private static int latest(List<Choice> choices, boolean[] taken, long from, long to) {
        for (int i = choices.size() - 1; i >= 0; i--) {
            long t = choices.get(i).t;
            if (t <= to && t >= from && !taken[i]) return i;
            if (t < from) break;
        }
        return -1;
    }

    /** An input's line, under the newest name it was chosen by (its name on the menu can change with the config). */
    private static Use use(Map<String, Use> uses, List<Choice> choices, String id) {
        Use u = uses.get(id);
        if (u != null) return u;
        String name = null;
        for (Choice c : choices) if (c.id.equals(id) && c.name != null && !c.name.isEmpty()) name = c.name;
        uses.put(id, u = new Use(id, name));
        return u;
    }

    private static boolean between(List<Long> times, long from, long to) {
        for (long t : times) if (t > from && t < to) return true;
        return false;
    }

    private static boolean chosenBetween(List<Choice> choices, long from, long to) {
        for (Choice c : choices) if (c.t > from && c.t < to) return true;
        return false;
    }
}
