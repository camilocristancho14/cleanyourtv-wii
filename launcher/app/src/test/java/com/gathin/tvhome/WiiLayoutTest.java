package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Plain JVM tests for the Wii menu's saved slot layout. */
public class WiiLayoutTest {

    private static List<String> l(String... keys) {
        return new ArrayList<>(Arrays.asList(keys));
    }

    @Test
    public void noSavedLayoutKeepsTheDefaultOrder() {
        assertEquals(l("a", "b", "c"), WiiLayout.arrange(l("a", "b", "c"), null, k -> true));
    }

    @Test
    public void savedSlotsKeepTheirTilesAndHoles() {
        assertEquals(l("c", null, "a", "b"), WiiLayout.arrange(l("a", "b", "c"), l("c", null, "a", "b"), k -> true));
    }

    @Test
    public void aNewAppTakesTheFirstFreeSlot() {
        assertEquals(l("c", "d", "a", "b"), WiiLayout.arrange(l("a", "b", "c", "d"), l("c", null, "a", "b"), k -> true));
    }

    @Test
    public void aNewAppFollowsTheLastSlotWhenThereIsNoHole() {
        assertEquals(l("b", "a", "c"), WiiLayout.arrange(l("a", "b", "c"), l("b", "a"), k -> true));
    }

    @Test
    public void severalNewAppsFillHolesInDefaultOrder() {
        assertEquals(l("x", "a", "y", "b", "z"),
                WiiLayout.arrange(l("a", "b", "x", "y", "z"), l(null, "a", null, "b"), k -> true));
    }

    @Test
    public void anUninstalledAppLeavesAHoleAndTrailingHolesAreCut() {
        assertEquals(l("a", null, "c"), WiiLayout.arrange(l("a", "c"), l("a", "b", "c"), k -> !k.equals("b")));
        assertEquals(l("a"), WiiLayout.arrange(l("a"), l("a", "b", "c"), k -> false));
    }

    @Test
    public void anInstalledButNotShownAppKeepsItsSlotReserved() {
        List<String> out = WiiLayout.arrange(l("a", "n"), l("a", "hidden"), k -> true);
        assertEquals(l("a", "hidden", "n"), out);
    }

    @Test
    public void duplicatesInASavedLayoutCountOnce() {
        assertEquals(l("a", "b"), WiiLayout.arrange(l("a", "b"), l("a", "a"), k -> true));
    }

    @Test
    public void moveSwapsWithATileOrFillsAnEmptySlot() {
        assertEquals(l("b", "a", "c"), WiiLayout.move(l("a", "b", "c"), 0, 1));
        assertEquals(l(null, "b", "c", "a"), WiiLayout.move(l("a", "b", "c"), 0, 3));
        assertEquals(l("a", "b", null, null, "c"), WiiLayout.move(l("a", "b", "c"), 2, 4));
        assertEquals(l("a", "b", "c"), WiiLayout.move(l("a", "b", null, "c"), 3, 2));
        assertEquals(l("a", "b", "c"), WiiLayout.move(l("a", "b", "c"), 1, 1));
    }

    @Test
    public void movingTheLastTileBackCutsTheEmptyTail() {
        assertEquals(l("a", "b"), WiiLayout.move(l("a", null, null, "b"), 3, 1));
    }

    @Test
    public void encodeAndDecodeRoundTrip() {
        List<String> slots = l("a/.A", null, null, "b/.B");
        assertEquals(slots, WiiLayout.decode(WiiLayout.encode(slots)));
        assertEquals(Collections.<String>emptyList(), WiiLayout.decode(WiiLayout.encode(Collections.<String>emptyList())));
    }

    @Test
    public void somethingThatIsNotALayoutIsIgnored() {
        assertNull(WiiLayout.decode(null));
        assertNull(WiiLayout.decode("a\nb"));
    }
}
