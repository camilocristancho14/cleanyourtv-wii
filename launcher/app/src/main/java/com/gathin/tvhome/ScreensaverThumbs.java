package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;

import java.io.File;

/** One frame of a screensaver clip, for the picker's row. Call off the main thread: decoding can take a moment. */
final class ScreensaverThumbs {
    private ScreensaverThumbs() { }

    /** A frame about one second in, scaled down to maxWidth. Null if the file will not decode. */
    static Bitmap frame(File file, int maxWidth) {
        if (file == null || maxWidth < 1) return null;
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(file.getAbsolutePath());
            Bitmap raw = r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (raw == null) raw = r.getFrameAtTime(-1L);
            if (raw == null) return null;
            return scale(raw, maxWidth);
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                r.release();
            } catch (Throwable ignored) { }
        }
    }

    private static Bitmap scale(Bitmap raw, int maxWidth) {
        if (raw.getWidth() <= maxWidth) return raw;
        int h = Math.max(1, Math.round(raw.getHeight() * (maxWidth / (float) raw.getWidth())));
        Bitmap scaled = Bitmap.createScaledBitmap(raw, maxWidth, h, true);
        if (scaled != raw) raw.recycle();
        return scaled;
    }
}
