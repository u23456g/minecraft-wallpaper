package dev.wallpapermenu.wallpaper;

import java.util.Optional;

public final class WebInputMapping {
    private WebInputMapping() { }

    public static Optional<Point> map(double x, double y, int drawX, int drawY, int drawWidth, int drawHeight,
                                      int sourceWidth, int sourceHeight) {
        if (drawWidth <= 0 || drawHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0
                || x < drawX || y < drawY || x >= drawX + drawWidth || y >= drawY + drawHeight) {
            return Optional.empty();
        }
        return Optional.of(new Point((x - drawX) * sourceWidth / drawWidth,
                (y - drawY) * sourceHeight / drawHeight));
    }

    public record Point(double x, double y) { }
}
