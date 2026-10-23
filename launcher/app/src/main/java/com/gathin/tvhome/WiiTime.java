package com.gathin.tvhome;

/**
 * Time rules of the Wii menu, kept free of Android so they run as plain JVM tests: the dark look from 16:00 to
 * 07:00 local time, its debug override, and the Spanish date line ("Mié 21/10").
 */
final class WiiTime {
    private WiiTime() { }

    /** Minutes after midnight when the dark look starts, and when the light one comes back. */
    static final int DARK_FROM = 16 * 60, LIGHT_FROM = 7 * 60;
    /** `adb shell setprop debug.wiitv.night 1` forces dark, `0` forces light, empty means by the clock. */
    static final String NIGHT_PROPERTY = "debug.wiitv.night";
    private static final String[] DAYS = {"Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb"};

    static boolean isNight(int hour, int minute) {
        int m = hour * 60 + minute;
        return m >= DARK_FROM || m < LIGHT_FROM;
    }

    /** The override wins when it is "1" or "0"; anything else falls back to the clock. */
    static boolean isNight(String override, int hour, int minute) {
        if ("1".equals(override)) return true;
        if ("0".equals(override)) return false;
        return isNight(hour, minute);
    }

    /** Dark or light for this moment in the device's time zone, honouring the debug property. */
    static boolean isNightNow() {
        java.util.Calendar now = java.util.Calendar.getInstance();
        return isNight(override(), now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE));
    }

    /** debug.wiitv.night through the hidden android.os.SystemProperties; "" when it can't be read (or off Android). */
    static String override() {
        return property(NIGHT_PROPERTY);
    }

    /** A system property through the hidden android.os.SystemProperties; "" when it can't be read (or off Android). */
    static String property(String name) {
        try {
            //noinspection PrivateApi: debug switches only; any failure just means the default.
            Object v = Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class, String.class).invoke(null, name, "");
            return v == null ? "" : v.toString().trim();
        } catch (Throwable t) {
            return "";
        }
    }

    /** "Mié 21/10": dayOfWeek as in Calendar (1 = Sunday), month 1..12. */
    static String date(int dayOfWeek, int day, int month) {
        return DAYS[(dayOfWeek + 6) % 7] + " " + day + "/" + month;
    }
}
