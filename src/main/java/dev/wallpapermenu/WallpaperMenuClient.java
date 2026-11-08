package dev.wallpapermenu;

import dev.wallpapermenu.wallpaper.WallpaperConfig;
import dev.wallpapermenu.wallpaper.WallpaperEntry;
import dev.wallpapermenu.wallpaper.SteamWallpaperLocator;
import dev.wallpapermenu.wallpaper.WallpaperScanner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.GameMenuScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;

public final class WallpaperMenuClient implements ClientModInitializer {
    public static final String MOD_ID = "wallpaper_menu";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private Double originalMusicVolume;
    public static volatile WallpaperConfig CONFIG;
    public static volatile List<WallpaperEntry> WALLPAPERS = List.of();

    @Override
    public void onInitializeClient() {
        dev.wallpapermenu.wallpaper.MediaSession.start();
        var configFile = FabricLoader.getInstance().getConfigDir().resolve("wallpaper-menu.json");
        CONFIG = WallpaperConfig.load(configFile);
        applyPackManifest(FabricLoader.getInstance().getConfigDir());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var music = client.options.getSoundVolumeOption(net.minecraft.sound.SoundCategory.MUSIC);
            if (CONFIG.audioVolume() > 0) {
                if (originalMusicVolume == null) originalMusicVolume = music.getValue();
                if (music.getValue() != 0.0) music.setValue(0.0);
            } else if (originalMusicVolume != null) {
                music.setValue(originalMusicVolume);
                originalMusicVolume = null;
            }
            if (!client.isWindowFocused()) WallpaperBackground.clearWebInputFocus();
            if (client.currentScreen == null || client.currentScreen instanceof GameMenuScreen) WallpaperBackground.close();
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            WallpaperBackground.shutdown();
            if (originalMusicVolume != null) {
                client.options.getSoundVolumeOption(net.minecraft.sound.SoundCategory.MUSIC).setValue(originalMusicVolume);
                client.options.write();
            }
            GlassButtons.close();
        });
        List<Path> steamRoots = defaultSteamRoots();
        if (CONFIG.workshopDirectory.isBlank() && CONFIG.localDirectory.isBlank()) {
            List<Path> discovered = SteamWallpaperLocator.discover(steamRoots);
            CONFIG.workshopDirectory = discovered.stream()
                    .filter(path -> path.toString().replace('\\', '/').contains("workshop/content/431960"))
                    .map(Path::toString)
                    .collect(java.util.stream.Collectors.joining(";"));
            CONFIG.localDirectory = discovered.stream()
                    .filter(path -> path.toString().replace('\\', '/').contains("Wallpaper Engine/projects/myprojects"))
                    .map(Path::toString)
                    .collect(java.util.stream.Collectors.joining(";"));
        }
        try {
            CONFIG.save(configFile);
        } catch (java.io.IOException exception) {
            LOGGER.warn("Could not save wallpaper config; using in-memory settings", exception);
        }
        WallpaperConfig config = CONFIG;
        Path configDirectory = FabricLoader.getInstance().getConfigDir();
        java.util.concurrent.CompletableFuture.supplyAsync(() -> new WallpaperScanner().scan(config.wallpaperRoots(configDirectory)))
                .thenAccept(entries -> {
                    WALLPAPERS = List.copyOf(entries);
                    LOGGER.info("Wallpaper Menu found {} supported wallpaper(s)", WALLPAPERS.stream().filter(WallpaperEntry::supported).count());
                })
                .exceptionally(error -> {
                    LOGGER.warn("Wallpaper scan failed; keeping vanilla menu background", error);
                    return null;
                });
    }

    private static void applyPackManifest(Path configDirectory) {
        Path manifest = WallpaperConfig.packManifest(configDirectory);
        if (!java.nio.file.Files.isRegularFile(manifest)) return;
        try {
            var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(manifest)).getAsJsonObject();
            if (json.has("defaultWallpaperId") && json.get("defaultWallpaperId").isJsonPrimitive()) {
                CONFIG.defaultWallpaperId = json.get("defaultWallpaperId").getAsString();
                CONFIG.applyPackDefaults();
            }
        } catch (Exception exception) {
            LOGGER.warn("Ignoring invalid wallpaper pack manifest {}", manifest, exception);
        }
    }

    static List<Path> defaultSteamRoots() {
        List<Path> roots = new java.util.ArrayList<>();
        String programFilesX86 = System.getenv("PROGRAMFILES(X86)");
        String programFiles = System.getenv("PROGRAMFILES");
        String userProfile = System.getenv("USERPROFILE");
        if (programFilesX86 != null && !programFilesX86.isBlank()) {
            roots.add(Path.of(programFilesX86, "Steam"));
        }
        if (programFiles != null && !programFiles.isBlank()) {
            roots.add(Path.of(programFiles, "Steam"));
        }
        if (userProfile != null && !userProfile.isBlank()) {
            roots.add(Path.of(userProfile, "Steam"));
            roots.add(Path.of(userProfile, "AppData", "Local", "Programs", "Steam"));
        }
        Path registrySteam = steamInstallPathFromRegistry();
        if (registrySteam != null) {
            roots.add(registrySteam);
        }
        return roots;
    }

    private static Path steamInstallPathFromRegistry() {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            return null;
        }
        for (String key : List.of(
                "HKCU\\Software\\Valve\\Steam",
                "HKLM\\SOFTWARE\\WOW6432Node\\Valve\\Steam",
                "HKLM\\SOFTWARE\\Valve\\Steam")) {
            Path path = queryRegistryPath(key);
            if (path != null) {
                return path;
            }
        }
        return null;
    }

    private static Path queryRegistryPath(String key) {
        try {
            Process process = new ProcessBuilder("reg", "query", key, "/v", "SteamPath")
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("SteamPath") && trimmed.contains("REG_")) {
                        String[] columns = trimmed.split("\\s{2,}");
                        if (columns.length >= 3 && !columns[2].isBlank()) {
                            process.waitFor();
                            return Path.of(columns[2].trim());
                        }
                    }
                }
            }
            process.waitFor();
        } catch (IOException exception) {
            LOGGER.debug("Steam registry lookup unavailable for {}", key);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.debug("Steam registry lookup interrupted for {}", key);
        } catch (RuntimeException exception) {
            LOGGER.debug("Steam registry path was invalid for {}", key, exception);
        }
        return null;
    }
}
