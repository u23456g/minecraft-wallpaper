package dev.wallpapermenu.wallpaper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class WallpaperScanner {
    public List<WallpaperEntry> scan(List<Path> roots) {
        List<WallpaperEntry> entries = new ArrayList<>();
        for (Path root : roots) {
            if (root == null || !Files.isDirectory(root)) {
                continue;
            }
            try (var children = Files.list(root)) {
                children.filter(Files::isDirectory)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .map(this::readEntry)
                        .flatMap(Optional::stream)
                        .forEach(entries::add);
            } catch (IOException ignored) {
                // A missing or unreadable source is a degraded-mode condition, not a crash.
            }
        }
        return entries;
    }

    private Optional<WallpaperEntry> readEntry(Path directory) {
        Path projectFile = directory.resolve("project.json");
        if (!Files.isRegularFile(projectFile)) {
            return Optional.empty();
        }
        try {
            JsonObject project = JsonParser.parseString(Files.readString(projectFile, StandardCharsets.UTF_8)).getAsJsonObject();
            String id = directory.getFileName().toString();
            String title = project.has("title") && !project.get("title").isJsonNull()
                    ? project.get("title").getAsString()
                    : id;
            WallpaperType type = WallpaperType.fromProjectType(
                    project.has("type") && !project.get("type").isJsonNull() ? project.get("type").getAsString() : null);
            Path mediaFile = switch (type) {
                case VIDEO -> findMp4(directory);
                case WEB -> findWebEntry(directory, project);
                case SCENE -> findScenePackage(directory, project);
                default -> null;
            };
            Path previewFile = findPreview(directory, project);
            return Optional.of(new WallpaperEntry(id, title, type, directory, mediaFile, previewFile,
                    type.isSelectable() && mediaFile != null));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Path findWebEntry(Path directory, JsonObject project) {
        if (!project.has("file") || project.get("file").isJsonNull()) return null;
        String relative = project.get("file").getAsString();
        Path entry = directory.resolve(relative).normalize();
        return entry.startsWith(directory.normalize()) && Files.isRegularFile(entry) ? entry : null;
    }

    private Path findScenePackage(Path directory, JsonObject project) {
        if (!project.has("file") || project.get("file").isJsonNull()) return null;
        String relative = project.get("file").getAsString();
        Path scene = directory.resolve(relative).normalize();
        if (!scene.startsWith(directory.normalize())) return null;
        Path packageFile = scene.resolveSibling("scene.pkg");
        return Files.isRegularFile(packageFile) ? packageFile : null;
    }

    private Path findPreview(Path directory, JsonObject project) {
        if (project.has("preview") && !project.get("preview").isJsonNull()) {
            Path preview = directory.resolve(project.get("preview").getAsString()).normalize();
            if (preview.startsWith(directory.normalize()) && Files.isRegularFile(preview)) return preview;
        }
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif");
                    })
                    .findFirst().orElse(null);
        } catch (IOException ignored) {
            return null;
        }
    }

    private Path findMp4(Path directory) {
        try (var files = Files.walk(directory, 2)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp4"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException ignored) {
            return null;
        }
    }
}
