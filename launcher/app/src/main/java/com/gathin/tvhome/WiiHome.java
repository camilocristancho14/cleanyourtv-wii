package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Nintendo Wii menu (config wii_style): the channels in pages of 4 columns by 3 rows (12 apps) that slide
 * sideways one page at a time, a curved bar with the clock, the wiiOS (Settings), SD (file manager) and envelope (Entradas) buttons.
 * It lives inside MainActivity, so the launcher is still the one HOME activity. D-pad navigation is explicit,
 * not left to focus search.
 */
final class WiiHome {
    static final int ROWS = WiiViews.Strip.ROWS, VISIBLE_COLS = WiiViews.Strip.VISIBLE_COLS;
    private static final int SCROLL_MS = 300, HAND_MS = 150;
    private static final String FILE_MANAGER = "com.alphainventor.filemanager";
    private static final String HDMI_SERVICE = "com.mediatek.tis/.HdmiInputService/";
    private static final String[] DAYS = {"Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb"};

    private final MainActivity act;
    private final AppAdapter apps;
    private final FrameLayout frame;
    private final WiiViews.Root root;
    private final WiiViews.Strip strip;
    private final ImageView hand;
    private final float u;
    private FrameLayout dialog;
    private WiiSounds sounds;
    private boolean started, dirty = true;
    /** First column of the page the strip is scrolled to (or heading to). */
    private int scrollCol;
    private String lastKey;
    private static final int RELOAD_DELAY_MS = 250;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable reloadTask = this::reload;
    /** The committed layout (tile key per slot, null = empty slot) and what the strip shows now (the same, or a move preview). */
    private List<String> layout = new ArrayList<>(), shown = layout;
    /** The tile picked up for rearranging: its key, where it came from and the slot it hovers over. */
    private String heldKey;
    private int heldOrigin, heldAt;
    /** The key-up that ends the long press which picked the tile up must not drop it. */
    private boolean ignoreUp, pendingReload;

