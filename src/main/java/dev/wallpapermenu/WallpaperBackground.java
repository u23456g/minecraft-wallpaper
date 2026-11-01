package dev.wallpapermenu;

import dev.wallpapermenu.wallpaper.ChromiumWebDecoder;
import dev.wallpapermenu.wallpaper.FrameBudget;
import dev.wallpapermenu.wallpaper.SceneDecoder;
import dev.wallpapermenu.wallpaper.VideoDecoder;
import dev.wallpapermenu.wallpaper.WallpaperEntry;
import dev.wallpapermenu.wallpaper.WallpaperType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Path;

public final class WallpaperBackground {
    private static final Identifier TEXTURE = Identifier.of("wallpaper_menu", "video");
    private static VideoDecoder decoder;
    private static ChromiumWebDecoder webDecoder;
    private static SceneDecoder sceneDecoder;
    private static dev.wallpapermenu.wallpaper.WallpaperAudio audio;
    private static boolean requestedAudio;
    private static int requestedFps;
    private static FrameTexture texture;
    private static Path requested;
    private static boolean ready;
    private static boolean stopping;
    private static int drawX;
    private static int drawY;
    private static int drawWidth;
    private static int drawHeight;
    private static boolean webInputFocused;
    private static boolean sceneInputFocused;

    private WallpaperBackground() { }

    public static boolean isLoading(net.minecraft.client.gui.screen.Screen screen) {
        return screen instanceof net.minecraft.client.gui.screen.world.LevelLoadingScreen
                || screen instanceof net.minecraft.client.gui.screen.DownloadingTerrainScreen
                || screen instanceof net.minecraft.client.gui.screen.ProgressScreen;
    }

