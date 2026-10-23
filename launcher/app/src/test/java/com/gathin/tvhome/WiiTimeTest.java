package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for the Wii menu's dark hours (16:00 to 07:00), the debug override and the date line. */
public class WiiTimeTest {

    @Test
    public void darkFromFourInTheAfternoonToSevenInTheMorning() {
        assertTrue(WiiTime.isNight(16, 0));
        assertTrue(WiiTime.isNight(23, 59));
        assertTrue(WiiTime.isNight(0, 0));
        assertTrue(WiiTime.isNight(6, 59));
        assertFalse(WiiTime.isNight(7, 0));
        assertFalse(WiiTime.isNight(12, 0));
        assertFalse(WiiTime.isNight(15, 59));
    }

    @Test
    public void theDebugPropertyForcesEitherLook() {
        assertTrue(WiiTime.isNight("1", 12, 0));
        assertFalse(WiiTime.isNight("0", 22, 0));
        assertTrue(WiiTime.isNight("", 16, 0));
        assertFalse(WiiTime.isNight("", 7, 0));
        assertFalse(WiiTime.isNight(null, 15, 59));
        assertTrue(WiiTime.isNight("yes", 6, 59));
    }

    @Test
    public void thePropertyReadsEmptyOffAndroid() {
        assertEquals("", WiiTime.override());
    }

    @Test
    public void spanishDateLine() {
        assertEquals("Mié 21/10", WiiTime.date(4, 21, 10));
        assertEquals("Dom 1/1", WiiTime.date(1, 1, 1));
        assertEquals("Sáb 31/12", WiiTime.date(7, 31, 12));
    }
}
