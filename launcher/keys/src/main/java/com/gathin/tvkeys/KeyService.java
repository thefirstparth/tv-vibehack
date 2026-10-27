package com.gathin.tvkeys;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.tv.TvContract;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Remote buttons: Android shows this service each button press before the TV acts on it. A button with a job (Jobs)
 * does that job instead, on release; every other button is handed straight back untouched, and navigation, OK, Back,
 * Home, volume and power are never even looked up. It never reads the screen. The idea and the shape follow Talkies'
 * RemoteKeys (MIT), itself after Plume and CleanYourTV.
 *
 * Switched on once in Settings → Device Preferences → Accessibility → Remote buttons (or with adb, README). If its
 * process ever crashes, Android switches it off and the buttons simply do what they did before.
 */
public class KeyService extends AccessibilityService {
    static final String TAG = "TvKeys";
    private static final long LEARN_MS = 60_000;

    /** Key code → job, read when the service starts and whenever Setup changes them. Main thread only. */
    private static final SparseArray<String> jobs = new SparseArray<>();
    private static long learnUntil;
    private static final StringBuilder learned = new StringBuilder();

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("jobs", MODE_PRIVATE);
    }

    static void load(Context c) {
        jobs.clear();
        for (java.util.Map.Entry<String, ?> e : prefs(c).getAll().entrySet()) {
            try {
                if (e.getKey().startsWith("k")) jobs.put(Integer.parseInt(e.getKey().substring(1)), String.valueOf(e.getValue()));
            } catch (NumberFormatException ignored) {
                // not ours
            }
        }
    }

    /** For the next minute each button press is logged ("Learned …") and does nothing, so its code can be read. */
    static void learn() {
        learned.setLength(0);
        learnUntil = SystemClock.uptimeMillis() + LEARN_MS;
    }

    static String learned() {
        return learned.toString();
    }

    @Override
    protected void onServiceConnected() {
        load(this);
        Log.i(TAG, "On, " + jobs.size() + " button(s) with a job");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (!Jobs.learnable(code)) return false;
        if (SystemClock.uptimeMillis() < learnUntil) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                String what = code + " " + KeyEvent.keyCodeToString(code) + " (scan " + e.getScanCode() + ")";
                Log.i(TAG, "Learned " + what);
                learned.append(learned.length() == 0 ? "" : ", ").append(what);
            }
            return true;
        }
        String job = jobs.get(code);
        if (job == null) return false;
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) run(job);
        return true;   // the down, any repeats and the up: the TV never sees this button
    }

    private void run(String job) {
        try {
            Intent i = null;
            if (job.startsWith(Jobs.APP)) {
                PackageManager pm = getPackageManager();
                String pkg = job.substring(Jobs.APP.length());
                i = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            } else if (job.startsWith(Jobs.INPUT)) {
                // the launcher's input tile switches the same way
                i = new Intent(Intent.ACTION_VIEW).setDataAndType(
                        TvContract.buildChannelUriForPassthroughInput(job.substring(Jobs.INPUT.length())), "vnd.android.cursor.item/channel");
            }
            if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            else if (!job.equals(Jobs.NONE)) Log.w(TAG, "Nothing to open for " + job);
        } catch (RuntimeException ex) {
            Log.w(TAG, "Couldn't do " + job + ": " + ex);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
