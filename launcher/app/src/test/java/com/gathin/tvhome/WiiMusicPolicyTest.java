package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WiiMusicPolicyTest {
    @Test
    public void playsOnlyWhenHomeIsInFrontAndAwake() {
        assertTrue(WiiMusicPolicy.shouldPlay(true, true, true, true, false));
        assertFalse("switched off", WiiMusicPolicy.shouldPlay(false, true, true, true, false));
        assertFalse("an app opened (paused)", WiiMusicPolicy.shouldPlay(true, false, true, true, false));
        assertFalse("another app's dialog has focus", WiiMusicPolicy.shouldPlay(true, true, false, true, false));
        assertFalse("screen off", WiiMusicPolicy.shouldPlay(true, true, true, false, false));
        assertFalse("screensaver", WiiMusicPolicy.shouldPlay(true, true, true, true, true));
    }

    @Test
    public void debugPropertyOverridesTheSetting() {
        assertTrue(WiiMusicPolicy.enabled(true, ""));
        assertFalse(WiiMusicPolicy.enabled(false, ""));
        assertFalse(WiiMusicPolicy.enabled(true, "0"));
        assertTrue(WiiMusicPolicy.enabled(false, "1"));
        assertTrue("anything else means the setting", WiiMusicPolicy.enabled(true, "yes"));
    }

    @Test
    public void fadesInOverOneSecondAndOutOverSixTenths() {
        float v = 0f;
        int steps = 0;
        while (v < WiiMusicPolicy.VOLUME) {
            v = WiiMusicPolicy.next(v, WiiMusicPolicy.VOLUME, 40);
            steps++;
        }
        assertEquals(WiiMusicPolicy.VOLUME, v, 0f);
        assertEquals(25, steps);   // 25 x 40 ms = 1 s
        steps = 0;
        while (v > 0f) {
            v = WiiMusicPolicy.next(v, 0f, 40);
            steps++;
        }
        assertEquals(0f, v, 0f);
        assertEquals(15, steps);   // 15 x 40 ms = 0.6 s
    }

    @Test
    public void fadeNeverOvershootsAndReversesMidway() {
        assertEquals(0.25f, WiiMusicPolicy.next(0.24f, 0.25f, 1000), 0f);
        assertEquals(0f, WiiMusicPolicy.next(0.01f, 0f, 1000), 0f);
        float half = WiiMusicPolicy.next(0.25f, 0f, 300);
        assertEquals(0.125f, half, 1e-6f);
        assertTrue(WiiMusicPolicy.next(half, 0.25f, 40) > half);
        assertEquals(0.25f, WiiMusicPolicy.next(0.25f, 0.25f, 40), 0f);
    }
}
