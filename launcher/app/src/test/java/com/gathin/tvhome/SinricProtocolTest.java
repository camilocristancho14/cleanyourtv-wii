package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SinricProtocolTest {
    // base64(hmac.new(b"s", b'{"a":1}', "sha256").digest()) in Python
    private static final String A1_SIGNATURE = "N76vZQ9wtA7JcGkpwunYNcvWNymYj0h4HmODoUchXwc=";
    private static final String PAYLOAD = "{\"action\":\"setVolume\",\"deviceId\":\"d1\",\"type\":\"request\",\"value\":{\"volume\":30}}";
    // base64(hmac.new(b"secret key", PAYLOAD.encode(), "sha256").digest()) in Python
    private static final String PAYLOAD_SIGNATURE = "jLOChLmKkRLLsnEeQaMtLj6jjWQ7SY4KnNwjiihLtr0=";

    private static String message(String payload, String sig) {
        return "{\"header\":{\"payloadVersion\":2,\"signatureVersion\":1},\"payload\":" + payload
                + ",\"signature\":{\"HMAC\":\"" + sig + "\"}}";
    }

    @Test public void hmacMatchesPython() {
        assertEquals(A1_SIGNATURE, SinricProtocol.hmacBase64("s", "{\"a\":1}"));
        assertEquals(PAYLOAD_SIGNATURE, SinricProtocol.hmacBase64("secret key", PAYLOAD));
    }

    @Test public void extractsTheRawPayloadSlice() {
        assertEquals(PAYLOAD, SinricProtocol.extractPayload(message(PAYLOAD, "x")));
        // Spacing and key order of the sender are preserved: the slice is never re-serialized.
        String spaced = "{ \"payload\": { \"a\" : 1 } ,\"signature\":{}}";
        assertEquals(" { \"a\" : 1 } ", SinricProtocol.extractPayload(spaced));
        assertNull(SinricProtocol.extractPayload("{\"a\":1}"));
        assertNull(SinricProtocol.extractPayload("{\"payload\":{}}"));
    }

    @Test public void verifiesAndRejects() {
        String good = message(PAYLOAD, PAYLOAD_SIGNATURE);
        assertTrue(SinricProtocol.verify("secret key", good, PAYLOAD_SIGNATURE));
        assertFalse(SinricProtocol.verify("other", good, PAYLOAD_SIGNATURE));
        assertFalse(SinricProtocol.verify("secret key", good.replace("30", "31"), PAYLOAD_SIGNATURE));
        assertFalse(SinricProtocol.verify("secret key", good, PAYLOAD_SIGNATURE.substring(1)));
        assertFalse(SinricProtocol.verify("secret key", good, null));
        assertFalse(SinricProtocol.verify("", good, PAYLOAD_SIGNATURE));
        assertFalse(SinricProtocol.verify("secret key", "{\"a\":1}", PAYLOAD_SIGNATURE));
    }

    @Test public void signedMessageSplicesTheSignedBytes() {
        String m = SinricProtocol.signedMessage(PAYLOAD, "secret key");
        assertEquals(message(PAYLOAD, PAYLOAD_SIGNATURE), m);
        assertEquals(PAYLOAD, SinricProtocol.extractPayload(m));
        assertTrue(SinricProtocol.verify("secret key", m, PAYLOAD_SIGNATURE));
    }

    @Test public void escapedSlashesStillRoundTrip() {
        String payload = "{\"label\":\"a\\/b\"}";
        String m = SinricProtocol.signedMessage(payload, "k");
        assertTrue(SinricProtocol.verify("k", m, SinricProtocol.hmacBase64("k", payload)));
    }
}
