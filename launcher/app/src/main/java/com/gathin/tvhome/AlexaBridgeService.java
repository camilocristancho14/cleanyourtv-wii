package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Alexa bridge: listens on an ntfy.sh topic for signed commands from the cloud and answers on a second topic
 * (wire protocol and setup in docs/alexa-bridge.md). Does nothing unless alexa.properties is present.
 * Log lines never include topics, the secret or message bodies.
 */
public class AlexaBridgeService extends Service {

    private static final String TAG = "AlexaBridge";
    private static final String CHANNEL = "alexa_bridge";
    private static final int NOTIFICATION_ID = 7421;
    private static final String PREFS = "alexa_bridge";
    private static final String PREF_LAST_ID = "last_id";
    private static final String CONFIG_FILE = "alexa.properties";
    private static final String CONFIG_FALLBACK = "/data/local/tmp/" + CONFIG_FILE;
    private static final int READ_TIMEOUT_MS = 90_000;
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final long BACKOFF_MIN_MS = 1_000L;
    private static final long BACKOFF_MAX_MS = 60_000L;
    private static final long HEARTBEAT_MIN = 15;
    /** ntfy turns a larger POST body into an attachment, which the Lambda could not read as an envelope. */
    private static final int MAX_ENVELOPE_BYTES = 4000;

    private static final class Cfg {
        String base, cmdTopic, stateTopic, secret;
    }

    private Cfg cfg;
    private volatile boolean running;
    private Thread reader;
    private volatile HttpURLConnection conn;
    private ScheduledExecutorService exec;
    private ScheduledFuture<?> appsPublish;
    private final AlexaProtocol.ReplayGuard replay = new AlexaProtocol.ReplayGuard();
    private BroadcastReceiver packageReceiver;

    /** Starts the bridge if a config file exists; safe to call repeatedly and from any process entry point. */
    static void start(Context ctx) {
        Context app = ctx.getApplicationContext();
        if (loadConfig(app) == null) return;
        try {
            app.startForegroundService(new Intent(app, AlexaBridgeService.class));
        } catch (RuntimeException e) {
            // Background start refused (Android 12+); the next launcher or accessibility start retries.
            Log.w(TAG, "start refused: " + e.getClass().getSimpleName());
        }
    }

