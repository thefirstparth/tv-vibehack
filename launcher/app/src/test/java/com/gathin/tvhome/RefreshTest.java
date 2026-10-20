package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The home screen asks the feed again: a minute after a saved copy, every 15 minutes otherwise. */
public class RefreshTest {

    private static final long T = 1_800_000_000_000L, MIN = 60_000L;

    @Test
    public void aSavedCopyIsAskedForAgainOnTheNextTick() {
        assertFalse(Refresh.due(T + 30_000L, T, true));
        assertTrue(Refresh.due(T + 45_000L, T, true));
        assertTrue(Refresh.due(T + MIN, T, true));
    }

    @Test
    public void aGoodCopyIsAskedForAgainAfterFifteenMinutes() {
        assertFalse(Refresh.due(T + MIN, T, false));
        assertFalse(Refresh.due(T + 14 * MIN, T, false));
        assertTrue(Refresh.due(T + 15 * MIN, T, false));
    }

    @Test
    public void neverAskedIsLeftToTheHomeAppearance() {
        assertFalse(Refresh.due(T, 0, true));
        assertFalse(Refresh.due(T, 0, false));
    }

    @Test
    public void aLiveMatchIsAskedForEveryTwoMinutes() {
        assertFalse(Refresh.due(T + MIN, T, false, true));
        assertTrue(Refresh.due(T + 2 * MIN, T, false, true));
        assertFalse(Refresh.due(T + 2 * MIN, T, false, false));
    }

    @Test
    public void anOldCopyIsAskedForAgainSoonTwiceAtMost() {
        assertTrue(Refresh.quickAgain(true, 0));
        assertTrue(Refresh.quickAgain(true, 1));
        assertFalse(Refresh.quickAgain(true, 2));
        assertFalse(Refresh.quickAgain(false, 0));
        assertTrue(Refresh.QUICK_MS == 8_000L);
    }
}
