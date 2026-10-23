package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for finding light banners and re-toning them for the Wii menu's dark tiles. */
public class BannerToneTest {
    static final int W = 64, H = 36;

    /** A banner of one colour with a logo block of another in the middle half. */
    static int[] banner(int back, int logo) {
        int[] px = new int[W * H];
        Arrays.fill(px, back);
        for (int y = H / 4; y < H * 3 / 4; y++) {
            for (int x = W / 4; x < W * 3 / 4; x++) px[y * W + x] = logo;
        }
        return px;
    }

    @Test
    public void whitePaperIsLight() {
        assertTrue(BannerTone.isLight(banner(0xFFFFFFFF, 0xFFE50914), W, H));
        assertTrue(BannerTone.isLight(banner(0xFFF2F2F2, 0xFF212121), W, H));
    }

    @Test
    public void transparentEdgeIsLight() {
        assertTrue(BannerTone.isLight(banner(0x00000000, 0xFF000000), W, H));
    }

    @Test
    public void colouredOrDarkPaperIsNot() {
        assertFalse(BannerTone.isLight(banner(0xFF0F79FF, 0xFFFFFFFF), W, H));
        assertFalse(BannerTone.isLight(banner(0xFF1A1C1F, 0xFFFFFFFF), W, H));
        assertFalse(BannerTone.isLight(banner(0xFFF27A21, 0xFFFFFFFF), W, H));
        // Pale but clearly tinted (a pastel pink): not paper.
        assertFalse(BannerTone.isLight(banner(0xFFFFC0D0, 0xFF000000), W, H));
    }

    @Test
    public void halfWhiteEdgeIsNot() {
        int[] px = banner(0xFFFFFFFF, 0xFFE50914);
        for (int y = 0; y < H; y++) {
            for (int x = W / 2; x < W; x++) px[y * W + x] = 0xFF0F79FF;
        }
        assertFalse(BannerTone.isLight(px, W, H));
    }

    @Test
    public void paperIsTheEdgeColourOrZeroWhenTransparent() {
        assertEquals(0xFFF2F2F2, BannerTone.paper(banner(0xFFF2F2F2, 0xFF212121), W, H));
        assertEquals(0, BannerTone.paper(banner(0x00000000, 0xFF000000), W, H));
    }

    @Test
    public void paperGoesTransparentAndRedStaysRed() {
        int[] px = {0xFFFFFFFF, 0xFFE50914, 0xFFFF0000};
        BannerTone.forDark(px, 0xFFFFFFFF);
        assertEquals(0, px[0] >>> 24);
        assertEquals(0xFFE50914, px[1]);
        assertEquals(0xFFFF0000, px[2]);
    }

    @Test
    public void offWhitePaperGoesTransparentToo() {
        int[] px = {0xFFF2F2F2};
        BannerTone.forDark(px, 0xFFF2F2F2);
        assertEquals(0, px[0] >>> 24);
    }

    @Test
    public void blackLetteringTurnsLight() {
        int[] px = {0xFF000000, 0xFF212121, 0xFF5F6368};
        BannerTone.forDark(px, 0xFFFFFFFF);
        for (int p : px) {
            assertEquals(255, p >>> 24);
            assertTrue("lifted: " + Integer.toHexString(p), (p & 0xFF) >= 0xB0);
        }
    }

    @Test
    public void paleEdgePixelsKeepTheirHueAtPartialAlpha() {
        // A red letter's anti-aliased edge, mostly paper: two-thirds opaque, a stronger red.
        int[] px = {0xFFFFC0C0};
        BannerTone.forDark(px, 0xFFFFFFFF);
        int a = px[0] >>> 24, r = (px[0] >> 16) & 0xFF, g = (px[0] >> 8) & 0xFF, b = px[0] & 0xFF;
        assertTrue(a > 140 && a < 190);
        assertEquals(255, r);
        assertEquals(g, b);
        assertTrue(g < 0xB0);
    }

    @Test
    public void brandColoursFarFromThePaperStayOpaque() {
        int[] px = {0xFF4285F4, 0xFF01875F};
        BannerTone.forDark(px, 0xFFFFFFFF);
        assertEquals(0xFF4285F4, px[0]);
        assertEquals(0xFF01875F, px[1]);
    }

    @Test
    public void transparentBannerOnlyLiftsDarkLettering() {
        int[] px = {0x00000000, 0xFF000000, 0xFF2DA6E6, 0x80FFFFFF};
        BannerTone.forDark(px, 0);
        assertEquals(0, px[0]);
        assertEquals(0xFFE4E4E4, px[1]);
        assertEquals(0xFF2DA6E6, px[2]);
        assertEquals(0x80FFFFFF, px[3]);
    }
}
