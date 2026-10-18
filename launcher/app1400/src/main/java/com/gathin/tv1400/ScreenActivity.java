package com.gathin.tv1400;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.WindowManager;

import com.gathin.tvshared.Feed;
import com.gathin.tvshared.SunLine;
import com.gathin.tvshared.SunTimes;

/**
 * 1400: the paper on the TV, opened from its tile or the home screen's moon pill. Opens in Sport mode from an hour
 * before a followed event until 30 minutes after it (or while anything is live), otherwise Today. Left and Right switch
 * mode, Up and Down go to the previous or next stop, Back closes it (and the home screen puts focus back where it was).
 * The screen stays on while it is open; after three hours without a button press it closes itself. Nothing runs once
 * it is closed.
 */
public class ScreenActivity extends Activity {

    private Screen screen;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Closes the app after the idle time (res/values: idle_minutes, 180; a test build can set it shorter). */
    private final Runnable idle = this::finish;
    private long idleMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        SunTimes.locate(getResources());
        setContentView(R.layout.screen);
        Type.apply(getWindow().getDecorView());
        screen = new Screen(this, getWindow(), findViewById(R.id.screenRoot));
        idleMs = getResources().getInteger(R.integer.idle_minutes) * 60_000L;
        readFakes(getIntent());
    }

    /** For screenshots only (launch extras): "at" (yyyy-MM-ddTHH:mm, the light's time), "mood" (LATE_NIGHT ... NIGHT), "sky" (clear, rain, storm). */
    private void readFakes(Intent i) {
        try {
            String at = i.getStringExtra("at");
            if (at != null) {
                screen.fakeAt = java.util.Calendar.getInstance();
                screen.fakeAt.setTime(new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.ENGLISH).parse(at));
            }
        } catch (Exception ignored) {
            screen.fakeAt = null;
        }
        try {
            String m = i.getStringExtra("mood");
            screen.fakeMood = m == null ? null : SunLine.Phase.valueOf(m.toUpperCase(java.util.Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            screen.fakeMood = null;
        }
        String sky = i.getStringExtra("sky");
        screen.fakeSky = sky == null ? null : "rain".equalsIgnoreCase(sky) ? Feed.Sky.RAIN : "storm".equalsIgnoreCase(sky) ? Feed.Sky.STORM : Feed.Sky.CLEAR;
    }

    @Override
    protected void onResume() {
        super.onResume();
        screen.start();
        stillHere();
    }

    @Override
    protected void onPause() {
        super.onPause();
        screen.stop();
        handler.removeCallbacks(idle);
    }

    private void stillHere() {
        handler.removeCallbacks(idle);
        handler.postDelayed(idle, idleMs);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        stillHere();
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    screen.switchMode();
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    screen.step(-1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    screen.step(1);
                    return true;
                default:
                    break;
            }
        }
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) return true;
        return super.dispatchKeyEvent(e);   // Back closes (the default); volume and the TV's own keys as usual
    }
}
