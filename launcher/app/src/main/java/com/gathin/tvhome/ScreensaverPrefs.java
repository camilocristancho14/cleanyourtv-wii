package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/** The owner's screensaver choice (ScreensaverFiles) and the on/off switch. Default is on, so an older install keeps playing. */
final class ScreensaverPrefs {
    private ScreensaverPrefs() { }

    static final String PREFS = "screensaver";
    private static final String ENABLED = "enabled";
    /** Absent or empty: ScreensaverFiles' automatic chain. {@link ScreensaverFiles#BLACK} or a clip's file name otherwise. */
    private static final String CHOICE = "choice";

    static boolean isEnabled(Context ctx) {
        return prefs(ctx).getBoolean(ENABLED, true);
    }

    /** Null when the owner has not picked a clip (automatic). */
    static String choice(Context ctx) {
        return prefs(ctx).getString(CHOICE, null);
    }

    static void setEnabled(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(ENABLED, on).apply();
    }

    static void setChoice(Context ctx, String choice) {
        prefs(ctx).edit().putString(CHOICE, choice).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
