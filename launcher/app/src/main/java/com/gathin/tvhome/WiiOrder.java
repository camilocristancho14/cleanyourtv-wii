package com.gathin.tvhome;

import java.util.Arrays;
import java.util.List;

/** Tile order of the Wii menu: video first, then music, then everything else; each group by a built-in list. */
final class WiiOrder {
    private WiiOrder() { }

    static final int CATEGORY_AUDIO = 1, CATEGORY_VIDEO = 2;   // ApplicationInfo.CATEGORY_*

    private static final List<String> VIDEO = Arrays.asList(
            "com.netflix.ninja", "com.disney.disneyplus", "com.wbd.stream", "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom", "com.apple.atve.androidtv.appletv", "com.rcntv.rcntv");
    private static final String MEDIA_PLAYER = "com.mediatek.wwtv.mediaplayer";
    private static final List<String> MUSIC = Arrays.asList(
            "com.spotify.tv.android", "com.google.android.youtube.tvmusic", "com.brkchen.music");
    private static final List<String> OTHER = Arrays.asList(
            "com.android.vending", "com.esaba.downloader", "com.alphainventor.filemanager", "com.tailscale.ipn");

    private static final int UNKNOWN_VIDEO = 100, PLAYER = 101, MUSIC_BASE = 150, UNKNOWN_MUSIC = 200,
            OTHER_BASE = 300, UNKNOWN_OTHER = 400;

    /** Sort rank of an app: lower comes first. Apps of the same rank are ordered by name. */
    static int rank(String pkg, int category) {
        int i = VIDEO.indexOf(pkg);
        if (i >= 0) return i;
        if (pkg.equals(MEDIA_PLAYER)) return PLAYER;
        i = MUSIC.indexOf(pkg);
        if (i >= 0) return MUSIC_BASE + i;
        i = OTHER.indexOf(pkg);
        if (i >= 0) return OTHER_BASE + i;
        if (category == CATEGORY_VIDEO) return UNKNOWN_VIDEO;
        if (category == CATEGORY_AUDIO) return UNKNOWN_MUSIC;
        return UNKNOWN_OTHER;
    }
}
