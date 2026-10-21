package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Alexa channel numbers to apps. Alexa grabs spoken app names ("Netflix") before they reach Sinric, so numbers are
 * the reliable path: 1 Netflix, 2 YouTube, 3 Disney+, then the rest in tile order. Assigned numbers are kept in
 * channels.txt ("<number> <package>" per line) so they stay stable; new apps take the next free number. No Android types.
 */
final class ChannelMap {
    static final String FILE_NAME = "channels.txt";
    static final List<String> PINNED = Arrays.asList(
            "com.netflix.ninja", "com.google.android.youtube.tv", "com.disney.disneyplus");
    private static final Map<String, Integer> WORDS = new HashMap<>();

    static {
        String[] es = {"cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez",
                "once", "doce", "trece", "catorce", "quince", "dieciseis", "diecisiete", "dieciocho", "diecinueve",
                "veinte", "veintiuno", "veintidos", "veintitres", "veinticuatro", "veinticinco"};
        String[] en = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
                "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
                "nineteen", "twenty"};
        for (int i = 0; i < es.length; i++) WORDS.put(es[i], i);
        for (int i = 0; i < en.length; i++) WORDS.put(en[i], i);
        WORDS.put("un", 1);
        WORDS.put("una", 1);
        WORDS.put("primero", 1);
        WORDS.put("segundo", 2);
        WORDS.put("tercero", 3);
    }

    private ChannelMap() {
    }

    /** The number in "1", "uno", "canal 2", "channel three", "número tres"; -1 when it is not a number. */
    static int parseNumber(String s) {
        if (s == null) return -1;
        String n = AppMatch.normalize(s);
        for (String prefix : new String[] {"canal", "channel", "numero", "number", "el", "la"}) {
            if (n.startsWith(prefix) && n.length() > prefix.length()) n = n.substring(prefix.length());
        }
        if (n.matches("[0-9]{1,4}")) return Integer.parseInt(n);
        Integer w = WORDS.get(n);
        return w == null ? -1 : w;
    }

    /** number -> package from channels.txt text; bad lines skipped. */
    static Map<Integer, String> decode(String text) {
        Map<Integer, String> m = new LinkedHashMap<>();
        if (text == null) return m;
        for (String line : text.split("\n")) {
            String[] p = line.trim().split("\\s+");
            if (p.length < 2 || !p[0].matches("[0-9]{1,4}") || m.containsValue(p[1])) continue;
            m.put(Integer.parseInt(p[0]), p[1]);
        }
        return m;
    }

    static String encode(Map<Integer, String> m, Map<String, String> labels) {
        StringBuilder sb = new StringBuilder();
        List<Integer> nums = new ArrayList<>(m.keySet());
        java.util.Collections.sort(nums);
        for (int n : nums) {
            String pkg = m.get(n);
            String label = labels.get(pkg);
            sb.append(n).append(' ').append(pkg);
            if (label != null) sb.append(' ').append(label.replace('\n', ' '));
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Saved numbers for apps still installed, pinned apps on 1..3 when free, then every other app (already in tile
     * order) on the next number after the highest one in use.
     */
    static Map<Integer, String> assign(Map<Integer, String> saved, List<String> ordered) {
        Map<Integer, String> m = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> e : saved.entrySet()) {
            if (ordered.contains(e.getValue())) m.put(e.getKey(), e.getValue());
        }
        for (int i = 0; i < PINNED.size(); i++) {
            String pkg = PINNED.get(i);
            if (ordered.contains(pkg) && !m.containsValue(pkg) && !m.containsKey(i + 1)) m.put(i + 1, pkg);
        }
        int next = PINNED.size();
        for (int k : m.keySet()) next = Math.max(next, k);
        for (String pkg : ordered) if (!m.containsValue(pkg)) m.put(++next, pkg);
        return m;
    }

    /** Number of the channel delta steps away from current (wrapping), or the first one when current is unknown. */
    static int step(Map<Integer, String> m, int current, int delta) {
        List<Integer> nums = new ArrayList<>(m.keySet());
        if (nums.isEmpty()) return -1;
        java.util.Collections.sort(nums);
        int i = nums.indexOf(current);
        if (i < 0) return delta >= 0 ? nums.get(0) : nums.get(nums.size() - 1);
        int size = nums.size();
        return nums.get(((i + delta) % size + size) % size);
    }
}
