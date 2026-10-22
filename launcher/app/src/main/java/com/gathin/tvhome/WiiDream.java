package com.gathin.tvhome;

import android.graphics.Color;
import android.media.MediaPlayer;
import android.service.dreams.DreamService;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.File;

/**
 * Screensaver "Video" (same component as the old Wii-menu dream): a muted, looping fullscreen video from
 * getExternalFilesDir(null)/screensaver.mp4, the same external-files pattern as WiiMusic's music.mp3.
 * SurfaceView (not a TextureView) so the TV's decoder can composite the frame directly. Scaled to fill the
 * screen, cropping rather than letterboxing. Any key or touch exits because the dream is not interactive.
 * No file, or a file that will not play: a plain black screen. The player is released when the dream stops.
 */
public class WiiDream extends DreamService {

    private static final String FILE_NAME = "screensaver.mp4";

    private FrameLayout frame;
    private SurfaceView surfaceView;
    private MediaPlayer player;
    private boolean dreaming;
    private boolean surfaceReady;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key or touch wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(true);   // a video, not the old dimmed menu
        frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        File video = videoFile();
        if (video != null) {
            surfaceView = new SurfaceView(this);
            surfaceView.setFocusable(false);
            surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    surfaceReady = true;
                    if (dreaming) startPlayback();
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
        } else {
            Log.i("WiiDream", "WiiDream: screensaver.mp4 missing, black screen");
        }
        setContentView(frame);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        dreaming = true;
        startPlayback();
    }

    @Override
    public void onDreamingStopped() {
        dreaming = false;
        releasePlayer();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        dreaming = false;
        releasePlayer();
        super.onDetachedFromWindow();
    }

    /** Starts the loop as soon as the dream and the surface are both up. Missing file: leave the black frame. */
    private void startPlayback() {
        if (!dreaming || player != null || !surfaceReady || surfaceView == null) return;
        File file = videoFile();
        if (file == null) return;
        SurfaceHolder holder = surfaceView.getHolder();
        if (holder.getSurface() == null || !holder.getSurface().isValid()) return;
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDisplay(holder);
            cropToScreen(mp);
            mp.setDataSource(file.getAbsolutePath());
            mp.setLooping(true);
            mp.setVolume(0f, 0f);
            mp.setOnPreparedListener(p -> {
                if (player != p || !dreaming) return;
                p.setLooping(true);
                p.setVolume(0f, 0f);
                cropToScreen(p);
                try {
                    p.start();
                    p.setVolume(0f, 0f);
                    Log.i("WiiDream", "WiiDream: screensaver video loaded: screensaver.mp4");
                } catch (Throwable t) {
                    Log.w("WiiDream", "WiiDream: screensaver video failed", t);
                    releasePlayer();
                }
            });
            mp.setOnErrorListener((p, what, extra) -> {
                Log.w("WiiDream", "WiiDream: screensaver video failed (" + what + "," + extra + ")");
                releasePlayer();
                return true;
            });
            player = mp;
            mp.prepareAsync();
        } catch (Throwable t) {
            Log.w("WiiDream", "WiiDream: screensaver video failed", t);
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

    /** getExternalFilesDir(null)/screensaver.mp4 (pushed with adb), if there and non-empty; otherwise null. */
    private File videoFile() {
        try {
            File dir = getExternalFilesDir(null);
            if (dir == null) return null;
            File f = new File(dir, FILE_NAME);
            if (!f.isFile() || f.length() <= 0) return null;
            return f;
        } catch (Throwable t) {
            return null;
        }
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
}
