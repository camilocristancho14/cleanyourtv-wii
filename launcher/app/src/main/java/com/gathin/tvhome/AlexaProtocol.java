package com.gathin.tvhome;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Pure logic of the Alexa bridge wire protocol (docs/alexa-bridge.md): no Android types, so JVM tests cover it. */
final class AlexaProtocol {
    static final long MAX_SKEW_SECONDS = 120;
    static final int REMEMBERED_IDS = 200;

    enum Verdict { OK, STALE, REPLAY }

    private AlexaProtocol() {
    }

    /** Lowercase hex HMAC-SHA256 of the payload string, both keyed and hashed as UTF-8. */
    static String hmacHex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sum = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(sum.length * 2);
            for (byte b : sum) hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
            return hex.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Constant-time check of the envelope's "s" against "p"; call before parsing p. */
    static boolean verify(String secret, String payload, String signature) {
        if (secret == null || secret.isEmpty() || payload == null || signature == null) return false;
        byte[] expected = hmacHex(secret, payload).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8));
    }

    /** Remembers the last {@link #REMEMBERED_IDS} command ids. */
    static final class ReplayGuard {
        private final Map<String, Boolean> seen = new LinkedHashMap<String, Boolean>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > REMEMBERED_IDS;
            }
        };

        /** True the first time an id is offered. */
        synchronized boolean firstSeen(String id) {
            return seen.put(id, Boolean.TRUE) == null;
        }
    }

    /** Timestamp and replay checks, for a payload whose signature has already verified. */
    static Verdict admit(long nowSeconds, long ts, String id, ReplayGuard guard) {
        if (Math.abs(nowSeconds - ts) > MAX_SKEW_SECONDS) return Verdict.STALE;
        if (id == null || id.isEmpty() || !guard.firstSeen(id)) return Verdict.REPLAY;
        return Verdict.OK;
    }

    /** round(level * max / 100) with level clamped to 0..100. */
    static int volumeForLevel(int level, int max) {
        int clamped = Math.max(0, Math.min(100, level));
        return (int) Math.round(clamped * (double) max / 100.0);
    }

    /** Volume steps for a -100..100 delta scaled to max, at least one step in the delta's direction. */
    static int stepsForDelta(int delta, int max) {
        if (delta == 0) return 0;
        int sign = delta > 0 ? 1 : -1;
        int steps = (int) Math.round(Math.min(100, Math.abs(delta)) * (double) max / 100.0);
        return sign * Math.max(1, steps);
    }

    /**
     * Parses a shell env file: KEY=VALUE lines, # comments, optional "export " and surrounding quotes.
     * Backslashes stay literal, which java.util.Properties would treat as escapes.
     */
    static Map<String, String> parseEnv(String text) {
        Map<String, String> out = new HashMap<>();
        for (String raw : text.split("\r?\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("export ")) line = line.substring(7).trim();
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String value = line.substring(eq + 1).trim();
            if (value.length() >= 2) {
                char q = value.charAt(0);
                if ((q == '"' || q == '\'') && value.charAt(value.length() - 1) == q) {
                    value = value.substring(1, value.length() - 1);
                }
            }
            out.put(line.substring(0, eq).trim(), value);
        }
        return out;
    }
}
