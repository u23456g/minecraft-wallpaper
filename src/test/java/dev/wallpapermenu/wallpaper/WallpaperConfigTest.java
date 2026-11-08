package dev.wallpapermenu.wallpaper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WallpaperConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void packDefaultIsAppliedOnlyWhenPlayerHasNoSelection() {
        WallpaperConfig config = new WallpaperConfig();
        config.defaultWallpaperId = "workshop-123";
        assertTrue(config.applyPackDefaults());
        assertEquals("workshop-123", config.selectedWallpaperId);
        assertFalse(config.applyPackDefaults());

        config.selectedWallpaperId = "player-choice";
        config.defaultWallpaperId = "pack-choice";
        assertFalse(config.applyPackDefaults());
        assertEquals("player-choice", config.selectedWallpaperId);
        assertEquals(Path.of("config", "wallpaper-menu", "packs"),
                WallpaperConfig.packDirectory(Path.of("config")));
    }

    @Test
    void loadsPortableManifestAndScansPlayerProvidedProject() throws IOException {
        Path manifest = WallpaperConfig.packManifest(tempDir);
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, "{\"defaultWallpaperId\":\"my-wallpaper\"}");
        Path project = Files.createDirectories(WallpaperConfig.packDirectory(tempDir).resolve("my-wallpaper"));
        Files.writeString(project.resolve("project.json"), "{\"type\":\"video\",\"title\":\"Local\"}");
        Files.write(project.resolve("video.mp4"), new byte[0]);
        WallpaperConfig config = new WallpaperConfig();
        config.loadPackManifest(tempDir);
        assertEquals("my-wallpaper", config.selectedWallpaperId);
        var entries = new WallpaperScanner().scan(config.wallpaperRoots(tempDir));
        assertEquals(1, entries.size());
        assertEquals(config.selectedWallpaperId, entries.getFirst().id());
        assertTrue(entries.getFirst().supported());
        config.selectedWallpaperId = "";
        config.packDefaultDisabled = true;
        config.loadPackManifest(tempDir);
        assertEquals("", config.selectedWallpaperId);
    }

    @Test
    void savesAndReloadsSettingsAndFallsBackOnInvalidJson() throws IOException {
        Path file = tempDir.resolve("config/wallpaper-menu.json");
        WallpaperConfig config = new WallpaperConfig();
        config.selectedWallpaperId = "wallpaper-42";
        config.workshopDirectory = tempDir.resolve("workshop").toString();
        config.dimPercent = 35;
        config.audioEnabled = true;

        config.save(file);
        WallpaperConfig loaded = WallpaperConfig.load(file);
        assertEquals("wallpaper-42", loaded.selectedWallpaperId);
        assertEquals(config.workshopDirectory, loaded.workshopDirectory);
        assertEquals(35, loaded.dimPercent);
        assertEquals(true, loaded.audioEnabled);
        assertEquals(1, loaded.wallpaperRoots().size());

        Files.writeString(file, "broken-json");
        WallpaperConfig fallback = WallpaperConfig.load(file);
        assertEquals("", fallback.selectedWallpaperId);
        assertFalse(fallback.audioEnabled);
    }
}
