package com.gathin.tvhome;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AppMatchTest {
    private static Map<String, String> apps(String... pkgAndLabel) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pkgAndLabel.length; i += 2) m.put(pkgAndLabel[i], pkgAndLabel[i + 1]);
        return m;
    }

    @Test public void normalizeStripsAccentsCaseAndPunctuation() {
        assertEquals("peliculas", AppMatch.normalize("Películas"));
        assertEquals("disneyplus", AppMatch.normalize("Disney+"));
        assertEquals("disneyplus", AppMatch.normalize("Disney Plus"));
        assertEquals("hbomax", AppMatch.normalize("HBO  Max!"));
        assertEquals("", AppMatch.normalize(" -- "));
    }

    @Test public void scoreLevels() {
        assertEquals(1.0, AppMatch.score("netflix", "Netflix"), 0);
        assertEquals(0.9, AppMatch.score("you", "YouTube"), 0);
        assertEquals(0.8, AppMatch.score("tube", "YouTube"), 0);
        assertEquals(1.0 - 1.0 / 7, AppMatch.score("netflx", "Netflix"), 1e-9);
        assertEquals(0.0, AppMatch.score("", "Netflix"), 0);
        // Two-letter queries never count as prefix or contains.
        assertTrue(AppMatch.score("tv", "TV Guide") < AppMatch.THRESHOLD);
        assertEquals(3, AppMatch.levenshtein("kitten", "sitting"));
    }

    @Test public void fuzzyMatchesInstalledLabelsFirst() {
        Map<String, String> installed = apps("com.netflix.ninja", "Netflix", "com.x.yt", "YouTube", "com.x.ytm", "YouTube Music");
        assertEquals("com.netflix.ninja", AppMatch.resolve(installed, "netflix"));
        assertEquals("com.netflix.ninja", AppMatch.resolve(installed, "Netflix"));
        assertEquals("com.netflix.ninja", AppMatch.resolve(installed, "netflx"));
        assertEquals("com.x.yt", AppMatch.resolve(installed, "youtube"));
        assertEquals("com.x.ytm", AppMatch.resolve(installed, "youtube music"));
        // Tie on score: the shorter label wins.
        assertEquals("com.x.yt", AppMatch.resolve(installed, "you"));
    }

    @Test public void fuzzyBeatsAliasWhenALabelMatches() {
        // A custom label that is not the alias package still wins over the table.
        Map<String, String> installed = apps("com.custom.nf", "Netflix", "com.netflix.ninja", "Streaming");
        assertEquals("com.custom.nf", AppMatch.resolve(installed, "netflix"));
    }

    @Test public void aliasFillsInWhenNoLabelMatchesAndPackageIsInstalled() {
        Map<String, String> installed = apps("com.wbd.stream", "Streaming Hub", "com.mediatek.tv.oneworld.tvcenter", "Fuente");
        assertEquals("com.wbd.stream", AppMatch.resolve(installed, "HBO Max"));
        assertEquals("com.wbd.stream", AppMatch.resolve(installed, "max"));
        assertEquals("com.mediatek.tv.oneworld.tvcenter", AppMatch.resolve(installed, "television"));
        assertEquals("com.mediatek.tv.oneworld.tvcenter", AppMatch.resolve(installed, "live tv"));
        assertEquals("com.mediatek.tv.oneworld.tvcenter", AppMatch.resolve(installed, "tv"));
    }

    @Test public void aliasForAnUninstalledPackageAndUnknownNamesGiveNoMatch() {
        Map<String, String> installed = apps("com.netflix.ninja", "Netflix");
        assertNull(AppMatch.resolve(installed, "spotify"));
        assertNull(AppMatch.resolve(installed, "zzzzzz"));
        assertNull(AppMatch.resolve(installed, ""));
        assertNull(AppMatch.resolve(apps(), "netflix"));
    }

    @Test public void aliasTableHasTheDocumentedEntries() {
        assertEquals("com.disney.disneyplus", AppMatch.aliasPackage("Disney+"));
        assertEquals("com.disney.disneyplus", AppMatch.aliasPackage("star plus"));
        assertEquals("com.amazon.amazonvideo.livingroom", AppMatch.aliasPackage("Prime Video"));
        assertEquals("com.apple.atve.androidtv.appletv", AppMatch.aliasPackage("apple tv"));
        assertEquals("com.rcntv.rcntv", AppMatch.aliasPackage("RCN"));
        assertEquals("com.android.tv.settings", AppMatch.aliasPackage("configuración"));
        assertEquals("com.android.tv.settings", AppMatch.aliasPackage("Ajustes"));
        assertEquals("com.android.vending", AppMatch.aliasPackage("play store"));
        assertEquals("com.spotify.tv.android", AppMatch.aliasPackage("spotify"));
        assertEquals("com.google.android.youtube.tvmusic", AppMatch.aliasPackage("YouTube Music"));
        assertNull(AppMatch.aliasPackage("nothing"));
    }
}
