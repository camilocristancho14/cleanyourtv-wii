package com.gathin.tvhome;

import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.json.JSONObject;

/**
 * Sinric Pro client for the Alexa bridge: a minimal websocket (RFC 6455) over TLS to ws.sinric.pro, so the APK needs
 * no websocket library. Answers signed requests through a {@link Handler}. Never logs the keys or request values.
 */
final class SinricClient {

    private static final String TAG = "SinricPro";
    private static final String HOST = "ws.sinric.pro";
    private static final int PORT = 443;
    private static final String VERSION = "1.0.0";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 6 * 60_000;
    private static final long PING_MIN = 4;
    private static final long PONG_WAIT_S = 10;
    private static final long BACKOFF_MIN_MS = 1_000L;
    private static final long BACKOFF_MAX_MS = 60_000L;
    private static final long EVENT_GAP_MS = 1_000L;
    private static final int MAX_MESSAGE_BYTES = 1 << 20;
    private static final int MAX_HEADER_LINE = 8192;

    static final class Result {
        final boolean ok;
        final String message;
        final JSONObject value;

        Result(boolean ok, String message, JSONObject value) {
            this.ok = ok;
            this.message = message;
            this.value = value;
        }

        static Result ok(JSONObject value) {
            return new Result(true, "OK", value);
        }

        static Result fail(String message) {
            return new Result(false, message, new JSONObject());
        }
    }

    interface Handler {
        Result handle(String action, JSONObject value) throws Exception;
    }

    private final String appKey, secret, deviceId, mac;
    private final Handler handler;
    private final SecureRandom random = new SecureRandom();
    private final Object writeLock = new Object();
    private volatile boolean running;
    private volatile Socket socket;
    private volatile OutputStream out;
    private volatile long lastPong;
    private long lastEvent;
    private Thread thread;

    SinricClient(String appKey, String secret, String deviceId, String mac, Handler handler) {
        this.appKey = appKey;
        this.secret = secret;
        this.deviceId = deviceId;
        this.mac = mac;
        this.handler = handler;
    }

    /** Header values go on the wire verbatim: printable ASCII only, so a config value cannot inject a header. */
    static boolean validToken(String s) {
        return s != null && s.matches("[\\x21-\\x7e]{1,200}");
    }

    synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "sinric-pro");
        thread.start();
    }

    synchronized void stop() {
        running = false;
        closeSocket();
        if (thread != null) thread.interrupt();
    }

    private void closeSocket() {
        Socket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void loop() {
        long backoff = BACKOFF_MIN_MS;
        while (running) {
            long started = SystemClock.elapsedRealtime();
            try {
                session();
            } catch (Exception e) {
                if (running) Log.w(TAG, "disconnected: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                out = null;
                closeSocket();
                socket = null;
            }
            if (!running) break;
            // A connection that stayed up was healthy: the next failure starts the backoff over.
            if (SystemClock.elapsedRealtime() - started > 30_000L) backoff = BACKOFF_MIN_MS;
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                break;
            }
            backoff = Math.min(backoff * 2, BACKOFF_MAX_MS);
        }
    }

    private void session() throws Exception {
        Socket plain = new Socket();
        socket = plain;
        plain.connect(new InetSocketAddress(HOST, PORT), CONNECT_TIMEOUT_MS);
        plain.setSoTimeout(CONNECT_TIMEOUT_MS);
        SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                .createSocket(plain, HOST, PORT, true);
        socket = ssl;
        if (!running) return;
        ssl.startHandshake();
        // A bare SSLSocket does not check the certificate's host name.
        if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(HOST, ssl.getSession())) {
            throw new SSLPeerUnverifiedException("host name");
        }
        InputStream in = new BufferedInputStream(ssl.getInputStream());
        OutputStream os = ssl.getOutputStream();

        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        String key = Base64.getEncoder().encodeToString(nonce);
        String request = "GET / HTTP/1.1\r\nHost: " + HOST + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\nSec-WebSocket-Version: 13\r\n"
                + "appkey: " + appKey + "\r\ndeviceids: " + deviceId + "\r\nplatform: Android\r\n"
                + "SDKVersion: " + VERSION + "\r\nversion: " + VERSION + "\r\nmac: " + mac + "\r\n"
                + "restoredevicestates: false\r\n\r\n";
        os.write(request.getBytes(StandardCharsets.US_ASCII));
        os.flush();

        String status = readLine(in);
        if (status == null || !status.startsWith("HTTP/1.1 101")) {
            throw new IOException("handshake " + (status == null ? "closed" : status.substring(0, Math.min(status.length(), 24))));
        }
        Map<String, String> headers = new HashMap<>();
        for (int i = 0; i < 64; i++) {
            String line = readLine(in);
            if (line == null) throw new IOException("handshake truncated");
            if (line.isEmpty()) break;
            int colon = line.indexOf(':');
            if (colon > 0) headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
        }
        if (!WsFrames.acceptKey(key).equals(headers.get("sec-websocket-accept"))) throw new IOException("bad accept key");

        out = os;
        lastPong = SystemClock.elapsedRealtime();
        ssl.setSoTimeout(READ_TIMEOUT_MS);
        Log.i(TAG, "connected");

        ScheduledExecutorService pinger = Executors.newSingleThreadScheduledExecutor();
        try {
            pinger.scheduleWithFixedDelay(() -> ping(pinger), PING_MIN, PING_MIN, TimeUnit.MINUTES);
            WsFrames.Reader reader = new WsFrames.Reader(in, MAX_MESSAGE_BYTES);
            while (running) {
                WsFrames.Frame f = reader.next();
                switch (f.opcode) {
                    case WsFrames.TEXT:
                        handleText(new String(f.payload, StandardCharsets.UTF_8));
                        break;
                    case WsFrames.PING:
                        sendFrame(WsFrames.PONG, f.payload);
                        break;
                    case WsFrames.PONG:
                        lastPong = SystemClock.elapsedRealtime();
                        break;
                    case WsFrames.CLOSE:
                        try {
                            sendFrame(WsFrames.CLOSE, f.payload);
                        } catch (IOException ignored) {
                        }
                        Log.w(TAG, "disconnected: closed by server");
                        return;
                    default:
                        break;
                }
            }
        } finally {
            pinger.shutdownNow();
        }
    }

    private void ping(ScheduledExecutorService pinger) {
        final long sent = SystemClock.elapsedRealtime();
        try {
            sendFrame(WsFrames.PING, new byte[0]);
        } catch (IOException e) {
            closeSocket();
            return;
        }
        pinger.schedule(() -> {
            if (lastPong < sent) {
                Log.w(TAG, "disconnected: no pong");
                closeSocket();
            }
        }, PONG_WAIT_S, TimeUnit.SECONDS);
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) >= 0) {
            if (b == '\n') {
                byte[] bytes = line.toByteArray();
                int n = bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? bytes.length - 1 : bytes.length;
                return new String(bytes, 0, n, StandardCharsets.ISO_8859_1);
            }
            line.write(b);
            if (line.size() > MAX_HEADER_LINE) throw new IOException("header too long");
        }
        return null;
    }

    private void sendFrame(int opcode, byte[] payload) throws IOException {
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        byte[] frame = WsFrames.encode(opcode, payload, mask);
        synchronized (writeLock) {
            OutputStream os = out;
            if (os == null) throw new IOException("not connected");
            os.write(frame);
            os.flush();
        }
    }

    private void sendText(String text) throws IOException {
        sendFrame(WsFrames.TEXT, text.getBytes(StandardCharsets.UTF_8));
    }

    // ---- messages ----

    private void handleText(String raw) {
        try {
            JSONObject msg = new JSONObject(raw);
            if (msg.has("timestamp")) return;
            JSONObject payload = msg.optJSONObject("payload");
            if (payload == null || !"request".equals(payload.optString("type"))
                    || !deviceId.equals(payload.optString("deviceId"))) return;
            JSONObject sig = msg.optJSONObject("signature");
            if (!SinricProtocol.verify(secret, raw, sig == null ? null : sig.optString("HMAC", null))) {
                Log.w(TAG, "request with invalid signature");
                respond(payload, Result.fail("Signature is invalid"));
                return;
            }
            String action = payload.optString("action");
            Log.i(TAG, "request " + action.substring(0, Math.min(action.length(), 40)));
            JSONObject value = payload.optJSONObject("value");
            Result result;
            try {
                result = handler.handle(action, value != null ? value : new JSONObject());
            } catch (Exception e) {
                Log.w(TAG, action + " failed: " + e.getClass().getSimpleName());
                result = Result.fail("Request failed");
            }
            respond(payload, result);
            if (result.ok && ("setVolume".equals(action) || "setMute".equals(action) || "setPowerState".equals(action))) {
                sendEvent(action, result.value);
            }
        } catch (Exception e) {
            Log.w(TAG, "message: " + e.getClass().getSimpleName());
        }
    }

    private void respond(JSONObject request, Result result) throws Exception {
        JSONObject p = new JSONObject()
                .put("action", request.optString("action"))
                .put("clientId", request.optString("clientId"))
                .put("createdAt", System.currentTimeMillis() / 1000L)
                .put("deviceId", deviceId)
                .put("message", result.message)
                .put("replyToken", request.optString("replyToken"))
                .put("scope", "device")
                .put("success", result.ok)
                .put("type", "response")
                .put("value", result.value);
        if (request.has("instanceId")) p.put("instanceId", request.optString("instanceId"));
        sendText(SinricProtocol.signedMessage(p.toString(), secret));
    }

    private void sendEvent(String action, JSONObject value) throws Exception {
        long now = SystemClock.elapsedRealtime();
        if (lastEvent != 0 && now - lastEvent < EVENT_GAP_MS) return;
        lastEvent = now;
        byte[] token = new byte[8];
        random.nextBytes(token);
        StringBuilder hex = new StringBuilder();
        for (byte b : token) hex.append(String.format(Locale.ROOT, "%02x", b));
        JSONObject p = new JSONObject()
                .put("action", action)
                .put("cause", new JSONObject().put("type", "PHYSICAL_INTERACTION"))
                .put("createdAt", System.currentTimeMillis() / 1000L)
                .put("deviceId", deviceId)
                .put("replyToken", hex.toString())
                .put("type", "event")
                .put("value", value);
        sendText(SinricProtocol.signedMessage(p.toString(), secret));
    }
}
