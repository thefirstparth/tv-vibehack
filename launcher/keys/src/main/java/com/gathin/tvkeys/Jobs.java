package com.gathin.tvkeys;

import android.view.KeyEvent;

/**
 * A button's job, as kept in the "jobs" preferences (key "k" + key code): "app:&lt;package&gt;" opens that app,
 * "input:&lt;TV input id&gt;" switches to that input (the Xbox's HDMI 2), "none" makes the button do nothing.
 */
final class Jobs {
    static final String APP = "app:", INPUT = "input:", NONE = "none";

    private Jobs() {
    }

    /** Navigation, OK, Back, Home, volume and power always keep their jobs: changing them could lock you out of the TV. */
    static boolean learnable(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_HOME: case KeyEvent.KEYCODE_VOLUME_UP: case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE: case KeyEvent.KEYCODE_POWER: case KeyEvent.KEYCODE_SLEEP:
            case KeyEvent.KEYCODE_WAKEUP: case KeyEvent.KEYCODE_TV_POWER: case KeyEvent.KEYCODE_UNKNOWN:
                return false;
            default:
                return true;
        }
    }

    static boolean valid(String job) {
        if (job == null) return false;
        if (job.equals(NONE)) return true;
        if (job.startsWith(APP)) return job.length() > APP.length() && !job.contains(" ");
        return job.startsWith(INPUT) && job.length() > INPUT.length() && !job.contains(" ");
    }
}