    public static boolean render(DrawContext context, int width, int height) {
        if (stopping || WallpaperMenuClient.CONFIG == null) return false;
        WallpaperEntry selectedEntry = WallpaperMenuClient.WALLPAPERS.stream()
                .filter(WallpaperEntry::supported)
                .filter(entry -> entry.id().equals(WallpaperMenuClient.CONFIG.selectedWallpaperId))
                .findFirst().orElse(null);
        Path selected = selectedEntry == null ? null : selectedEntry.mediaFile();
        int volume = WallpaperMenuClient.CONFIG.audioVolume();
        boolean audioEnabled = volume > 0;
        int fps = Math.clamp(WallpaperMenuClient.CONFIG.wallpaperFps, 30, 120);
        int[] capture = FrameBudget.captureSize(MinecraftClient.getInstance().getWindow().getFramebufferWidth());
        boolean rendererMissing = selected != null && decoder == null && webDecoder == null && sceneDecoder == null;
        if (!java.util.Objects.equals(selected, requested) || requestedFps != fps || rendererMissing) {
            close();
            requested = selected;
            requestedAudio = audioEnabled;
            requestedFps = fps;
            if (selected != null) {
                try {
                    switch (selectedEntry.type()) {
                        case WEB -> webDecoder = new ChromiumWebDecoder(selected, fps, volume);
                        case SCENE -> sceneDecoder = new SceneDecoder(selectedEntry.directory(),
                                capture[0], capture[1], fps, audioEnabled);
                        default -> {
                            decoder = new VideoDecoder(selected, capture[0], capture[1], fps);
                            if (audioEnabled) audio = new dev.wallpapermenu.wallpaper.WallpaperAudio(selected, volume);
                        }
                    }
                } catch (IOException exception) {
                    WallpaperMenuClient.LOGGER.warn("Wallpaper renderer unavailable; using vanilla background", exception);
                }
            }
        }
        if (requestedVolume != volume || audioEnabled != requestedAudio) applyAudioVolume(volume);
        if (webDecoder != null && webDecoder.failure() != null) {
            WallpaperMenuClient.LOGGER.warn("Web wallpaper rendering failed: {}; using vanilla background", webDecoder.failure());
            release();
            return false;
        }
        if (sceneDecoder != null && sceneDecoder.failure() != null) {
            WallpaperMenuClient.LOGGER.warn("Scene wallpaper rendering failed: {}; using vanilla background", sceneDecoder.failure());
            release();
            return false;
        }
        if (audio != null && audio.failure() != null) {
            WallpaperMenuClient.LOGGER.warn("Wallpaper audio unavailable: {}", audio.failure());
            audio.close();
            audio = null;
        }
        int sourceWidth;
        int sourceHeight;
        boolean bgra;
        byte[] frame;
        if (webDecoder != null) {
            sourceWidth = ChromiumWebDecoder.WIDTH;
            sourceHeight = ChromiumWebDecoder.HEIGHT;
            bgra = false;
            frame = webDecoder.pollFrame();
        } else if (sceneDecoder != null) {
            sourceWidth = sceneDecoder.width();
            sourceHeight = sceneDecoder.height();
            bgra = true;
            frame = sceneDecoder.pollFrame();
        } else {
            if (decoder == null) return false;
            if (decoder.failure() != null) {
                WallpaperMenuClient.LOGGER.warn("Video decode failed: {}; using vanilla background", decoder.failure());
                release();
                return false;
            }
            sourceWidth = decoder.width();
            sourceHeight = decoder.height();
            bgra = false;
            frame = decoder.pollFrame();
        }
        if (frame != null) {
            if (texture == null || texture.width() != sourceWidth || texture.height() != sourceHeight) {
                if (texture != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(TEXTURE);
                texture = new FrameTexture(sourceWidth, sourceHeight);
                MinecraftClient.getInstance().getTextureManager().registerTexture(TEXTURE, texture);
            }
            texture.upload(frame, bgra);
            if (!ready) WallpaperMenuClient.LOGGER.info("Wallpaper {} first frame uploaded: {}", sourceKind(), requested);
            ready = true;
        }
        if (!ready) return false;
        // Fill the screen for both sources; crop only the excess to remove side bars.
        float scale = Math.max(width / (float) sourceWidth, height / (float) sourceHeight);
        int drawWidth = Math.round(sourceWidth * scale);
        int drawHeight = Math.round(sourceHeight * scale);
        WallpaperBackground.drawX = (width - drawWidth) / 2;
        WallpaperBackground.drawY = (height - drawHeight) / 2;
        WallpaperBackground.drawWidth = drawWidth;
        WallpaperBackground.drawHeight = drawHeight;
        context.drawTexture(TEXTURE, drawX, drawY, drawWidth, drawHeight,
                0.0f, 0.0f, sourceWidth, sourceHeight, sourceWidth, sourceHeight);
        int alpha = Math.clamp(WallpaperMenuClient.CONFIG.dimPercent, 0, 100) * 255 / 100;
        context.fill(0, 0, width, height, alpha << 24);
        return true;
    }

    private static String sourceKind() {
        if (webDecoder != null) return "web";
        if (sceneDecoder != null) return "scene";
        return "video";
    }

    public static void clearWebInputFocus() {
        if (sceneDecoder != null && sceneInputFocused) sceneDecoder.clearInput();
        webInputFocused = false;
        sceneInputFocused = false;
    }

    public static boolean dispatchMouse(String type, double x, double y, int button, int clickCount) {
        if (!ready) return false;
        int sourceWidth = webDecoder != null ? ChromiumWebDecoder.WIDTH : sceneDecoder != null ? sceneDecoder.width() : 0;
        int sourceHeight = webDecoder != null ? ChromiumWebDecoder.HEIGHT : sceneDecoder != null ? sceneDecoder.height() : 0;
        var point = dev.wallpapermenu.wallpaper.WebInputMapping.map(x, y, drawX, drawY, drawWidth, drawHeight,
                sourceWidth, sourceHeight);
        if (point.isEmpty()) {
            if (type.equals("mousePressed")) clearWebInputFocus();
            return false;
        }
        if (webDecoder != null) {
            if (!webInputFocused && !type.equals("mousePressed")) return false;
            if (type.equals("mousePressed")) webInputFocused = true;
            if ((type.equals("mouseReleased") || type.equals("mouseMoved")) && !webInputFocused) return false;
            webDecoder.dispatchMouse(type, point.get().x(), point.get().y(), button, clickCount);
            return true;
        }
        if (sceneDecoder != null) {
            // Hover/parallax does not require a click; keys still require explicit focus.
            if (!sceneInputFocused && type.equals("mouseReleased")) return false;
            boolean sent = sceneDecoder.dispatchMouse(type, point.get().x(), point.get().y(), button, clickCount);
            if (type.equals("mousePressed")) sceneInputFocused = sent;
            return sent;
        }
        return false;
    }

    public static boolean dispatchScroll(double x, double y, double deltaX, double deltaY) {
        if (!ready || sceneDecoder == null && webDecoder == null) return false;
        int sourceWidth = webDecoder != null ? ChromiumWebDecoder.WIDTH : sceneDecoder.width();
        int sourceHeight = webDecoder != null ? ChromiumWebDecoder.HEIGHT : sceneDecoder.height();
        var point = dev.wallpapermenu.wallpaper.WebInputMapping.map(x, y, drawX, drawY, drawWidth, drawHeight,
                sourceWidth, sourceHeight);
        if (point.isEmpty() || (!webInputFocused && !sceneInputFocused)) return false;
        if (webDecoder != null) {
            webDecoder.dispatchScroll(point.get().x(), point.get().y(), deltaX, deltaY);
            return true;
        }
        return sceneDecoder.dispatchScroll(point.get().x(), point.get().y(), deltaX * 1.2, deltaY * 1.2);
    }

    public static boolean hasWebInputFocus() {
        return webDecoder != null && ready && webInputFocused;
    }

    public static boolean dispatchKey(String type, int keyCode, int scanCode, int modifiers) {
        if (webDecoder != null && hasWebInputFocus()) {
            webDecoder.dispatchKey(type, keyCode, scanCode, modifiers);
            return true;
        }
        if (sceneDecoder != null && ready && sceneInputFocused) {
            return sceneDecoder.dispatchKey(type, keyCode, scanCode, modifiers);
        }
        return false;
    }

    public static boolean dispatchText(String text) {
        if (webDecoder != null && hasWebInputFocus()) {
            webDecoder.dispatchText(text);
            return true;
        }
        if (sceneDecoder != null && ready && sceneInputFocused) {
            sceneDecoder.dispatchText(text);
            return true;
        }
        return false;
    }

    /** True while a wallpaper frame is actually being drawn behind the menu. */
    public static boolean isShowing() {
        return ready && requested != null;
    }

    private static void release() {
        if (webDecoder != null) webDecoder.close();
        webDecoder = null;
        if (sceneDecoder != null) sceneDecoder.close();
        sceneDecoder = null;
        if (audio != null) audio.close();
        audio = null;
        if (decoder != null) decoder.close();
        decoder = null;
        if (texture != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(TEXTURE);
        texture = null;
        ready = false;
        webInputFocused = false;
        sceneInputFocused = false;
    }

    private static int requestedVolume = -1;

    public static void setAudioEnabled(boolean enabled) { setAudioVolume(enabled ? 100 : 0); }

    public static void setAudioVolume(int percent) {
        if (WallpaperMenuClient.CONFIG == null) return;
        int volume = Math.clamp(percent, 0, 100);
        WallpaperMenuClient.CONFIG.audioVolumePercent = volume;
        WallpaperMenuClient.CONFIG.audioEnabled = volume > 0;
        applyAudioVolume(volume);
    }

    private static void applyAudioVolume(int volume) {
        boolean enabled = volume > 0;
        if (requestedAudio == enabled && requestedVolume == volume) return;
        requestedVolume = volume;
        if (sceneDecoder != null) sceneDecoder.setVolume(volume);
        if (webDecoder != null) webDecoder.setVolume(volume);
        if (!enabled && audio != null) { audio.close(); audio = null; }
        if (enabled && decoder != null && requested != null && audio == null)
            audio = new dev.wallpapermenu.wallpaper.WallpaperAudio(requested, volume);
        if (audio != null) audio.setVolume(volume);
        requestedAudio = enabled;
    }

    public static void shutdown() {
        stopping = true;
        close();
    }

    public static void close() {
        release();
        requested = null;
        requestedFps = 0;
        requestedVolume = -1;
        webInputFocused = false;
        sceneInputFocused = false;
    }
}
