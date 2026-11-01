package dev.wallpapermenu.wallpaper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class WallpaperConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String selectedWallpaperId = "";
    public String workshopDirectory = "";
    public String localDirectory = "";
    public int dimPercent = 15;
    public int wallpaperFps = 60;
    public boolean audioEnabled; // Legacy configuration migration only.
    public int audioVolumePercent = -1;

    public int audioVolume() {
        return audioVolumePercent < 0 ? (audioEnabled ? 100 : 0) : Math.clamp(audioVolumePercent, 0, 100);
    }
    public boolean glassButtonsEnabled;
    public int glassButtonTransparency = 35;
    public int titleScalePercent = 100;
    public int titleTransparency = 0;

    public static WallpaperConfig load(Path file) {
        if (!Files.isRegularFile(file)) {
            return new WallpaperConfig();
        }
        try {
            WallpaperConfig config = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), WallpaperConfig.class);
            return config == null ? new WallpaperConfig() : config;
        } catch (Exception ignored) {
            return new WallpaperConfig();
        }
    }

    public void save(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
    }

    public List<Path> wallpaperRoots() {
        return List.of(workshopDirectory, localDirectory).stream()
                .filter(path -> path != null && !path.isBlank())
                .flatMap(path -> java.util.Arrays.stream(path.split(";")))
                .map(String::trim)
                .filter(path -> !path.isBlank())
                .map(Path::of)
                .toList();
    }
}
