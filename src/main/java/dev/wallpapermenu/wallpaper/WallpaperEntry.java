package dev.wallpapermenu.wallpaper;

import java.nio.file.Path;

public record WallpaperEntry(
        String id,
        String title,
        WallpaperType type,
        Path directory,
        Path mediaFile,
        Path previewFile,
        boolean supported
) {
    public boolean isMissing() {
        return mediaFile == null || !java.nio.file.Files.isRegularFile(mediaFile);
    }
}
