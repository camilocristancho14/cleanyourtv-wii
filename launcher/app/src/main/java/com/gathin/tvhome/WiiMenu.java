package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Wii menu's channels without the Home activity: the apps in Wii order and the owner's saved layout.
 * WiiHome builds its live strip from these.
 */
final class WiiMenu {
    private WiiMenu() { }

    /** The tile source of the Wii menu: grouped by category, no tiles for the apps the side buttons open. */
    static AppAdapter apps(Context ctx, AppAdapter.TileListener listener, Config cfg) {
        AppAdapter apps = new AppAdapter(ctx, listener, 0, cfg);
        apps.setGroupByCategory(true);
        apps.hidePackages(WiiOrder.NO_TILE);
        return apps;
    }

    /** Reloads the installed TV apps (banners and all), then pins the Protector de pantalla channel first. */
    static void reload(Context ctx, AppAdapter apps) {
        List<ResolveInfo> leanback = ctx.getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        apps.reload(Collections.<Cards.Card>emptyList(), leanback);
        apps.pinFirst(new AppAdapter.Entry(ctx.getString(R.string.screensaver_channel),
                new ComponentName(ctx, ScreensaverActivity.class), null,
                ctx.getDrawable(R.drawable.ic_screensaver)));
    }

    /** The slots as the owner arranged them (WiiLayout), new apps added, removed ones gone; saved back if it changed. */
    static List<String> layout(Context ctx, AppAdapter apps, boolean save) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < apps.appCount(); i++) keys.add(apps.keyAt(i));
        List<String> saved = WiiLayout.load(ctx);
        List<String> layout = WiiLayout.arrange(keys, saved, key -> installed(ctx, key));
        if (save && saved != null && !layout.equals(saved)) WiiLayout.save(ctx, layout);
        return layout;
    }

    static boolean installed(Context ctx, String key) {
        ComponentName cn = ComponentName.unflattenFromString(key);
        if (cn == null) return false;
        try {
            ctx.getPackageManager().getPackageInfo(cn.getPackageName(), 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
