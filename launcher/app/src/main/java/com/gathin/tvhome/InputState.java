package com.gathin.tvhome;

/** The Xbox tile's on or off, apart from InputTile (which draws it) so it can be unit-tested. */
final class InputState {

    private InputState() {
    }

    /**
     * Whether the tile shows on, from the port's state and the state of the HDMI-CEC device behind it (null when the TV
     * lists none). The port alone says "connected" while the Xbox sleeps (Sleep mode keeps HDMI up), so a device that
     * says standby turns the tile off. Only an explicit standby does: a device input that reports nothing useful
     * (disconnected while the port is up) leaves the port's answer, so the tile is never greyer than before.
     */
    static boolean on(int port, Integer device) {
        if (port != android.media.tv.TvInputManager.INPUT_STATE_CONNECTED) return false;
        return device == null || device != android.media.tv.TvInputManager.INPUT_STATE_CONNECTED_STANDBY;
    }
}
