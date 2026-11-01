package dev.wallpapermenu.wallpaper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WallpaperConfigTest {
    @TempDir
    Path tempDir;

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
