package com.gathin.tvhome;

/**
 * What the Wi-Fi pill shows for the connection, from the default network's state (a ConnectivityManager callback, no
 * polling). Pure, so it is unit-tested.
 * - Wi-Fi with internet: the bars only; the network's name beside them while the pill is focused.
 * - Ethernet with internet: the word "Ethernet" at all times, as before (there are no bars to show).
 * - No network, or one without internet (not validated): the bars with a small amber "!" and "No internet", focused
 *   or not, until the connection is back.
 */
final class WifiPill {

    enum State { WIFI, ETHERNET, NO_INTERNET }

    private WifiPill() {
    }

    static State of(boolean connected, boolean wifi, boolean ethernet, boolean validated) {
        if (!connected || !validated) return State.NO_INTERNET;
        if (ethernet && !wifi) return State.ETHERNET;
        return State.WIFI;
    }

    /** Only the icon (a square pill): Wi-Fi with internet, while the pill is not focused. */
    static boolean iconOnly(State s, boolean focused) {
        return s == State.WIFI && !focused;
    }

    /** The amber "!" on the icon. */
    static boolean alert(State s) {
        return s == State.NO_INTERNET;
    }

    /** The words beside the icon ("" when icon only): the network's name, "Ethernet" or "No internet". */
    static String label(State s, boolean focused, String networkName, String ethernet, String noInternet) {
        switch (s) {
            case NO_INTERNET:
                return noInternet;
            case ETHERNET:
                return ethernet;
            default:
                return focused ? networkName : "";
        }
    }
}
