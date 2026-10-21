package com.gathin.tvhome;

import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AlexaProtocolTest {
    private static final String PAYLOAD = "{\"v\":1}";
    // hmac.new(b"test-secret", b'{"v":1}', "sha256").hexdigest() in Python
    private static final String SIGNATURE = "9d7c0d9772869f30c11844dace34b5fc615c5e6fa49530695080fc4b02bf4cc4";

    @Test public void signatureMatchesPythonHmac() {
        assertEquals(SIGNATURE, AlexaProtocol.hmacHex("test-secret", PAYLOAD));
        assertTrue(AlexaProtocol.verify("test-secret", PAYLOAD, SIGNATURE));
    }

    @Test public void tamperedOrWrongKeyOrMalformedSignaturesFail() {
        assertFalse(AlexaProtocol.verify("test-secret", "{\"v\":2}", SIGNATURE));
        assertFalse(AlexaProtocol.verify("other-secret", PAYLOAD, SIGNATURE));
        assertFalse(AlexaProtocol.verify("test-secret", PAYLOAD, SIGNATURE.toUpperCase()));
        assertFalse(AlexaProtocol.verify("test-secret", PAYLOAD, SIGNATURE.substring(1)));
        assertFalse(AlexaProtocol.verify("test-secret", PAYLOAD, ""));
        assertFalse(AlexaProtocol.verify("test-secret", PAYLOAD, null));
        assertFalse(AlexaProtocol.verify("", PAYLOAD, SIGNATURE));
    }

    @Test public void signsNonAsciiAsUtf8() {
        assertEquals(64, AlexaProtocol.hmacHex("clave-ñ", "{\"l\":\"Películas\"}").length());
        assertTrue(AlexaProtocol.verify("clave-ñ", "{\"l\":\"Películas\"}",
                AlexaProtocol.hmacHex("clave-ñ", "{\"l\":\"Películas\"}")));
    }

    @Test public void timestampWindowIsPlusMinus120Seconds() {
        AlexaProtocol.ReplayGuard guard = new AlexaProtocol.ReplayGuard();
        assertEquals(AlexaProtocol.Verdict.OK, AlexaProtocol.admit(1000, 1000, "a", guard));
        assertEquals(AlexaProtocol.Verdict.OK, AlexaProtocol.admit(1000, 880, "b", guard));
        assertEquals(AlexaProtocol.Verdict.OK, AlexaProtocol.admit(1000, 1120, "c", guard));
        assertEquals(AlexaProtocol.Verdict.STALE, AlexaProtocol.admit(1000, 879, "d", guard));
        assertEquals(AlexaProtocol.Verdict.STALE, AlexaProtocol.admit(1000, 1121, "e", guard));
    }

    @Test public void staleCommandsDoNotUseUpTheirId() {
        AlexaProtocol.ReplayGuard guard = new AlexaProtocol.ReplayGuard();
        assertEquals(AlexaProtocol.Verdict.STALE, AlexaProtocol.admit(1000, 0, "x", guard));
        assertEquals(AlexaProtocol.Verdict.OK, AlexaProtocol.admit(1000, 1000, "x", guard));
    }

    @Test public void repeatedIdsAreReplays() {
        AlexaProtocol.ReplayGuard guard = new AlexaProtocol.ReplayGuard();
        assertEquals(AlexaProtocol.Verdict.OK, AlexaProtocol.admit(1000, 1000, "a", guard));
        assertEquals(AlexaProtocol.Verdict.REPLAY, AlexaProtocol.admit(1000, 1000, "a", guard));
        assertEquals(AlexaProtocol.Verdict.REPLAY, AlexaProtocol.admit(1000, 1000, "", guard));
        assertEquals(AlexaProtocol.Verdict.REPLAY, AlexaProtocol.admit(1000, 1000, null, guard));
    }

    @Test public void onlyTheLast200IdsAreRemembered() {
        AlexaProtocol.ReplayGuard guard = new AlexaProtocol.ReplayGuard();
        for (int i = 0; i < 201; i++) assertTrue(guard.firstSeen("id" + i));
        assertTrue(guard.firstSeen("id0"));
        assertFalse(guard.firstSeen("id200"));
    }

    @Test public void volumeScaling() {
        assertEquals(0, AlexaProtocol.volumeForLevel(0, 15));
        assertEquals(8, AlexaProtocol.volumeForLevel(50, 15));
        assertEquals(15, AlexaProtocol.volumeForLevel(100, 15));
        assertEquals(15, AlexaProtocol.volumeForLevel(250, 15));
        assertEquals(0, AlexaProtocol.volumeForLevel(-5, 15));
        assertEquals(3, AlexaProtocol.stepsForDelta(20, 15));
        assertEquals(-8, AlexaProtocol.stepsForDelta(-50, 15));
        assertEquals(1, AlexaProtocol.stepsForDelta(1, 15));
        assertEquals(-1, AlexaProtocol.stepsForDelta(-1, 15));
        assertEquals(0, AlexaProtocol.stepsForDelta(0, 15));
    }

    @Test public void envFileParsing() {
        Map<String, String> env = AlexaProtocol.parseEnv(
                "# comment\n\nNTFY_BASE=https://ntfy.sh\r\nexport CMD_TOPIC = \"abc\"\nSTATE_TOPIC='def'\n"
                        + "HMAC_SECRET=a=b\\c#d\nbroken line\n=novalue\n");
        assertEquals("https://ntfy.sh", env.get("NTFY_BASE"));
        assertEquals("abc", env.get("CMD_TOPIC"));
        assertEquals("def", env.get("STATE_TOPIC"));
        assertEquals("a=b\\c#d", env.get("HMAC_SECRET"));
        assertEquals(4, env.size());
    }
}
