package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for the Wii menu's paging arithmetic and the tiles it leaves out. */
public class WiiPagesTest {

    @Test
    public void aPageIsFourColumnsOfThree() {
        assertEquals(3, WiiPages.ROWS);
        assertEquals(4, WiiPages.COLS);
        assertEquals(12, WiiPages.PER_PAGE);
    }

    @Test
    public void thereIsAlwaysAPageAndTheLastOneIsPaddedToTwelve() {
        assertEquals(1, WiiPages.pages(0));
        assertEquals(12, WiiPages.slots(0));
        assertEquals(1, WiiPages.pages(12));
        assertEquals(12, WiiPages.slots(12));
        assertEquals(2, WiiPages.pages(13));
        assertEquals(24, WiiPages.slots(13));
        assertEquals(3, WiiPages.pages(25));
        assertEquals(36, WiiPages.slots(36));
    }

    @Test
    public void columnsMapToTheFirstColumnOfTheirPage() {
        for (int col = 0; col < 4; col++) assertEquals(0, WiiPages.pageStart(col));
        for (int col = 4; col < 8; col++) assertEquals(4, WiiPages.pageStart(col));
        assertEquals(8, WiiPages.pageStart(8));
        assertEquals(0, WiiPages.pageStart(-1));
    }

    @Test
    public void appsFillColumnByColumn() {
        // App 11 is the bottom tile of the page's last column; app 12 opens the next page at the top left.
        assertEquals(3, 11 / WiiPages.ROWS);
        assertEquals(0, WiiPages.pageStart(11 / WiiPages.ROWS));
        assertEquals(4, WiiPages.pageStart(12 / WiiPages.ROWS));
        assertEquals(0, 12 % WiiPages.ROWS);
    }

    @Test
    public void settingsAndTheFileManagerHaveNoTile() {
        assertTrue(WiiOrder.NO_TILE.contains("com.android.tv.settings"));
        assertTrue(WiiOrder.NO_TILE.contains("com.alphainventor.filemanager"));
    }
}
