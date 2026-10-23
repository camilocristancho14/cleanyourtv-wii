package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.io.File;

/**
 * "Ver ahora": the screensaver the owner has selected, fullscreen, right now.
 * The same player as WiiDream. Any key (including BACK) or a touch leaves. The on/off switch is ignored
 * here so a clip can be checked before the dream is allowed to start. Does not change system timeouts.
 */
public class ScreensaverPreviewActivity extends Activity {

    private ScreensaverPlayer player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        player = new ScreensaverPlayer(this);
        player.setLogTag("Screensaver");
        setContentView(player.view());
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        // Preview the selection even when the dream switch is off.
        File video = ScreensaverFiles.resolve(getExternalFilesDir(null), ScreensaverPrefs.choice(this), true);
        player.setVideo(video);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) player.start();
    }

    @Override
    protected void onPause() {
        if (player != null) player.stop();
        super.onPause();
        finish();   // never left behind in the back stack
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // The key that leaves the preview should not also act on the picker, so it is consumed here.
        if (event.getAction() == KeyEvent.ACTION_UP) finish();
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) finish();
        return true;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
