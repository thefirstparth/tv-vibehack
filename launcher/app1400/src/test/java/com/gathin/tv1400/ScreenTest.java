package com.gathin.tv1400;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.List;

/** The 1400 app's Money figures, as the TV prints them, and how the keys move along its tabs and pages. */
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

    private static final List<String> TABS = List.of("Sport", "Minute", "Minute", "Minute", "Money", "This week", "Results");

    @Test
    public void leftAndRightGoToTheFirstPageOfTheNextSectionAndStopAtTheEnds() {
        assertEquals(1, Screen.sideOf(TABS, 0, 1));
        assertEquals(4, Screen.sideOf(TABS, 2, 1));
        assertEquals(0, Screen.sideOf(TABS, 3, -1));
        assertEquals(1, Screen.sideOf(TABS, 4, -1));
        assertEquals(0, Screen.sideOf(TABS, 0, -1));
        assertEquals(6, Screen.sideOf(TABS, 6, 1));
    }

    @Test
    public void upAndDownStayInsideTheSection() {
        assertEquals(2, Screen.pageOf(TABS, 1, 1));
        assertEquals(3, Screen.pageOf(TABS, 3, 1));
        assertEquals(1, Screen.pageOf(TABS, 1, -1));
        assertEquals(0, Screen.pageOf(TABS, 0, 1));
        assertEquals(4, Screen.pageOf(TABS, 4, -1));
    }

    @Test
    public void onlyASectionWithPagesSaysWhichPage() {
        assertEquals("1 / 3", Screen.pageMark(TABS, 1));
        assertEquals("3 / 3", Screen.pageMark(TABS, 3));
        assertNull(Screen.pageMark(TABS, 0));
        assertNull(Screen.pageMark(TABS, 6));
    }
}
