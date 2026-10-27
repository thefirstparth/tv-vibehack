package com.gathin.tvkeys;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

/** Which buttons can be given a job, and which jobs are kept. */
public class JobsTest {

    @Test
    public void navigationVolumeHomeAndPowerAlwaysKeepTheirJobs() {
        int[] kept = {KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME,
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER,
                KeyEvent.KEYCODE_TV_POWER};
        for (int code : kept) assertFalse(String.valueOf(code), Jobs.learnable(code));
    }

    @Test
    public void appAndAssistantButtonsCanBeGivenAJob() {
        assertTrue(Jobs.learnable(KeyEvent.KEYCODE_ASSIST));
        assertTrue(Jobs.learnable(KeyEvent.KEYCODE_SEARCH));
        assertTrue(Jobs.learnable(KeyEvent.KEYCODE_TV_INPUT));
        assertTrue(Jobs.learnable(1102));   // a vendor button, such as an app's own
    }

    @Test
    public void onlyWellFormedJobsAreKept() {
        assertTrue(Jobs.valid("app:com.fancode.tv"));
        assertTrue(Jobs.valid("input:com.mediatek.tvinput/.hdmi.HDMIInputService/HW5"));
        assertTrue(Jobs.valid("none"));
        assertFalse(Jobs.valid("app:"));
        assertFalse(Jobs.valid("input:"));
        assertFalse(Jobs.valid("fancode"));
        assertFalse(Jobs.valid(null));
    }
}
