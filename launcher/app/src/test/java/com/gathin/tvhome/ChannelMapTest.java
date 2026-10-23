package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class ChannelMapTest {
    @Test public void parsesNumbers() {
        assertEquals(1, ChannelMap.parseNumber("1"));
        assertEquals(1, ChannelMap.parseNumber("uno"));
        assertEquals(1, ChannelMap.parseNumber("Uno"));
        assertEquals(3, ChannelMap.parseNumber("canal tres"));
        assertEquals(16, ChannelMap.parseNumber("dieciséis"));
        assertEquals(2, ChannelMap.parseNumber("channel two"));
        assertEquals(-1, ChannelMap.parseNumber("netflix"));
        assertEquals(-1, ChannelMap.parseNumber(""));
    }

    @Test public void assignsPinnedThenOrderStable() {
        List<String> apps = Arrays.asList("a", "com.disney.disneyplus", "com.netflix.ninja", "b",
                "com.google.android.youtube.tv");
        Map<Integer, String> m = ChannelMap.assign(new HashMap<>(), apps);
        assertEquals("com.netflix.ninja", m.get(1));
        assertEquals("com.google.android.youtube.tv", m.get(2));
        assertEquals("com.disney.disneyplus", m.get(3));
        assertEquals("a", m.get(4));
        assertEquals("b", m.get(5));
        Map<Integer, String> again = ChannelMap.assign(ChannelMap.decode(ChannelMap.encode(m, new HashMap<>())),
                Arrays.asList("new", "b", "a", "com.netflix.ninja", "com.google.android.youtube.tv",
                        "com.disney.disneyplus"));
        assertEquals("a", again.get(4));
        assertEquals("b", again.get(5));
        assertEquals("new", again.get(6));
        assertEquals(1, ChannelMap.step(again, 6, 1));
        assertEquals(6, ChannelMap.step(again, 1, -1));
    }
}
