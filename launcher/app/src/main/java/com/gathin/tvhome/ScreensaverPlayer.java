package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.File;

/**
 * Muted, looping fullscreen video on a SurfaceView, scaled to fill the screen (crop, not letterbox).
 * Shared by WiiDream and the "Ver ahora" preview. A null file leaves the black background.
 * The host calls {@link #start()} when it is on screen and {@link #stop()} when it leaves.
 */
final class ScreensaverPlayer {
    private final Context ctx;
    private final FrameLayout frame;
    private SurfaceView surfaceView;
    private MediaPlayer player;
    private File file;
    private boolean playing;
    private boolean surfaceReady;
    private String tag = "Screensaver";

    ScreensaverPlayer(Context ctx) {
        this.ctx = ctx;
        frame = new FrameLayout(ctx);
        frame.setBackgroundColor(Color.BLACK);
    }

    View view() {
        return frame;
    }

    void setLogTag(String tag) {
        if (tag != null) this.tag = tag;
    }

    /** Plays this file. Null, or a file that is not there, clears the picture and stays black. */
    void setVideo(File video) {
        File next = video != null && video.isFile() && video.length() > 0 ? video : null;
        if (same(file, next)) return;
        file = next;
        releasePlayer();
        if (file == null) {
            removeSurface();
            return;
        }
        ensureSurface();
        if (playing && surfaceReady) startPlayback();
    }

    void start() {
        playing = true;
        startPlayback();
    }

    void stop() {
        playing = false;
        releasePlayer();
    }

    private void ensureSurface() {
        if (surfaceView != null) return;
        surfaceView = new SurfaceView(ctx);
        surfaceView.setFocusable(false);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                surfaceReady = true;
                if (playing) startPlayback();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                surfaceReady = false;
                releasePlayer();
            }
        });
        frame.addView(surfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void removeSurface() {
        surfaceReady = false;
        if (surfaceView == null) return;
        frame.removeView(surfaceView);
        surfaceView = null;
    }

    /** Starts the loop once the host is playing and the surface is up. No file: leave the black frame. */
    private void startPlayback() {
        if (!playing || player != null || !surfaceReady || surfaceView == null || file == null) return;
        SurfaceHolder holder = surfaceView.getHolder();
        if (holder.getSurface() == null || !holder.getSurface().isValid()) return;
        final File playingFile = file;
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDisplay(holder);
            cropToScreen(mp);
            mp.setDataSource(playingFile.getAbsolutePath());
            mp.setLooping(true);
            mp.setVolume(0f, 0f);
            mp.setOnPreparedListener(p -> {
                if (player != p || !playing) return;
                p.setLooping(true);
                p.setVolume(0f, 0f);
                cropToScreen(p);
                try {
                    p.start();
                    p.setVolume(0f, 0f);
                    Log.i(tag, tag + ": screensaver video loaded: " + playingFile.getName());
                } catch (Throwable t) {
                    Log.w(tag, tag + ": screensaver video failed", t);
                    releasePlayer();
                }
            });
            mp.setOnErrorListener((p, what, extra) -> {
                Log.w(tag, tag + ": screensaver video failed (" + what + "," + extra + ")");
                releasePlayer();
                return true;
            });
            player = mp;
            mp.prepareAsync();
        } catch (Throwable t) {
            Log.w(tag, tag + ": screensaver video failed", t);
            try {
                mp.release();
            } catch (Throwable ignored) { }
            if (player == mp) player = null;
        }
    }

    /** Fill the screen and keep the picture's shape; a device that cannot crop still stretches to the surface. */
    private static void cropToScreen(MediaPlayer mp) {
        try {
            mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
        } catch (Throwable ignored) { }
    }

    private void releasePlayer() {
        MediaPlayer p = player;
        player = null;
        if (p == null) return;
        try { p.setOnPreparedListener(null); } catch (Throwable ignored) { }
        try { p.setOnErrorListener(null); } catch (Throwable ignored) { }
        try { p.setOnCompletionListener(null); } catch (Throwable ignored) { }
        try { p.setDisplay(null); } catch (Throwable ignored) { }
        try { p.release(); } catch (Throwable ignored) { }
    }

    private static boolean same(File a, File b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.getAbsolutePath().equals(b.getAbsolutePath());
    }
}
