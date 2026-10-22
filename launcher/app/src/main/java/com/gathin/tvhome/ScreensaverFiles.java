package com.gathin.tvhome;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Which screensaver clip to play, with no Android types so the rules run as plain JVM tests.
 * Clips live in getExternalFilesDir(null)/screensavers/*.mp4. Nothing chosen yet: the first of those
 * (by file name), then the older files/screensaver.mp4, then black. "black" is the Pantalla negra choice.
 */
final class ScreensaverFiles {
    private ScreensaverFiles() { }

    static final String DIR = "screensavers";
    static final String LEGACY = "screensaver.mp4";
    /** Stored choice for Pantalla negra. Not a file name: clips are stored with their ".mp4". */
    static final String BLACK = "black";

    /**
     * The clip to play, or null for a black screen.
     * {@code choice} null or empty is the automatic chain. {@link #BLACK} is black.
     * A named clip that is missing or not a plain file inside {@code screensavers/} is black too
     * (it does not silently switch to another video). {@code enabled} false is always black.
     */
    static File resolve(File externalRoot, String choice, boolean enabled) {
        if (!enabled || externalRoot == null) return null;
        if (BLACK.equals(choice)) return null;
        if (choice != null && !choice.isEmpty()) {
            return clip(new File(externalRoot, DIR), choice);
        }
        return automatic(externalRoot);
    }

    /** First screensavers clip, then the legacy file, then null. */
    static File automatic(File externalRoot) {
        if (externalRoot == null) return null;
        List<File> clips = list(new File(externalRoot, DIR));
        if (!clips.isEmpty()) return clips.get(0);
        File legacy = new File(externalRoot, LEGACY);
        if (legacy.isFile() && legacy.length() > 0) return legacy;
        return null;
    }

    /** Non-empty .mp4 files in this directory, ordered by name (case-insensitive, then exact). */
    static List<File> list(File dir) {
        List<File> out = new ArrayList<>();
        if (dir == null || !dir.isDirectory()) return out;
        File[] files = dir.listFiles();
        if (files == null) return out;
        for (File f : files) {
            if (!f.isFile() || f.length() <= 0) continue;
            if (!isMp4(f.getName())) continue;
            out.add(f);
        }
        Collections.sort(out, (a, b) -> {
            int c = String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName());
            return c != 0 ? c : a.getName().compareTo(b.getName());
        });
        return out;
    }

    /**
     * A name for the row: drop a trailing .mp4, turn "_" and "-" into spaces, and capitalise each word.
     * "ocean_sunset.mp4" is "Ocean Sunset". The rest of each word is left as it was written.
     */
    static String displayName(String filename) {
        if (filename == null || filename.isEmpty()) return "";
        String name = filename;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0 && slash < name.length() - 1) name = name.substring(slash + 1);
        if (name.length() >= 4 && name.regionMatches(true, name.length() - 4, ".mp4", 0, 4)) {
            name = name.substring(0, name.length() - 4);
        }
        StringBuilder sb = new StringBuilder(name.length());
        boolean cap = true;
        boolean gap = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_' || c == '-' || c == ' ' || c == '\t') {
                if (sb.length() > 0) gap = true;
                continue;
            }
            if (gap) {
                sb.append(' ');
                cap = true;
                gap = false;
            }
            sb.append(cap ? Character.toUpperCase(c) : c);
            cap = false;
        }
        return sb.toString();
    }

    /** The named clip inside dir, or null. Rejects paths that are not a single file name in that directory. */
    private static File clip(File dir, String name) {
        if (dir == null || name == null || name.isEmpty() || ".".equals(name) || "..".equals(name)) return null;
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) return null;
        if (!isMp4(name)) return null;
        File file = new File(dir, name);
        try {
            String dirPath = dir.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (!filePath.startsWith(dirPath + File.separator)) return null;
        } catch (IOException e) {
            return null;
        }
        if (!file.isFile() || file.length() <= 0) return null;
        return file;
    }

    private static boolean isMp4(String name) {
        return name != null && name.length() >= 4 && name.regionMatches(true, name.length() - 4, ".mp4", 0, 4);
    }
}
