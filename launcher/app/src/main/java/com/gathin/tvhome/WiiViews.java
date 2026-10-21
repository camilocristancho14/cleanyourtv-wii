package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Views of the Wii-menu style home (config wii_style). All sizes derive from the screen height, 1080 units tall. */
final class WiiViews {
    private WiiViews() { }

    static final int BLUE = 0xFF2DA6E6;
    static final int GRAY_TEXT = 0xFF8D8F93;
    static final int TILE_BORDER = 0xFFC3C6C9;

    /** The blue glow the Wii draws around the selected channel. */
    static final class Glow {
        private Glow() { }

        static void draw(Canvas c, Paint p, RectF r, float radius, float room, float u) {
            final int steps = 6;
            p.setStyle(Paint.Style.STROKE);
            p.setColor(BLUE);
            for (int k = steps; k >= 1; k--) {
                float f = k / (float) steps;
                float grow = room * f;
                p.setStrokeWidth(room / steps * 1.9f);
                p.setAlpha((int) (96 * (1 - f) * (1 - f) + 20));
                c.drawRoundRect(r.left - grow, r.top - grow, r.right + grow, r.bottom + grow,
                        radius + grow, radius + grow, p);
            }
            p.setAlpha(255);
            p.setStrokeWidth(3.5f * u);
            c.drawRoundRect(r.left - 1.2f * u, r.top - 1.2f * u, r.right + 1.2f * u, r.bottom + 1.2f * u,
                    radius + 1.2f * u, radius + 1.2f * u, p);
        }

        /** The small lift a focused channel gets. */
        static void pop(View v, boolean focused) {
            v.animate().cancel();
            v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                    .translationZ(focused ? 8f : 0f).setDuration(140).start();
        }
    }

    /** Light gray backdrop with faint horizontal lines. */
    static final class Backdrop extends View {
        private final Paint fill = new Paint();
        private final Paint lines = new Paint();
        private final float u;

