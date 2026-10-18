package com.gathin.tv1400;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The 1400 app's Money figures, as the TV prints them. */
public class ScreenTest {

    @Test
    public void rupeesInIndianGrouping() {
        assertEquals("1,46,430", Screen.indian(146430));
        assertEquals("12,34,56,789", Screen.indian(123456789));
        assertEquals("999", Screen.indian(999));
        assertEquals("₹1,46,430", Screen.price(146430, "gold"));
        assertEquals("₹94.96", Screen.price(94.96, "usdinr"));
        assertEquals("74,729", Screen.price(74729.27, "index"));
    }

    @Test
    public void movesSignedWithTheirUnits() {
        assertEquals("+414", Screen.signed(413.66, "index"));
        assertEquals("−8,738", Screen.signed(-8738.39, "index"));
        assertEquals("−5,932", Screen.signed(-5932, "gold"));
        assertEquals("+1,05,000", Screen.signed(105000, "gold"));
        assertEquals("+0.44", Screen.signed(0.44, "usdinr"));
        assertEquals("0", Screen.signed(0, "index"));
        assertEquals("+0.56%", Screen.signedPct(0.56));
        assertEquals("−10.47%", Screen.signedPct(-10.47));
    }
}
