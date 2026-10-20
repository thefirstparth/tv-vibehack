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

    private Refresh() {
    }

    /** now and asked in milliseconds (asked 0: never asked, which the Home appearance does itself). */
    static boolean due(long now, long asked, boolean saved) {
        if (asked <= 0) return false;
        return now - asked >= (saved ? AFTER_SAVED : AFTER_GOOD);
    }
}
