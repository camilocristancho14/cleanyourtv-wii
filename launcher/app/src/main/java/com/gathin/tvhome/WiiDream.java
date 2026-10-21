package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Random;

/**
 * Screensaver "Wii Menu": the Wii menu's lined backdrop (charcoal from 16:00 to 07:00, see WiiTime) with its
 * seven-segment clock and Spanish date floating slowly around the screen, so nothing burns in. Dimmed, not
 * interactive (any key wakes the TV). Only the small clock group moves, on its own hardware layer, and it rests
 * most of the time: cheap on a 1 GB TV.
 */
public class WiiDream extends DreamService {

    /** A glide every 20 s, lasting 12 s, never more than a quarter of the screen away. */
    private static final long DRIFT_EVERY_MS = 20_000L, DRIFT_MS = 12_000L;
    private static final float MAX_STEP = 0.25f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final WiiViews.Look look = new WiiViews.Look();
    private WiiViews.Backdrop backdrop;
    private WiiViews.Clock clock;
    private TextView date;
    private LinearLayout group;
    private boolean registered;

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            update();
        }
    };

    private final Runnable drift = new Runnable() {
        @Override
        public void run() {
            glide();
            handler.postDelayed(this, DRIFT_EVERY_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        float u = getResources().getDisplayMetrics().heightPixels / 1080f;
        Typeface font = getResources().getFont(R.font.outfit_medium);
        look.p = WiiTime.isNightNow() ? WiiViews.Palette.DARK : WiiViews.Palette.LIGHT;
        FrameLayout frame = new FrameLayout(this);
        backdrop = new WiiViews.Backdrop(this, u, look);
        frame.addView(backdrop, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setGravity(Gravity.CENTER_HORIZONTAL);
        group.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        clock = new WiiViews.Clock(this, font, look);
        group.addView(clock, new LinearLayout.LayoutParams(Math.round(640 * u), Math.round(190 * u)));
        date = WiiViews.Root.newDate(this, font, look);
        date.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 92 * u);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = Math.round(18 * u);
        group.addView(date, dlp);
        frame.addView(group, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(frame);
        update();
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(timeReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(timeReceiver, f);
        registered = true;
        update();
        group.setAlpha(0f);
        group.post(() -> {
            View parent = (View) group.getParent();
            group.setTranslationX(Math.max(0, parent.getWidth() - group.getWidth()) * (0.15f + 0.7f * random.nextFloat()));
            group.setTranslationY(Math.max(0, parent.getHeight() - group.getHeight()) * (0.15f + 0.7f * random.nextFloat()));
            group.animate().alpha(1f).setDuration(1200).start();
        });
        handler.removeCallbacks(drift);
        handler.postDelayed(drift, DRIFT_EVERY_MS);
    }

    @Override
    public void onDreamingStopped() {
        stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private void stop() {
        handler.removeCallbacks(drift);
        if (group != null) group.animate().cancel();
        if (registered) {
            unregisterReceiver(timeReceiver);
            registered = false;
        }
    }

    /** A slow glide to a nearby spot that keeps the clock and date on screen. */
    private void glide() {
        View parent = (View) group.getParent();
        float maxX = Math.max(0, parent.getWidth() - group.getWidth());
        float maxY = Math.max(0, parent.getHeight() - group.getHeight());
        float stepX = parent.getWidth() * MAX_STEP, stepY = parent.getHeight() * MAX_STEP;
        float x = clamp(group.getTranslationX() + (random.nextFloat() * 2 - 1) * stepX, maxX);
        float y = clamp(group.getTranslationY() + (random.nextFloat() * 2 - 1) * stepY, maxY);
        group.animate().translationX(x).translationY(y).setDuration(DRIFT_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    private static float clamp(float v, float max) {
        return Math.max(0, Math.min(max, v));
    }

    /** The time, the date, and dark or light: on start and on every minute tick. */
    private void update() {
        Calendar now = Calendar.getInstance();
        clock.setTime(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), DateFormat.is24HourFormat(this));
        date.setText(WiiTime.date(now.get(Calendar.DAY_OF_WEEK), now.get(Calendar.DAY_OF_MONTH), now.get(Calendar.MONTH) + 1));
        WiiViews.Palette p = WiiTime.isNightNow() ? WiiViews.Palette.DARK : WiiViews.Palette.LIGHT;
        if (p != look.p) {
            look.p = p;
            date.setTextColor(p.text);
            backdrop.invalidate();
            clock.invalidate();
        }
    }
}
