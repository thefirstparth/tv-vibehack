package com.gathin.tvhome;

import static android.media.tv.TvInputManager.INPUT_STATE_CONNECTED;
import static android.media.tv.TvInputManager.INPUT_STATE_CONNECTED_STANDBY;
import static android.media.tv.TvInputManager.INPUT_STATE_DISCONNECTED;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The Xbox tile: green while the Xbox is on, grey while it sleeps, even though Sleep mode keeps the port connected. */
public class InputStateTest {

    @Test
    public void withNoDeviceListedThePortDecidesAsBefore() {
        assertTrue(InputState.on(INPUT_STATE_CONNECTED, null));
        assertFalse(InputState.on(INPUT_STATE_CONNECTED_STANDBY, null));
        assertFalse(InputState.on(INPUT_STATE_DISCONNECTED, null));
    }

    @Test
    public void aDeviceInStandbyTurnsTheTileOffThoughThePortIsConnected() {
        assertTrue(InputState.on(INPUT_STATE_CONNECTED, INPUT_STATE_CONNECTED));
        assertFalse(InputState.on(INPUT_STATE_CONNECTED, INPUT_STATE_CONNECTED_STANDBY));
    }

    @Test
    public void aDeviceThatSaysNothingUsefulLeavesThePortsAnswer() {
        assertTrue(InputState.on(INPUT_STATE_CONNECTED, INPUT_STATE_DISCONNECTED));
        assertFalse(InputState.on(INPUT_STATE_DISCONNECTED, INPUT_STATE_CONNECTED));
    }
}
