package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for the Wii menu's video, music, rest grouping. */
public class WiiOrderTest {

    private static final int UNDEFINED = -1;

    private static List<String> sorted(String... pkgs) {
        List<String> list = new ArrayList<>(Arrays.asList(pkgs));
        Collections.sort(list, (a, b) -> Integer.compare(WiiOrder.rank(a, UNDEFINED), WiiOrder.rank(b, UNDEFINED)));
        return list;
    }

    @Test
    public void videoThenMusicThenTheRest() {
        List<String> tv = sorted("com.tailscale.ipn", "com.android.vending", "com.brkchen.music", "com.mediatek.wwtv.mediaplayer",
                "com.spotify.tv.android", "com.rcntv.rcntv", "com.apple.atve.androidtv.appletv",
                "com.amazon.amazonvideo.livingroom", "com.google.android.youtube.tv", "com.wbd.stream",
                "com.disney.disneyplus", "com.netflix.ninja", "com.alphainventor.filemanager", "com.esaba.downloader",
                "com.google.android.youtube.tvmusic");
        assertEquals(Arrays.asList("com.netflix.ninja", "com.disney.disneyplus", "com.wbd.stream",
                "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom",
                "com.apple.atve.androidtv.appletv", "com.rcntv.rcntv", "com.mediatek.wwtv.mediaplayer",
                "com.spotify.tv.android", "com.google.android.youtube.tvmusic", "com.brkchen.music",
                "com.android.vending", "com.esaba.downloader", "com.alphainventor.filemanager", "com.tailscale.ipn"), tv);
    }

    @Test
    public void unknownAppsFollowTheirCategory() {
        int video = WiiOrder.rank("com.iptv.player", WiiOrder.CATEGORY_VIDEO);
        int audio = WiiOrder.rank("com.some.radio", WiiOrder.CATEGORY_AUDIO);
        int other = WiiOrder.rank("com.some.tool", UNDEFINED);
        assertTrue(WiiOrder.rank("com.rcntv.rcntv", UNDEFINED) < video);
        assertTrue(video < WiiOrder.rank("com.mediatek.wwtv.mediaplayer", UNDEFINED));
        assertTrue(WiiOrder.rank("com.mediatek.wwtv.mediaplayer", UNDEFINED) < WiiOrder.rank("com.spotify.tv.android", UNDEFINED));
        assertTrue(WiiOrder.rank("com.brkchen.music", UNDEFINED) < audio);
        assertTrue(audio < WiiOrder.rank("com.android.vending", UNDEFINED));
        assertTrue(WiiOrder.rank("com.tailscale.ipn", UNDEFINED) < other);
    }

    @Test
    public void aKnownAppKeepsItsPlaceWhateverItsCategory() {
        assertEquals(WiiOrder.rank("com.netflix.ninja", UNDEFINED), WiiOrder.rank("com.netflix.ninja", WiiOrder.CATEGORY_AUDIO));
    }

    @Test
    public void categoryConstantsMatchAndroid() {
        assertEquals(android.content.pm.ApplicationInfo.CATEGORY_AUDIO, WiiOrder.CATEGORY_AUDIO);
        assertEquals(android.content.pm.ApplicationInfo.CATEGORY_VIDEO, WiiOrder.CATEGORY_VIDEO);
    }
}
