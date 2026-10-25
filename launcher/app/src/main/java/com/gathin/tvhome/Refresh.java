package com.gathin.tvhome;

/**
 * When the home screen asks the feed again while it stays on screen, checked on the existing minute tick (no timer of
 * its own). Besides the one request each time Home appears: a minute after a saved copy (the site's cache answered
 * with an old copy while it makes a fresh one, or the request failed), and every 15 minutes otherwise. Pure, so it is
 * unit-tested.
 */
final class Refresh {

    /** After a saved copy: by the next minute tick (the tick may come a little early, so 45 s). */
    static final long AFTER_SAVED = 45_000L;
    /** A good copy is asked for again once it is this old. */
    static final long AFTER_GOOD = 15 * 60_000L;
    /** While a match is live, every two minutes (its score moves). */
    static final long AFTER_LIVE = 2 * 60_000L;
    /**
     * After an old or failed copy, the next request comes this soon (the site's cache has its fresh copy 2 to 6 s after
     * a request, 20 Oct), at most QUICK_TRIES times in a row, before the minute tick takes over.
     */
    static final long QUICK_MS = 8_000L;
    static final int QUICK_TRIES = 2;

    private Refresh() {
    }

    /** now and asked in milliseconds (asked 0: never asked, which the Home appearance does itself). */
    static boolean due(long now, long asked, boolean saved) {
        return due(now, asked, saved, false);
    }

    /** As above; live: a match is live now, so a good copy is asked for again after two minutes. */
    static boolean due(long now, long asked, boolean saved, boolean live) {
        if (asked <= 0) return false;
        return now - asked >= (saved ? AFTER_SAVED : live ? AFTER_LIVE : AFTER_GOOD);
    }

    /** The network has just come back (or changed): ask now if what's shown is an old or failed copy, unless a request
     *  went out in the last NETWORK_GAP (the callback also fires once when Home starts, right after its own request).
     *  After standby the TV's Wi-Fi often reconnects after Home's request and its quick tries (26 Oct: 30-50% of real
     *  requests got an old copy), which left the lines stale until the next minute tick. */
    static boolean onNetworkBack(long now, long asked, boolean saved) {
        return saved && now - asked >= NETWORK_GAP;
    }

    static final long NETWORK_GAP = 3_000L;

    /** Ask again in QUICK_MS: the answer was an old or failed copy, and fewer than QUICK_TRIES quick tries were made. */
    static boolean quickAgain(boolean saved, int triesSoFar) {
        return saved && triesSoFar < QUICK_TRIES;
    }
}
