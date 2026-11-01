package dev.wallpapermenu;

import dev.wallpapermenu.wallpaper.MediaSession;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Bottom-right "now playing" badge. Wallpaper Engine scenes with a music visualiser react to the
 * track that Windows reports, so the same media session is shown here as plain text.
 */
public final class NowPlayingOverlay {
    private NowPlayingOverlay() { }

    private static String lastLogged = "";

    public static void render(DrawContext context, TextRenderer textRenderer, int width, int height) {
        if (textRenderer == null || !WallpaperBackground.isShowing()) return;
        String track = System.getProperty("wallpapermenu.debugNowPlaying", "");
        if (track.isBlank()) track = MediaSession.displayText();
        if (track == null || track.isBlank()) return;
        if (!track.equals(lastLogged)) {
            lastLogged = track;
            WallpaperMenuClient.LOGGER.info("Now playing badge drawn: {} (scaled {}x{})", track, width, height);
        }
        Text label = Text.literal("♪ " + track);
        int labelWidth = textRenderer.getWidth(label);
        int x = Math.max(4, width - labelWidth - 8);
        int y = height - 14;
        context.fill(x - 4, y - 3, x + labelWidth + 4, y + 10, 0x66000000);
        context.drawTextWithShadow(textRenderer, label, x, y, 0xFFFFFFFF);
    }
}
