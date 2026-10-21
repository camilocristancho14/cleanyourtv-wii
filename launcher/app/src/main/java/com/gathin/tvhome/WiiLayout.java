package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The owner's own slot layout of the Wii menu. A layout is a list of tile keys by slot (a component, flattened
 * short); null is an empty slot. No saved layout means the category order, packed from the first slot.
 */
final class WiiLayout {
    private WiiLayout() { }

    static final String PREFS = "wii_layout";
    private static final String KEY = "slots";
    private static final String MAGIC = "wii-layout 1";

    /**
     * The layout to show. Without a saved one it is the default order. With one, saved slots keep their tiles;
     * a saved tile that is not shown (defaultOrder) is dropped when its app is gone and otherwise keeps its slot
     * reserved (installed, but hidden or disabled); apps the layout has not seen take the first free slot, or
     * follow the last one. Trailing empty slots are cut.
     */
    static List<String> arrange(List<String> defaultOrder, List<String> saved, Predicate<String> installed) {
        if (saved == null) return new ArrayList<>(defaultOrder);
        List<String> out = new ArrayList<>();
        for (String k : saved) {
            boolean keep = k != null && !out.contains(k) && (defaultOrder.contains(k) || installed.test(k));
            out.add(keep ? k : null);
        }
        for (String k : defaultOrder) {
            if (out.contains(k)) continue;
            int free = out.indexOf(null);
            if (free >= 0) out.set(free, k);
            else out.add(k);
        }
        trim(out);
        return out;
    }

    /** Puts the tile of one slot into another: swaps with the tile there, or just moves into an empty slot. */
    static List<String> move(List<String> slots, int from, int to) {
        List<String> out = new ArrayList<>(slots);
        while (out.size() <= Math.max(from, to)) out.add(null);
        String tmp = out.get(to);
        out.set(to, out.get(from));
        out.set(from, tmp);
        trim(out);
        return out;
    }

    private static void trim(List<String> slots) {
        while (!slots.isEmpty() && slots.get(slots.size() - 1) == null) slots.remove(slots.size() - 1);
    }

    static String encode(List<String> slots) {
        StringBuilder sb = new StringBuilder(MAGIC);
        for (String k : slots) sb.append('\n').append(k == null ? "" : k);
        return sb.toString();
    }

    /** The layout in this text, or null when it is not one. */
    static List<String> decode(String text) {
        if (text == null) return null;
        String[] lines = text.split("\n", -1);
        if (!lines[0].equals(MAGIC)) return null;
        List<String> out = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) out.add(lines[i].isEmpty() ? null : lines[i]);
        return out;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The saved layout, or null when the owner has not arranged the menu. */
    static List<String> load(Context ctx) {
        return decode(prefs(ctx).getString(KEY, null));
    }

    static void save(Context ctx, List<String> slots) {
        prefs(ctx).edit().putString(KEY, encode(slots)).apply();
    }
}
