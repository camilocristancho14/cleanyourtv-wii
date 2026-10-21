package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;

/** The Wii menu's own small sounds (res/raw, made by tools/make_wii_sounds.py). Never throws: no sound beats a crash. */
final class WiiSounds {
    private static final float VOLUME = 0.45f;
    private static final long TICK_GAP_MS = 60, SWOOSH_GAP_MS = 120;
    private static boolean chimed;

    private SoundPool pool;
    private int startup, tick, swoosh, confirm;
    private final boolean[] ready = new boolean[4];
    private boolean startupWanted;
    private long lastTick, lastSwoosh;

    WiiSounds(Context ctx) {
        try {
            pool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            pool.setOnLoadCompleteListener((p, id, status) -> {
                if (status != 0) return;
                synchronized (this) {
                    if (id == startup) ready[0] = true;
                    else if (id == tick) ready[1] = true;
                    else if (id == swoosh) ready[2] = true;
                    else if (id == confirm) ready[3] = true;
                    if (id == startup && startupWanted) {
                        startupWanted = false;
                        play(id);
                    }
                }
            });
            startup = pool.load(ctx, R.raw.wii_startup, 1);
            tick = pool.load(ctx, R.raw.wii_tick, 1);
            swoosh = pool.load(ctx, R.raw.wii_swoosh, 1);
            confirm = pool.load(ctx, R.raw.wii_confirm, 1);
        } catch (Throwable t) {
            pool = null;
        }
    }

    /** The chime, once per process: when Home first shows (it waits for the sound to load if it has to). */
    synchronized void startup() {
        if (chimed || pool == null) return;
        chimed = true;
        if (ready[0]) play(startup);
        else startupWanted = true;
    }

    synchronized void tick() {
        long now = SystemClock.uptimeMillis();
        if (!ready[1] || now - lastTick < TICK_GAP_MS) return;
        lastTick = now;
        play(tick);
    }

    synchronized void swoosh() {
        long now = SystemClock.uptimeMillis();
        if (!ready[2] || now - lastSwoosh < SWOOSH_GAP_MS) return;
        lastSwoosh = now;
        play(swoosh);
    }

    synchronized void confirm() {
        if (ready[3]) play(confirm);
    }

    void release() {
        SoundPool p = pool;
        pool = null;
        if (p != null) {
            try {
                p.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private void play(int id) {
        try {
            if (pool != null) pool.play(id, VOLUME, VOLUME, 1, 0, 1f);
        } catch (Throwable ignored) {
        }
    }
}
