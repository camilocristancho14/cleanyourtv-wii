package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Protector de pantalla: the Wii channel that picks the clip WiiDream plays.
 * Spanish, D-pad only (up and down through the rows; the list scrolls to the focused one).
 * Light or dark with the Wii menu (WiiTime). Does not change the TV's screensaver timeout.
 */
public class ScreensaverActivity extends Activity {

    private static final int KIND_TOGGLE = 0, KIND_PREVIEW = 1, KIND_VIDEO = 2, KIND_BLACK = 3;

    private final WiiViews.Look look = new WiiViews.Look();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService thumbs = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "screensaver-thumb");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private final List<View> rows = new ArrayList<>();
    private final List<Row> choices = new ArrayList<>();

    private float u;
    private Typeface font;
    private WiiSounds sounds;
    private WiiViews.Backdrop backdrop;
    private TextView title;
    private TextView heading;
    private TextView toggleState;
    private View lastFocused;
    private boolean painted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        u = getResources().getDisplayMetrics().heightPixels / 1080f;
        font = getResources().getFont(R.font.outfit_medium);
        look.p = WiiTime.isNightNow() ? WiiViews.Palette.DARK : WiiViews.Palette.LIGHT;
        File root = getExternalFilesDir(null);
        if (root != null) new File(root, ScreensaverFiles.DIR).mkdirs();

        FrameLayout frame = new FrameLayout(this);
        backdrop = new WiiViews.Backdrop(this, u, look);
        frame.addView(backdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int side = (int) (36 * u);
        column.setPadding(side, (int) (28 * u), side, (int) (24 * u));

        title = text(getString(R.string.screensaver_channel), 46 * u, true);
        title.setPadding(0, 0, 0, (int) (8 * u));
        column.addView(title, matchWrap());

        toggleState = text("", 26 * u, true);
        View toggle = row(KIND_TOGGLE, null);
        LinearLayout toggleBox = (LinearLayout) toggle;
        TextView toggleLabel = text(getString(R.string.screensaver_enabled), 28 * u, false);
        toggleBox.addView(toggleLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        toggleBox.addView(toggleState, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        toggle.setOnClickListener(v -> {
            boolean on = !ScreensaverPrefs.isEnabled(this);
            ScreensaverPrefs.setEnabled(this, on);
            confirm();
            showToggle();
        });
        column.addView(toggle, rowLp());
        rows.add(toggle);

        View preview = row(KIND_PREVIEW, null);
        TextView previewLabel = text(getString(R.string.screensaver_preview), 30 * u, true);
        previewLabel.setGravity(Gravity.CENTER);
        ((LinearLayout) preview).addView(previewLabel, matchWrap());
        preview.setOnClickListener(v -> {
            confirm();
            startActivity(new android.content.Intent(this, ScreensaverPreviewActivity.class));
        });
        column.addView(preview, rowLp());
        rows.add(preview);

        heading = text(getString(R.string.screensaver_pick), 22 * u, false);
        heading.setPadding((int) (8 * u), (int) (16 * u), 0, (int) (4 * u));
        column.addView(heading, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setFocusable(false);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, matchWrap());
        column.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        List<File> clips = ScreensaverFiles.list(root == null ? null : new File(root, ScreensaverFiles.DIR));
        String shown = shownChoice(clips);
        int focusAt = 0;
        if (clips.isEmpty()) {
            heading.setText(R.string.screensaver_empty);
        }
        for (File clip : clips) {
            View video = videoRow(clip);
            list.addView(video, rowLp());
            rows.add(video);
            if (clip.getName().equals(shown)) focusAt = rows.size() - 1;
        }
        View black = videoRow(null);
        list.addView(black, rowLp());
        rows.add(black);
        if (ScreensaverFiles.BLACK.equals(shown)) focusAt = rows.size() - 1;

        wireKeys();
        int panel = Math.min(getResources().getDisplayMetrics().widthPixels,
                Math.round(Math.min(getResources().getDisplayMetrics().widthPixels * 0.62f, 1080 * u)));
        frame.addView(column, new FrameLayout.LayoutParams(panel, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        setContentView(frame);
        showToggle();
        applyColors();
        final int focus = focusAt;
        rows.get(focus).post(() -> rows.get(focus).requestFocus());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sounds == null) sounds = new WiiSounds(this);
        showToggle();
        applyColors();
    }

    @Override
    protected void onDestroy() {
        thumbs.shutdownNow();
        if (sounds != null) sounds.release();
        sounds = null;
        super.onDestroy();
    }

    /** What the rows should mark: the saved clip, Pantalla negra, or the automatic first clip. */
    private String shownChoice(List<File> clips) {
        String choice = ScreensaverPrefs.choice(this);
        if (ScreensaverFiles.BLACK.equals(choice)) return ScreensaverFiles.BLACK;
        if (choice != null && !choice.isEmpty()) {
            for (File f : clips) if (f.getName().equals(choice)) return choice;
            return null;
        }
        return clips.isEmpty() ? null : clips.get(0).getName();
    }

    private void showToggle() {
        boolean on = ScreensaverPrefs.isEnabled(this);
        toggleState.setText(on ? R.string.screensaver_on : R.string.screensaver_off);
        toggleState.setTextColor(on ? WiiViews.BLUE : look.p.text);
        rows.get(0).setContentDescription(getString(R.string.screensaver_enabled) + ", " + toggleState.getText());
    }

    private void select(String choice) {
        ScreensaverPrefs.setChoice(this, choice);
        confirm();
        for (Row row : choices) {
            boolean marked = choice.equals(row.choice);
            row.mark.setVisibility(marked ? View.VISIBLE : View.INVISIBLE);
            row.view.setContentDescription(row.label + (marked ? ", seleccionado" : ""));
        }
    }

    private View videoRow(File clip) {
        boolean black = clip == null;
        String choice = black ? ScreensaverFiles.BLACK : clip.getName();
        String label = black ? getString(R.string.screensaver_black) : ScreensaverFiles.displayName(clip.getName());
        if (label.isEmpty()) label = black ? getString(R.string.screensaver_black) : clip.getName();
        View view = row(black ? KIND_BLACK : KIND_VIDEO, choice);
        LinearLayout box = (LinearLayout) view;
        ImageView thumb = new ImageView(this);
        int tw = (int) (168 * u), th = (int) (94 * u);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackgroundColor(black ? 0xFF000000 : 0xFF2A2D31);
        thumb.setClipToOutline(true);
        thumb.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 10 * u);
            }
        });
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(tw, th);
        tp.setMarginEnd((int) (18 * u));
        box.addView(thumb, tp);
        TextView name = text(label, 28 * u, false);
        box.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView mark = text("✓", 32 * u, true);
        mark.setTag("mark");
        mark.setTextColor(WiiViews.BLUE);
        mark.setVisibility(View.INVISIBLE);
        box.addView(mark, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        String shown = currentMark();
        boolean marked = choice.equals(shown);
        mark.setVisibility(marked ? View.VISIBLE : View.INVISIBLE);
        view.setContentDescription(label + (marked ? ", seleccionado" : ""));
        view.setOnClickListener(v -> select(choice));
        choices.add(new Row(view, choice, label, mark));
        if (!black) loadThumb(thumb, clip);
        return view;
    }

    /** The choice currently stored, or the first clip when nothing is stored yet. */
    private String currentMark() {
        String choice = ScreensaverPrefs.choice(this);
        if (choice != null && !choice.isEmpty()) return choice;
        File root = getExternalFilesDir(null);
        List<File> clips = ScreensaverFiles.list(root == null ? null : new File(root, ScreensaverFiles.DIR));
        return clips.isEmpty() ? null : clips.get(0).getName();
    }

    private void loadThumb(ImageView thumb, File file) {
        thumb.setTag(file);
        thumbs.execute(() -> {
            Bitmap bmp = ScreensaverThumbs.frame(file, Math.max(1, Math.round(240 * u)));
            main.post(() -> {
                if (isDestroyed() || thumb.getTag() != file) {
                    if (bmp != null) bmp.recycle();
                    return;
                }
                if (bmp != null) thumb.setImageBitmap(bmp);
            });
        });
    }

    private View row(int kind, String choice) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int) (18 * u);
        row.setPadding(pad, (int) (10 * u), pad, (int) (10 * u));
        row.setMinimumHeight((int) (kind == KIND_VIDEO || kind == KIND_BLACK ? 118 * u : 84 * u));
        row.setBackground(new RowBackground(u, 18 * u, look));
        row.setFocusable(true);
        row.setClickable(true);
        row.setOnFocusChangeListener((v, gained) -> {
            if (!gained) return;
            if (lastFocused != null && lastFocused != v && sounds != null) sounds.tick();
            lastFocused = v;
        });
        row.setTag(choice);
        return row;
    }

    private void wireKeys() {
        for (int i = 0; i < rows.size(); i++) {
            final int at = i;
            rows.get(i).setOnKeyListener((v, keyCode, event) -> {
                if (!isDpad(keyCode)) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && at > 0) rows.get(at - 1).requestFocus();
                    else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && at < rows.size() - 1) rows.get(at + 1).requestFocus();
                }
                return true;
            });
        }
    }

    private void applyColors() {
        boolean dark = WiiTime.isNightNow();
        WiiViews.Palette next = dark ? WiiViews.Palette.DARK : WiiViews.Palette.LIGHT;
        if (painted && look.p == next) return;
        look.p = next;
        painted = true;
        title.setTextColor(look.p.pillText);
        heading.setTextColor(look.p.text);
        for (View row : rows) {
            if (row instanceof ViewGroup) tint((ViewGroup) row);
            row.invalidate();
        }
        showToggle();
        if (backdrop != null) backdrop.invalidate();
    }

    /** Row labels follow the palette. The check stays blue, and the on/off word is coloured in showToggle. */
    private void tint(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (!(child instanceof TextView) || child == toggleState) continue;
            TextView t = (TextView) child;
            t.setTextColor("mark".equals(child.getTag()) ? WiiViews.BLUE : look.p.pillText);
        }
    }

    private TextView text(String value, float px, boolean bold) {
        TextView t = new TextView(this);
        t.setTypeface(font);
        if (bold) t.getPaint().setFakeBoldText(true);
        t.setText(value);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        t.setTextColor(look.p.pillText);
        return t;
    }

    private void confirm() {
        if (sounds != null) sounds.confirm();
    }

    private static boolean isDpad(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (6 * u);
        return lp;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static final class Row {
        final View view;
        final String choice;
        final String label;
        final TextView mark;

        Row(View view, String choice, String label, TextView mark) {
            this.view = view;
            this.choice = choice;
            this.label = label;
            this.mark = mark;
        }
    }

    /** Rounded Wii row: white (or charcoal) fill, gray edge, the menu's blue glow while focused. */
    private static final class RowBackground extends Drawable {
        private final float u, radius;
        private final WiiViews.Look look;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private boolean focused;

        RowBackground(float u, float radius, WiiViews.Look look) {
            this.u = u;
            this.radius = radius;
            this.look = look;
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean now = false;
            for (int s : state) if (s == android.R.attr.state_focused) now = true;
            if (now == focused) return false;
            focused = now;
            invalidateSelf();
            return true;
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float room = 8 * u;
            rect.set(b.left + room, b.top + room, b.right - room, b.bottom - room);
            if (rect.width() <= 0 || rect.height() <= 0) return;
            WiiViews.Palette p = look.p;
            if (focused) WiiViews.Glow.draw(c, paint, rect, radius, room, u);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                    p.pillTop, p.pillBottom, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, radius, radius, paint);
            paint.setShader(null);
            if (!focused) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1f, 2f * u));
                paint.setColor(p.pillEdge);
                c.drawRoundRect(rect, radius, radius, paint);
            }
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
