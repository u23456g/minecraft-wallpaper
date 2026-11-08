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
    public String defaultWallpaperId = "";
    public boolean packDefaultDisabled;
    public String workshopItemId = "";
    public String packWallpaperTitle = "";

    public String workshopUrl() {
        return workshopItemId != null && workshopItemId.matches("[0-9]{1,20}")
                ? "https://steamcommunity.com/sharedfiles/filedetails/?id=" + workshopItemId : "";
    }

    public boolean needsWorkshop(List<WallpaperEntry> entries) {
        return !packDefaultDisabled && !workshopUrl().isEmpty()
                && workshopItemId.equals(selectedWallpaperId)
                && entries.stream().noneMatch(entry -> entry.supported() && entry.id().equals(selectedWallpaperId));
    }
    private transient boolean personalSettingsLoaded;
    private static final java.util.Map<String, int[]> PACK_RANGES = java.util.Map.of(
            "dimPercent", new int[]{0, 100}, "wallpaperFps", new int[]{30, 120},
            "audioVolumePercent", new int[]{0, 100}, "glassButtonTransparency", new int[]{0, 100},
            "titleScalePercent", new int[]{50, 150}, "titleTransparency", new int[]{0, 100});
    public int dimPercent = 15;

    public boolean applyPackDefaults() {
        if (packDefaultDisabled) return false;
        if (selectedWallpaperId != null && !selectedWallpaperId.isBlank()) return false;
        if (defaultWallpaperId == null || defaultWallpaperId.isBlank()) return false;
        selectedWallpaperId = defaultWallpaperId.trim();
        return true;
    }

    public void loadPackManifest(Path configDirectory) throws IOException {
        Path file = packManifest(configDirectory);
        if (!Files.isRegularFile(file)) return;
        var json = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        var workshop = json.get("workshopItemId");
        workshopItemId = workshop != null && workshop.isJsonPrimitive() && workshop.getAsJsonPrimitive().isString()
                ? workshop.getAsString() : "";
        var title = json.get("wallpaperTitle");
        packWallpaperTitle = title != null && title.isJsonPrimitive() && title.getAsJsonPrimitive().isString()
                ? title.getAsString() : "";
        var id = json.get("defaultWallpaperId");
        if (id != null && id.isJsonPrimitive() && id.getAsJsonPrimitive().isString()) {
            defaultWallpaperId = id.getAsString();
            applyPackDefaults();
        }
        if (!personalSettingsLoaded && !packDefaultDisabled && json.has("settings") && json.get("settings").isJsonObject()) {
            var settings = json.getAsJsonObject("settings");
            dimPercent = packInt(settings, "dimPercent", dimPercent);
            wallpaperFps = packInt(settings, "wallpaperFps", wallpaperFps);
            audioVolumePercent = packInt(settings, "audioVolumePercent", audioVolume());
            glassButtonTransparency = packInt(settings, "glassButtonTransparency", glassButtonTransparency);
            titleScalePercent = packInt(settings, "titleScalePercent", titleScalePercent);
            titleTransparency = packInt(settings, "titleTransparency", titleTransparency);
            var glass = settings.get("glassButtonsEnabled");
            if (glass != null && glass.isJsonPrimitive() && glass.getAsJsonPrimitive().isBoolean()) {
                glassButtonsEnabled = glass.getAsBoolean();
            }
        }
    }

    private static int packInt(com.google.gson.JsonObject settings, String name, int fallback) {
        var value = settings.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return fallback;
        try {
            int[] range = PACK_RANGES.get(name);
            return Math.clamp(value.getAsBigDecimal().intValueExact(), range[0], range[1]);
        } catch (ArithmeticException | NumberFormatException exception) {
            return fallback;
        }
    }

    public void exportPackDefault(Path configDirectory) throws IOException {
        exportPackDefault(configDirectory, null);
    }

    public void exportPackDefault(Path configDirectory, WallpaperEntry entry) throws IOException {
        if (selectedWallpaperId == null || selectedWallpaperId.isBlank()) {
            throw new IOException("请先选择一张壁纸");
        }
        var manifest = new com.google.gson.JsonObject();
        manifest.addProperty("defaultWallpaperId", selectedWallpaperId);
        if (entry != null && selectedWallpaperId.equals(entry.id())) {
            manifest.addProperty("wallpaperTitle", entry.title());
            Path parent = entry.directory().toAbsolutePath().normalize().getParent();
            if (selectedWallpaperId.matches("[0-9]{1,20}") && parent != null
                    && parent.toString().replace('\\', '/').toLowerCase(java.util.Locale.ROOT).endsWith("/workshop/content/431960")) {
                manifest.addProperty("workshopItemId", selectedWallpaperId);
            }
        }
        var settings = new com.google.gson.JsonObject();
        settings.addProperty("dimPercent", dimPercent);
        settings.addProperty("wallpaperFps", wallpaperFps);
        settings.addProperty("audioVolumePercent", audioVolume());
        settings.addProperty("glassButtonsEnabled", glassButtonsEnabled);
        settings.addProperty("glassButtonTransparency", glassButtonTransparency);
        settings.addProperty("titleScalePercent", titleScalePercent);
        settings.addProperty("titleTransparency", titleTransparency);
        manifest.add("settings", settings);
        Path target = packManifest(configDirectory);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "pack-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(manifest), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static Path packDirectory(Path configDirectory) {
        return configDirectory.resolve("wallpaper-menu/packs");
    }

    public static Path packManifest(Path configDirectory) {
        return configDirectory.resolve("wallpaper-menu/pack.json");
    }
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
            if (config == null) return new WallpaperConfig();
            config.personalSettingsLoaded = true;
            return config;
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

    public List<Path> wallpaperRoots(Path configDirectory) {
        java.util.ArrayList<Path> roots = new java.util.ArrayList<>();
        roots.add(packDirectory(configDirectory));
        roots.addAll(wallpaperRoots());
        return List.copyOf(roots);
    }
}
