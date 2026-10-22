package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.Calendar;

/**
 * Screensaver "Wii Menu", as the Wii's own Screen Burn-in Reduction: the Wii had no clock screensaver, it simply
 * dimmed the menu it was showing. So this is the Wii menu itself (the owner's tiles in their order, first page, the
 * tray with its buttons, the live clock and date, dark from 16:00 to 07:00) still, with no cursor or glow, under a
 * gray veil that fades in and breathes very slightly (WiiDim). The whole menu moves a few pixels every few minutes
 * and nothing else moves. Dimmed, silent, not interactive: any key wakes the TV.
 */
public class WiiDream extends DreamService {

    private WiiViews.Root root;
    private WiiViews.Veil veil;
    private GradientDrawable under;
    private float u;
    private boolean registered;

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            update();
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        int screenH = getResources().getDisplayMetrics().heightPixels;
        u = screenH / 1080f;
        root = new WiiViews.Root(this, screenH, getResources().getFont(R.font.outfit_medium));
        root.setDark(WiiTime.isNightNow());
        AppAdapter apps = WiiMenu.apps(this, null, Config.load(this));
        WiiMenu.reload(this, apps);
        WiiMenu.still(root, apps, WiiMenu.layout(this, apps, false));
        FrameLayout frame = new FrameLayout(this);
        // Shows in the few pixels the shifted menu uncovers at the screen edge.
        under = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0, 0});
        frame.setBackground(under);
        frame.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        veil = new WiiViews.Veil(this);
        frame.addView(veil, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
        veil.start(root.isDark());
        update();
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
        if (veil != null) veil.stop();
        if (registered) {
            unregisterReceiver(timeReceiver);
            registered = false;
        }
    }

    /** On start and every minute: the time, the date, dark or light, and the menu's pixel-shift spot. */
    private void update() {
        Calendar now = Calendar.getInstance();
        root.clock.setTime(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), DateFormat.is24HourFormat(this));
        root.date.setText(WiiTime.date(now.get(Calendar.DAY_OF_WEEK), now.get(Calendar.DAY_OF_MONTH), now.get(Calendar.MONTH) + 1));
        boolean dark = WiiTime.isNightNow();
        if (dark != root.isDark()) {
            root.setDark(dark);
            veil.setNight(dark);
        }
        WiiViews.Palette p = root.look.p;
        under.setColors(new int[] {p.backTop, p.backBottom});
        int[] shift = WiiDim.shift(veil.minutes(), Math.max(1, Math.round(WiiDim.SHIFT_PX * u)));
        root.setTranslationX(shift[0]);
        root.setTranslationY(shift[1]);
    }
}
