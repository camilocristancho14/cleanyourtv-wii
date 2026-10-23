package com.gathin.tvhome;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Signing rules of the Sinric Pro websocket protocol (payload-slice HMAC): no Android types, so JVM tests cover it. */
final class SinricProtocol {
    private static final String PAYLOAD_MARKER = "\"payload\":";
    private static final String SIGNATURE_MARKER = ",\"signature\"";

    private SinricProtocol() {
    }

    /** Base64 (no wrap) HMAC-SHA256 of the payload string, both keyed and hashed as UTF-8. */
    static String hmacBase64(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The raw text between the first {@code "payload":} and the following {@code ,"signature"}, or null. */
    static String extractPayload(String raw) {
        int begin = raw.indexOf(PAYLOAD_MARKER);
        if (begin < 0) return null;
        begin += PAYLOAD_MARKER.length();
        int end = raw.indexOf(SIGNATURE_MARKER, begin);
        return end < 0 ? null : raw.substring(begin, end);
    }

    /** Constant-time check of a received message's signature against the slice of its raw text. */
    static boolean verify(String secret, String raw, String signature) {
        if (secret == null || secret.isEmpty() || signature == null) return false;
        String payload = extractPayload(raw);
        if (payload == null || payload.isEmpty()) return false;
        return MessageDigest.isEqual(hmacBase64(secret, payload).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    /** The exact text to transmit: the payload string is signed and spliced in unchanged. */
    static String signedMessage(String payload, String secret) {
        return "{\"header\":{\"payloadVersion\":2,\"signatureVersion\":1},\"payload\":" + payload
                + ",\"signature\":{\"HMAC\":\"" + hmacBase64(secret, payload) + "\"}}";
    }
}
