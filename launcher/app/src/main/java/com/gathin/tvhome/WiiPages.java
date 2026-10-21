package com.gathin.tvhome;

/** Page arithmetic of the Wii menu: pages of 4 columns by 3 rows, filled column by column. */
final class WiiPages {
    private WiiPages() { }

    static final int ROWS = 3, COLS = 4, PER_PAGE = ROWS * COLS;

    /** Pages for this many apps; there is always at least one. */
    static int pages(int apps) {
        return Math.max(1, (apps + PER_PAGE - 1) / PER_PAGE);
    }

    /** Slots to build: the last page is padded with empty ones to a full 12. */
    static int slots(int apps) {
        return pages(apps) * PER_PAGE;
    }

    /** First column of the page a column belongs to. */
    static int pageStart(int col) {
        return Math.max(0, col) / COLS * COLS;
    }
}
