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

        /** Same glow, traced along an arbitrary outline (e.g. the SD card's folded corner). */
        static void draw(Canvas c, Paint p, Path shape, float room, float u) {
            final int steps = 6;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(BLUE);
            for (int k = steps; k >= 1; k--) {
                float f = k / (float) steps;
                p.setStrokeWidth(2 * room * f + room / steps * 1.9f);
                p.setAlpha((int) ((96 * (1 - f) * (1 - f) + 20) * 0.6f));
                c.drawPath(shape, p);
            }
            p.setAlpha(255);
        }

        /** The small lift a focused channel gets. */
        static void pop(View v, boolean focused) {
            v.animate().cancel();
            v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                    .translationZ(focused ? 8f : 0f).setDuration(140).start();
        }
    }

    /** The colours of one look: the light Wii menu by day, a dark one from 16:00 to 07:00 (WiiTime). */
    static final class Palette {
        final int backTop, backBottom, lines;
        final int tile, tileBorder, empty, emptyBorder, label;
        final int trayTop, trayBottom, trayShade, wave, shine;
        final int socketTop, socketBottom, socketRim, socketInner;
        final int buttonHi, buttonLo, ring, specular, glyph, shadow;
        final int cardTop, cardBottom, cardBand, cardEdge, cardText;
        final int clock, clockEdge, text, suffix;
        final int tabTop, tabBottom, tabRim, tabShine;
        final int scrim, panelTop, panelBottom, panelEdge, pillTop, pillBottom, pillEdge, pillText;

        private Palette(int[] c) {
            int i = 0;
            backTop = c[i++]; backBottom = c[i++]; lines = c[i++];
            tile = c[i++]; tileBorder = c[i++]; empty = c[i++]; emptyBorder = c[i++]; label = c[i++];
            trayTop = c[i++]; trayBottom = c[i++]; trayShade = c[i++]; wave = c[i++]; shine = c[i++];
            socketTop = c[i++]; socketBottom = c[i++]; socketRim = c[i++]; socketInner = c[i++];
            buttonHi = c[i++]; buttonLo = c[i++]; ring = c[i++]; specular = c[i++]; glyph = c[i++]; shadow = c[i++];
            cardTop = c[i++]; cardBottom = c[i++]; cardBand = c[i++]; cardEdge = c[i++]; cardText = c[i++];
            clock = c[i++]; clockEdge = c[i++]; text = c[i++]; suffix = c[i++];
            tabTop = c[i++]; tabBottom = c[i++]; tabRim = c[i++]; tabShine = c[i++];
            scrim = c[i++]; panelTop = c[i++]; panelBottom = c[i++]; panelEdge = c[i++];
            pillTop = c[i++]; pillBottom = c[i++]; pillEdge = c[i++]; pillText = c[i];
        }

        static final Palette LIGHT = new Palette(new int[] {
                0xFFF6F6F6, 0xFFE3E4E5, 0x0F000000,
                0xFFFFFFFF, TILE_BORDER, 0xFFD9DADC, 0xFFC9CBCD, 0xFF7C7E82,
                0xFFDDDFE3, 0xFFD2D4D9, 0x0A000000, 0xFF34B1E6, 0xE6FFFFFF,
                0xFFC5C8CD, 0xFFDDDFE3, 0xFFEEEFF2, 0x26000000,
                0xFFFFFFFF, 0xFFD3D5D8, 0xFF3DB4E8, 0x99FFFFFF, 0xFF9A9CA0, 0x33000000,
                0xFFFFFFFF, 0xFFE3F2FA, 0xFF5BC0EC, 0xFF3AA9DE, 0xFF49B3E5,
                0xFF96989C, 0xFF808286, GRAY_TEXT, 0xFF8E9094,
                0xFFEBEBEB, 0xFFD5D5D5, 0xFFBDBDBD, 0xFFF4F4F4,
                0x78F2F4F6, 0xE6FFFFFF, 0xD9E4E8EB, 0xFFB3D5E6,
                0xFFFFFFFF, 0xFFEFF0F1, 0xFF9FA3A7, 0xFF3E4043,
        });

        static final Palette DARK = new Palette(new int[] {
                0xFF25282C, 0xFF1A1C1F, 0x10FFFFFF,
                0xFF2A2D31, 0xFF4C5157, 0xFF222428, 0xFF383C41, 0xFFC4C7CB,
                0xFF303337, 0xFF222427, 0x1A000000, 0xFF34B1E6, 0x33FFFFFF,
                0xFF1B1D20, 0xFF2B2E32, 0xFF44484E, 0x40000000,
                0xFF5A5E64, 0xFF33363A, 0xFF3DB4E8, 0x22FFFFFF, 0xFFC9CCD0, 0x66000000,
                0xFF4A535B, 0xFF2F3A43, 0xFF2F8FC0, 0xFF3DB4E8, 0xFF8FD3F5,
                0xFFC0C3C7, 0xFF9DA0A5, 0xFFC4C7CB, 0xFFB0B3B8,
                0xFF3E4146, 0xFF2E3135, 0xFF55595F, 0x26FFFFFF,
                0x99101214, 0xF02C2F33, 0xF0202326, 0xFF3C6F8C,
                0xFF3A3D42, 0xFF2E3135, 0xFF5A5E63, 0xFFE0E2E5,
        });
    }

    /** The palette all Wii views read when they draw; Root.setPalette swaps it and redraws. */
    static final class Look {
        Palette p = Palette.LIGHT;
    }

    /** Backdrop with faint horizontal lines: light gray by day, charcoal at night. */
    static final class Backdrop extends View {
        private final Paint fill = new Paint();
        private final Paint lines = new Paint();
        private final float u;
        private final Look look;
        private Palette built;
        private int builtH;

        Backdrop(Context ctx, float u, Look look) {
            super(ctx);
            this.u = u;
            this.look = look;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        private void build(int h) {
            Palette p = look.p;
            int period = Math.max(4, Math.round(7 * u));
            Bitmap tile = Bitmap.createBitmap(1, period, Bitmap.Config.ARGB_8888);
            tile.setPixel(0, 0, p.lines);
            lines.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
            fill.setShader(new LinearGradient(0, 0, 0, h, p.backTop, p.backBottom, Shader.TileMode.CLAMP));
            built = p;
            builtH = h;
        }

        @Override
        protected void onDraw(Canvas c) {
            if (built != look.p || builtH != getHeight()) build(getHeight());
            c.drawRect(0, 0, getWidth(), getHeight(), fill);
            c.drawRect(0, 0, getWidth(), getHeight(), lines);
        }
    }

    /**
     * The curved tray: a thin blue wave along its top that dips under the clock, a white shine under the wave, a
     * gray gradient below, and at both ends a round recessed socket (a capsule running off the screen edge) for the
     * side buttons. Root tells it where the sockets are.
     */
    static final class Bar extends View {
        /** Height of the wave over the wings and in the trough, as fractions of the bar's height. */
        static final float WING = 0.197f, TROUGH = 0.494f;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint socket = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path body = new Path();
        private final Path top = new Path();
        private final Path inner = new Path();
        private final RectF cap = new RectF();
        private final float u;
        private final Look look;
        private Palette built;
        private float leftX = -1, rightX, socketY, socketR;

        Bar(Context ctx, float u, Look look) {
            super(ctx);
            this.u = u;
            this.look = look;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(4f * u);
            edge.setStrokeJoin(Paint.Join.ROUND);
            shine.setStyle(Paint.Style.STROKE);
            shine.setStrokeWidth(3f * u);
            shade.setStyle(Paint.Style.STROKE);
            socket.setStyle(Paint.Style.FILL);
        }

        /** Centres of the two sockets (bar coordinates) and their radius. */
        void setSockets(float left, float right, float y, float r) {
            leftX = left;
            rightX = right;
            socketY = y;
            socketR = r;
            built = null;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            float wing = h * WING, trough = h * TROUGH;
            top.reset();
            top.moveTo(-10 * u, wing);
            top.lineTo(w * 0.16f, wing);
            top.cubicTo(w * 0.25f, wing, w * 0.23f, trough, w * 0.32f, trough);
            top.lineTo(w * 0.68f, trough);
            top.cubicTo(w * 0.77f, trough, w * 0.75f, wing, w * 0.84f, wing);
            top.lineTo(w + 10 * u, wing);
            body.set(top);
            body.lineTo(w + 10 * u, h + 10 * u);
            body.lineTo(-10 * u, h + 10 * u);
            body.close();
            inner.set(top);
            inner.offset(0, 4f * u);
            built = null;
        }

        private void build() {
            Palette p = look.p;
            int h = getHeight();
            fill.setShader(new LinearGradient(0, h * WING, 0, h, p.trayTop, p.trayBottom, Shader.TileMode.CLAMP));
            socket.setShader(new LinearGradient(0, socketY - socketR, 0, socketY + socketR,
                    p.socketTop, p.socketBottom, Shader.TileMode.CLAMP));
            edge.setColor(p.wave);
            shine.setColor(p.shine);
            built = p;
        }

        @Override
        protected void onDraw(Canvas c) {
            if (built != look.p) build();
            Palette p = look.p;
            c.drawPath(body, fill);
            // A soft shadow that hangs under the wave, inside the tray only.
            c.save();
            c.clipPath(body);
            shade.setColor(p.trayShade);
            for (int k = 1; k <= 4; k++) {
                shade.setStrokeWidth(k * 13 * u);
                c.drawPath(top, shade);
            }
            c.restore();
            if (leftX >= 0 && socketR > 0) {
                float w = getWidth(), r = socketR;
                capsule(c, -3 * r, leftX + r);
                capsule(c, rightX - r, w + 3 * r);
            }
            c.drawPath(inner, shine);
            c.drawPath(top, edge);
        }

        /** A recessed capsule: darker at the top like a hollow, a dark inner lip and a light rim around it. */
        private void capsule(Canvas c, float l, float r) {
            Palette p = look.p;
            float rr = socketR;
            cap.set(l, socketY - rr, r, socketY + rr);
            socket.setStyle(Paint.Style.FILL);
            c.drawRoundRect(cap, rr, rr, socket);
            Shader s = socket.getShader();
            socket.setShader(null);
            socket.setStyle(Paint.Style.STROKE);
            socket.setStrokeWidth(5 * u);
            socket.setColor(p.socketInner);
            cap.inset(4.5f * u, 4.5f * u);
            cap.offset(0, 1.5f * u);
            c.drawRoundRect(cap, rr, rr, socket);
            cap.set(l, socketY - rr, r, socketY + rr);
            socket.setStrokeWidth(3 * u);
            socket.setColor(p.socketRim);
            c.drawRoundRect(cap, rr, rr, socket);
            socket.setShader(s);
            socket.setStyle(Paint.Style.FILL);
        }
    }

    /** Seven-segment clock: large gray digits ("2:09"), a small AM/PM to the right; "14:09" in the 24-hour setting. */
    static final class Clock extends View {
        private static final int[] MASKS = {0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F};
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path seg = new Path();
        private final Look look;
        private String digits = "";
        private String suffix = "";

        Clock(Context ctx, Typeface font, Look look) {
            super(ctx);
            this.look = look;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            text.setTypeface(font);
            text.setFakeBoldText(true);
            paint.setStrokeJoin(Paint.Join.ROUND);
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
            float h = getHeight() * 0.68f;
            float t = h * 0.13f;
            float dw = h * 0.56f, colonW = h * 0.22f, gap = h * 0.17f;
            float total = 0;
            for (int i = 0; i < digits.length(); i++) total += (digits.charAt(i) == ':' ? colonW : dw) + (i > 0 ? gap : 0);
            text.setTextSize(h * 0.36f);
            text.setColor(look.p.suffix);
            float suffixGap = gap * 1.8f;
            float suffixW = suffix.isEmpty() ? 0 : text.measureText(suffix) + suffixGap;
            float x = (getWidth() - total - suffixW) / 2f;
            float baseline = getHeight() * 0.9f;
            c.save();
            c.translate(0, baseline);
            for (int i = 0; i < digits.length(); i++) {
                char ch = digits.charAt(i);
                if (i > 0) x += gap;
                if (ch == ':') {
                    float cx = x + colonW / 2, r = t * 0.55f;
                    dot(c, cx, -h * 0.70f, r);
                    dot(c, cx, -h * 0.30f, r);
                    x += colonW;
                } else {
                    digit(c, x, -h, dw, h, t, MASKS[ch - '0']);
                    x += dw;
                }
            }
            c.restore();
            if (!suffix.isEmpty()) c.drawText(suffix, x + suffixGap, baseline, text);
        }

        private void dot(Canvas c, float cx, float cy, float r) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(look.p.clock);
            c.drawCircle(cx, cy, r, paint);
        }

        private void digit(Canvas c, float x, float y, float w, float h, float t, int mask) {
            float l = x + t / 2, r = x + w - t / 2, tp = y + t / 2, bt = y + h - t / 2, mid = y + h / 2;
            float d = t * 0.22f;
            if ((mask & 1) != 0) segment(c, l + d, tp, r - d, tp, t);
            if ((mask & 2) != 0) segment(c, r, tp + d, r, mid - d, t);
            if ((mask & 4) != 0) segment(c, r, mid + d, r, bt - d, t);
            if ((mask & 8) != 0) segment(c, l + d, bt, r - d, bt, t);
            if ((mask & 16) != 0) segment(c, l, mid + d, l, bt - d, t);
            if ((mask & 32) != 0) segment(c, l, tp + d, l, mid - d, t);
            if ((mask & 64) != 0) segment(c, l + d, mid, r - d, mid, t);
        }

        /** One lit segment: a long hexagon, filled gray with a slightly darker edge. Unlit segments are not drawn. */
        private void segment(Canvas c, float x0, float y0, float x1, float y1, float t) {
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
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(look.p.clock);
            c.drawPath(seg, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(t * 0.14f);
            paint.setColor(look.p.clockEdge);
            c.drawPath(seg, paint);
        }
    }

    /** The tray controls: the round wiiOS and Entradas buttons with their blue rings, and the SD card. */
    static final class Control extends View {
        static final int OS = 0, SD = 1, INPUTS = 2;
        private final int kind;
        private final float u, room;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private final Typeface font;
        private final Look look;

        Control(Context ctx, int kind, float u, Typeface font, Look look) {
            super(ctx);
            this.kind = kind;
            this.u = u;
            this.room = 12 * u;
            this.font = font;
            this.look = look;
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
            if (kind == SD) drawCard(c); else drawRound(c);
        }

        private void drawRound(Canvas c) {
            Palette p = look.p;
            float d = Math.min(rect.width(), rect.height());
            float cx = rect.centerX(), cy = rect.centerY(), r = d / 2;
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(p.shadow);
            c.drawCircle(cx + 3 * u, cy + 6 * u, r - 1 * u, paint);
            if (isFocused()) {
                RectF ring = new RectF(cx - r, cy - r, cx + r, cy + r);
                Glow.draw(c, paint, ring, r, room, u);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFFFFF);
            paint.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 1.35f, p.buttonHi, p.buttonLo, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r - 2 * u, paint);
            // The glossy highlight over the upper half.
            paint.setShader(new LinearGradient(0, cy - r, 0, cy, p.specular, p.specular & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawOval(cx - r * 0.72f, cy - r * 0.86f, cx + r * 0.62f, cy + r * 0.05f, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4.5f * u);
            paint.setColor(p.ring);
            c.drawCircle(cx, cy, r - 2.5f * u, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(p.glyph);
            if (kind == OS) {
                paint.setTypeface(font);
                paint.setFakeBoldText(true);
                paint.setTextSize(d * 0.25f);
                paint.setTextAlign(Paint.Align.CENTER);
                c.drawText("wiiOS", cx, cy + d * 0.09f, paint);
                paint.setTextAlign(Paint.Align.LEFT);
                paint.setFakeBoldText(false);
            } else {
                drawInputsGlyph(c, cx, cy, d);
            }
        }

        /** The inputs symbol: an arrow coming in from the left through the open side of a box. */
        private void drawInputsGlyph(Canvas c, float cx, float cy, float d) {
            float sw = d * 0.055f;
            float bl = cx - d * 0.08f, br = cx + d * 0.25f, bt = cy - d * 0.19f, bb = cy + d * 0.19f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(sw);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            path.reset();
            path.moveTo(bl, cy - d * 0.09f);
            path.lineTo(bl, bt);
            path.lineTo(br, bt);
            path.lineTo(br, bb);
            path.lineTo(bl, bb);
            path.lineTo(bl, cy + d * 0.09f);
            c.drawPath(path, paint);
            float tip = cx + d * 0.13f, head = d * 0.11f;
            c.drawLine(cx - d * 0.29f, cy, tip - head * 0.6f, cy, paint);
            paint.setStyle(Paint.Style.FILL);
            path.reset();
            path.moveTo(tip, cy);
            path.lineTo(tip - head, cy - head * 0.9f);
            path.lineTo(tip - head, cy + head * 0.9f);
            path.close();
            c.drawPath(path, paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
        }

        /** The SD card: small card with a folded corner, white over a light-blue band, a blue "SD" and a blue outline. */
        private void drawCard(Canvas c) {
            Palette p = look.p;
            float w = rect.width() * 0.80f, h = rect.height() * 0.86f;
            float l = rect.centerX() - w / 2, t = rect.centerY() - h / 2;
            float cut = w * 0.16f, rad = w * 0.07f;
            path.reset();
            path.moveTo(l + rad, t);
            path.lineTo(l + w - cut, t);
            path.lineTo(l + w, t + cut);
            path.lineTo(l + w, t + h - rad);
            path.quadTo(l + w, t + h, l + w - rad, t + h);
            path.lineTo(l + rad, t + h);
            path.quadTo(l, t + h, l, t + h - rad);
            path.lineTo(l, t + rad);
            path.quadTo(l, t, l + rad, t);
            path.close();
            boolean focused = isFocused();
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(p.shadow);
            c.save();
            c.translate(3 * u, 5 * u);
            c.drawPath(path, paint);
            c.restore();
            if (focused) Glow.draw(c, paint, path, room, u);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFFFFF);
            paint.setShader(new LinearGradient(l, t, l, t + h, p.cardTop, p.cardBottom, Shader.TileMode.CLAMP));
            c.drawPath(path, paint);
            paint.setShader(null);
            c.save();
            c.clipPath(path);
            paint.setColor(p.cardBand);
            c.drawRect(l, t + h * 0.80f, l + w, t + h, paint);
            c.restore();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4.5f * u);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(focused ? BLUE : p.cardEdge);
            c.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(p.cardText);
            paint.setTypeface(font);
            paint.setFakeBoldText(true);
            paint.setTextSkewX(-0.3f);
            paint.setTextSize(w * 0.42f);
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("SD", l + w / 2, t + h * 0.56f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSkewX(0f);
            paint.setFakeBoldText(false);
        }
    }

    /**
     * Banners made for a white tile (white or transparent edges, see BannerTone), re-toned once for the dark tiles:
     * the paper made transparent and dark lettering lifted. Cached per banner drawable, so each is analysed once; a
     * banner that is not light maps to itself and costs only the 64x36 sample.
     */
    static final class DarkBanners {
        private DarkBanners() { }

        /** Largest re-toned bitmap, about a tile's width on a 1080p screen. */
        static final int MAX_W = 400;
        private static final java.util.Map<Drawable, Drawable> CACHE = new java.util.WeakHashMap<>();

        static Drawable of(Context ctx, Drawable d) {
            Drawable done = CACHE.get(d);
            if (done == null) {
                done = make(ctx, d);
                CACHE.put(d, done);
            }
            return done;
        }

        private static Drawable make(Context ctx, Drawable d) {
            int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) return d;
            int sw = 64, sh = Math.max(1, Math.round(64f * ih / iw));
            int[] sample = pixels(d, sw, sh);
            if (sample == null || !BannerTone.isLight(sample, sw, sh)) return d;
            int paper = BannerTone.paper(sample, sw, sh);
            int w = Math.min(iw, MAX_W), h = Math.max(1, Math.round((float) w * ih / iw));
            int[] px = pixels(d, w, h);
            if (px == null) return d;
            BannerTone.forDark(px, paper);
            Bitmap out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
            return new android.graphics.drawable.BitmapDrawable(ctx.getResources(), out);
        }

        /** The drawable rendered at w x h, as ARGB pixels; null if it cannot be drawn. */
        private static int[] pixels(Drawable d, int w, int h) {
            Rect old = d.copyBounds();
            try {
                Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                d.setBounds(0, 0, w, h);
                d.draw(new Canvas(bmp));
                int[] px = new int[w * h];
                bmp.getPixels(px, 0, w, 0, 0, w, h);
                bmp.recycle();
                return px;
            } catch (RuntimeException | OutOfMemoryError e) {
                return null;
            } finally {
                d.setBounds(old);
            }
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
        private final Look look;
        private boolean empty = true;
        /** The app's own banner; at night the tile shows DarkBanners.of(art) instead. */
        private Drawable art;

        Tile(Context ctx, float u, Typeface font, Look look) {
            super(ctx);
            this.u = u;
            this.look = look;
            this.room = 10 * u;
            this.radius = 16 * u;
            setWillNotDraw(false);
            setClipChildren(false);
            setFocusable(false);
            card = new FrameLayout(ctx);
            LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.setMargins((int) room, (int) room, (int) room, (int) room);
            addView(card, lp);
            fillWhite.setCornerRadius(radius);
            fillGray.setCornerRadius(radius);
            border.setColor(Color.TRANSPARENT);
            border.setCornerRadius(radius);
            borderEmpty.setColor(Color.TRANSPARENT);
            borderEmpty.setCornerRadius(radius);
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
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 20 * u);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER);
            label.setPadding((int) (10 * u), 0, (int) (10 * u), (int) (12 * u));
            card.addView(label, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
            applyLook();
            bindEmpty();
        }

        /** Re-colours the tile for the current palette (white by day, charcoal at night). */
        void applyLook() {
            Palette p = look.p;
            int stroke = Math.max(1, Math.round(2 * u));
            fillWhite.setColor(p.tile);
            fillGray.setColor(p.empty);
            border.setStroke(stroke, p.tileBorder);
            borderEmpty.setStroke(stroke, p.emptyBorder);
            label.setTextColor(p.label);
            if (art != null) banner.setImageDrawable(p == Palette.DARK ? DarkBanners.of(getContext(), art) : art);
            card.invalidate();
        }

        boolean isEmpty() {
            return empty;
        }

        void bindEmpty() {
            stopWobble();
            empty = true;
            setFocusable(false);
            setClickable(false);
            setOnClickListener(null);
            setOnLongClickListener(null);
            setContentDescription(null);
            card.setBackground(fillGray);
            card.setForeground(borderEmpty);
            art = null;
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
            stopWobble();
            empty = false;
            setFocusable(true);
            setClickable(true);
            setContentDescription(e.label);
            card.setBackground(fillWhite);
            card.setForeground(border);
            art = e.banner;
            if (e.banner != null) {
                banner.setImageDrawable(look.p == Palette.DARK ? DarkBanners.of(getContext(), e.banner) : e.banner);
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

        private android.animation.ObjectAnimator wobble;

        /** Picked up for rearranging: lifted a bit higher than a focused tile, and wobbling slightly. */
        private void stopWobble() {
            if (wobble != null) wobble.cancel();
            wobble = null;
            setRotation(0f);
        }

        void setHeld(boolean held) {
            stopWobble();
            animate().cancel();
            if (held) {
                animate().scaleX(1.12f).scaleY(1.12f).translationZ(16f).setDuration(140).start();
                wobble = android.animation.ObjectAnimator.ofFloat(this, ROTATION, -1.6f, 1.6f);
                wobble.setDuration(120);
                wobble.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                wobble.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                wobble.start();
            } else {
                animate().scaleX(isFocused() ? 1.06f : 1f).scaleY(isFocused() ? 1.06f : 1f)
                        .translationZ(isFocused() ? 8f : 0f).setDuration(140).start();
            }
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
        Pill(Context ctx, float u, Typeface font, Look look) {
            super(ctx);
            setTypeface(font);
            getPaint().setFakeBoldText(true);
            setTextColor(look.p.pillText);
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 34 * u);
            setGravity(Gravity.CENTER);
            setSingleLine(true);
            setIncludeFontPadding(false);
            setFocusable(true);
            setClickable(true);
            setBackground(new PillBackground(u, look));
        }
    }

    private static final class PillBackground extends Drawable {
        private final float u, room;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Look look;
        private boolean focused;

        PillBackground(float u, Look look) {
            this.u = u;
            this.look = look;
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
            paint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, look.p.pillTop, look.p.pillBottom, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, radius, radius, paint);
            paint.setShader(null);
            if (!focused) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2f * u);
                paint.setColor(look.p.pillEdge);
                c.drawRoundRect(rect, radius, radius, paint);
            }
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /**
     * A paging arrow, as on the Wii: a glossy light-blue triangle with a white border, no tab
     * behind it, over the sliver of the next or previous page. Same in both themes. Never focusable.
     */
    static final class Arrow extends View {
        static final int BLUE_HI = 0xFFE4FBFF, BLUE_MID = 0xFF8FDDFB, BLUE_LO = 0xFF3FB4EC, BLUE_EDGE = 0xFF2C7FC4;
        private final boolean left;
        private final float u;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path tri = new Path();
        private final Look look;
        private Palette built;
        private LinearGradient gloss;
        private RadialGradient triFill;

        Arrow(Context ctx, boolean left, float u, Look look) {
            super(ctx);
            this.left = left;
            this.u = u;
            this.look = look;
            setFocusable(false);
            setClickable(false);
            setAlpha(0f);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            built = null;
        }

        /** Shapes in a right-hand tab (pointing right); onDraw mirrors them for the left one. */
        private void build() {
            Palette p = look.p;
            built = p;
            float w = getWidth(), h = getHeight();
            float th = Math.min(h * 0.5f, 78 * u), tw = th * 0.56f;
            float bx = w * 0.5f - tw * 0.42f, tx = bx + tw, cy = h / 2, top = cy - th / 2, bot = cy + th / 2;
            tri.reset();
            tri.moveTo(bx, top);
            tri.lineTo(tx, cy);
            tri.lineTo(bx, bot);
            // The Wii's base bows in slightly.
            tri.quadTo(bx + tw * 0.10f, cy, bx, top);
            tri.close();
            triFill = new RadialGradient(bx + tw * 0.2f, cy, tw * 1.05f,
                    new int[] {BLUE_HI, BLUE_MID, BLUE_LO}, new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            gloss = new LinearGradient(0, top, 0, cy, 0x99FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        }

        @Override
        protected void onDraw(Canvas c) {
            if (built != look.p) build();
            float w = getWidth(), h = getHeight();
            c.save();
            if (left) c.scale(-1, 1, w / 2, 0);
            paint.setStyle(Paint.Style.STROKE);
            // The triangle: white border, blue outline, glossy light-blue body.
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(9 * u);
            paint.setColor(0xF2FFFFFF);
            c.drawPath(tri, paint);
            paint.setStrokeWidth(4 * u);
            paint.setColor(BLUE_EDGE);
            c.drawPath(tri, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(triFill);
            c.drawPath(tri, paint);
            paint.setShader(gloss);
            c.drawPath(tri, paint);
            paint.setShader(null);
            c.restore();
        }
    }

    /** The channels as one horizontal strip of columns of three, four columns to a page; WiiHome slides it a page at a time. */
    static final class Strip extends ViewGroup {
        static final int ROWS = WiiPages.ROWS, VISIBLE_COLS = WiiPages.COLS;
        final float u, room;
        final java.util.List<Tile> tiles = new java.util.ArrayList<>();
        private final Typeface font;
        private final Look look;
        /** Geometry from the last measure, in pixels. */
        int pitch, tileW, tileH, margin, gapY;

        Strip(Context ctx, float u, Typeface font, Look look) {
            super(ctx);
            this.u = u;
            this.room = 10 * u;
            this.font = font;
            this.look = look;
            setClipChildren(false);
            setClipToPadding(false);
        }

        /** Columns the strip has room for, empty slots included. */
        int columns() {
            return (tiles.size() + ROWS - 1) / ROWS;
        }

        void setCount(int n) {
            while (tiles.size() < n) {
                Tile t = new Tile(getContext(), u, font, look);
                tiles.add(t);
                addView(t);
            }
            while (tiles.size() > n) removeView(tiles.remove(tiles.size() - 1));
        }

        /** Left edge of a column's tile (no scroll), in strip coordinates. */
        float columnLeft(int col) {
            return margin + col * (float) pitch;
        }

        private void geometry(int w, int h) {
            pitch = Math.round(w * 0.21f);
            tileW = pitch - Math.round(w * 0.0125f);
            margin = Math.round((w - (3 * pitch + tileW)) / 2f);
            gapY = Math.round(21.6f * u);
            tileH = Math.round((h - 2 * room - 2 * gapY) / 3f);
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
            setMeasuredDimension(w, h);
            geometry(w, h);
            int cw = tileW + Math.round(2 * room), ch = tileH + Math.round(2 * room);
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            for (int i = 0; i < tiles.size(); i++) {
                int col = i / ROWS, row = i % ROWS;
                int x = Math.round(columnLeft(col) - room);
                int y = row * (tileH + gapY);
                Tile tile = tiles.get(i);
                tile.layout(x, y, x + tile.getMeasuredWidth(), y + tile.getMeasuredHeight());
            }
        }
    }

    /** The hand cursor: a plain view WiiHome adds on top of everything, sized from the screen height. */
    static ImageView newHand(Context ctx) {
        ImageView hand = new ImageView(ctx);
        hand.setImageResource(R.drawable.ic_wii_hand);
        hand.setAlpha(0f);
        hand.setTranslationZ(1000f);
        hand.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return hand;
    }

    /**
     * Screen Burn-in Reduction (WiiDim): a flat gray veil over the whole menu that fades in over 2 s and then breathes
     * very slightly. Stepped from a handler, a few times a second, and only redrawn when the shown level changes.
     */
    static final class Veil extends View {
        private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        private long since;
        private boolean night, on;
        private int shown = -1;
        private final Runnable step = new Runnable() {
            @Override
            public void run() {
                long t = android.os.SystemClock.uptimeMillis() - since;
                int level = WiiDim.level(WiiDim.alpha(night, t));
                if (level != shown) {
                    shown = level;
                    setAlpha(level / 255f);
                }
                handler.postDelayed(this, WiiDim.stepMs(t));
            }
        };

        Veil(Context ctx) {
            super(ctx);
            setFocusable(false);
            setClickable(false);
            setAlpha(0f);
            setVisibility(GONE);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setNight(false);
        }

        /** One flat colour: the view's alpha applies straight to it, no offscreen layer. */
        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }

        void setNight(boolean dark) {
            night = dark;
            setBackgroundColor(WiiDim.color(dark));
            shown = -1;
        }

        boolean isOn() {
            return on;
        }

        /** Whole minutes since the veil started (the pixel shift's clock). */
        long minutes() {
            return on ? (android.os.SystemClock.uptimeMillis() - since) / 60_000L : 0;
        }

        void start(boolean dark) {
            setNight(dark);
            since = android.os.SystemClock.uptimeMillis();
            on = true;
            setAlpha(0f);
            setVisibility(VISIBLE);
            handler.removeCallbacks(step);
            handler.post(step);
        }

        void stop() {
            on = false;
            handler.removeCallbacks(step);
            setVisibility(GONE);
            setAlpha(0f);
        }
    }

    /** The whole screen: places every child from the screen size. */
    static final class Root extends ViewGroup {
        /** Side buttons: diameter (of the screen height), and their sockets' radius over the button's. */
        static final float BUTTON = 0.155f, SOCKET = 1.34f, BUTTON_X = 0.091f, BUTTON_Y = 0.843f, BAR_TOP = 0.66f;
        final float u;
        final Look look = new Look();
        final Backdrop backdrop;
        final Bar bar;
        final Strip strip;
        final Arrow prev, next;
        final Clock clock;
        final TextView date;
        final Control os, sd, inputs;
        final Typeface font;

        Root(Context ctx, float screenHeightPx, Typeface font) {
            super(ctx);
            this.u = screenHeightPx / 1080f;
            this.font = font;
            setClipChildren(false);
            backdrop = new Backdrop(ctx, u, look);
            addView(backdrop);
            bar = new Bar(ctx, u, look);
            addView(bar);
            strip = new Strip(ctx, u, font, look);
            addView(strip);
            prev = new Arrow(ctx, true, u, look);
            addView(prev);
            next = new Arrow(ctx, false, u, look);
            addView(next);
            clock = new Clock(ctx, font, look);
            addView(clock);
            date = newDate(ctx, font, look);
            date.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 72 * u);
            addView(date);
            os = new Control(ctx, Control.OS, u, font, look);
            sd = new Control(ctx, Control.SD, u, font, look);
            inputs = new Control(ctx, Control.INPUTS, u, font, look);
            os.setContentDescription("Ajustes");
            sd.setContentDescription("Archivos");
            inputs.setContentDescription("Entradas");
            addView(os);
            addView(sd);
            addView(inputs);
        }

        /** The gray bold date line under the clock ("Mié 21/10"). */
        static TextView newDate(Context ctx, Typeface font, Look look) {
            TextView date = new TextView(ctx);
            date.setTypeface(font);
            date.getPaint().setFakeBoldText(true);
            date.setTextColor(look.p.text);
            date.setGravity(Gravity.CENTER);
            date.setSingleLine(true);
            date.setIncludeFontPadding(false);
            date.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            return date;
        }

        boolean isDark() {
            return look.p == Palette.DARK;
        }

        /** Switches every Wii view to the dark or the light palette and redraws them. */
        void setDark(boolean dark) {
            Palette p = dark ? Palette.DARK : Palette.LIGHT;
            if (look.p == p) return;
            look.p = p;
            date.setTextColor(p.text);
            for (Tile t : strip.tiles) t.applyLook();
            redraw(this);
        }

        static void redraw(View v) {
            v.invalidate();
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) redraw(g.getChildAt(i));
            }
        }

        Rect rectOf(View v, int w, int h) {
            float room = 10 * u;
            RectF r;
            if (v == backdrop) {
                r = new RectF(0, 0, w, h);
            } else if (v == bar) {
                r = new RectF(0, h * BAR_TOP, w, h);
            } else if (v == strip) {
                r = new RectF(0, h * 0.045f - room, w, h * 0.675f + room);
            } else if (v == prev || v == next) {
                // A tab flush with the screen edge, centred on the middle row, over the neighbouring page's sliver.
                float aw = 84 * u, ah = 168 * u, cy = h * 0.36f;
                r = v == prev ? new RectF(0, cy - ah / 2, aw, cy + ah / 2) : new RectF(w - aw, cy - ah / 2, w, cy + ah / 2);
            } else if (v == clock) {
                r = new RectF(w * 0.33f, h * 0.700f, w * 0.67f, h * 0.812f);
            } else if (v == date) {
                r = new RectF(w * 0.30f, h * 0.845f, w * 0.70f, h * 0.935f);
            } else if (v == os || v == inputs) {
                float d = h * BUTTON + 2 * 12 * u;
                float cx = v == os ? w * BUTTON_X : w * (1 - BUTTON_X), cy = h * BUTTON_Y;
                r = new RectF(cx - d / 2, cy - d / 2, cx + d / 2, cy + d / 2);
            } else if (v == sd) {
                float bw = w * 0.040f + 2 * 12 * u, bh = h * 0.095f + 2 * 12 * u;
                float cx = w * 0.205f, cy = h * 0.874f;
                r = new RectF(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2);
            } else {
                r = new RectF();
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
            if (changed) {
                float top = h * BAR_TOP;
                bar.setSockets(w * BUTTON_X, w * (1 - BUTTON_X), h * BUTTON_Y - top, h * BUTTON / 2 * SOCKET);
            }
        }
    }
}
