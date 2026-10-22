package com.gathin.tvhome;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for the Screen Burn-in Reduction veil (fade in, breath) and the pixel shift of the dimmed menu. */
public class WiiDimTest {

    @Test
    public void fadesInFromNothingOverTwoSeconds() {
        assertEquals(0f, WiiDim.alpha(false, 0), 1e-6);
        assertEquals(0f, WiiDim.alpha(true, 0), 1e-6);
        float half = WiiDim.alpha(false, WiiDim.FADE_MS / 2);
        assertTrue(half > 0.15f && half < WiiDim.DAY_ALPHA);
        for (long t = 0; t < WiiDim.FADE_MS; t += 100) {
            assertTrue(WiiDim.alpha(true, t + 100) >= WiiDim.alpha(true, t) - 1e-6);
        }
    }

    @Test
    public void thenBreathesFourPercentAroundTheBase() {
        for (boolean night : new boolean[] {false, true}) {
            float lo = 1f, hi = 0f;
            for (long t = WiiDim.FADE_MS; t < WiiDim.FADE_MS + 3 * WiiDim.PULSE_MS; t += 50) {
                float a = WiiDim.alpha(night, t);
                lo = Math.min(lo, a);
                hi = Math.max(hi, a);
            }
            assertEquals(WiiDim.base(night) - WiiDim.PULSE, lo, 0.002);
            assertEquals(WiiDim.base(night) + WiiDim.PULSE, hi, 0.002);
        }
    }

    @Test
    public void softGrayByDayCharcoalAtNight() {
        assertTrue(WiiDim.DAY_ALPHA - WiiDim.PULSE >= 0.45f - 1e-6 && WiiDim.DAY_ALPHA + WiiDim.PULSE <= 0.55f + 1e-6);
        assertTrue(WiiDim.NIGHT_ALPHA - WiiDim.PULSE >= 0.55f - 1e-6 && WiiDim.NIGHT_ALPHA + WiiDim.PULSE <= 0.65f + 1e-6);
    }

    @Test
    public void oneBreathEveryEightSeconds() {
        long t = WiiDim.FADE_MS + 1234;
        assertEquals(WiiDim.alpha(false, t), WiiDim.alpha(false, t + WiiDim.PULSE_MS), 1e-4);
    }

    @Test
    public void stepsFastWhileFadingThenSlow() {
        assertEquals(WiiDim.FADE_STEP_MS, WiiDim.stepMs(0));
        assertEquals(WiiDim.PULSE_STEP_MS, WiiDim.stepMs(WiiDim.FADE_MS));
        assertEquals(0, WiiDim.level(-0.1f));
        assertEquals(128, WiiDim.level(0.5f));
        assertEquals(255, WiiDim.level(1.2f));
    }

    @Test
    public void shiftStaysPutForThreeMinutesThenMoves() {
        assertArrayEquals(new int[] {0, 0}, WiiDim.shift(0, 4));
        assertArrayEquals(new int[] {0, 0}, WiiDim.shift(2, 4));
        assertArrayEquals(new int[] {4, 0}, WiiDim.shift(3, 4));
        assertArrayEquals(new int[] {4, 4}, WiiDim.shift(6, 4));
        assertArrayEquals(new int[] {0, 0}, WiiDim.shift(-5, 4));
    }

    @Test
    public void shiftVisitsNineSpotsWithinTheRadiusAndRepeats() {
        Set<String> spots = new HashSet<>();
        long cycle = 9L * WiiDim.SHIFT_EVERY_MIN;
        for (long m = 0; m < cycle; m++) {
            int[] s = WiiDim.shift(m, 4);
            assertTrue(Math.abs(s[0]) <= 4 && Math.abs(s[1]) <= 4);
            spots.add(s[0] + "," + s[1]);
            assertArrayEquals(s, WiiDim.shift(m + cycle, 4));
        }
        assertEquals(9, spots.size());
    }
}