        Backdrop(Context ctx, float u) {
            super(ctx);
            this.u = u;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            int period = Math.max(4, Math.round(7 * u));
            Bitmap tile = Bitmap.createBitmap(1, period, Bitmap.Config.ARGB_8888);
            tile.setPixel(0, 0, 0x0F000000);
            lines.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            fill.setShader(new LinearGradient(0, 0, 0, h, 0xFFF6F6F6, 0xFFE3E4E5, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawRect(0, 0, getWidth(), getHeight(), fill);
            c.drawRect(0, 0, getWidth(), getHeight(), lines);
        }
    }

    /** The curved bottom bar: wings at both sides, a shallow trough in the middle under the clock. */
    static final class Bar extends View {
        static final float WING = 0.147f, TROUGH = 0.441f;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path body = new Path();
        private final Path top = new Path();
        private final Path inner = new Path();
        private final float u;

        Bar(Context ctx, float u) {
            super(ctx);
            this.u = u;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            edge.setStyle(Paint.Style.STROKE);
            edge.setColor(0xFFA4CBDF);
            edge.setStrokeWidth(3.5f * u);
            edge.setStrokeJoin(Paint.Join.ROUND);
            shine.setStyle(Paint.Style.STROKE);
            shine.setColor(0xCCFFFFFF);
            shine.setStrokeWidth(2.5f * u);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            float wing = h * WING, trough = h * TROUGH;
            top.reset();
            top.moveTo(-10 * u, wing);
            top.lineTo(w * 0.13f, wing);
            top.cubicTo(w * 0.25f, wing, w * 0.23f, trough, w * 0.35f, trough);
            top.lineTo(w * 0.65f, trough);
            top.cubicTo(w * 0.77f, trough, w * 0.75f, wing, w * 0.87f, wing);
            top.lineTo(w + 10 * u, wing);
            body.set(top);
            body.lineTo(w + 10 * u, h + 10 * u);
            body.lineTo(-10 * u, h + 10 * u);
            body.close();
            inner.set(top);
            inner.offset(0, 4.5f * u);
            fill.setShader(new LinearGradient(0, wing, 0, h, 0xFFEBECED, 0xFFD2D4D6, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawPath(body, fill);
            c.drawPath(top, edge);
            c.drawPath(inner, shine);
        }
    }

    /** Seven-segment clock: "2:09 AM" in the 12-hour setting, "14:09" in the 24-hour one. */
    static final class Clock extends View {
        private static final int[] MASKS = {0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F};
        private static final int LIT = 0xFF8E9094, DIM = 0xFFDCDDDF;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path seg = new Path();
        private String digits = "";
        private String suffix = "";

        Clock(Context ctx, Typeface font) {
            super(ctx);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            text.setColor(LIT);
            text.setTypeface(font);
        }

        void setTime(int hour24, int minute, boolean is24) {
            int h = is24 ? hour24 : (hour24 % 12 == 0 ? 12 : hour24 % 12);
            digits = (is24 ? String.format(java.util.Locale.US, "%02d", h) : Integer.toString(h))
                    + ":" + String.format(java.util.Locale.US, "%02d", minute);
            suffix = is24 ? "" : (hour24 < 12 ? "AM" : "PM");
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (digits.isEmpty()) return;
            float h = getHeight() * 0.64f;
            float t = h * 0.14f;
            float dw = h * 0.56f, colonW = h * 0.2f, gap = h * 0.14f;
            float total = 0;
            for (int i = 0; i < digits.length(); i++) total += (digits.charAt(i) == ':' ? colonW : dw) + (i > 0 ? gap : 0);
            text.setTextSize(h * 0.30f);
            text.setFakeBoldText(true);
            float suffixW = suffix.isEmpty() ? 0 : text.measureText(suffix) + gap * 1.2f;
            float x = (getWidth() - total - suffixW) / 2f;
            float baseline = getHeight() * 0.92f;
            c.save();
            c.translate(0, baseline);
            c.skew(-0.09f, 0);
            for (int i = 0; i < digits.length(); i++) {
                char ch = digits.charAt(i);
                if (i > 0) x += gap;
                if (ch == ':') {
                    paint.setColor(LIT);
                    c.drawRect(x + colonW / 2 - t / 2, -h * 0.66f, x + colonW / 2 + t / 2, -h * 0.66f + t, paint);
                    c.drawRect(x + colonW / 2 - t / 2, -h * 0.30f, x + colonW / 2 + t / 2, -h * 0.30f + t, paint);
                    x += colonW;
                } else {
                    digit(c, x, -h, dw, h, t, MASKS[ch - '0']);
                    x += dw;
                }
            }
            c.restore();
            if (!suffix.isEmpty()) c.drawText(suffix, x + gap * 1.2f - 4 * getResources().getDisplayMetrics().density, baseline - h * 0.02f, text);
        }

        private void digit(Canvas c, float x, float y, float w, float h, float t, int mask) {
            float l = x + t / 2, r = x + w - t / 2, tp = y + t / 2, bt = y + h - t / 2, mid = y + h / 2;
            float d = t * 0.12f;
            segment(c, l + d, tp, r - d, tp, t, (mask & 1) != 0);
            segment(c, r, tp + d, r, mid - d, t, (mask & 2) != 0);
            segment(c, r, mid + d, r, bt - d, t, (mask & 4) != 0);
            segment(c, l + d, bt, r - d, bt, t, (mask & 8) != 0);
            segment(c, l, mid + d, l, bt - d, t, (mask & 16) != 0);
            segment(c, l, tp + d, l, mid - d, t, (mask & 32) != 0);
            segment(c, l + d, mid, r - d, mid, t, (mask & 64) != 0);
        }

        private void segment(Canvas c, float x0, float y0, float x1, float y1, float t, boolean lit) {
            seg.reset();
            float k = t / 2;
            if (y0 == y1) {
                seg.moveTo(x0, y0);
                seg.lineTo(x0 + k, y0 - k);
                seg.lineTo(x1 - k, y0 - k);
                seg.lineTo(x1, y0);
                seg.lineTo(x1 - k, y0 + k);
                seg.lineTo(x0 + k, y0 + k);
            } else {
                seg.moveTo(x0, y0);
                seg.lineTo(x0 + k, y0 + k);
                seg.lineTo(x0 + k, y1 - k);
                seg.lineTo(x0, y1);
                seg.lineTo(x0 - k, y1 - k);
                seg.lineTo(x0 - k, y0 + k);
            }
            seg.close();
            paint.setColor(lit ? LIT : DIM);
            c.drawPath(seg, paint);
        }
    }

    /** Round "wiiOS" and envelope buttons and the SD card: the Wii's bottom bar controls. */
    static final class Control extends View {
        static final int OS = 0, SD = 1, MAIL = 2;
        private final int kind;
        private final float u, room;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private final Typeface font;

        Control(Context ctx, int kind, float u, Typeface font) {
            super(ctx);
            this.kind = kind;
            this.u = u;
            this.room = 12 * u;
            this.font = font;
            setFocusable(true);
            setClickable(true);
        }

        @Override
        protected void onFocusChanged(boolean gained, int direction, Rect previouslyFocusedRect) {
            super.onFocusChanged(gained, direction, previouslyFocusedRect);
            Glow.pop(this, gained);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth() - 2 * room, h = getHeight() - 2 * room;
            rect.set(room, room, room + w, room + h);
            if (kind == SD) drawCard(c); else drawRound(c, kind == OS);
        }

        private void drawRound(Canvas c, boolean os) {
            float d = Math.min(rect.width(), rect.height());
            float cx = rect.centerX(), cy = rect.centerY(), r = d / 2;
            if (isFocused()) {
                RectF ring = new RectF(cx - r, cy - r, cx + r, cy + r);
                Glow.draw(c, paint, ring, r, room, u);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(cx, cy - r * 0.2f, r, 0xFFFFFFFF, 0xFFDADCDE, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r - 3 * u, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5 * u);
            paint.setColor(0xFFB9D8E6);
            c.drawCircle(cx, cy, r - 3 * u, paint);
            paint.setStrokeWidth(1.5f * u);
            paint.setColor(0xFFE9F4F9);
            c.drawCircle(cx, cy, r - 7 * u, paint);
            paint.setStyle(Paint.Style.FILL);
            if (os) {
                paint.setColor(0xFF9A9CA0);
                paint.setTypeface(font);
                paint.setFakeBoldText(true);
                paint.setTextSize(d * 0.27f);
                paint.setTextAlign(Paint.Align.CENTER);
                c.drawText("wiiOS", cx, cy + d * 0.095f, paint);
                paint.setTextAlign(Paint.Align.LEFT);
                paint.setFakeBoldText(false);
            } else {
                float ew = d * 0.50f, eh = d * 0.34f;
                RectF env = new RectF(cx - ew / 2, cy - eh / 2, cx + ew / 2, cy + eh / 2);
                paint.setColor(0xFF909296);
                c.drawRoundRect(env, 4 * u, 4 * u, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(0xFFE4E5E7);
                paint.setStrokeWidth(2.2f * u);
                paint.setStrokeJoin(Paint.Join.ROUND);
                path.reset();
                path.moveTo(env.left + 3 * u, env.top + 4 * u);
                path.lineTo(cx, cy + eh * 0.12f);
                path.lineTo(env.right - 3 * u, env.top + 4 * u);
                c.drawPath(path, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }

        private void drawCard(Canvas c) {
            float w = rect.width() * 0.84f, h = rect.height() * 0.9f;
            float l = rect.centerX() - w / 2, t = rect.centerY() - h / 2;
            float cut = w * 0.30f;
            path.reset();
            path.moveTo(l, t);
            path.lineTo(l + w - cut, t);
            path.lineTo(l + w, t + cut);
            path.lineTo(l + w, t + h);
            path.lineTo(l, t + h);
            path.close();
            if (isFocused()) {
                RectF box = new RectF(l, t, l + w, t + h);
                Glow.draw(c, paint, box, 6 * u, room, u);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(l, t, l + w, t + h, 0xFFFFFFFF, 0xFFD6EAF4, Shader.TileMode.CLAMP));
            c.drawPath(path, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4.5f * u);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(0xFF74BADC);
            c.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF8F979C);
            paint.setTypeface(font);
            paint.setFakeBoldText(true);
            paint.setTextSize(w * 0.30f);
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("SD", l + w / 2, t + h * 0.66f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setFakeBoldText(false);
        }
    }

    /** The page arrow: a gray slot with a blue-outlined triangle. */
    static final class Arrow extends View {
        private final boolean pointsRight;
        private final float u, room;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path tri = new Path();
        private final RectF rect = new RectF();

        Arrow(Context ctx, boolean pointsRight, float u) {
            super(ctx);
            this.pointsRight = pointsRight;
            this.u = u;
            this.room = 10 * u;
            setFocusable(true);
            setClickable(true);
        }

        @Override
        protected void onFocusChanged(boolean gained, int direction, Rect previouslyFocusedRect) {
            super.onFocusChanged(gained, direction, previouslyFocusedRect);
            Glow.pop(this, gained);
        }

        @Override
        protected void onDraw(Canvas c) {
            rect.set(room, room, getWidth() - room, getHeight() - room);
            float radius = 16 * u;
            if (isFocused()) Glow.draw(c, paint, rect, radius, room, u);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFE2E3E5);
            c.drawRoundRect(rect, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * u);
            paint.setColor(TILE_BORDER);
            c.drawRoundRect(rect, radius, radius, paint);
            float cx = rect.centerX(), cy = rect.centerY();
            float hw = rect.width() * 0.17f, hh = Math.min(rect.height() * 0.2f, rect.width() * 0.3f);
            float dir = pointsRight ? 1 : -1;
            tri.reset();
            tri.moveTo(cx - dir * hw, cy - hh);
            tri.lineTo(cx + dir * hw, cy);
            tri.lineTo(cx - dir * hw, cy + hh);
            tri.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x66FFFFFF);
            c.drawPath(tri, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4.5f * u);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(0xFF63B4E0);
            c.drawPath(tri, paint);
        }
    }

    /** A channel: a white rounded tile with a thin gray border, or an empty gray slot. */
    static final class Tile extends FrameLayout {
        private final float u, room, radius;
        final FrameLayout card;
        final ImageView banner, icon;
        final TextView label;
        private final GradientDrawable fillWhite = new GradientDrawable();
        private final GradientDrawable fillGray = new GradientDrawable();
        private final GradientDrawable border = new GradientDrawable();
        private final GradientDrawable borderEmpty = new GradientDrawable();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private boolean empty = true;

        Tile(Context ctx, float u, Typeface font) {
            super(ctx);
            this.u = u;
            this.room = 10 * u;
            this.radius = 16 * u;
            setWillNotDraw(false);
            setClipChildren(false);
            setFocusable(false);
            card = new FrameLayout(ctx);
            LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.setMargins((int) room, (int) room, (int) room, (int) room);
            addView(card, lp);
            fillWhite.setColor(0xFFFFFFFF);
            fillWhite.setCornerRadius(radius);
            fillGray.setColor(0xFFD9DADC);
            fillGray.setCornerRadius(radius);
            int stroke = Math.max(1, Math.round(2 * u));
            border.setColor(Color.TRANSPARENT);
            border.setCornerRadius(radius);
            border.setStroke(stroke, TILE_BORDER);
            borderEmpty.setColor(Color.TRANSPARENT);
            borderEmpty.setCornerRadius(radius);
            borderEmpty.setStroke(stroke, 0xFFC9CBCD);
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
                }
            });
            card.setClipToOutline(true);
            banner = new ImageView(ctx);
            banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(banner, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            icon = new ImageView(ctx);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LayoutParams ilp = new LayoutParams((int) (84 * u), (int) (84 * u), Gravity.CENTER);
            ilp.bottomMargin = (int) (22 * u);
            card.addView(icon, ilp);
            label = new TextView(ctx);
            label.setTypeface(font);
            label.setTextColor(0xFF7C7E82);
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 20 * u);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER);
            label.setPadding((int) (10 * u), 0, (int) (10 * u), (int) (12 * u));
            card.addView(label, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
            bindEmpty();
        }

        boolean isEmpty() {
            return empty;
        }

        void bindEmpty() {
            empty = true;
            setFocusable(false);
            setClickable(false);
            setOnClickListener(null);
            setOnLongClickListener(null);
            setContentDescription(null);
            card.setBackground(fillGray);
            card.setForeground(borderEmpty);
            banner.setImageDrawable(null);
            banner.setVisibility(GONE);
            icon.setImageDrawable(null);
            icon.setVisibility(GONE);
            label.setVisibility(GONE);
            setScaleX(1f);
            setScaleY(1f);
            setTranslationZ(0f);
        }

        void bindApp(AppAdapter.Entry e) {
            empty = false;
            setFocusable(true);
            setClickable(true);
            setContentDescription(e.label);
            card.setBackground(fillWhite);
            card.setForeground(border);
            if (e.banner != null) {
                banner.setImageDrawable(e.banner);
                banner.setVisibility(VISIBLE);
                icon.setVisibility(GONE);
                label.setVisibility(GONE);
            } else {
                banner.setImageDrawable(null);
                banner.setVisibility(GONE);
                icon.setImageDrawable(e.icon);
                icon.setVisibility(VISIBLE);
                label.setText(e.label);
                label.setVisibility(VISIBLE);
            }
        }

        @Override
        protected void onFocusChanged(boolean gained, int direction, Rect previouslyFocusedRect) {
            super.onFocusChanged(gained, direction, previouslyFocusedRect);
            if (!empty) Glow.pop(this, gained);
        }

        @Override
        protected void onDraw(Canvas c) {
            if (empty || !isFocused()) return;
            rect.set(room, room, getWidth() - room, getHeight() - room);
            Glow.draw(c, paint, rect, radius, room, u);
        }
    }

    /** A row of the Entradas dialog: a white pill that gets the blue outline when focused. */
    static final class Pill extends TextView {
        Pill(Context ctx, float u, Typeface font) {
            super(ctx);
            setTypeface(font);
            getPaint().setFakeBoldText(true);
            setTextColor(0xFF3E4043);
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 34 * u);
            setGravity(Gravity.CENTER);
            setSingleLine(true);
            setIncludeFontPadding(false);
            setFocusable(true);
            setClickable(true);
            setBackground(new PillBackground(u));
        }
    }

    private static final class PillBackground extends Drawable {
        private final float u, room;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private boolean focused;

        PillBackground(float u) {
            this.u = u;
            this.room = 9 * u;
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean now = false;
            for (int s : state) if (s == android.R.attr.state_focused) now = true;
            boolean changed = now != focused;
            focused = now;
            return changed;
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            rect.set(b.left + room, b.top + room, b.right - room, b.bottom - room);
            float radius = rect.height() / 2;
            if (focused) Glow.draw(c, paint, rect, radius, room, u);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0xFFFFFFFF, 0xFFEFF0F1, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, radius, radius, paint);
            paint.setShader(null);
            if (!focused) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2f * u);
                paint.setColor(0xFF9FA3A7);
                c.drawRoundRect(rect, radius, radius, paint);
            }
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** The whole screen: places every child from the screen size. */
    static final class Root extends ViewGroup {
        final float u;
        final Backdrop backdrop;
        final Bar bar;
        final Tile[] tiles = new Tile[WiiHome.PER_PAGE];
        final Arrow left, right;
        final Clock clock;
        final TextView date;
        final Control os, sd, mail;
        final ImageView hand;
        final Typeface font;

