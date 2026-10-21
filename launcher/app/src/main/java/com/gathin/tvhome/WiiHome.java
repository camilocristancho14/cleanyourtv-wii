package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Build;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
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
 * The Nintendo Wii menu (config wii_style): twelve channels a page, a curved bar with the clock, the
 * wiiOS (Settings), SD (file manager) and envelope (Entradas) buttons. It lives inside MainActivity, so
 * the launcher is still the one HOME activity. D-pad navigation is explicit, not left to focus search.
 */
final class WiiHome {
    static final int COLS = 4, ROWS = 3, PER_PAGE = COLS * ROWS;
    private static final String FILE_MANAGER = "com.alphainventor.filemanager";
    private static final String HDMI_SERVICE = "com.mediatek.tis/.HdmiInputService/";
    private static final String[] DAYS = {"Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb"};

    private final MainActivity act;
    private final AppAdapter apps;
    private final FrameLayout frame;
    private final WiiViews.Root root;
    private final float u;
    private FrameLayout dialog;
    private boolean started, dirty = true;
    private int page;
    private String lastKey;

    private final IntentFilter packageFilter = new IntentFilter();
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
            reload();
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
        this.u = act.getResources().getDisplayMetrics().heightPixels / 1080f;
        this.apps = new AppAdapter(act, act, 0, cfg);
        Typeface font = act.getResources().getFont(R.font.outfit_medium);
        frame = new FrameLayout(act);
        root = new WiiViews.Root(act, act.getResources().getDisplayMetrics().heightPixels, font);
        frame.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addDataScheme("package");
        timeFilter.addAction(Intent.ACTION_TIME_TICK);
        timeFilter.addAction(Intent.ACTION_TIME_CHANGED);
        timeFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        wire();
        frame.getViewTreeObserver().addOnGlobalFocusChangeListener(this::moveHand);
    }

    View view() {
        return frame;
    }

    // ---- lifecycle -------------------------------------------------------------------------------

    void onStart() {
        started = true;
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
    }

    void onPause() {
        closeDialog(false);
    }

    void onStop() {
        started = false;
        dirty = true;
        act.unregisterReceiver(packageReceiver);
        act.unregisterReceiver(timeReceiver);
    }

    void onHome() {
        closeDialog(false);
        lastKey = null;
        page = 0;
        bindPage();
        focusSlot(0);
    }

    void onBack() {
        closeDialog(true);
    }

    // ---- apps ------------------------------------------------------------------------------------

    private void reload() {
        View focused = frame.findFocus();
        for (int i = 0; i < PER_PAGE; i++) {
            if (root.tiles[i] == focused && !root.tiles[i].isEmpty()) lastKey = apps.keyAt(page * PER_PAGE + i);
        }
        final List<ResolveInfo> leanback = act.getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        apps.reload(Collections.<Cards.Card>emptyList(), leanback);
        dirty = false;
        int at = -1;
        for (int i = 0; lastKey != null && i < apps.appCount(); i++) {
            if (lastKey.equals(apps.keyAt(i))) at = i;
        }
        if (at >= 0) page = at / PER_PAGE;
        page = Math.max(0, Math.min(page, pageCount() - 1));
        bindPage();
        if (dialog == null && (focused != null || at >= 0 || frame.findFocus() == null)) {
            final int slot = at >= 0 ? at % PER_PAGE : 0;
            root.post(() -> focusSlot(slot));
        }
    }

    private int pageCount() {
        return Math.max(1, (apps.appCount() + PER_PAGE - 1) / PER_PAGE);
    }

    private void bindPage() {
        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            WiiViews.Tile tile = root.tiles[i];
            if (index < apps.appCount()) {
                final AppAdapter.Entry e = apps.entryAt(index);
                tile.bindApp(e);
                tile.setOnClickListener(v -> {
                    lastKey = apps.keyAt(page * PER_PAGE + indexOf(v));
                    apps.launch(e);
                });
            } else {
                tile.bindEmpty();
            }
        }
        root.left.setVisibility(page > 0 ? View.VISIBLE : View.INVISIBLE);
        root.right.setVisibility(page < pageCount() - 1 ? View.VISIBLE : View.INVISIBLE);
        root.left.setFocusable(page > 0);
        root.right.setFocusable(page < pageCount() - 1);
        root.requestLayout();
    }

    private int indexOf(View tile) {
        for (int i = 0; i < PER_PAGE; i++) if (root.tiles[i] == tile) return i;
        return 0;
    }

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        root.clock.setTime(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), DateFormat.is24HourFormat(act));
        root.date.setText(DAYS[now.get(Calendar.DAY_OF_WEEK) - 1] + " " + now.get(Calendar.DAY_OF_MONTH)
                + "/" + (now.get(Calendar.MONTH) + 1));
    }

    // ---- navigation ------------------------------------------------------------------------------

    private boolean occupied(int i) {
        return i >= 0 && i < PER_PAGE && !root.tiles[i].isEmpty();
    }

    private int lastOccupiedInRow(int row) {
        for (int c = COLS - 1; c >= 0; c--) if (occupied(row * COLS + c)) return row * COLS + c;
        return -1;
    }

    private void focusSlot(int slot) {
        if (occupied(slot)) {
            root.tiles[slot].requestFocus();
        } else if (occupied(0)) {
            root.tiles[0].requestFocus();
        } else {
            root.os.requestFocus();
        }
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

    private void upFromBar(View from) {
        float x = centerX(from);
        int col = 0;
        float bestDist = Float.MAX_VALUE;
        for (int c = 0; c < COLS; c++) {
            float d = Math.abs(centerX(root.tiles[c]) - x);
            if (d < bestDist) {
                bestDist = d;
                col = c;
            }
        }
        for (int r = ROWS - 1; r >= 0; r--) {
            int t = occupied(r * COLS + col) ? r * COLS + col : lastOccupiedInRow(r);
            if (t >= 0) {
                root.tiles[t].requestFocus();
                return;
            }
        }
    }

    private boolean onTileKey(int i, int keyCode) {
        int r = i / COLS, c = i % COLS;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (c > 0) root.tiles[i - 1].requestFocus();
                else if (page > 0) root.left.requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (c < COLS - 1 && occupied(i + 1)) root.tiles[i + 1].requestFocus();
                else if (page < pageCount() - 1) root.right.requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (r > 0) root.tiles[i - COLS].requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                for (int rr = r + 1; rr < ROWS; rr++) {
                    int t = occupied(rr * COLS + c) ? rr * COLS + c : lastOccupiedInRow(rr);
                    if (t >= 0) {
                        root.tiles[t].requestFocus();
                        return true;
                    }
                }
                nearestControl(centerX(root.tiles[i])).requestFocus();
                return true;
            default:
                return false;
        }
    }

    private int middleRowTarget(boolean fromLeft) {
        int t = fromLeft ? COLS : 2 * COLS - 1;
        if (occupied(t)) return t;
        t = lastOccupiedInRow(1);
        if (t >= 0) return fromLeft ? COLS : t;
        t = lastOccupiedInRow(0);
        return t >= 0 ? (fromLeft ? 0 : t) : -1;
    }

    private boolean onArrowKey(boolean isRight, int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (isRight) {
                    int t = middleRowTarget(false);
                    if (t >= 0) root.tiles[t].requestFocus();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!isRight) {
                    int t = middleRowTarget(true);
                    if (t >= 0) root.tiles[t].requestFocus();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                (isRight ? root.mail : root.os).requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
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
        for (int i = 0; i < PER_PAGE; i++) {
            final int slot = i;
            root.tiles[i].setOnKeyListener((v, keyCode, event) -> {
                if (!isDpad(keyCode)) return false;
                return event.getAction() != KeyEvent.ACTION_DOWN || onTileKey(slot, keyCode);
            });
        }
        root.left.setOnKeyListener((v, keyCode, event) -> {
            if (!isDpad(keyCode)) return false;
            return event.getAction() != KeyEvent.ACTION_DOWN || onArrowKey(false, keyCode);
        });
        root.right.setOnKeyListener((v, keyCode, event) -> {
            if (!isDpad(keyCode)) return false;
            return event.getAction() != KeyEvent.ACTION_DOWN || onArrowKey(true, keyCode);
        });
        root.left.setOnClickListener(v -> turn(-1));
        root.right.setOnClickListener(v -> turn(1));
        View.OnKeyListener bar = (v, keyCode, event) -> {
            if (!isDpad(keyCode)) return false;
            return event.getAction() != KeyEvent.ACTION_DOWN || onBarKey(v, keyCode);
        };
        root.os.setOnKeyListener(bar);
        root.sd.setOnKeyListener(bar);
        root.mail.setOnKeyListener(bar);
        root.os.setOnClickListener(v -> act.open(act.systemIntent(android.provider.Settings.ACTION_SETTINGS)));
        root.os.setOnLongClickListener(v -> {
            act.openLauncherSettings();
            return true;
        });
        root.sd.setOnClickListener(v -> openFileManager());
        root.mail.setOnClickListener(v -> showEntradas());
    }

    private void turn(int by) {
        View arrow = by < 0 ? root.left : root.right;
        page = Math.max(0, Math.min(pageCount() - 1, page + by));
        lastKey = null;
        bindPage();
        if (arrow.getVisibility() == View.VISIBLE) {
            arrow.requestFocus();
        } else {
            int t = middleRowTarget(by < 0);
            root.tiles[Math.max(t, 0)].requestFocus();
        }
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

    // ---- hand cursor -----------------------------------------------------------------------------

    private void moveHand(View old, View now) {
        View hand = root.hand;
        boolean onPage = now != null && now.getParent() == root;
        if (!onPage) {
            hand.animate().cancel();
            hand.animate().alpha(0f).setDuration(100).start();
            return;
        }
        float x = now.getLeft() + now.getWidth() * 0.60f;
        float y = now.getTop() + now.getHeight() * 0.50f;
        if (hand.getAlpha() < 0.5f) {
            hand.setTranslationX(x);
            hand.setTranslationY(y);
        }
        hand.animate().cancel();
        hand.animate().translationX(x).translationY(y).alpha(1f).setDuration(130).start();
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
