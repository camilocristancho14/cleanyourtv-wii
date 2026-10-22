package com.gathin.tvhome;

/**
 * Banners drawn for a white tile (Netflix, YouTube, Play Store...) look like white boxes on the Wii menu's dark tiles.
 * This finds them from their edges and turns their white paper into transparency, with dark gray or black lettering
 * turned light so the logo stays readable. Plain Java over ARGB pixel arrays, so it is tested on the JVM.
 */
final class BannerTone {
    private BannerTone() { }

    /** Share of the edge that must be white, near-white or transparent for the banner to count as light. */
    static final float LIGHT_SHARE = 0.6f;

    /** Width of the sampled edge band: 1/16 of the shorter side, at least one pixel. */
    static int band(int w, int h) {
        return Math.max(1, Math.min(w, h) / 16);
    }

    static boolean isClear(int argb) {
        return (argb >>> 24) < 48;
    }

    static boolean isPaper(int argb) {
        if ((argb >>> 24) < 200) return false;
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        return min >= 215 && max - min <= 30;
    }

    /** True when most of the banner's edge band is white, near-white or transparent. */
    static boolean isLight(int[] px, int w, int h) {
        int light = 0, all = 0, t = band(w, h);
        for (int y = 0; y < h; y++) {
            boolean rowInBand = y < t || y >= h - t;
            for (int x = 0; x < w; x++) {
                if (!rowInBand && x >= t && x < w - t) {
                    x = w - t - 1;
                    continue;
                }
                int p = px[y * w + x];
                if (isClear(p) || isPaper(p)) light++;
                all++;
            }
        }
        return all > 0 && light >= all * LIGHT_SHARE;
    }

    /**
     * The paper colour along the edge: the average of its opaque near-white pixels, or 0 when the edge is mostly
     * transparent (the banner has no paper to remove).
     */
    static int paper(int[] px, int w, int h) {
        long r = 0, g = 0, b = 0;
        int papers = 0, clears = 0, t = band(w, h);
        for (int y = 0; y < h; y++) {
            boolean rowInBand = y < t || y >= h - t;
            for (int x = 0; x < w; x++) {
                if (!rowInBand && x >= t && x < w - t) {
                    x = w - t - 1;
                    continue;
                }
                int p = px[y * w + x];
                if (isClear(p)) {
                    clears++;
                } else if (isPaper(p)) {
                    papers++;
                    r += (p >> 16) & 0xFF;
                    g += (p >> 8) & 0xFF;
                    b += p & 0xFF;
                }
            }
        }
        if (papers == 0 || clears > papers) return 0;
        return 0xFF000000 | (int) (r / papers) << 16 | (int) (g / papers) << 8 | (int) (b / papers);
    }

    /** Light black lettering is lifted to (gray lettering a little less, keeping the logo's shading). */
    static final int LIFT = 0xE4;
    /** Distance from the paper (largest channel gap) from which a pixel is logo, kept opaque in its own colour. */
    static final int SOLID = 96;

    /**
     * Re-tones a light banner for a dark tile, in place. With an opaque paper, every pixel is first white-balanced to
     * the paper; pixels within SOLID of it are un-blended from white (paper goes fully transparent, anti-aliased
     * edges keep their hue), farther ones are logo and stay opaque in their own colour. Then dark, unsaturated pixels
     * (black or gray lettering) are lifted towards light gray; saturated colours (Netflix red, Play green) are kept.
     */
    static void forDark(int[] px, int paper) {
        boolean unblend = (paper >>> 24) != 0;
        int pr = Math.max(1, (paper >> 16) & 0xFF), pg = Math.max(1, (paper >> 8) & 0xFF), pb = Math.max(1, paper & 0xFF);
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int a = p >>> 24;
            if (a == 0) continue;
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
            if (unblend) {
                r = Math.min(255, r * 255 / pr);
                g = Math.min(255, g * 255 / pg);
                b = Math.min(255, b * 255 / pb);
                int k = Math.max(255 - r, Math.max(255 - g, 255 - b));
                if (k == 0) {
                    px[i] = 0;
                    continue;
                }
                if (k < SOLID) {
                    r = 255 - (255 - r) * SOLID / k;
                    g = 255 - (255 - g) * SOLID / k;
                    b = 255 - (255 - b) * SOLID / k;
                    a = a * k / SOLID;
                }
            }
            int lum = (r * 299 + g * 587 + b * 114) / 1000;
            if (lum < 128) {
                int chroma = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
                float n = Math.max(0, 96 - chroma) / 96f;
                if (n > 0) {
                    int target = LIFT - lum / 3;
                    r += Math.round(n * (target - r));
                    g += Math.round(n * (target - g));
                    b += Math.round(n * (target - b));
                }
            }
            px[i] = a << 24 | r << 16 | g << 8 | b;
        }
    }
}
