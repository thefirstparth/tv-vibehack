package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The Wi-Fi pill: icon only when all is well, a problem always said in words with the amber "!". */
public class WifiPillTest {

    private static final String NAME = "Home Wi-Fi", ETH = "Ethernet", NO = "No internet";

    @Test
    public void wifiWithInternetIsIconOnlyAndNamedWhenFocused() {
        WifiPill.State s = WifiPill.of(true, true, false, true);
        assertEquals(WifiPill.State.WIFI, s);
        assertTrue(WifiPill.iconOnly(s, false));
        assertFalse(WifiPill.iconOnly(s, true));
        assertFalse(WifiPill.alert(s));
        assertEquals("", WifiPill.label(s, false, NAME, ETH, NO));
        assertEquals(NAME, WifiPill.label(s, true, NAME, ETH, NO));
    }

    @Test
    public void disconnectedSaysNoInternetWithTheAlertFocusedOrNot() {
        WifiPill.State s = WifiPill.of(false, false, false, false);
        assertEquals(WifiPill.State.NO_INTERNET, s);
        assertTrue(WifiPill.alert(s));
        assertFalse(WifiPill.iconOnly(s, false));
        assertEquals(NO, WifiPill.label(s, false, NAME, ETH, NO));
        assertEquals(NO, WifiPill.label(s, true, NAME, ETH, NO));
    }

    @Test
    public void connectedWithoutInternetIsAProblemToo() {
        assertEquals(WifiPill.State.NO_INTERNET, WifiPill.of(true, true, false, false));
        assertEquals(WifiPill.State.NO_INTERNET, WifiPill.of(true, false, true, false));
    }

    @Test
    public void ethernetKeepsItsWord() {
        WifiPill.State s = WifiPill.of(true, false, true, true);
        assertEquals(WifiPill.State.ETHERNET, s);
        assertFalse(WifiPill.iconOnly(s, false));
        assertFalse(WifiPill.alert(s));
        assertEquals(ETH, WifiPill.label(s, false, NAME, ETH, NO));
    }

    @Test
    public void backOnlineReturnsToIconOnly() {
        WifiPill.State down = WifiPill.of(true, true, false, false), up = WifiPill.of(true, true, false, true);
        assertTrue(WifiPill.alert(down));
        assertTrue(WifiPill.iconOnly(up, false));
        assertFalse(WifiPill.alert(up));
    }
}
