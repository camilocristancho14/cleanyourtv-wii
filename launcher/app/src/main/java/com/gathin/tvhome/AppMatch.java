package com.gathin.tvhome;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Maps a spoken channel name to an installed app: fuzzy match on labels, then a built-in alias table. No Android types. */
final class AppMatch {
    static final double THRESHOLD = 0.72;
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        alias("com.netflix.ninja", "netflix");
        alias("com.google.android.youtube.tv", "youtube");
        alias("com.google.android.youtube.tvmusic", "youtube music");
        alias("com.disney.disneyplus", "disney", "disney plus", "star", "star plus");
        alias("com.wbd.stream", "hbo", "max", "hbo max");
        alias("com.amazon.amazonvideo.livingroom", "prime", "prime video", "amazon");
        alias("com.apple.atve.androidtv.appletv", "apple tv");
        alias("com.rcntv.rcntv", "rcn");
        alias("com.mediatek.tv.oneworld.tvcenter", "live tv", "tv", "television");
        alias("com.spotify.tv.android", "spotify");
        alias("com.android.tv.settings", "settings", "ajustes", "configuracion");
        alias("com.android.vending", "play store");
    }

    private AppMatch() {
    }

    private static void alias(String pkg, String... names) {
        for (String name : names) ALIASES.put(normalize(name), pkg);
    }

    /** Accents stripped, lowercase, "+" read as "plus", everything but letters and digits removed. */
    static String normalize(String s) {
        String n = MARKS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
        return NON_ALNUM.matcher(n.toLowerCase(Locale.ROOT).replace("+", " plus ")).replaceAll("");
    }

    /** Alias-table package for a spoken name, or null. */
    static String aliasPackage(String query) {
        return ALIASES.get(normalize(query));
    }

    /** 1.0 exact, 0.9 prefix, 0.8 contains (these two only for 3+ letters, so "tv" cannot hit every label), else Levenshtein ratio. */
    static double score(String query, String label) {
        String q = normalize(query);
        String l = normalize(label);
        if (q.isEmpty() || l.isEmpty()) return 0;
        if (q.equals(l)) return 1.0;
        if (q.length() >= 3) {
            if (l.startsWith(q)) return 0.9;
            if (l.contains(q)) return 0.8;
        }
        return 1.0 - levenshtein(q, l) / (double) Math.max(q.length(), l.length());
    }

    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    /** Package of the installed app a spoken name means: best label at or above {@link #THRESHOLD}, else the alias if installed. */
    static String resolve(Map<String, String> labelsByPackage, String query) {
        String best = null;
        double bestScore = 0;
        for (Map.Entry<String, String> e : labelsByPackage.entrySet()) {
            double s = score(query, e.getValue());
            boolean better = s > bestScore;
            if (!better && s == bestScore && best != null) {
                String bestLabel = labelsByPackage.get(best);
                // Equal scores: the shorter label is the closer name, then the package name for a stable pick.
                better = e.getValue().length() < bestLabel.length()
                        || (e.getValue().length() == bestLabel.length() && e.getKey().compareTo(best) < 0);
            }
            if (better) {
                best = e.getKey();
                bestScore = s;
            }
        }
        if (best != null && bestScore >= THRESHOLD) return best;
        String aliased = aliasPackage(query);
        return aliased != null && labelsByPackage.containsKey(aliased) ? aliased : null;
    }
}