    private static Cfg loadConfig(Context ctx) {
        File external = ctx.getExternalFilesDir(null);
        List<File> candidates = new ArrayList<>();
        if (external != null) candidates.add(new File(external, CONFIG_FILE));
        candidates.add(new File(CONFIG_FALLBACK));
        for (File file : candidates) {
            try (InputStream in = new FileInputStream(file)) {
                byte[] bytes = new byte[(int) Math.min(file.length(), 16_384L)];
                int n = 0;
                while (n < bytes.length) {
                    int r = in.read(bytes, n, bytes.length - n);
                    if (r < 0) break;
                    n += r;
                }
                Map<String, String> env = AlexaProtocol.parseEnv(new String(bytes, 0, n, StandardCharsets.UTF_8));
                Cfg c = new Cfg();
                c.base = env.get("NTFY_BASE");
                c.cmdTopic = env.get("CMD_TOPIC");
                c.stateTopic = env.get("STATE_TOPIC");
                c.secret = env.get("HMAC_SECRET");
                if (c.base == null || c.cmdTopic == null || c.stateTopic == null || c.secret == null
                        || c.base.isEmpty() || c.cmdTopic.isEmpty() || c.stateTopic.isEmpty() || c.secret.isEmpty()
                        || !c.base.startsWith("http")) continue;
                while (c.base.endsWith("/")) c.base = c.base.substring(0, c.base.length() - 1);
                return c;
            } catch (Exception ignored) {
                // Missing or unreadable (the /data/local/tmp path is usually not visible to apps): try the next.
            }
        }
        return null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Must go foreground within seconds of startForegroundService, even when about to stop.
        startForeground(NOTIFICATION_ID, notification());
        if (running) return START_STICKY;
        cfg = loadConfig(this);
        if (cfg == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleWithFixedDelay(this::publishState, HEARTBEAT_MIN, HEARTBEAT_MIN, TimeUnit.MINUTES);
        exec.execute(this::publishApps);
        registerPackageReceiver();
        reader = new Thread(this::readLoop, "alexa-bridge");
        reader.start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (packageReceiver != null) {
            unregisterReceiver(packageReceiver);
            packageReceiver = null;
        }
        HttpURLConnection c = conn;
        if (c != null) c.disconnect();
        if (reader != null) reader.interrupt();
        if (exec != null) exec.shutdownNow();
        super.onDestroy();
    }

    private Notification notification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.alexa_bridge_name),
                NotificationManager.IMPORTANCE_MIN));
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.alexa_bridge_name))
                .setOngoing(true)
                .build();
    }

    // ---- ntfy stream ----

    private void readLoop() {
        long backoff = BACKOFF_MIN_MS;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        while (running) {
            long started = SystemClock.elapsedRealtime();
            HttpURLConnection c = null;
            try {
                String lastId = prefs.getString(PREF_LAST_ID, null);
                String since = lastId != null ? lastId : "30s";
                c = (HttpURLConnection) new URL(cfg.base + "/" + enc(cfg.cmdTopic) + "/json?since=" + enc(since))
                        .openConnection();
                c.setConnectTimeout(CONNECT_TIMEOUT_MS);
                c.setReadTimeout(READ_TIMEOUT_MS);
                conn = c;
                if (c.getResponseCode() != 200) {
                    // An id the server no longer knows would fail forever: start over from the recent past.
                    prefs.edit().remove(PREF_LAST_ID).apply();
                    throw new java.io.IOException("http " + c.getResponseCode());
                }
                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = in.readLine()) != null) {
                        String id = handleStreamLine(line);
                        if (id != null) prefs.edit().putString(PREF_LAST_ID, id).apply();
                    }
                }
            } catch (Exception e) {
                if (running) Log.w(TAG, "stream: " + e.getClass().getSimpleName());
            } finally {
                if (c != null) c.disconnect();
                conn = null;
            }
            if (!running) break;
            // A stream that stayed up was healthy: the next failure starts the backoff over.
            if (SystemClock.elapsedRealtime() - started > 30_000L) backoff = BACKOFF_MIN_MS;
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                break;
            }
            backoff = Math.min(backoff * 2, BACKOFF_MAX_MS);
        }
    }

    /** Returns the ntfy message id when the line was a message event, else null. */
    private String handleStreamLine(String line) {
        try {
            JSONObject event = new JSONObject(line);
            if (!"message".equals(event.optString("event"))) return null;
            final String envelope = event.optString("message");
            exec.execute(() -> handleEnvelope(envelope));
            return event.optString("id", null);
        } catch (Exception e) {
            return null;
        }
    }

    private static String enc(String s) throws java.io.UnsupportedEncodingException {
        return URLEncoder.encode(s, "UTF-8");
    }

    // ---- commands ----

    private void handleEnvelope(String envelope) {
        String cmd;
        JSONObject args;
        try {
            JSONObject env = new JSONObject(envelope);
            String p = env.getString("p");
            if (!AlexaProtocol.verify(cfg.secret, p, env.optString("s", null))) return;
            JSONObject payload = new JSONObject(p);
            if (payload.optInt("v") != 1) return;
            long now = System.currentTimeMillis() / 1000L;
            if (AlexaProtocol.admit(now, payload.getLong("ts"), payload.optString("id", null), replay)
                    != AlexaProtocol.Verdict.OK) return;
            cmd = payload.getString("cmd");
            args = payload.optJSONObject("args");
            if (args == null) args = new JSONObject();
        } catch (Exception e) {
            return;
        }
        long delayMs = 0;
        try {
            switch (cmd) {
                case "power_off": powerOff(); delayMs = 2_000; break;
                case "power_on": powerOn(); delayMs = 2_000; break;
                case "volume_set": volumeSet(args.getInt("level")); break;
                case "volume_adjust": volumeAdjust(args.getInt("delta")); break;
                case "mute": mute(args.getBoolean("mute")); break;
                case "media": media(args.getString("key")); break;
                case "launch": launch(args.getString("package")); break;
                case "channel_skip": channelSkip(args.getInt("delta")); break;
                case "key": key(args.getInt("keycode")); break;
                case "publish_apps": publishApps(); break;
                default: return;
            }
        } catch (Exception e) {
            Log.w(TAG, cmd + " failed: " + e.getClass().getSimpleName());
        }
        // Power changes take a moment to show in isInteractive().
        if (delayMs > 0) exec.schedule(this::publishState, delayMs, TimeUnit.MILLISECONDS);
        else publishState();
    }

    private AudioManager audio() {
        return (AudioManager) getSystemService(AUDIO_SERVICE);
    }

    private static AccessibilityService accessibility() {
        AccessibilityService s = HomeKeyService.instance;
        return s != null ? s : InputKeyService.instance;
    }

    /**
     * Lock-screen action from a running accessibility service (API 28+). Otherwise KEYCODE_SLEEP through
     * dispatchMediaKeyEvent, which only reaches media sessions and normally needs INJECT_EVENTS to act: best effort.
     */
    private void powerOff() {
        AccessibilityService a11y = accessibility();
        if (a11y != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && a11y.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) return;
        sendKey(KeyEvent.KEYCODE_SLEEP);
    }

    @SuppressWarnings("deprecation")
    private void powerOn() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock lock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "tvhome:alexa");
        lock.setReferenceCounted(false);
        lock.acquire(3_000L);
    }

    private void volumeSet(int level) {
        AudioManager am = audio();
        am.setStreamVolume(AudioManager.STREAM_MUSIC,
                AlexaProtocol.volumeForLevel(level, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)),
                AudioManager.FLAG_SHOW_UI);
    }

    private void volumeAdjust(int delta) {
        AudioManager am = audio();
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int target = am.getStreamVolume(AudioManager.STREAM_MUSIC) + AlexaProtocol.stepsForDelta(delta, max);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(0, Math.min(max, target)), AudioManager.FLAG_SHOW_UI);
    }

    private void mute(boolean mute) {
        audio().adjustStreamVolume(AudioManager.STREAM_MUSIC,
                mute ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI);
    }

    private void media(String name) {
        int code;
        switch (name) {
            case "play": code = KeyEvent.KEYCODE_MEDIA_PLAY; break;
            case "pause": code = KeyEvent.KEYCODE_MEDIA_PAUSE; break;
            case "stop": code = KeyEvent.KEYCODE_MEDIA_STOP; break;
            case "next": code = KeyEvent.KEYCODE_MEDIA_NEXT; break;
            case "previous": code = KeyEvent.KEYCODE_MEDIA_PREVIOUS; break;
            case "rewind": code = KeyEvent.KEYCODE_MEDIA_REWIND; break;
            case "fastforward": code = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD; break;
            case "play_pause": code = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE; break;
            default: return;
        }
        sendKey(code);
    }

    private void channelSkip(int delta) {
        int code = delta > 0 ? KeyEvent.KEYCODE_CHANNEL_UP : KeyEvent.KEYCODE_CHANNEL_DOWN;
        for (int i = 0; i < Math.min(Math.abs(delta), 20); i++) sendKey(code);
    }

    private void key(int keycode) {
        if (keycode > 0 && keycode <= KeyEvent.getMaxKeyCode()) sendKey(keycode);
    }

    private void sendKey(int code) {
        AudioManager am = audio();
        long now = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0));
    }

    /** Tries the accessibility service's context first: it is exempt from background-start limits where this service is not. */
    private void launch(String pkg) {
        PackageManager pm = getPackageManager();
        Intent open = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (open == null) open = pm.getLaunchIntentForPackage(pkg);
        if (open == null) return;
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        AccessibilityService a11y = accessibility();
        if (a11y != null) {
            try {
                a11y.startActivity(open);
                return;
            } catch (RuntimeException ignored) {
            }
        }
        startActivity(open);
    }

    // ---- state ----

    private void publishState() {
        try {
            AudioManager am = audio();
            int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            int volume = Math.round(am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max);
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            JSONObject state = new JSONObject()
                    .put("v", 1).put("type", "state").put("ts", System.currentTimeMillis() / 1000L)
                    .put("power", pm.isInteractive() ? "ON" : "OFF")
                    .put("volume", volume)
                    .put("muted", am.isStreamMute(AudioManager.STREAM_MUSIC));
            post(envelope(state.toString()));
        } catch (Exception e) {
            Log.w(TAG, "state: " + e.getClass().getSimpleName());
        }
    }

    private void publishApps() {
        try {
            PackageManager pm = getPackageManager();
            Map<String, String> labels = new HashMap<>();
            Map<String, Boolean> leanback = new HashMap<>();
            for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
                for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
                    String pkg = ri.activityInfo.packageName;
                    if (labels.containsKey(pkg)) continue;
                    labels.put(pkg, String.valueOf(ri.loadLabel(pm)));
                    leanback.put(pkg, Intent.CATEGORY_LEANBACK_LAUNCHER.equals(category));
                }
            }
            List<String> pkgs = new ArrayList<>(labels.keySet());
            Collections.sort(pkgs, (a, b) -> {
                int byLabel = labels.get(a).compareToIgnoreCase(labels.get(b));
                return byLabel != 0 ? byLabel : a.compareTo(b);
            });
            String message = appsEnvelope(pkgs, labels);
            if (message.getBytes(StandardCharsets.UTF_8).length > MAX_ENVELOPE_BYTES) {
                // Too many apps for one ntfy message: phone-only apps go first, then the end of the list.
                List<String> tv = new ArrayList<>();
                for (String pkg : pkgs) if (leanback.get(pkg)) tv.add(pkg);
                pkgs = tv;
                message = appsEnvelope(pkgs, labels);
                while (pkgs.size() > 1 && message.getBytes(StandardCharsets.UTF_8).length > MAX_ENVELOPE_BYTES) {
                    pkgs = pkgs.subList(0, pkgs.size() - 1);
                    message = appsEnvelope(pkgs, labels);
                }
                Log.w(TAG, "app list trimmed to " + pkgs.size());
            }
            post(message);
        } catch (Exception e) {
            Log.w(TAG, "apps: " + e.getClass().getSimpleName());
        }
    }

    private String appsEnvelope(List<String> pkgs, Map<String, String> labels) throws org.json.JSONException {
        JSONArray apps = new JSONArray();
        for (String pkg : pkgs) apps.put(new JSONObject().put("l", labels.get(pkg)).put("p", pkg));
        return envelope(new JSONObject().put("v", 1).put("type", "apps")
                .put("ts", System.currentTimeMillis() / 1000L).put("apps", apps).toString());
    }

    private String envelope(String payload) throws org.json.JSONException {
        return new JSONObject().put("p", payload).put("s", AlexaProtocol.hmacHex(cfg.secret, payload)).toString();
    }

    private void post(String body) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(cfg.base + "/" + enc(cfg.stateTopic)).openConnection();
            c.setConnectTimeout(CONNECT_TIMEOUT_MS);
            c.setReadTimeout(CONNECT_TIMEOUT_MS);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            c.getResponseCode();
        } catch (Exception e) {
            Log.w(TAG, "publish: " + e.getClass().getSimpleName());
        } finally {
            if (c != null) c.disconnect();
        }
    }

    // ---- app list changes ----

    private void registerPackageReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        packageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // An update sends REMOVED, ADDED and REPLACED together: publish once, after it settles.
                if (exec == null || exec.isShutdown()) return;
                if (appsPublish != null) appsPublish.cancel(false);
                appsPublish = exec.schedule(AlexaBridgeService.this::publishApps, 3, TimeUnit.SECONDS);
            }
        };
        registerReceiver(packageReceiver, filter);
    }
}
