package com.gathin.tvhome;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The launcher's own diary: one short line per event in files/diary/YYYY-MM-DD.tsv (the TV's local date), written on a
 * background thread so nothing waits for storage. "wall-ms TAB kind TAB value": H a Home return's time to its first
 * drawn frame (ms), C a cold start's time (ms), F a feed answer (1 fresh, 0 an old or failed copy), N the network
 * lost. Kept 35 days. Read back once a day by DiarySend for the day's summary.
 */
final class Diary {

    static final String HOME = "H", COLD = "C", FEED = "F", NET_LOST = "N";
    static final int KEEP_DAYS = 35;

    private static Diary one;
    private final File dir;
    final ExecutorService io = Executors.newSingleThreadExecutor();

    private Diary(Context c) {
        dir = new File(c.getFilesDir(), "diary");
    }

    static synchronized Diary get(Context c) {
        if (one == null) one = new Diary(c.getApplicationContext());
        return one;
    }

    void home(long ms, boolean cold) {
        add(cold ? COLD : HOME, ms);
    }

    void feed(boolean fresh) {
        add(FEED, fresh ? 1 : 0);
    }

    void netLost() {
        add(NET_LOST, 0);
    }

    private String lastDate = "";

    private void add(String kind, long value) {
        long now = System.currentTimeMillis();
        String line = now + "\t" + kind + "\t" + value + "\n";
        io.execute(() -> {
            try {
                if (!dir.isDirectory() && !dir.mkdirs()) return;
                LocalDate today = LocalDate.now(ZoneId.systemDefault());
                if (!today.toString().equals(lastDate)) {   // once a day, also when nothing is sent anywhere
                    lastDate = today.toString();
                    prune(today);
                }
                try (FileOutputStream out = new FileOutputStream(file(lastDate), true)) {
                    out.write(line.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {
                // a diary line is never worth an error on the home screen
            }
        });
    }

    private File file(String date) {
        return new File(dir, date + ".tsv");
    }

    /** One day's lines (call on io). */
    List<String> lines(String date) {
        try {
            File f = file(date);
            return f.isFile() ? Files.readAllLines(f.toPath(), StandardCharsets.UTF_8) : Collections.emptyList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Files older than KEEP_DAYS go (call on io): at most three a day, so a clock that jumps ahead by mistake can cost
     * a few days of the diary, never all of it; and nothing before the clock is set.
     */
    void prune(LocalDate today) {
        if (today.isBefore(DiarySend.CLOCK_FLOOR)) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        String oldest = today.minusDays(KEEP_DAYS).toString();
        java.util.Arrays.sort(files);
        int gone = 0;
        for (File f : files) {
            String n = f.getName();
            if (gone == 3 || !n.endsWith(".tsv") || n.compareTo(oldest) >= 0) continue;
            if (f.delete()) gone++;
        }
    }
}
