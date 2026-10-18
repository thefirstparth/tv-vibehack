package com.gathin.tvshared;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The site's TV feed (/api/live/tv), for the home screen's lines and the 1400 app: one request on a background thread
 * when asked (5 second timeout, no retries), the last good answer kept in a file, and that copy shown with its time when
 * a request fails. The feed decides what to show; this class knows only how fresh its copy is and what of it is still
 * believable (the rules below), so every screen says the same thing.
 */
public final class Feed {

    public static final long MIN = 60_000L, HOUR = 60 * MIN, DAY = 24 * HOUR;
    /** A copy of a live line is believed for ten minutes after it was fetched, as on the site. */
    public static final long LIVE_FOR = 10 * MIN;
    /** An answer older than this (the feed's own as_of: the site's edge answering with its cached copy) is shown as a saved copy, with its time. */
    public static final long OLD_AFTER = 20 * MIN;
    private static final int TIMEOUT_MS = 5000;

    private final String url;
    private final File file;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private JSONObject value;    // the feed's value, or null
    private long fetchedAt;      // when that copy was fetched (or, for an old cached copy, made)
    private boolean saved;       // showing the saved copy: the last request failed, or the answer was an old copy
    private boolean old;         // the last answer was the site's old cached copy (a fresh one is on its way)
    private boolean loading;
    /** An address is set, a request has failed and no good copy was ever received. */
    private boolean neverAnswered;

    /** url: the feed's address (empty: off, nothing is ever requested); file: where this screen keeps its copy. */
    public Feed(Context ctx, String url, String file) {
        this.url = url == null ? "" : url.trim();
        this.file = new File(ctx.getFilesDir(), file);
    }

    /** The feed's address from local.properties (built into this module), with extra query text such as "saver=1". */
    public static String address(Context ctx, String query) {
        String u = ctx.getString(R.string.tv_feed_url).trim();
        return u.isEmpty() || query == null ? u : u + (u.contains("?") ? "&" : "?") + query;
    }

    public boolean enabled() {
        return !url.isEmpty();
    }

    public JSONObject value() {
        return value;
    }

    public long fetchedAt() {
        return fetchedAt;
    }

    public boolean saved() {
        return saved;
    }

    public boolean old() {
        return old;
    }

    public boolean neverAnswered() {
        return neverAnswered;
    }

    /** One request on a background thread; done runs on the main thread once the state is updated. */
    public void refresh(Runnable done) {
        if (!enabled() || loading) return;
        loading = true;
        io.execute(() -> {
            JSONObject v = null;
            long at = System.currentTimeMillis();
            boolean fromSaved = false, fromOld = false;
            try {
                JSONObject j = new JSONObject(get(url));
                v = j.optBoolean("ok") ? j.optJSONObject("value") : null;
                if (v == null) throw new IllegalStateException("no value");
                // the site's edge may answer with its cached copy while it makes a fresh one: an old copy is said as such
                long sent = 0;
                try { sent = Instant.parse(j.optString("as_of")).toEpochMilli(); } catch (Exception ignored) { }
                if (sent > 0 && at - sent > OLD_AFTER) {
                    at = sent;
                    fromOld = true;
                }
                JSONObject keep = new JSONObject().put("at", at).put("value", v);
                try (FileOutputStream out = new FileOutputStream(file)) { out.write(keep.toString().getBytes(StandardCharsets.UTF_8)); }
            } catch (Exception e) {
                v = null;
                try {
                    JSONObject keep = new JSONObject(read(new FileInputStream(file)));
                    v = keep.optJSONObject("value");
                    at = keep.optLong("at", 0);
                    fromSaved = v != null;
                } catch (Exception ignored) {
                }
            }
            final JSONObject got = v;
            final long a = at;
            final boolean s = fromSaved || fromOld, o = fromOld;
            main.post(() -> {
                loading = false;
                value = got;
                fetchedAt = a;
                saved = s;
                old = o;
                neverAnswered = got == null;
                if (done != null) done.run();
            });
        });
    }

    private static String get(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            return read(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws Exception {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = s.read(buf)) > 0 && out.size() < 256 * 1024) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    // ---- what is still believable ---------------------------------------------------------------------------------

    /** From a saved copy, a line whose start has passed is dropped, unless it is live and the copy under ten minutes old. */
    public List<JSONObject> usable(JSONArray list, long now) {
        List<JSONObject> out = new ArrayList<>();
        if (list == null) return out;
        for (int i = 0; i < list.length(); i++) {
            JSONObject it = list.optJSONObject(i);
            long start = startOf(it);
            if (it == null || start == 0) continue;
            if (saved && start <= now && !(isLive(it) && now - fetchedAt < LIVE_FOR)) continue;
            out.add(it);
        }
        return out;
    }

    public static long startOf(JSONObject it) {
        try {
            return it == null ? 0 : Instant.parse(it.optString("start")).toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean isLive(JSONObject it) {
        return "live".equals(it.optString("state"));
    }

    /** Live and believable now: said live by the feed, and that word is under ten minutes old. */
    public boolean liveNow(JSONObject it, long now) {
        return isLive(it) && now - fetchedAt < LIVE_FOR;
    }

    /** A start that has passed with no live word from the feed to go on: "Started" (no red dot, no score). */
    public boolean started(JSONObject it, long now) {
        String st = it.optString("state");
        if ("live".equals(st)) return !liveNow(it, now);
        return "next".equals(st) && startOf(it) <= now && !it.optBoolean("starting") && !it.optBoolean("late");
    }

    /** Scores and a top three are shown: live (believed) or finished, and not merely "Started". */
    public boolean scores(JSONObject it, long now) {
        return (liveNow(it, now) || "done".equals(it.optString("state"))) && !started(it, now);
    }

    /** Anything in the home screen's list live now. */
    public JSONObject liveItem(long now) {
        JSONArray list = value == null ? null : value.optJSONArray("sport");
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject it = list.optJSONObject(i);
            if (it != null && liveNow(it, now)) return it;
        }
        return null;
    }

    // ---- Bengaluru's sky, for the light ----------------------------------------------------------------------------

    /** Clear unless the feed's weather word says rain, or a storm or a warning. */
    public enum Sky {
        CLEAR, RAIN, STORM;

        public static Sky of(String word, boolean warning) {
            if (warning || "Storms likely".equals(word)) return STORM;
            if ("Rain likely".equals(word) || "Heavy rain".equals(word) || "Very heavy rain".equals(word)) return RAIN;
            return CLEAR;
        }
    }

    /** Bengaluru's sky from the feed's weather word or warning (clear without a feed). */
    public Sky sky() {
        JSONArray wx = value == null ? null : value.optJSONArray("weather");
        for (int i = 0; wx != null && i < wx.length(); i++) {
            JSONObject c = wx.optJSONObject(i);
            if (c != null && "Bengaluru".equals(c.optString("city"))) {
                // a warning sends its own wording: storm for any official warning
                return Sky.of(c.optBoolean("warn") ? "" : c.optString("word", ""), c.optBoolean("warn"));
            }
        }
        return Sky.CLEAR;
    }
}
