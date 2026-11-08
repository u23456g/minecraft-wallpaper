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
    private boolean workshopScanRunning, workshopNoticeShown;
    private long nextWorkshopScan;
    private boolean downloadAttempted;
    public static volatile boolean workshopDownloadRunning;
    public static volatile String verifiedWorkshopDownload = "";
    public static volatile String workshopDownloadStatus = "等待 Steam 下载";

    public static void requestWorkshopDownload() {
        if (workshopDownloadRunning || !CONFIG.needsWorkshop(WALLPAPERS)) return;
        workshopDownloadRunning = true;
        verifiedWorkshopDownload = "";
        workshopDownloadStatus = "正在请求 Steam 下载…";
        String item = CONFIG.workshopItemId;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                dev.wallpapermenu.wallpaper.SteamWorkshopDownload.request(defaultSteamRoots(), item);
                verifiedWorkshopDownload = item;
                workshopDownloadStatus = "Steam 报告下载成功，等待本地扫描…";
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                workshopDownloadStatus = "下载等待已中断，可重试";
            } catch (Exception exception) {
                workshopDownloadStatus = exception.getMessage();
                LOGGER.warn("Steam Workshop download request failed", exception);
            } finally {
                workshopDownloadRunning = false;
            }
        });
    }
    private volatile boolean stopping;
    public static volatile boolean initialScanComplete;
    public static volatile WallpaperConfig CONFIG;
    public static volatile List<WallpaperEntry> WALLPAPERS = List.of();

    @Override
    public void onInitializeClient() {
        dev.wallpapermenu.wallpaper.MediaSession.start();
        var configFile = FabricLoader.getInstance().getConfigDir().resolve("wallpaper-menu.json");
        CONFIG = WallpaperConfig.load(configFile);
        applyPackManifest(FabricLoader.getInstance().getConfigDir());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            checkWorkshop(client);
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
            stopping = true;
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
                    initialScanComplete = true;
                    LOGGER.info("Wallpaper Menu found {} supported wallpaper(s)", WALLPAPERS.stream().filter(WallpaperEntry::supported).count());
                })
                .exceptionally(error -> {
                    initialScanComplete = true;
                    LOGGER.warn("Wallpaper scan failed; keeping vanilla menu background", error);
                    return null;
                });
    }

    private void checkWorkshop(net.minecraft.client.MinecraftClient client) {
        if (stopping || !initialScanComplete || !CONFIG.needsWorkshop(WALLPAPERS)) return;
        if (!downloadAttempted) {
            downloadAttempted = true;
            requestWorkshopDownload();
        }
        if (!workshopNoticeShown && client.currentScreen instanceof net.minecraft.client.gui.screen.TitleScreen) {
            workshopNoticeShown = true;
            client.setScreen(new WorkshopWallpaperScreen(client.currentScreen));
        }
        long now = System.nanoTime();
        if (workshopScanRunning || now < nextWorkshopScan) return;
        workshopScanRunning = true;
        nextWorkshopScan = now + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        var roots = CONFIG.wallpaperRoots(FabricLoader.getInstance().getConfigDir());
        String wanted = CONFIG.selectedWallpaperId;
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            var all = new java.util.LinkedHashSet<>(roots);
            all.addAll(SteamWallpaperLocator.discover(defaultSteamRoots()));
            return new WallpaperScanner().scan(List.copyOf(all));
        }).whenComplete((entries, error) -> client.execute(() -> {
            workshopScanRunning = false;
            if (stopping) return;
            if (error != null) {
                LOGGER.debug("Workshop wallpaper detection failed; will retry", error);
            } else if (CONFIG.needsWorkshop(WALLPAPERS) && wanted.equals(CONFIG.selectedWallpaperId)
                    && wanted.equals(verifiedWorkshopDownload) && !workshopDownloadRunning
                    && entries.stream().anyMatch(entry -> entry.supported() && wanted.equals(entry.id()))) {
                WALLPAPERS = List.copyOf(entries);
                LOGGER.info("Workshop wallpaper {} is now locally available", wanted);
            }
        }));
    }

    private static void applyPackManifest(Path configDirectory) {
        Path manifest = WallpaperConfig.packManifest(configDirectory);
        if (!java.nio.file.Files.isRegularFile(manifest)) return;
        try {
            CONFIG.loadPackManifest(configDirectory);
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
