package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Left and Right along the tiles: reading order across rows, never off either end. */
public class NavigationTest {

    // 4 a row: the dock 0-3, row 2 4-7, row 3 8-11, then 12-13 on a short last row
    private static final int COUNT = 14;

    @Test
    public void rightAtARowsEndGoesToTheNextRowsFirstTile() {
        assertEquals(4, MainActivity.sideTarget(3, COUNT, true));    // FanCode -> Prime Video
        assertEquals(8, MainActivity.sideTarget(7, COUNT, true));    // Spotify -> 1400
        assertEquals(2, MainActivity.sideTarget(1, COUNT, true));    // within a row as before
    }

    @Test
    public void leftAtARowsStartGoesToTheRowAbovesLastTile() {
        assertEquals(3, MainActivity.sideTarget(4, COUNT, false));
        assertEquals(7, MainActivity.sideTarget(8, COUNT, false));
    }

    @Test
    public void theFirstAndLastTilesStayPut() {
        assertEquals(0, MainActivity.sideTarget(0, COUNT, false));
        assertEquals(13, MainActivity.sideTarget(13, COUNT, true));
    }
}
