package dev.wallpapermenu.wallpaper;

public final class CdpInputMapping {
    private CdpInputMapping() {
    }

    /** Converts GLFW modifier bits to the Chromium DevTools Protocol bit layout. */
    public static int modifiers(int glfwModifiers) {
        int cdpModifiers = 0;
        if ((glfwModifiers & 0x0001) != 0) cdpModifiers |= 2; // Shift
        if ((glfwModifiers & 0x0002) != 0) cdpModifiers |= 4; // Control
        if ((glfwModifiers & 0x0004) != 0) cdpModifiers |= 8; // Alt
        if ((glfwModifiers & 0x0008) != 0) cdpModifiers |= 1; // Meta / Super
        return cdpModifiers;
    }
}
