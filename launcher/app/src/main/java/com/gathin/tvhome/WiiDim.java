package com.gathin.tvhome;

/**
 * The Wii's Screen Burn-in Reduction, kept free of Android so it runs as plain JVM tests: after a while with no
 * input the console just dims the screen it is showing under a gray veil, which breathes very slightly. The veil's
 * strength over time, and the few-pixel shift of the whole menu every few minutes, are worked out here.
 */
final class WiiDim {
    private WiiDim() { }

    /** Veil strength by day (soft gray) and at night (charcoal), and their colours. */
    static final float DAY_ALPHA = 0.50f, NIGHT_ALPHA = 0.60f;
    static final int DAY_COLOR = 0xFF7E8085, NIGHT_COLOR = 0xFF0E0F11;
    /** The fade in, and one full breath of the pulse: +-PULSE around the veil's strength. */
    static final long FADE_MS = 2_000L, PULSE_MS = 8_000L;
    static final float PULSE = 0.04f;
    /** Steps of the pulse while fading in (smooth), and after (one veil level every few steps is plenty). */
    static final long FADE_STEP_MS = 33L, PULSE_STEP_MS = 250L;
    /** The menu moves to the next pixel-shift spot every SHIFT_EVERY_MIN minutes, at most SHIFT_PX (at 1080p) away. */
    static final int SHIFT_EVERY_MIN = 3, SHIFT_PX = 4;

    /** Unit offsets of the shift spots, walked in order: the centre, then a ring around it. */
    private static final int[][] SPOTS = {{0, 0}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};

    static float base(boolean night) {
        return night ? NIGHT_ALPHA : DAY_ALPHA;
    }

    static int color(boolean night) {
        return night ? NIGHT_COLOR : DAY_COLOR;
    }

    /** The veil's alpha this long after dimming started: an eased fade in, then the slow breath around the base. */
    static float alpha(boolean night, long elapsedMs) {
        float ramp = elapsedMs >= FADE_MS ? 1f : Math.max(0f, elapsedMs) / (float) FADE_MS;
        ramp = ramp * ramp * (3 - 2 * ramp);
        double breath = Math.sin(2 * Math.PI * (elapsedMs % PULSE_MS) / PULSE_MS);
        return ramp * (base(night) + PULSE * (float) breath);
    }

    /** The veil's alpha as the 0..255 level the screen actually shows; a step only redraws when this changes. */
    static int level(float alpha) {
        return Math.max(0, Math.min(255, Math.round(alpha * 255)));
    }

    static long stepMs(long elapsedMs) {
        return elapsedMs < FADE_MS ? FADE_STEP_MS : PULSE_STEP_MS;
    }

    /** The menu's offset {dx, dy} in pixels this many minutes after dimming started; maxPx is the ring's radius. */
    static int[] shift(long minutes, int maxPx) {
        int[] s = SPOTS[(int) (Math.max(0, minutes) / SHIFT_EVERY_MIN % SPOTS.length)];
        return new int[] {s[0] * maxPx, s[1] * maxPx};
    }
}
