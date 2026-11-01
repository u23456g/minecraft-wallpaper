package dev.wallpapermenu.wallpaper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SteamWallpaperLocator {
    private static final String APP_ID = "431960";

    private SteamWallpaperLocator() {
    }

    public static List<Path> discover(List<Path> steamRoots) {
        Set<Path> roots = new LinkedHashSet<>();
        for (Path steamRoot : steamRoots) {
            if (steamRoot == null) {
                continue;
            }
            addLibraryRoots(roots, steamRoot);
            Path libraryFile = steamRoot.resolve("steamapps/libraryfolders.vdf");
            if (Files.isRegularFile(libraryFile)) {
                for (Path library : parseLibraryPaths(libraryFile)) {
                    addLibraryRoots(roots, library);
                }
            }
        }
        return List.copyOf(roots);
    }

    private static void addLibraryRoots(Set<Path> roots, Path library) {
        Path steamApps = library.resolve("steamapps");
        Path workshop = steamApps.resolve("workshop/content").resolve(APP_ID);
        if (Files.isDirectory(workshop)) {
            roots.add(workshop);
        }
        Path local = steamApps.resolve("common/Wallpaper Engine/projects/myprojects");
        if (Files.isDirectory(local)) {
            roots.add(local);
        }
    }

    static List<Path> parseLibraryPaths(Path libraryFile) {
        List<Path> paths = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(libraryFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("\"path\"")) {
                    continue;
                }
                int firstQuote = trimmed.indexOf('"', 6);
                int secondQuote = firstQuote < 0 ? -1 : trimmed.indexOf('"', firstQuote + 1);
                if (firstQuote >= 0 && secondQuote > firstQuote) {
                    String value = trimmed.substring(firstQuote + 1, secondQuote).replace("\\\\", "\\");
                    paths.add(Path.of(value));
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Steam may be closed or its library file may be temporarily incomplete.
        }
        return paths;
    }
}
