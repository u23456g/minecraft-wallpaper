package dev.wallpapermenu.wallpaper;

/** Pure Win32 message encoding; GLFW key numbers are not Windows virtual keys. */
public final class SceneInputMapping {
    private SceneInputMapping() { }

    public static int point(int x, int y) { return (y & 0xffff) << 16 | (x & 0xffff); }
    public static int buttonMask(int button) {
        return switch (button) { case 0 -> 1; case 1 -> 2; case 2 -> 16; default -> 0; };
    }
    public static int wheel(double delta) { return ((int) Math.round(delta) & 0xffff) << 16; }
    public static int virtualKey(int key) {
        if (key == 32 || key >= 48 && key <= 57 || key >= 65 && key <= 90) return key;
        if (key >= 290 && key <= 313) return 0x70 + key - 290;
        if (key >= 320 && key <= 329) return 0x60 + key - 320;
        return switch (key) {
            case 256 -> 0x1b; case 257, 335 -> 0x0d; case 258 -> 9; case 259 -> 8;
            case 260 -> 0x2d; case 261 -> 0x2e; case 262 -> 0x27; case 263 -> 0x25;
            case 264 -> 0x28; case 265 -> 0x26; case 266 -> 0x21; case 267 -> 0x22;
            case 268 -> 0x24; case 269 -> 0x23; case 280 -> 0x14; case 281 -> 0x91;
            case 282 -> 0x90; case 283 -> 0x2c; case 284 -> 0x13;
            case 340, 344 -> 0x10; case 341, 345 -> 0x11; case 342, 346 -> 0x12;
            case 343 -> 0x5b; case 347 -> 0x5c; case 348 -> 0x5d;
            case 330 -> 0x6e; case 331 -> 0x6f; case 332 -> 0x6a; case 333 -> 0x6d;
            case 334 -> 0x6b; case 336 -> 0xbb;
            case 39 -> 0xde; case 44 -> 0xbc; case 45 -> 0xbd; case 46 -> 0xbe;
            case 47 -> 0xbf; case 59 -> 0xba; case 61 -> 0xbb;
            case 91 -> 0xdb; case 92 -> 0xdc; case 93 -> 0xdd; case 96 -> 0xc0;
            default -> 0;
        };
    }
    public static long keyFlags(int scanCode, boolean up, boolean repeat, boolean alt) {
        return 1L | ((long) (scanCode & 0xff) << 16) | ((scanCode & 0x100) != 0 ? 1L << 24 : 0)
                | (alt ? 1L << 29 : 0) | (up || repeat ? 1L << 30 : 0) | (up ? 1L << 31 : 0);
    }
}
