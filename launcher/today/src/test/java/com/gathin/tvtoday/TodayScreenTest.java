package com.gathin.tvtoday;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The Today app's Money figures, as the TV prints them. */
public class TodayScreenTest {

    @Test
    public void rupeesInIndianGrouping() {
        assertEquals("1,46,430", TodayScreen.indian(146430));
        assertEquals("12,34,56,789", TodayScreen.indian(123456789));
        assertEquals("999", TodayScreen.indian(999));
        assertEquals("₹1,46,430", TodayScreen.price(146430, "gold"));
        assertEquals("₹94.96", TodayScreen.price(94.96, "usdinr"));
        assertEquals("74,729", TodayScreen.price(74729.27, "index"));
    }

    @Test
    public void movesSignedWithTheirUnits() {
        assertEquals("+414", TodayScreen.signed(413.66, "index"));
        assertEquals("−8,738", TodayScreen.signed(-8738.39, "index"));
        assertEquals("−5,932", TodayScreen.signed(-5932, "gold"));
        assertEquals("+1,05,000", TodayScreen.signed(105000, "gold"));
        assertEquals("+0.44", TodayScreen.signed(0.44, "usdinr"));
        assertEquals("0", TodayScreen.signed(0, "index"));
        assertEquals("+0.56%", TodayScreen.signedPct(0.56));
        assertEquals("−10.47%", TodayScreen.signedPct(-10.47));
    }
}
