package dev.wallpapermenu.wallpaper;

public enum WallpaperType {
    VIDEO,
    WEB,
    SCENE,
    APPLICATION,
    IMAGE,
    UNKNOWN;

    public static WallpaperType fromProjectType(String type) {
        if (type == null) {
            return UNKNOWN;
        }
        return switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "video" -> VIDEO;
            case "web" -> WEB;
            case "scene" -> SCENE;
            case "application" -> APPLICATION;
            case "image" -> IMAGE;
            default -> UNKNOWN;
        };
    }

    public boolean isSelectable() {
        return this == VIDEO || this == WEB || this == SCENE;
    }
}
