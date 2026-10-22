package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;

/**
 * The Wii Home background music (res/raw/wii_music.ogg, an original loop made by tools/make_wii_music.py). Soft,
 * looping, faded in and out by WiiMusicPolicy; it never asks for audio focus, so it can't pause or duck another
 * app, and it only sounds while Home is in front anyway. Lives from onStart to onStop. Never throws.
 */
final class WiiMusic {
    private static final long STEP_MS = 40;
    /** A beat of silence before fading in: no blip when Home only flashes by, and room for the startup chime. */
    private static final long START_DELAY_MS = 500;

    private final Context ctx;
    private final boolean setting;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private boolean resumed, focused, screenOn = true, dreaming, registered, failed;
    private float volume, target;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(a)) screenOn = false;
            else if (Intent.ACTION_SCREEN_ON.equals(a)) screenOn = true;
            else if (Intent.ACTION_DREAMING_STARTED.equals(a)) dreaming = true;
            else if (Intent.ACTION_DREAMING_STOPPED.equals(a)) dreaming = false;
            update();
        }
    };

    private final Runnable step = this::step;
    private final Runnable begin = this::begin;

    WiiMusic(Context ctx, boolean setting) {
        this.ctx = ctx;
        this.setting = setting;
    }

    void onStart() {
        try {
            PowerManager pm = ctx.getSystemService(PowerManager.class);
            screenOn = pm == null || pm.isInteractive();
            dreaming = false;
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_SCREEN_OFF);
            f.addAction(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_DREAMING_STARTED);
            f.addAction(Intent.ACTION_DREAMING_STOPPED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                ctx.registerReceiver(screenReceiver, f);
            }
            registered = true;
        } catch (Throwable ignored) {
        }
    }

    void onResume() {
        resumed = true;
        update();
    }

    void onPause() {
        resumed = false;
        update();
    }

    void onWindowFocusChanged(boolean hasFocus) {
        focused = hasFocus;
        update();
    }

    /** Stops at once (Home is no longer seen, so no fade is heard) and lets go of the player. */
    void onStop() {
        resumed = false;
        handler.removeCallbacks(step);
        handler.removeCallbacks(begin);
        if (registered) {
            try {
                ctx.unregisterReceiver(screenReceiver);
            } catch (Throwable ignored) {
            }
            registered = false;
        }
        release();
    }

    private void update() {
        boolean play = WiiMusicPolicy.shouldPlay(
                WiiMusicPolicy.enabled(setting, WiiTime.property(WiiMusicPolicy.MUSIC_PROPERTY)),
                resumed, focused, screenOn, dreaming);
        float want = play ? WiiMusicPolicy.VOLUME : 0f;
        if (want == target) return;
        target = want;
        handler.removeCallbacks(begin);
        handler.removeCallbacks(step);
        if (play && (player == null || !player.isPlaying())) {
            handler.postDelayed(begin, START_DELAY_MS);
        } else {
            handler.post(step);
        }
    }

    /** Starts (or resumes) the paused loop silent, then fades it up. */
    private void begin() {
        if (target <= 0f || !ensurePlayer()) return;
        try {
            volume = 0f;
            player.setVolume(0f, 0f);
            player.start();
        } catch (Throwable t) {
            release();
            return;
        }
        handler.post(step);
    }

    private void step() {
        if (player == null) return;
        volume = WiiMusicPolicy.next(volume, target, STEP_MS);
        try {
            player.setVolume(volume, volume);
            if (volume != target) {
                handler.postDelayed(step, STEP_MS);
            } else if (target == 0f && player.isPlaying()) {
                player.pause();
            }
        } catch (Throwable t) {
            release();
        }
    }

    private boolean ensurePlayer() {
        if (player != null) return true;
        if (failed) return false;
        MediaPlayer ext = openExternal();
        if (ext != null) {
            player = ext;
            return true;
        }
        MediaPlayer mp = new MediaPlayer();
        try (AssetFileDescriptor fd = ctx.getResources().openRawResourceFd(R.raw.wii_music)) {
            setUp(mp);
            mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            mp.prepare();
            player = mp;
            return true;
        } catch (Throwable t) {
            // A TV that can't play it once won't the next time either: stay quiet for this Home session.
            failed = true;
            mp.release();
            return false;
        }
    }

    private void setUp(MediaPlayer mp) {
        mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        mp.setLooping(true);
        mp.setOnErrorListener((p, what, extra) -> {
            release();
            return true;
        });
    }

    /** getExternalFilesDir(null)/music.mp3 (pushed with adb), if there and playable; otherwise null. */
    private MediaPlayer openExternal() {
        MediaPlayer mp = null;
        try {
            File dir = ctx.getExternalFilesDir(null);
            if (dir == null) return null;
            File f = new File(dir, "music.mp3");
            if (!f.isFile() || f.length() <= 0) return null;
            mp = new MediaPlayer();
            setUp(mp);
            mp.setDataSource(f.getAbsolutePath());
            mp.prepare();
            Log.i("WiiMusic", "WiiMusic: external music loaded: music.mp3");
            return mp;
        } catch (Throwable t) {
            Log.w("WiiMusic", "WiiMusic: external music failed, using bundled loop", t);
            if (mp != null) {
                try { mp.release(); } catch (Throwable ignored) { }
            }
            return null;
        }
    }

    private void release() {
        handler.removeCallbacks(step);
        MediaPlayer p = player;
        player = null;
        volume = 0f;
        target = 0f;
        if (p != null) {
            try {
                p.release();
            } catch (Throwable ignored) {
            }
        }
    }
}
