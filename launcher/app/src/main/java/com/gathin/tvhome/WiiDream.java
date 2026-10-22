package com.gathin.tvhome;

import android.service.dreams.DreamService;
import android.util.Log;

import java.io.File;

/**
 * Screensaver "Video": a muted, looping fullscreen clip chosen in the Protector de pantalla channel.
 * The file comes from ScreensaverPrefs (default: the first .mp4 in screensavers/, then the older
 * screensaver.mp4, otherwise black). The picture is ScreensaverPlayer, the same view the "Ver ahora"
 * preview uses. Any key or touch exits because the dream is not interactive. When the channel's switch
 * is off, the dream stays black and finishes at once so it does not hold the screen. System timeouts
 * are left as the TV set them. The player is released when the dream stops.
 */
public class WiiDream extends DreamService {

    private ScreensaverPlayer player;
    /** Switch off: show black and leave as soon as the dream actually starts. */
    private boolean off;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key or touch wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(true);   // a video, not the old dimmed menu
        player = new ScreensaverPlayer(this);
        player.setLogTag("WiiDream");
        setContentView(player.view());
        off = !ScreensaverPrefs.isEnabled(this);
        if (off) {
            Log.i("WiiDream", "WiiDream: screensaver off, finishing");
            return;
        }
        File video = ScreensaverFiles.resolve(getExternalFilesDir(null), ScreensaverPrefs.choice(this), true);
        if (video == null) {
            Log.i("WiiDream", "WiiDream: no screensaver video, black screen");
            return;
        }
        player.setVideo(video);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (off) {
            finish();
            return;
        }
        if (player != null) player.start();
    }

    @Override
    public void onDreamingStopped() {
        if (player != null) player.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (player != null) player.stop();
        super.onDetachedFromWindow();
    }
}
