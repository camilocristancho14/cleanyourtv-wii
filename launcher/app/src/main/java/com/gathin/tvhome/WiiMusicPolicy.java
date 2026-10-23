package com.gathin.tvhome;

/**
 * When the Wii Home music plays, and how loud, kept free of Android so it runs as plain JVM tests. It plays only
 * while Home is resumed and has window focus, the screen is on and no screensaver runs, and it is switched on.
 */
final class WiiMusicPolicy {
    private WiiMusicPolicy() { }

    /** `adb shell setprop debug.wiitv.music 0` silences it, `1` lets it play even when the setting is off. */
    static final String MUSIC_PROPERTY = "debug.wiitv.music";
    /** Full volume (MediaPlayer scale, 0..1): soft, behind the menu sounds. */
    static final float VOLUME = 0.25f;
    static final long FADE_IN_MS = 1000, FADE_OUT_MS = 600;

    /** The setting (wii_music), unless the debug property says "0" or "1". */
    static boolean enabled(boolean setting, String override) {
        if ("0".equals(override)) return false;
        if ("1".equals(override)) return true;
        return setting;
    }

    static boolean shouldPlay(boolean enabled, boolean resumed, boolean focused, boolean screenOn, boolean dreaming) {
        return enabled && resumed && focused && screenOn && !dreaming;
    }

    /**
     * The volume one step of `stepMs` later on the way to `target`: a straight ramp that takes FADE_IN_MS from
     * silence to VOLUME going up, FADE_OUT_MS from VOLUME to silence going down. Never overshoots.
     */
    static float next(float volume, float target, long stepMs) {
        float d = VOLUME * stepMs / (volume < target ? FADE_IN_MS : FADE_OUT_MS);
        // Within a step (plus float dust) of the target: land on it exactly, so the fade ends.
        if (Math.abs(target - volume) <= d + 1e-5f) return target;
        return volume < target ? volume + d : volume - d;
    }
}
