package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Wii menu's channels without the Home activity: the apps in Wii order, the owner's saved layout, and a still
 * first page bound into a WiiViews.Root. WiiHome builds its live strip from these; WiiDream shows the still one.
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

    /** Reloads the installed TV apps (banners and all). */
    static void reload(Context ctx, AppAdapter apps) {
        List<ResolveInfo> leanback = ctx.getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        apps.reload(Collections.<Cards.Card>emptyList(), leanback);
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

    /**
     * The first page as Home shows it, but still: the tiles of the first page and the sliver of the next one, the
     * arrow to the next page when there is one, nothing focusable, so there is no cursor and no glow.
     */
    static void still(WiiViews.Root root, AppAdapter apps, List<String> layout) {
        int rows = WiiViews.Strip.ROWS, cols = WiiViews.Strip.VISIBLE_COLS;
        int slots = WiiPages.slots(layout.size());
        root.strip.setCount(Math.min(slots, (cols + 1) * rows));
        for (int i = 0; i < root.strip.tiles.size(); i++) {
            WiiViews.Tile tile = root.strip.tiles.get(i);
            int at = apps.positionOf(i < layout.size() ? layout.get(i) : null);
            if (at < 0) tile.bindEmpty();
            else tile.bindApp(apps.entryAt(at));
            tile.setFocusable(false);
            tile.setClickable(false);
        }
        root.strip.setTranslationX(0f);
        root.prev.setAlpha(0f);
        root.next.setAlpha(slots / rows > cols ? 1f : 0f);
        for (View v : new View[] {root.os, root.sd, root.inputs}) {
            v.setFocusable(false);
            v.setClickable(false);
        }
        root.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    }
}
