package dev.wallpapermenu.wallpaper;

/**
 * Chooses a 16:9 capture size that matches the game framebuffer so wallpaper textures are not
 * upscaled (upscaling was the remaining softness after the black-bar fix).
 */
public final class FrameBudget {
    public static final int MIN_WIDTH = 1920;
    public static final int MAX_WIDTH = 2560;

    private FrameBudget() {
    }

    public static int[] captureSize(int framebufferWidth) {
        int width = clampEven(framebufferWidth);
        return new int[]{width, width * 9 / 16};
    }

    public static int clampEven(int framebufferWidth) {
        int width = Math.clamp(framebufferWidth, MIN_WIDTH, MAX_WIDTH);
        return width % 2 == 0 ? width : width - 1;
    }
}