    private final IntentFilter packageFilter = new IntentFilter();
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
            // An install or update sends several broadcasts in a row: one reload for the lot.
            handler.removeCallbacks(reloadTask);
            handler.postDelayed(reloadTask, RELOAD_DELAY_MS);
        }
    };
    private final IntentFilter timeFilter = new IntentFilter();
    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClock();
        }
    };

    WiiHome(MainActivity act, Config cfg) {
        this.act = act;
        int screenH = act.getResources().getDisplayMetrics().heightPixels;
        this.u = screenH / 1080f;
        this.apps = new AppAdapter(act, act, 0, cfg);
        apps.setGroupByCategory(true);
        apps.hidePackages(WiiOrder.NO_TILE);
        Typeface font = act.getResources().getFont(R.font.outfit_medium);
        frame = new FrameLayout(act);
        root = new WiiViews.Root(act, screenH, font);
        strip = root.strip;
        frame.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // The cursor is the last child of the frame: nothing the strip scales or lifts can draw over it.
        hand = WiiViews.newHand(act);
        float hh = screenH * 0.115f;
        frame.addView(hand, new FrameLayout.LayoutParams(Math.round(hh * 40f / 44f), Math.round(hh)));
        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        packageFilter.addDataScheme("package");
        timeFilter.addAction(Intent.ACTION_TIME_TICK);
        timeFilter.addAction(Intent.ACTION_TIME_CHANGED);
        timeFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        wire();
        bindStrip(layout);
        frame.getViewTreeObserver().addOnGlobalFocusChangeListener(this::onFocusChanged);
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> placeHand(frame.findFocus(), false, HAND_MS));
    }

    View view() {
        return frame;
    }

    // ---- lifecycle -------------------------------------------------------------------------------

    void onStart() {
        started = true;
        sounds = new WiiSounds(act);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            act.registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED);
            act.registerReceiver(timeReceiver, timeFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            act.registerReceiver(packageReceiver, packageFilter);
            act.registerReceiver(timeReceiver, timeFilter);
        }
        updateClock();
        if (dirty) reload();
    }

    void onResume() {
        updateClock();
        if (dialog == null && frame.findFocus() == null) focusSlot(0);
        if (sounds != null) sounds.startup();
    }

    void onPause() {
        dropHeld();
        closeDialog(false);
    }

    void onStop() {
        dropHeld();
        handler.removeCallbacks(reloadTask);
        started = false;
        dirty = true;
        act.unregisterReceiver(packageReceiver);
        act.unregisterReceiver(timeReceiver);
        if (sounds != null) sounds.release();
        sounds = null;
    }

    void onHome() {
        dropHeld();
        closeDialog(false);
        lastKey = null;
        bindStrip(layout);
        showColumn(0, true);
        focusSlot(0);
    }

    void onBack() {
        if (holding()) {
            dropHeld();
            return;
        }
        closeDialog(true);
    }

    // ---- apps ------------------------------------------------------------------------------------

    private List<String> defaultKeys() {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < apps.appCount(); i++) keys.add(apps.keyAt(i));
        return keys;
    }

    private boolean installed(String key) {
        ComponentName cn = ComponentName.unflattenFromString(key);
        if (cn == null) return false;
        try {
            act.getPackageManager().getPackageInfo(cn.getPackageName(), 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void reload() {
        if (holding()) {
            pendingReload = true;
            return;
        }
        View focused = frame.findFocus();
        int focusedSlot = strip.tiles.indexOf(focused);
        if (focusedSlot >= 0 && focusedSlot < layout.size() && layout.get(focusedSlot) != null) lastKey = layout.get(focusedSlot);
        final List<ResolveInfo> leanback = act.getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        apps.reload(Collections.<Cards.Card>emptyList(), leanback);
        dirty = false;
        List<String> saved = WiiLayout.load(act);
        layout = WiiLayout.arrange(defaultKeys(), saved, this::installed);
        if (saved != null && !layout.equals(saved)) WiiLayout.save(act, layout);
        int at = lastKey == null ? -1 : layout.indexOf(lastKey);
        bindStrip(layout);
        if (at >= 0) showColumn(at / ROWS, false);
        if (dialog == null && (focused != null || at >= 0 || frame.findFocus() == null)) {
            final int slot = at >= 0 ? at : 0;
            root.post(() -> focusSlot(slot));
        }
    }

    private boolean holding() {
        return heldKey != null;
    }

    /** One tile per slot; the last page is padded with empty slots to a full 12. */
    private void bindStrip(List<String> slots) {
        shown = slots;
        strip.setCount(WiiPages.slots(slots.size() + (holding() ? 1 : 0)));
        for (int i = 0; i < strip.tiles.size(); i++) bindSlot(i);
        showColumn(scrollCol, false);
        strip.requestLayout();
    }

    private void bindSlot(final int slot) {
        WiiViews.Tile tile = strip.tiles.get(slot);
        final String key = slot < shown.size() ? shown.get(slot) : null;
        int at = apps.positionOf(key);
        if (at < 0) {
            tile.bindEmpty();
            tile.setOnKeyListener(null);
            return;
        }
        final AppAdapter.Entry e = apps.entryAt(at);
        tile.bindApp(e);
        tile.setOnClickListener(v -> {
            lastKey = key;
            if (sounds != null) sounds.confirm();
            apps.launch(e);
        });
        tile.setOnLongClickListener(v -> pickUp(slot));
        tile.setOnKeyListener((v, keyCode, event) -> {
            if (holding()) return onHeldKey(keyCode, event);
            if (!isDpad(keyCode)) return false;
            return event.getAction() != KeyEvent.ACTION_DOWN || onTileKey(slot, keyCode);
        });
    }

    // ---- rearranging -----------------------------------------------------------------------------

    private boolean pickUp(int slot) {
        if (holding() || dialog != null || slot >= layout.size() || layout.get(slot) == null) return true;
        heldKey = layout.get(slot);
        heldOrigin = heldAt = slot;
        ignoreUp = true;
        hand.setImageResource(R.drawable.ic_wii_hand_grab);
        strip.setCount(Math.max(strip.tiles.size(), WiiPages.slots(layout.size() + 1)));
        showColumn(scrollCol, false);
        strip.tiles.get(slot).setHeld(true);
        if (sounds != null) sounds.confirm();
        placeHand(frame.findFocus(), true, HAND_MS);
        return true;
    }

    private boolean onHeldKey(int keyCode, KeyEvent event) {
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (isDpad(keyCode)) {
            if (down) moveHeld(keyCode);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (!down) {
                if (ignoreUp) ignoreUp = false;
                else dropHeld();
            }
            return true;
        }
        return false;
    }

    private void moveHeld(int keyCode) {
        int to = heldAt;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: to -= ROWS; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: to += ROWS; break;
            case KeyEvent.KEYCODE_DPAD_UP: if (to % ROWS > 0) to--; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: if (to % ROWS < ROWS - 1) to++; break;
            default: break;
        }
        if (to < 0 || to == heldAt || to >= WiiPages.slots(layout.size() + 1)) return;
        heldAt = to;
        shown = WiiLayout.move(layout, heldOrigin, to);
        strip.setCount(Math.max(strip.tiles.size(), WiiPages.slots(layout.size() + 1)));
        // The tile under the hand is bound and focused first, so the focus never lands on a slot about to empty.
        bindSlot(to);
        strip.tiles.get(to).requestFocus();
        for (int i = 0; i < strip.tiles.size(); i++) if (i != to) bindSlot(i);
        strip.tiles.get(to).setHeld(true);
    }

    /** Puts the held tile down: it swaps with the tile there, or takes the empty slot. The layout is saved. */
    private void dropHeld() {
        if (!holding()) return;
        boolean moved = heldAt != heldOrigin;
        String key = heldKey;
        int at = heldAt;
        heldKey = null;
        ignoreUp = false;
        hand.setImageResource(R.drawable.ic_wii_hand);
        if (moved) {
            layout = new ArrayList<>(shown);
            WiiLayout.save(act, layout);
        }
        lastKey = key;
        bindStrip(layout);
        if (at < strip.tiles.size() && !strip.tiles.get(at).isEmpty()) {
            strip.tiles.get(at).requestFocus();
            strip.tiles.get(at).setHeld(false);
        }
        if (sounds != null) sounds.confirm();
        placeHand(frame.findFocus(), true, HAND_MS);
        if (pendingReload) {
            pendingReload = false;
            reload();
        }
    }

    /** The arrows show only where a previous or next page exists. */
    private void updateArrows(boolean animate) {
        setArrow(root.prev, scrollCol > 0, animate);
        setArrow(root.next, scrollCol + VISIBLE_COLS < strip.columns(), animate);
    }

    private static void setArrow(View arrow, boolean show, boolean animate) {
        arrow.animate().cancel();
        float alpha = show ? 1f : 0f;
        if (animate) arrow.animate().alpha(alpha).setDuration(150).start();
        else arrow.setAlpha(alpha);
    }

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        root.clock.setTime(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), DateFormat.is24HourFormat(act));
        root.date.setText(DAYS[now.get(Calendar.DAY_OF_WEEK) - 1] + " " + now.get(Calendar.DAY_OF_MONTH)
                + "/" + (now.get(Calendar.MONTH) + 1));
    }

    // ---- scrolling -------------------------------------------------------------------------------

    /** Slides the strip, if the column is on another page, to that page; the neighbours' slivers stay. True if it moved. */
    private boolean showColumn(int col, boolean animate) {
        int s = Math.min(WiiPages.pageStart(col), Math.max(0, strip.columns() - VISIBLE_COLS));
        boolean moved = s != scrollCol;
        scrollCol = s;
        updateArrows(animate && moved);
        float target = -s * (float) strip.pitch;
        strip.animate().cancel();
        if (animate && moved && strip.pitch > 0) {
            if (sounds != null) sounds.swoosh();
            strip.animate().translationX(target).setDuration(SCROLL_MS).setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f)).start();
        } else {
            strip.setTranslationX(target);
        }
        return moved;
    }

    // ---- navigation ------------------------------------------------------------------------------

    private boolean occupied(int i) {
        return i >= 0 && i < strip.tiles.size() && !strip.tiles.get(i).isEmpty();
    }

    /** The tile of the column nearest to this row, or -1 when the column has none. */
    private int inColumn(int col, int row) {
        int best = -1;
        for (int r = 0; r < ROWS; r++) {
            int slot = col * ROWS + r;
            if (occupied(slot) && (best < 0 || Math.abs(r - row) < Math.abs(best % ROWS - row))) best = slot;
        }
        return best;
    }

    private void focusSlot(int slot) {
        if (occupied(slot)) {
            strip.tiles.get(slot).requestFocus();
            return;
        }
        for (int i = 0; i < strip.tiles.size(); i++) {
            if (occupied(i)) {
                strip.tiles.get(i).requestFocus();
                return;
            }
        }
        root.os.requestFocus();
    }

    private static float centerX(View v) {
        return v.getLeft() + v.getWidth() / 2f;
    }

    private View nearestControl(float x) {
        View best = root.os;
        for (View c : new View[] {root.sd, root.mail}) {
            if (Math.abs(centerX(c) - x) < Math.abs(centerX(best) - x)) best = c;
        }
        return best;
    }

    /** Where the tile's centre will be on screen once the strip has finished sliding. */
    private float screenCenterX(int index) {
        WiiViews.Tile t = strip.tiles.get(index);
        return strip.getLeft() + t.getLeft() + t.getWidth() / 2f - scrollCol * (float) strip.pitch;
    }

    private void upFromBar(View from) {
        float x = centerX(from);
        int best = -1;
        float bestDist = Float.MAX_VALUE;
        for (int c = scrollCol; c < scrollCol + VISIBLE_COLS; c++) {
            int t = inColumn(c, ROWS - 1);
            if (t < 0) continue;
            float d = Math.abs(screenCenterX(t) - x);
            if (d < bestDist) {
                bestDist = d;
                best = t;
            }
        }
        if (best >= 0) strip.tiles.get(best).requestFocus();
    }

    private void focusBeside(int i, int step) {
        int row = i % ROWS;
        for (int c = i / ROWS + step; c >= 0 && c < strip.columns(); c += step) {
            int t = inColumn(c, row);
            if (t >= 0) {
                strip.tiles.get(t).requestFocus();
                return;
            }
        }
    }

    private boolean onTileKey(int i, int keyCode) {
        int col = i / ROWS;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                focusBeside(i, -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                focusBeside(i, 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                for (int j = i - 1; j >= col * ROWS; j--) {
                    if (occupied(j)) {
                        strip.tiles.get(j).requestFocus();
                        break;
                    }
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                for (int j = i + 1; j < (col + 1) * ROWS; j++) {
                    if (occupied(j)) {
                        strip.tiles.get(j).requestFocus();
                        return true;
                    }
                }
                nearestControl(screenCenterX(i)).requestFocus();
                return true;
            default:
                return false;
        }
    }

    private boolean onBarKey(View v, int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (v == root.sd) root.os.requestFocus();
                else if (v == root.mail) root.sd.requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (v == root.os) root.sd.requestFocus();
                else if (v == root.sd) root.mail.requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                upFromBar(v);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return true;
            default:
                return false;
        }
    }

    private static boolean isDpad(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    private void wire() {
        View.OnKeyListener bar = (v, keyCode, event) -> {
            if (!isDpad(keyCode)) return false;
            return event.getAction() != KeyEvent.ACTION_DOWN || onBarKey(v, keyCode);
        };
        root.os.setOnKeyListener(bar);
        root.sd.setOnKeyListener(bar);
        root.mail.setOnKeyListener(bar);
        root.os.setOnClickListener(v -> {
            confirmSound();
            act.open(act.systemIntent(android.provider.Settings.ACTION_SETTINGS));
        });
        root.os.setOnLongClickListener(v -> {
            act.openLauncherSettings();
            return true;
        });
        root.sd.setOnClickListener(v -> {
            confirmSound();
            openFileManager();
        });
        root.mail.setOnClickListener(v -> {
            confirmSound();
            showEntradas();
        });
    }

    private void confirmSound() {
        if (sounds != null) sounds.confirm();
    }

    private void openFileManager() {
        Intent i = act.getPackageManager().getLeanbackLaunchIntentForPackage(FILE_MANAGER);
        if (i == null) i = act.getPackageManager().getLaunchIntentForPackage(FILE_MANAGER);
        if (i == null) {
            Toast.makeText(act, R.string.not_available, Toast.LENGTH_SHORT).show();
            return;
        }
        act.open(i);
    }

    // ---- hand cursor and focus sounds ------------------------------------------------------------

    private void onFocusChanged(View old, View now) {
        boolean paged = false;
        if (now != null && now.getParent() == strip) {
            int index = strip.tiles.indexOf(now);
            if (index >= 0) paged = showColumn(index / ROWS, true);
        }
        if (old != null && now != null && sounds != null) sounds.tick();
        placeHand(now, true, paged ? SCROLL_MS : HAND_MS);
    }

    /** The hand glides to the focused tile or button; for a tile it aims where the strip is heading, not where it is. */
    private void placeHand(View now, boolean animate, int ms) {
        boolean onTile = now != null && now.getParent() == strip;
        boolean onBar = now != null && now.getParent() == root;
        if (!onTile && !onBar) {
            hand.animate().cancel();
            hand.animate().alpha(0f).setDuration(100).start();
            return;
        }
        float x = now.getLeft() + now.getWidth() * 0.60f;
        float y = now.getTop() + now.getHeight() * 0.50f;
        if (holding() && onTile) {
            // The fist has no pointing finger: its middle sits where the fingertip would.
            x -= hand.getLayoutParams().width * 4.5f / 40f;
            y -= hand.getLayoutParams().height * 10f / 44f;
        }
        if (onTile) {
            x += strip.getLeft() - scrollCol * (float) strip.pitch;
            y += strip.getTop();
        }
        hand.animate().cancel();
        if (!animate || hand.getAlpha() < 0.5f) {
            hand.setTranslationX(x);
            hand.setTranslationY(y);
            if (animate) hand.animate().alpha(1f).setDuration(100).start();
            else hand.setAlpha(1f);
            return;
        }
        hand.animate().translationX(x).translationY(y).alpha(1f).setDuration(ms)
                .setInterpolator(ms == SCROLL_MS ? new PathInterpolator(0.4f, 0f, 0.2f, 1f) : new DecelerateInterpolator(1.4f)).start();
    }

    // ---- Entradas --------------------------------------------------------------------------------

    private static final class Input {
        final String label, id;

        Input(String label, String id) {
            this.label = label;
            this.id = id;
        }
    }

    private List<Input> hdmiInputs() {
        TvInputManager tim = (TvInputManager) act.getSystemService(Context.TV_INPUT_SERVICE);
        List<TvInputInfo> all = tim == null ? new ArrayList<>() : new ArrayList<>(tim.getTvInputList());
        Set<String> ids = new HashSet<>();
        for (TvInputInfo info : all) ids.add(info.getId());
        // A CEC device (Apple TV, soundbar) has an input of its own under its port: it opens through that one,
        // which also makes it the active source, and the port keeps the row.
        Map<String, String> cec = new HashMap<>();
        List<TvInputInfo> ports = new ArrayList<>();
        for (TvInputInfo info : all) {
            if (info.getType() != TvInputInfo.TYPE_HDMI || !info.isPassthroughInput() || info.isHidden(act)) continue;
            String parent = info.getParentId();
            if (parent != null && ids.contains(parent)) {
                if (!cec.containsKey(parent)) cec.put(parent, info.getId());
            } else {
                ports.add(info);
            }
        }
        Collections.sort(ports, (a, b) -> a.getId().compareTo(b.getId()));
        List<Input> out = new ArrayList<>();
        // "HDMI 2" in the TV's own label wins; two ports that don't agree on numbers are numbered in order.
        Pattern digit = Pattern.compile("(\\d+)\\s*$");
        Set<Integer> numbers = new HashSet<>();
        int[] parsed = new int[ports.size()];
        boolean consistent = true;
        for (int i = 0; i < ports.size(); i++) {
            CharSequence l = ports.get(i).loadLabel(act);
            Matcher m = digit.matcher(l == null ? "" : l.toString());
            parsed[i] = m.find() ? Integer.parseInt(m.group(1)) : -1;
            if (parsed[i] < 1 || parsed[i] > 9 || !numbers.add(parsed[i])) consistent = false;
        }
        for (int i = 0; i < ports.size(); i++) {
            TvInputInfo info = ports.get(i);
            int n = consistent ? parsed[i] : i + 1;
            String open = cec.containsKey(info.getId()) ? cec.get(info.getId()) : info.getId();
            out.add(new Input("HDMI " + n, open));
        }
        if (out.isEmpty()) {
            for (int n = 1; n <= 3; n++) out.add(new Input("HDMI " + n, HDMI_SERVICE + "HW" + (n + 1)));
        }
        Collections.sort(out, (a, b) -> a.label.compareTo(b.label));
        return out;
    }

    private List<Input> otherInputs() {
        List<Input> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) act.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        Set<String> ids = new HashSet<>();
        List<TvInputInfo> all = tim.getTvInputList();
        for (TvInputInfo info : all) ids.add(info.getId());
        for (TvInputInfo info : all) {
            if (info.getType() == TvInputInfo.TYPE_HDMI || info.getType() == TvInputInfo.TYPE_TUNER) continue;
            if (!info.isPassthroughInput() || info.isHidden(act)) continue;
            if (info.getParentId() != null && ids.contains(info.getParentId())) continue;
            CharSequence l = info.loadLabel(act);
            out.add(new Input(l == null ? info.getId() : l.toString(), info.getId()));
        }
        return out;
    }

    /** The Live TV app, tuned to the hardware tuner when that app takes the tune URI; else its start screen. */
    private Intent liveTv() {
        Intent live = act.liveTvIntent();
        String pkg = act.liveTvPackage();
        if (live == null && !pkg.isEmpty()) live = act.getPackageManager().getLaunchIntentForPackage(pkg);
        if (live == null) return null;
        TvInputManager tim = (TvInputManager) act.getSystemService(Context.TV_INPUT_SERVICE);
        String tunerId = null;
        if (tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.getType() != TvInputInfo.TYPE_TUNER
                        || (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) continue;
                boolean hw = info.getId().matches(".*/HW\\d+$");
                boolean analog = info.getId().toLowerCase(Locale.ROOT).contains("analog");
                if (hw && !analog && (tunerId == null || info.getId().compareTo(tunerId) < 0)) tunerId = info.getId();
            }
        }
        if (tunerId != null && !pkg.isEmpty()) {
            Intent tuned = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(tunerId),
                            "vnd.android.cursor.item/channel")
                    .setPackage(pkg);
            if (tuned.resolveActivity(act.getPackageManager()) != null) return tuned;
        }
        return live;
    }

    private void showEntradas() {
        if (dialog != null) return;
        final List<Input> rows = new ArrayList<>(hdmiInputs());
        rows.addAll(otherInputs());
        final Intent live = liveTv();

        dialog = new FrameLayout(act);
        dialog.setBackgroundColor(0x78F2F4F6);
        dialog.setOnClickListener(v -> closeDialog(true));
        LinearLayout panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xE6FFFFFF, 0xD9E4E8EB});
        glass.setCornerRadius(34 * u);
        glass.setStroke(Math.max(2, Math.round(3 * u)), 0xFFB3D5E6);
        panel.setBackground(glass);
        int pad = (int) (30 * u);
        panel.setPadding(pad, (int) (22 * u), pad, pad);
        TextView title = new TextView(act);
        title.setText(R.string.inputs);
        title.setTypeface(root.font);
        title.getPaint().setFakeBoldText(true);
        title.setTextColor(WiiViews.GRAY_TEXT);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 44 * u);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, (int) (10 * u));
        panel.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final List<WiiViews.Pill> pills = new ArrayList<>();
        for (final Input in : rows) {
            pills.add(pill(panel, in.label, () -> act.openInput(in.id)));
        }
        pills.add(pill(panel, act.getString(R.string.live_tv), () -> {
            if (live != null) act.open(live);
            else Toast.makeText(act, R.string.not_available, Toast.LENGTH_SHORT).show();
        }));
        for (int i = 0; i < pills.size(); i++) {
            final int at = i;
            pills.get(i).setOnKeyListener((v, keyCode, event) -> {
                if (!isDpad(keyCode)) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && at > 0) pills.get(at - 1).requestFocus();
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && at < pills.size() - 1) pills.get(at + 1).requestFocus();
                }
                return true;
            });
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                (int) (frame.getWidth() * 0.34f), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        dialog.addView(panel, lp);
        frame.addView(dialog, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        dialog.setAlpha(0f);
        dialog.animate().alpha(1f).setDuration(140).start();
        pills.get(0).requestFocus();
    }

    private WiiViews.Pill pill(LinearLayout panel, String text, final Runnable action) {
        WiiViews.Pill p = new WiiViews.Pill(act, u, root.font);
        p.setText(text);
        p.setOnClickListener(v -> {
            confirmSound();
            closeDialog(true);
            action.run();
        });
        panel.addView(p, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (110 * u)));
        return p;
    }

    private void closeDialog(boolean refocus) {
        if (dialog == null) return;
        dialog.animate().cancel();
        frame.removeView(dialog);
        dialog = null;
        root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (refocus) root.mail.requestFocus();
    }
}