        Root(Context ctx, float screenHeightPx, Typeface font) {
            super(ctx);
            this.u = screenHeightPx / 1080f;
            this.font = font;
            setClipChildren(false);
            backdrop = new Backdrop(ctx, u);
            addView(backdrop);
            bar = new Bar(ctx, u);
            addView(bar);
            for (int i = 0; i < tiles.length; i++) {
                tiles[i] = new Tile(ctx, u, font);
                addView(tiles[i]);
            }
            left = new Arrow(ctx, false, u);
            right = new Arrow(ctx, true, u);
            left.setContentDescription("Anterior");
            right.setContentDescription("Siguiente");
            addView(left);
            addView(right);
            clock = new Clock(ctx, font);
            addView(clock);
            date = new TextView(ctx);
            date.setTypeface(font);
            date.getPaint().setFakeBoldText(true);
            date.setTextColor(GRAY_TEXT);
            date.setGravity(Gravity.CENTER);
            date.setSingleLine(true);
            date.setIncludeFontPadding(false);
            date.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 66 * u);
            addView(date);
            os = new Control(ctx, Control.OS, u, font);
            sd = new Control(ctx, Control.SD, u, font);
            mail = new Control(ctx, Control.MAIL, u, font);
            os.setContentDescription("Ajustes");
            sd.setContentDescription("Archivos");
            mail.setContentDescription("Entradas");
            addView(os);
            addView(sd);
            addView(mail);
            hand = new ImageView(ctx);
            hand.setImageResource(R.drawable.ic_wii_hand);
            hand.setAlpha(0f);
            hand.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(hand);
        }

