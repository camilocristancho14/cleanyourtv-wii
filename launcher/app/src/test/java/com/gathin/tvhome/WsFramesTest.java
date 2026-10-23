package com.gathin.tvhome;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WsFramesTest {
    private static final byte[] MASK = {0x37, (byte) 0xfa, 0x21, 0x3d};

    private static byte[] bytes(int... v) {
        byte[] b = new byte[v.length];
        for (int i = 0; i < v.length; i++) b[i] = (byte) v[i];
        return b;
    }

    /** An unmasked server frame. */
    private static byte[] server(boolean fin, int opcode, byte[] payload) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write((fin ? 0x80 : 0) | opcode);
        if (payload.length < 126) {
            o.write(payload.length);
        } else if (payload.length <= 0xffff) {
            o.write(126);
            o.write(payload.length >> 8);
            o.write(payload.length);
        } else {
            o.write(127);
            for (int i = 7; i >= 0; i--) o.write((int) (((long) payload.length) >>> (8 * i)));
        }
        o.write(payload, 0, payload.length);
        return o.toByteArray();
    }

    private static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.write(p, 0, p.length);
        return o.toByteArray();
    }

    private static WsFrames.Reader reader(byte[] data) {
        return new WsFrames.Reader(new ByteArrayInputStream(data), 1 << 20);
    }

    @Test public void acceptKeyMatchesRfcExample() {
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", WsFrames.acceptKey("dGhlIHNhbXBsZSBub25jZQ=="));
    }

    @Test public void encodesTheRfcMaskedHello() {
        assertArrayEquals(bytes(0x81, 0x85, 0x37, 0xfa, 0x21, 0x3d, 0x7f, 0x9f, 0x4d, 0x51, 0x58),
                WsFrames.encode(WsFrames.TEXT, "Hello".getBytes(StandardCharsets.UTF_8), MASK));
    }

    @Test public void encodedFramesRoundTripAtEveryLengthClass() throws IOException {
        for (int n : new int[] {0, 1, 125, 126, 127, 65535, 65536, 70000}) {
            byte[] payload = new byte[n];
            for (int i = 0; i < n; i++) payload[i] = (byte) (i * 31);
            byte[] wire = WsFrames.encode(WsFrames.BINARY, payload, MASK);
            assertEquals(n < 126 ? 2 : n <= 0xffff ? 4 : 10, wire.length - 4 - n);
            WsFrames.Frame f = WsFrames.readFrame(new ByteArrayInputStream(wire), 1 << 20);
            assertTrue(f.fin);
            assertEquals(WsFrames.BINARY, f.opcode);
            assertArrayEquals(payload, f.payload);
        }
    }

    @Test public void readsUnmaskedServerFramesWith16And64BitLengths() throws IOException {
        byte[] mid = new byte[300];
        byte[] big = new byte[70000];
        java.util.Arrays.fill(mid, (byte) 'a');
        java.util.Arrays.fill(big, (byte) 'b');
        WsFrames.Reader r = reader(cat(server(true, WsFrames.TEXT, mid), server(true, WsFrames.TEXT, big)));
        assertArrayEquals(mid, r.next().payload);
        assertArrayEquals(big, r.next().payload);
    }

    @Test public void joinsFragmentsAndPassesInterleavedControlFramesThrough() throws IOException {
        byte[] wire = cat(
                server(false, WsFrames.TEXT, "hel".getBytes(StandardCharsets.UTF_8)),
                server(true, WsFrames.PING, "p".getBytes(StandardCharsets.UTF_8)),
                server(false, WsFrames.CONT, "lo ".getBytes(StandardCharsets.UTF_8)),
                server(true, WsFrames.CONT, "world".getBytes(StandardCharsets.UTF_8)));
        WsFrames.Reader r = reader(wire);
        WsFrames.Frame ping = r.next();
        assertEquals(WsFrames.PING, ping.opcode);
        WsFrames.Frame msg = r.next();
        assertEquals(WsFrames.TEXT, msg.opcode);
        assertEquals("hello world", new String(msg.payload, StandardCharsets.UTF_8));
    }

    @Test public void readsCloseWithCode() throws IOException {
        WsFrames.Frame f = reader(server(true, WsFrames.CLOSE, bytes(0x03, 0xe8))).next();
        assertEquals(WsFrames.CLOSE, f.opcode);
        assertArrayEquals(bytes(0x03, 0xe8), f.payload);
    }

    @Test public void rejectsProtocolViolations() {
        byte[][] bad = {
                bytes(0xc1, 0x00),                        // reserved bit
                bytes(0x80, 0x00),                        // continuation with nothing to continue
                bytes(0x09, 0x00),                        // fragmented control frame
                bytes(0x89, 126, 0x00, 0x7e),             // oversized control frame
                bytes(0x81, 127, 0xff, 0, 0, 0, 0, 0, 0, 0), // absurd length
                cat(server(false, WsFrames.TEXT, new byte[1]), server(true, WsFrames.TEXT, new byte[1])), // interleaved data
        };
        for (byte[] wire : bad) {
            try {
                reader(wire).next();
                fail("accepted " + java.util.Arrays.toString(wire));
            } catch (IOException expected) {
                // ok
            }
        }
    }

    @Test public void truncatedStreamIsEof() {
        try {
            reader(bytes(0x81, 0x05, 'a')).next();
            fail();
        } catch (EOFException expected) {
            // ok
        } catch (IOException e) {
            fail(e.toString());
        }
    }

    @Test public void messageLimitAppliesAcrossFragments() {
        byte[] wire = cat(server(false, WsFrames.TEXT, new byte[60]), server(true, WsFrames.CONT, new byte[60]));
        try {
            new WsFrames.Reader(new ByteArrayInputStream(wire), 100).next();
            fail();
        } catch (IOException expected) {
            // ok
        }
    }
}
