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
    void exportedPresetWorksOnFreshPlayerAndContainsNoPersonalPaths() throws IOException {
        WallpaperConfig author = new WallpaperConfig();
        author.selectedWallpaperId = "123456";
        author.workshopDirectory = "private-author-directory";
        author.exportPackDefault(tempDir);
        String json = Files.readString(WallpaperConfig.packManifest(tempDir));
        assertFalse(json.contains("private-author-directory"));
        WallpaperConfig player = new WallpaperConfig();
        player.loadPackManifest(tempDir);
        assertEquals("123456", player.selectedWallpaperId);
        author.selectedWallpaperId = "second";
        author.exportPackDefault(tempDir);
        player = new WallpaperConfig();
        player.loadPackManifest(tempDir);
        assertEquals("second", player.selectedWallpaperId);
        author.selectedWallpaperId = "";
        org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> author.exportPackDefault(tempDir));
        assertTrue(Files.readString(WallpaperConfig.packManifest(tempDir)).contains("second"));
    }

    @Test
    void exportsAuthorSettingsWithoutOverwritingExistingPlayerSettings() throws IOException {
        WallpaperConfig author = new WallpaperConfig();
        author.selectedWallpaperId = "author-wallpaper";
        author.glassButtonsEnabled = true;
        author.glassButtonTransparency = 72;
        author.dimPercent = 40;
        author.titleScalePercent = 125;
        author.titleTransparency = 30;
        author.wallpaperFps = 90;
        author.audioVolumePercent = 25;
        author.exportPackDefault(tempDir);
        WallpaperConfig fresh = WallpaperConfig.load(tempDir.resolve("missing.json"));
        fresh.loadPackManifest(tempDir);
        assertEquals(72, fresh.glassButtonTransparency);
        assertTrue(fresh.glassButtonsEnabled);
        assertEquals(40, fresh.dimPercent);
        assertEquals(125, fresh.titleScalePercent);
        assertEquals(30, fresh.titleTransparency);
        assertEquals(90, fresh.wallpaperFps);
        assertEquals(25, fresh.audioVolume());
        Path personal = tempDir.resolve("personal.json");
        WallpaperConfig player = new WallpaperConfig();
        player.glassButtonTransparency = 10;
        player.save(personal);
        player = WallpaperConfig.load(personal);
        player.loadPackManifest(tempDir);
        assertEquals(10, player.glassButtonTransparency);
        assertEquals(0, player.audioVolume());
        Files.writeString(WallpaperConfig.packManifest(tempDir),
                "{\"settings\":{\"glassButtonTransparency\":999,\"titleScalePercent\":1,\"wallpaperFps\":\"bad\",\"dimPercent\":2.5}}");
        WallpaperConfig bounded = new WallpaperConfig();
        bounded.loadPackManifest(tempDir);
        assertEquals(100, bounded.glassButtonTransparency);
        assertEquals(50, bounded.titleScalePercent);
        assertEquals(60, bounded.wallpaperFps);
        assertEquals(15, bounded.dimPercent);
    }

    @Test
    void workshopExportDetectsMissingThenLocallyAvailableProject() throws IOException {
        Path directory = tempDir.resolve("steamapps/workshop/content/431960/123456");
        WallpaperEntry entry = new WallpaperEntry("123456", "Test wallpaper", WallpaperType.VIDEO,
                directory, directory.resolve("video.mp4"), null, true);
        WallpaperConfig author = new WallpaperConfig();
        author.selectedWallpaperId = entry.id();
        author.exportPackDefault(tempDir, entry);
        WallpaperConfig player = new WallpaperConfig();
        player.loadPackManifest(tempDir);
        assertEquals("https://steamcommunity.com/sharedfiles/filedetails/?id=123456", player.workshopUrl());
        assertEquals("Test wallpaper", player.packWallpaperTitle);
        assertTrue(player.needsWorkshop(java.util.List.of()));
        assertFalse(player.needsWorkshop(java.util.List.of(entry)));
        player.selectedWallpaperId = "personal-choice";
        assertFalse(player.needsWorkshop(java.util.List.of()));
        player.selectedWallpaperId = entry.id();
        player.packDefaultDisabled = true;
        assertFalse(player.needsWorkshop(java.util.List.of()));
        player.workshopItemId = "123&url=evil";
        assertEquals("", player.workshopUrl());
        WallpaperEntry local = new WallpaperEntry("123456", "Local", WallpaperType.VIDEO,
                tempDir.resolve("packs/123456"), null, null, true);
        author.exportPackDefault(tempDir, local);
        player = new WallpaperConfig();
        player.loadPackManifest(tempDir);
        assertEquals("", player.workshopUrl());
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