        float gridLeft(int w) { return w * 0.095f; }
        float gridRight(int w) { return w * 0.905f; }
        float gapX(int w) { return w * 0.0125f; }
        float gridTop(int h) { return h * 0.045f; }
        float gapY(int h) { return h * 0.02f; }
        float tileW(int w) { return (gridRight(w) - gridLeft(w) - 3 * gapX(w)) / 4f; }
        float tileH(int h) { return (h * 0.675f - gridTop(h) - 2 * gapY(h)) / 3f; }

        /** Slot rectangle of a channel, without the glow room. */
        RectF slot(int w, int h, int index) {
            int r = index / WiiHome.COLS, c = index % WiiHome.COLS;
            float x = gridLeft(w) + c * (tileW(w) + gapX(w));
            float y = gridTop(h) + r * (tileH(h) + gapY(h));
            return new RectF(x, y, x + tileW(w), y + tileH(h));
        }

        Rect rectOf(View v, int w, int h) {
            float room = 10 * u;
            RectF r;
            if (v == backdrop) {
                r = new RectF(0, 0, w, h);
            } else if (v == bar) {
                r = new RectF(0, h * 0.66f, w, h);
            } else if (v == left || v == right) {
                float cw = w * 0.056f;
                float cx = v == left ? gridLeft(w) / 2f : (gridRight(w) + w) / 2f;
                RectF mid = slot(w, h, 4);
                r = new RectF(cx - cw / 2, mid.top, cx + cw / 2, mid.bottom);
                r.inset(-room, -room);
            } else if (v == clock) {
                r = new RectF(w * 0.35f, h * 0.693f, w * 0.65f, h * 0.805f);
            } else if (v == date) {
                r = new RectF(w * 0.30f, h * 0.832f, w * 0.70f, h * 0.915f);
            } else if (v == os || v == mail) {
                float d = h * 0.125f + 2 * 12 * u;
                float cx = v == os ? w * 0.095f : w * 0.905f, cy = h * 0.835f;
                r = new RectF(cx - d / 2, cy - d / 2, cx + d / 2, cy + d / 2);
            } else if (v == sd) {
                float bw = w * 0.044f + 2 * 12 * u, bh = h * 0.092f + 2 * 12 * u;
                float cx = w * 0.215f, cy = h * 0.868f;
                r = new RectF(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2);
            } else if (v == hand) {
                float hh = h * 0.115f;
                r = new RectF(0, 0, hh * 40f / 44f, hh);
            } else {
                int idx = -1;
                for (int i = 0; i < tiles.length; i++) if (tiles[i] == v) idx = i;
                r = idx < 0 ? new RectF() : slot(w, h, idx);
                if (idx >= 0) r.inset(-room, -room);
            }
            return new Rect(Math.round(r.left), Math.round(r.top), Math.round(r.right), Math.round(r.bottom));
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
            setMeasuredDimension(w, h);
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                Rect r = rectOf(v, w, h);
                v.measure(MeasureSpec.makeMeasureSpec(r.width(), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(r.height(), MeasureSpec.EXACTLY));
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int w = r - l, h = b - t;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                Rect rc = rectOf(v, w, h);
                v.layout(rc.left, rc.top, rc.right, rc.bottom);
            }
        }
    }
}
