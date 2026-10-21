package com.gathin.tvhome;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** RFC 6455 framing for the minimal websocket client: no Android types, so JVM tests cover it. */
final class WsFrames {
    static final int CONT = 0, TEXT = 1, BINARY = 2, CLOSE = 8, PING = 9, PONG = 10;
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private WsFrames() {
    }

    static final class Frame {
        final boolean fin;
        final int opcode;
        final byte[] payload;

        Frame(boolean fin, int opcode, byte[] payload) {
            this.fin = fin;
            this.opcode = opcode;
            this.payload = payload;
        }
    }

    /** The Sec-WebSocket-Accept value a server must answer for this Sec-WebSocket-Key. */
    static String acceptKey(String key) {
        try {
            return Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-1").digest((key + GUID).getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A final, masked client frame (clients must mask); mask is 4 bytes. */
    static byte[] encode(int opcode, byte[] payload, byte[] mask) {
        int n = payload.length;
        int header = n < 126 ? 2 : n <= 0xffff ? 4 : 10;
        byte[] out = new byte[header + 4 + n];
        out[0] = (byte) (0x80 | opcode);
        if (n < 126) {
            out[1] = (byte) (0x80 | n);
        } else if (n <= 0xffff) {
            out[1] = (byte) (0x80 | 126);
            out[2] = (byte) (n >> 8);
            out[3] = (byte) n;
        } else {
            out[1] = (byte) (0x80 | 127);
            for (int i = 0; i < 8; i++) out[2 + i] = (byte) ((long) n >>> (56 - 8 * i));
        }
        System.arraycopy(mask, 0, out, header, 4);
        for (int i = 0; i < n; i++) out[header + 4 + i] = (byte) (payload[i] ^ mask[i & 3]);
        return out;
    }

    private static void readFully(InputStream in, byte[] buf, int len) throws IOException {
        int n = 0;
        while (n < len) {
            int r = in.read(buf, n, len - n);
            if (r < 0) throw new EOFException("closed");
            n += r;
        }
    }

    /** One frame off the stream; a server-masked frame is unmasked. */
    static Frame readFrame(InputStream in, int maxPayload) throws IOException {
        byte[] h = new byte[8];
        readFully(in, h, 2);
        boolean fin = (h[0] & 0x80) != 0;
        int opcode = h[0] & 0x0f;
        if ((h[0] & 0x70) != 0) throw new IOException("reserved bits");
        boolean masked = (h[1] & 0x80) != 0;
        long len = h[1] & 0x7f;
        if (len == 126) {
            readFully(in, h, 2);
            len = ((h[0] & 0xff) << 8) | (h[1] & 0xff);
        } else if (len == 127) {
            readFully(in, h, 8);
            len = 0;
            for (int i = 0; i < 8; i++) len = (len << 8) | (h[i] & 0xff);
        }
        if (len < 0 || len > maxPayload) throw new IOException("frame too large");
        if (opcode >= CLOSE && (len > 125 || !fin)) throw new IOException("bad control frame");
        byte[] mask = new byte[4];
        if (masked) readFully(in, mask, 4);
        byte[] payload = new byte[(int) len];
        readFully(in, payload, payload.length);
        if (masked) for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
        return new Frame(fin, opcode, payload);
    }

    /** Joins fragmented data messages; control frames may arrive between fragments and are returned as they come. */
    static final class Reader {
        private final InputStream in;
        private final int max;
        private final ByteArrayOutputStream message = new ByteArrayOutputStream();
        private int messageOpcode = -1;

        Reader(InputStream in, int max) {
            this.in = in;
            this.max = max;
        }

        Frame next() throws IOException {
            while (true) {
                Frame f = readFrame(in, max);
                if (f.opcode >= CLOSE) return f;
                if (f.opcode == CONT) {
                    if (messageOpcode < 0) throw new IOException("unexpected continuation");
                } else {
                    if (messageOpcode >= 0) throw new IOException("interleaved data frames");
                    messageOpcode = f.opcode;
                }
                if (message.size() + f.payload.length > max) throw new IOException("message too large");
                message.write(f.payload, 0, f.payload.length);
                if (f.fin) {
                    Frame whole = new Frame(true, messageOpcode, message.toByteArray());
                    message.reset();
                    messageOpcode = -1;
                    return whole;
                }
            }
        }
    }
}
