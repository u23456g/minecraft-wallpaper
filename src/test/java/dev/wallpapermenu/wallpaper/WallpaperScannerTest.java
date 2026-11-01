package dev.wallpapermenu.wallpaper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WallpaperScannerTest {
    @TempDir
    Path tempDir;

    @Test
    void scansMp4AndLeavesWebAndMalformedFoldersUnselectable() throws IOException {
        Path video = Files.createDirectory(tempDir.resolve("video-id"));
        Files.writeString(video.resolve("project.json"), "{\"title\":\"Demo video\",\"type\":\"video\"}");
        Files.write(video.resolve("loop.MP4"), new byte[]{0});
        Files.write(video.resolve("preview.jpg"), new byte[]{0});
        Files.writeString(video.resolve("project.json"), "{\"title\":\"Demo video\",\"type\":\"video\",\"preview\":\"preview.jpg\"}");

        Path web = Files.createDirectory(tempDir.resolve("web-id"));
        Files.writeString(web.resolve("project.json"), "{\"title\":\"Web demo\",\"type\":\"web\",\"file\":\"index.html\"}");
        Files.writeString(web.resolve("index.html"), "<html><body>Demo</body></html>");

        Path scene = Files.createDirectory(tempDir.resolve("scene-id"));
        Files.writeString(scene.resolve("project.json"), "{\"title\":\"Scene demo\",\"type\":\"scene\",\"file\":\"scene.json\",\"preview\":\"preview.jpg\"}");
        Files.write(scene.resolve("preview.jpg"), new byte[]{0});
        Files.write(scene.resolve("scene.pkg"), new byte[]{'P','K','G','V','0','0','0','1'});

        Path sceneWithoutPackage = Files.createDirectory(tempDir.resolve("scene-missing-package"));
        Files.writeString(sceneWithoutPackage.resolve("project.json"), "{\"type\":\"scene\",\"file\":\"scene.json\"}");

        Path malformed = Files.createDirectory(tempDir.resolve("malformed"));
        Files.writeString(malformed.resolve("project.json"), "not-json");
        Files.createDirectory(tempDir.resolve("no-project"));

        List<WallpaperEntry> entries = new WallpaperScanner().scan(List.of(tempDir));

        assertEquals(4, entries.size());
        WallpaperEntry videoEntry = entries.stream().filter(entry -> entry.id().equals("video-id")).findFirst().orElseThrow();
        assertEquals("Demo video", videoEntry.title());
        assertEquals(WallpaperType.VIDEO, videoEntry.type());
        assertTrue(videoEntry.supported());
        assertEquals("loop.MP4", videoEntry.mediaFile().getFileName().toString());
        assertEquals("preview.jpg", videoEntry.previewFile().getFileName().toString());

        WallpaperEntry webEntry = entries.stream().filter(entry -> entry.id().equals("web-id")).findFirst().orElseThrow();
        assertEquals(WallpaperType.WEB, webEntry.type());
        assertTrue(webEntry.supported());
        assertEquals("index.html", webEntry.mediaFile().getFileName().toString());

        WallpaperEntry sceneEntry = entries.stream().filter(entry -> entry.id().equals("scene-id")).findFirst().orElseThrow();
        assertEquals(WallpaperType.SCENE, sceneEntry.type());
        assertTrue(sceneEntry.supported());
        assertEquals("scene.pkg", sceneEntry.mediaFile().getFileName().toString());
        assertEquals("preview.jpg", sceneEntry.previewFile().getFileName().toString());
        assertFalse(entries.stream().filter(entry -> entry.id().equals("scene-missing-package")).findFirst().orElseThrow().supported());

        Path missingEntry = Files.createDirectory(tempDir.resolve("web-missing"));
        Files.writeString(missingEntry.resolve("project.json"), "{\"type\":\"web\",\"file\":\"missing.html\"}");
        Path traversalEntry = Files.createDirectory(tempDir.resolve("web-traversal"));
        Files.writeString(traversalEntry.resolve("project.json"), "{\"type\":\"web\",\"file\":\"../outside.html\"}");
        Files.writeString(tempDir.resolve("outside.html"), "<html></html>");
        List<WallpaperEntry> rescanned = new WallpaperScanner().scan(List.of(tempDir));
        assertFalse(rescanned.stream().filter(entry -> entry.id().equals("web-missing")).findFirst().orElseThrow().supported());
        assertFalse(rescanned.stream().filter(entry -> entry.id().equals("web-traversal")).findFirst().orElseThrow().supported());
    }
}
