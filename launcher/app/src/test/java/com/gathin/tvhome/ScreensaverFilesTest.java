package com.gathin.tvhome;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for screensaver names and which file WiiDream plays. */
public class ScreensaverFilesTest {

    private Path tmp;

    @Before
    public void setUp() throws IOException {
        tmp = Files.createTempDirectory("screensaver-files");
    }

    @After
    public void tearDown() throws IOException {
        if (tmp == null) return;
        Files.walk(tmp).sorted(Comparator.reverseOrder()).forEach(p -> {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) { }
        });
    }

    @Test
    public void namesDropTheExtensionAndCapitaliseEachWord() {
        assertEquals("Ocean Sunset", ScreensaverFiles.displayName("ocean_sunset.mp4"));
        assertEquals("My Cool Clip", ScreensaverFiles.displayName("my-cool_clip.MP4"));
        assertEquals("Already There", ScreensaverFiles.displayName("Already_There.mp4"));
        assertEquals("A B", ScreensaverFiles.displayName("a__b.mp4"));
        assertEquals("Foo", ScreensaverFiles.displayName("foo_.mp4"));
        assertEquals("Ep.01 Intro", ScreensaverFiles.displayName("ep.01_intro.mp4"));
        assertEquals("Ñandú Azul", ScreensaverFiles.displayName("Ñandú_azul.mp4"));
        assertEquals("NASA Night", ScreensaverFiles.displayName("NASA_night.mp4"));
        assertEquals("Ocean Sunset", ScreensaverFiles.displayName("/tv/screensavers/ocean_sunset.mp4"));
        assertEquals("", ScreensaverFiles.displayName(null));
        assertEquals("", ScreensaverFiles.displayName(""));
        assertEquals("", ScreensaverFiles.displayName(".mp4"));
    }

    @Test
    public void listKeepsRealMp4sInNameOrder() throws IOException {
        File dir = dir();
        file(dir, "b.mp4", 8);
        file(dir, "A.mp4", 8);
        file(dir, "a.mp4", 8);
        file(dir, "notes.txt", 8);
        file(dir, "empty.mp4", 0);
        file(dir, "clip.MP4", 4);
        assertTrue(new File(dir, "nested").mkdir());
        file(new File(dir, "nested"), "inside.mp4", 8);
        List<File> clips = ScreensaverFiles.list(dir);
        assertEquals(4, clips.size());
        assertEquals("A.mp4", clips.get(0).getName());
        assertEquals("a.mp4", clips.get(1).getName());
        assertEquals("b.mp4", clips.get(2).getName());
        assertEquals("clip.MP4", clips.get(3).getName());
        assertTrue(ScreensaverFiles.list(null).isEmpty());
        assertTrue(ScreensaverFiles.list(new File(dir, "missing")).isEmpty());
    }

    @Test
    public void nothingChosenPlaysTheFirstClipThenTheLegacyFileThenBlack() throws IOException {
        File root = tmp.toFile();
        assertNull(ScreensaverFiles.resolve(root, null, true));
        file(root, ScreensaverFiles.LEGACY, 12);
        assertEquals(ScreensaverFiles.LEGACY, ScreensaverFiles.resolve(root, "", true).getName());
        File dir = dir();
        file(dir, "zeta.mp4", 4);
        file(dir, "alpha.mp4", 4);
        assertEquals("alpha.mp4", ScreensaverFiles.resolve(root, null, true).getName());
    }

    @Test
    public void aChoicePlaysThatClipAndBlackStaysBlack() throws IOException {
        File root = tmp.toFile();
        File dir = dir();
        file(dir, "alpha.mp4", 4);
        file(dir, "black.mp4", 4);
        file(root, ScreensaverFiles.LEGACY, 12);
        assertEquals("black.mp4", ScreensaverFiles.resolve(root, "black.mp4", true).getName());
        assertNull("Pantalla negra", ScreensaverFiles.resolve(root, ScreensaverFiles.BLACK, true));
        assertNull("missing explicit clip stays black", ScreensaverFiles.resolve(root, "gone.mp4", true));
        assertNull(ScreensaverFiles.resolve(root, "../" + ScreensaverFiles.LEGACY, true));
        assertNull(ScreensaverFiles.resolve(root, "nested/inside.mp4", true));
        assertNull(ScreensaverFiles.resolve(root, "alpha.mp4", false));
        assertNull(ScreensaverFiles.resolve(null, null, true));
    }

    private File dir() {
        File d = new File(tmp.toFile(), ScreensaverFiles.DIR);
        assertTrue(d.mkdirs() || d.isDirectory());
        return d;
    }

    private static void file(File dir, String name, int bytes) throws IOException {
        File f = new File(dir, name);
        if (bytes <= 0) {
            assertTrue(f.createNewFile());
            return;
        }
        byte[] data = new byte[bytes];
        data[0] = 1;
        Files.write(f.toPath(), data);
    }
}
