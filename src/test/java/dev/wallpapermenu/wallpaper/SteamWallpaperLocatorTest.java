package dev.wallpapermenu.wallpaper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamWallpaperLocatorTest {
    @TempDir
    Path tempDir;

    @Test
    void discoversWorkshopAndLocalProjectsAcrossSteamLibraries() throws IOException {
        Path steam = tempDir.resolve("Steam");
        Path library = tempDir.resolve("Games");
        Files.createDirectories(steam.resolve("steamapps"));
        Files.createDirectories(library.resolve("steamapps/workshop/content/431960"));
        Files.createDirectories(library.resolve("steamapps/common/Wallpaper Engine/projects/myprojects"));
        Files.writeString(steam.resolve("steamapps/libraryfolders.vdf"),
                "\"libraryfolders\" {\n  \"1\" {\n    \"path\" \"" + library.toString().replace("\\", "\\\\") + "\"\n  }\n}");

        List<Path> roots = SteamWallpaperLocator.discover(List.of(steam));

        assertEquals(2, roots.size());
        assertTrue(roots.contains(library.resolve("steamapps/workshop/content/431960")));
        assertTrue(roots.contains(library.resolve("steamapps/common/Wallpaper Engine/projects/myprojects")));
    }

    @Test
    void ignoresMissingOrMalformedLibraryFiles() throws IOException {
        Path steam = Files.createDirectory(tempDir.resolve("Steam"));
        Files.createDirectories(steam.resolve("steamapps"));
        Files.writeString(steam.resolve("steamapps/libraryfolders.vdf"), "not vdf");

        assertTrue(SteamWallpaperLocator.discover(List.of(steam)).isEmpty());
    }
}
