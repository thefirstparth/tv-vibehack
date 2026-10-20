package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** The side Parth follows stands out: the other side's name (and score) is found in each way a line is worded. */
public class OpponentTest {

    private static String part(String line, int[] r) {
        return line.substring(r[0], r[1]);
    }

    @Test
    public void plainLineDimsTheOtherName() {
        String line = "Real Madrid v RB Leipzig";
        int[][] r = Opponent.inLine("football", "Real Madrid", "RB Leipzig", "", "", "a");
        assertEquals(1, r.length);
        assertEquals("RB Leipzig", part(line, r[0]));
        r = Opponent.inLine("nba", "Lakers", "Warriors", "", "", "b");
        assertEquals("Lakers", part("Lakers v Warriors", r[0]));
    }

    @Test
    public void footballScoreDimsTheOtherNameAndItsNumber() {
        String line = "Real Madrid 2–1 Sevilla";
        int[][] r = Opponent.inLine("football", "Real Madrid", "Sevilla", "2", "1", "a");
        assertEquals("1", part(line, r[0]));
        assertEquals("Sevilla", part(line, r[1]));
        line = "Getafe 0–3 Real Madrid";
        r = Opponent.inLine("football", "Getafe", "Real Madrid", "0", "3", "b");
        assertEquals("Getafe", part(line, r[0]));
        assertEquals("0", part(line, r[1]));
    }

    @Test
    public void cricketDimsTheOtherSideWithItsScore() {
        String line = "India 245/3 v Australia 120/4";
        int[][] r = Opponent.inLine("cricket", "India", "Australia", "245/3", "120/4", "a");
        assertEquals("Australia 120/4", part(line, r[0]));
        line = "Australia 120/4 v India 245/3";
        r = Opponent.inLine("cricket", "Australia", "India", "120/4", "245/3", "b");
        assertEquals("Australia 120/4", part(line, r[0]));
        r = Opponent.inLine("cricket", "India", "New Zealand", "", "", "a");
        assertEquals("New Zealand", part("India v New Zealand", r[0]));
    }

    @Test
    public void nothingWithoutOurs() {
        assertEquals(0, Opponent.inLine("football", "Spain", "Portugal", "", "", "").length);
        assertEquals(0, Opponent.inLine("tennis", "Alcaraz", "Djokovic", "", "", null).length);
        assertEquals(0, Opponent.inLine("football", "Real Madrid", "", "", "", "a").length);
        assertNull(Opponent.inScore("2", "1", ""));
    }

    @Test
    public void scoreOnItsOwn() {
        assertArrayEquals(new int[]{2, 3}, Opponent.inScore("2", "1", "a"));   // "2–1": the 1
        assertArrayEquals(new int[]{0, 1}, Opponent.inScore("2", "1", "b"));   // the 2
        assertArrayEquals(new int[]{3, 5}, Opponent.inScore("10", "11", "a"));
    }
}
