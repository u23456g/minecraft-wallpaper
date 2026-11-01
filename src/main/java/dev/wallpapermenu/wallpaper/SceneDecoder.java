package dev.wallpapermenu.wallpaper;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinGDI;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.ptr.PointerByReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Renders a Wallpaper Engine {@code scene.pkg} by letting the engine draw it into a hidden
 * offscreen window and grabbing that window with {@code PrintWindow(PW_RENDERFULLCONTENT)}.
 *
 * <p>GDI BitBlt based capture (ffmpeg gdigrab) returns black for this GPU-rendered window and
 * cannot see occluded windows, so it is not usable here. PrintWindow was verified to return the
 * scene pixels both while occluded and while positioned outside the visible desktop.
 */
public final class SceneDecoder implements AutoCloseable {
    private static final int OFFSCREEN_X = 4000;
    private static final int OFFSCREEN_Y = 2000;
    private static final long WINDOW_WAIT_MS = 15_000L;
    private static final int PRINT_WINDOW_RENDER_FULL_CONTENT = 2;
    private static final int GWL_STYLE = -16;
    private static final int GWL_EXSTYLE = -20;
    private static final int WS_CAPTION = 0x00C00000;
    private static final int WS_THICKFRAME = 0x00040000;
    private static final int WS_SYSMENU = 0x00080000;
    private static final int WS_MINIMIZEBOX = 0x00020000;
    private static final int WS_MAXIMIZEBOX = 0x00010000;
    private static final int WS_EX_APPWINDOW = 0x00040000;
    private static final int WS_EX_TOOLWINDOW = 0x00000080;
    private static final int WS_EX_NOACTIVATE = 0x08000000;
    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOMOVE = 0x0002;
    private static final int SWP_NOACTIVATE = 0x0010;
    private static final int SWP_NOOWNERZORDER = 0x0200;
    private static final int SWP_FRAMECHANGED = 0x0020;
    private static final int HWND_TOP = 0;

    private final AtomicReference<byte[]> latest = new AtomicReference<>();
    private final Thread worker;
    private final String windowTitle;
    private final Path projectFile;
    private final boolean audioEnabled;
    private final int width;
    private final int height;
    private volatile boolean closed;
    private final Thread shutdownHook = new Thread(this::close, "wallpaper-scene-shutdown");
    private volatile String failure;

    public SceneDecoder(Path projectDirectory, int width, int height, int fps, boolean audioEnabled) throws IOException {
        this.width = width;
        this.height = height;
        this.audioEnabled = audioEnabled;
        Path projectFile = projectDirectory.resolve("project.json");
        if (!Files.isRegularFile(projectFile)) {
            throw new IOException("project.json missing for scene wallpaper: " + projectDirectory);
        }
        this.projectFile = projectFile;
        this.windowTitle = "WallpaperMenu Scene " + Integer.toHexString(projectDirectory.toString().hashCode())
                + "-" + System.nanoTime();
        int safeFps = Math.clamp(fps, 15, 120);
        openScene(projectFile, audioEnabled);
        this.worker = new Thread(() -> captureLoop(safeFps), "wallpaper-scene-decoder");
        this.worker.setDaemon(true);
        this.worker.start();
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }


    private static Path engineExecutable() {
        List<Path> candidates = new ArrayList<>();
        String programFiles = System.getenv("ProgramFiles(X86)");
        String programFiles64 = System.getenv("ProgramFiles");
        String[] drives = {"E:", "D:", "C:", "F:"};
        for (String drive : drives) {
            candidates.add(Path.of(drive + "\\Steam\\steamapps\\common\\wallpaper_engine\\wallpaper64.exe"));
            candidates.add(Path.of(drive + "\\SteamLibrary\\steamapps\\common\\wallpaper_engine\\wallpaper64.exe"));
        }
        for (String root : new String[]{programFiles, programFiles64}) {
            if (root != null) {
                candidates.add(Path.of(root, "Steam", "steamapps", "common", "wallpaper_engine", "wallpaper64.exe"));
            }
        }
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }

    private void openScene(Path projectFile, boolean audioEnabled) throws IOException {
        Path executable = engineExecutable();
        if (executable == null) {
            throw new IOException("Wallpaper Engine executable not found; scene wallpapers need it installed");
        }
        List<String> command = new ArrayList<>(List.of(executable.toString(),
                "-control", "openWallpaper",
                "-file", projectFile.toAbsolutePath().toString(),
                "-playInWindow", windowTitle,
                "-width", Integer.toString(width),
                "-height", Integer.toString(height),
                "-x", Integer.toString(OFFSCREEN_X),
                "-y", Integer.toString(OFFSCREEN_Y)));
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        long deadline = System.currentTimeMillis() + WINDOW_WAIT_MS;
        while (System.currentTimeMillis() < deadline && !closed) {
            if (findWindow() != null) break;
            try {
                Thread.sleep(150L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        WinDef.HWND window = findWindow();
        if (window == null) {
            throw new IOException("Wallpaper Engine did not open the scene window in time");
        }
        configureCaptureWindow(window);
        try {
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        setEngineMuted(!audioEnabled);
    }

    private WinDef.HWND findWindow() {
        return User32.INSTANCE.FindWindow(null, windowTitle);
    }

    private void configureCaptureWindow(WinDef.HWND window) {
        int style = User32.INSTANCE.GetWindowLong(window, GWL_STYLE);
        style &= ~(WS_CAPTION | WS_THICKFRAME | WS_SYSMENU | WS_MINIMIZEBOX | WS_MAXIMIZEBOX);
        style |= 0x80000000; // WS_POPUP: the requested size is the client size.
        User32.INSTANCE.SetWindowLong(window, GWL_STYLE, style);
        int exStyle = User32.INSTANCE.GetWindowLong(window, GWL_EXSTYLE);
        exStyle &= ~WS_EX_APPWINDOW;
        exStyle |= WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE | 0x00080000 | 0x00000020; // LAYERED | TRANSPARENT
        User32.INSTANCE.ShowWindow(window, 0); // Hide once to refresh the taskbar style.
        User32.INSTANCE.SetWindowLong(window, GWL_EXSTYLE, exStyle);
        if (!User32.INSTANCE.SetLayeredWindowAttributes(window, 0, (byte) 0, 2)) {
            removeScene();
            throw new IllegalStateException("Could not make scene host invisible");
        }
        // Move only after the engine has created the window. Creating directly at negative
        // coordinates is rejected by Wallpaper Engine, but an already-created window can be
        // parked outside the virtual desktop without showing a frame over Minecraft.
        User32.INSTANCE.SetWindowPos(window, null, -width, -height, width, height,
                SWP_NOACTIVATE | SWP_NOOWNERZORDER | SWP_FRAMECHANGED);
        User32.INSTANCE.ShowWindow(window, 4); // SW_SHOWNOACTIVATE
    }

    public void setAudioEnabled(boolean enabled) {
        if (!closed) setEngineMuted(!enabled);
    }

    public void setVolume(int percent) {
        if (closed) return;
        int volume = Math.clamp(percent, 0, 100);
        Path executable = engineExecutable();
        if (executable == null) return;
        try {
            new ProcessBuilder(executable.toString(), "-control", "applyProperties", "-location", windowTitle,
                    "-properties", "RAW~({\"volume\":" + volume + "})~END")
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start().waitFor(2, TimeUnit.SECONDS);
            setEngineMuted(volume == 0);
        } catch (IOException exception) {
            System.err.println("WallpaperMenu: could not set scene volume: " + exception);
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }

    private void setEngineMuted(boolean muted) {
        Path executable = engineExecutable();
        if (executable == null) return;
        try {
            new ProcessBuilder(executable.toString(), "-control", muted ? "mute" : "unmute")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start().waitFor(2, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException ignored) {
            // Muting is best effort; playback still works without it.
        }
    }

    private void captureLoop(int fps) {
        WinDef.HWND window = findWindow();
        if (window == null) {
            failure = "scene window disappeared before capture";
            return;
        }
        WinGDI.BITMAPINFO info = new WinGDI.BITMAPINFO();
        info.bmiHeader.biWidth = width;
        info.bmiHeader.biHeight = -height;
        info.bmiHeader.biPlanes = 1;
        info.bmiHeader.biBitCount = 32;
        info.bmiHeader.biCompression = WinGDI.BI_RGB;
        info.bmiHeader.write();
        WinDef.HDC screenDc = GDI32.INSTANCE.CreateCompatibleDC(null);
        PointerByReference bitsRef = new PointerByReference();
        WinDef.HBITMAP bitmap = GDI32.INSTANCE.CreateDIBSection(screenDc, info, WinGDI.DIB_RGB_COLORS,
                bitsRef, null, 0);
        if (bitmap == null || bitsRef.getValue() == null) {
            GDI32.INSTANCE.DeleteDC(screenDc);
            failure = "could not allocate a capture surface for the scene wallpaper";
            return;
        }
        Pointer bits = bitsRef.getValue();
        WinNT.HANDLE previous = GDI32.INSTANCE.SelectObject(screenDc, bitmap);
        int frameBytes = width * height * 4;
        long frameNanos = 1_000_000_000L / fps;
        try {
            while (!closed) {
                long started = System.nanoTime();
                if (!User32.INSTANCE.PrintWindow(window, screenDc, PRINT_WINDOW_RENDER_FULL_CONTENT)) {
                    failure = "PrintWindow failed for the scene wallpaper window";
                    break;
                }
                byte[] frame = new byte[frameBytes];
                bits.read(0, frame, 0, frameBytes);
                latest.set(frame);
                long remaining = frameNanos - (System.nanoTime() - started);
                if (remaining > 0) {
                    Thread.sleep(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException exception) {
            if (!closed) failure = exception.getMessage();
        } finally {
            latest.set(null);
            GDI32.INSTANCE.SelectObject(screenDc, previous);
            GDI32.INSTANCE.DeleteObject(bitmap);
            GDI32.INSTANCE.DeleteDC(screenDc);
        }
    }

    public byte[] pollFrame() {
        return latest.getAndSet(null);
    }

    private int inputButtons;
    private final java.util.Map<Integer, Integer> inputKeys = new java.util.HashMap<>();

    // JNA User32 declares PostMessage void; this binding preserves the native BOOL result.
    interface InputUser32 extends com.sun.jna.win32.StdCallLibrary {
        InputUser32 INSTANCE = com.sun.jna.Native.load("user32", InputUser32.class);
        boolean PostMessageW(WinDef.HWND window, int message, WinDef.WPARAM wParam, WinDef.LPARAM lParam);
        boolean ClientToScreen(WinDef.HWND window, WinDef.POINT point);
    }

    private boolean postInput(WinDef.HWND window, int message, long wParam, long lParam) {
        return !closed && window != null && User32.INSTANCE.IsWindow(window)
                && InputUser32.INSTANCE.PostMessageW(window, message, new WinDef.WPARAM(wParam), new WinDef.LPARAM(lParam));
    }

    /** Sends a mouse event to the hidden Wallpaper Engine window without activating it. */
    public synchronized boolean dispatchMouse(String type, double x, double y, int button, double unused) {
        WinDef.HWND window = findWindow();
        if (window == null || closed) return false;
        int mask = SceneInputMapping.buttonMask(button);
        int message;
        int nextButtons = inputButtons;
        switch (type) {
            case "mouseMoved" -> message = 0x0200;
            case "mousePressed" -> {
                if (mask == 0) return false;
                message = switch (button) { case 1 -> 0x0204; case 2 -> 0x0207; default -> 0x0201; };
                nextButtons |= mask;
            }
            case "mouseReleased" -> {
                if (mask == 0 || (inputButtons & mask) == 0) return false;
                message = switch (button) { case 1 -> 0x0205; case 2 -> 0x0208; default -> 0x0202; };
                nextButtons &= ~mask;
            }
            default -> { return false; }
        }
        int px = Math.clamp((int) Math.round(x), 0, width - 1);
        int py = Math.clamp((int) Math.round(y), 0, height - 1);
        if ("mouseMoved".equals(type)) {
            // SceneScript polls the real cursor in window coordinates; WM_MOUSEMOVE alone
            // cannot update input.cursorWorldPosition for an offscreen pop-out. Align our
            // window under that cursor, behind the foreground game, without moving the cursor.
            WinDef.POINT cursor = new WinDef.POINT();
            WinDef.HWND foreground = User32.INSTANCE.GetForegroundWindow();
            if (foreground != null && !foreground.equals(window) && User32.INSTANCE.GetCursorPos(cursor)) {
                User32.INSTANCE.SetWindowPos(window, foreground, cursor.x - px, cursor.y - py, width, height,
                        SWP_NOACTIVATE | SWP_NOOWNERZORDER);
            }
        }
        boolean sent = postInput(window, message, nextButtons, SceneInputMapping.point(px, py));
        if (sent) inputButtons = nextButtons;
        return sent;
    }

    public synchronized boolean dispatchScroll(double x, double y, double deltaX, double deltaY) {
        WinDef.HWND window = findWindow();
        if (window == null || closed) return false;
        WinDef.POINT point = new WinDef.POINT((int) x, (int) y);
        if (!InputUser32.INSTANCE.ClientToScreen(window, point)) return false;
        int position = SceneInputMapping.point(point.x, point.y);
        boolean sent = true;
        if (deltaY != 0) sent = postInput(window, 0x020A, SceneInputMapping.wheel(-deltaY) | inputButtons, position);
        if (deltaX != 0) sent &= postInput(window, 0x020E, SceneInputMapping.wheel(deltaX) | inputButtons, position);
        return sent;
    }

    /** Sends a Windows virtual-key message to the hidden scene window. */
    public synchronized boolean dispatchKey(String type, int keyCode, int scanCode, int modifiers) {
        int vk = SceneInputMapping.virtualKey(keyCode);
        boolean up = "keyUp".equals(type);
        if (vk == 0 || (!up && !"keyDown".equals(type)) || up && !inputKeys.containsKey(vk)) return false;
        boolean alt = (modifiers & 4) != 0;
        int message = alt ? (up ? 0x0105 : 0x0104) : (up ? 0x0101 : 0x0100);
        boolean sent = postInput(findWindow(), message, vk,
                SceneInputMapping.keyFlags(scanCode, up, inputKeys.containsKey(vk), alt));
        if (sent) {
            if (up) inputKeys.remove(vk);
            else inputKeys.put(vk, scanCode);
        }
        return sent;
    }

    public synchronized boolean dispatchText(String text) {
        WinDef.HWND window = findWindow();
        if (text == null || text.isEmpty()) return false;
        // WM_CHAR on Unicode windows takes UTF-16 code units, including surrogate pairs.
        for (int i = 0; i < text.length(); i++) {
            if (!postInput(window, 0x0102, text.charAt(i), 1)) return false;
        }
        return true;
    }

    public synchronized void clearInput() {
        WinDef.HWND window = findWindow();
        for (int button = 0; button < 3; button++) {
            int mask = SceneInputMapping.buttonMask(button);
            if ((inputButtons & mask) == 0) continue;
            inputButtons &= ~mask;
            postInput(window, switch (button) { case 1 -> 0x0205; case 2 -> 0x0208; default -> 0x0202; }, inputButtons, 0);
        }
        inputKeys.forEach((key, scan) -> postInput(window, 0x0101, key, SceneInputMapping.keyFlags(scan, true, false, false)));
        inputKeys.clear();
    }

    public String failure() {
        return failure;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Frames arrive as BGRA from the DIB, so the texture upload must use GL_BGRA. */
    public boolean bgra() {
        return true;
    }

    @Override
    public synchronized void close() {
        closed = true;
        worker.interrupt();
        boolean interrupted = false;
        // Do not destroy the HWND while PrintWindow is using it, even if shutdown is interrupted.
        while (worker.isAlive()) {
            try { worker.join(); }
            catch (InterruptedException exception) { interrupted = true; }
        }
        removeScene();
        // Never globally unmute Wallpaper Engine here: that resumes unrelated desktop music.
        try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
        catch (IllegalStateException ignored) { /* JVM shutdown already in progress. */ }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private void removeScene() {
        Path executable = engineExecutable();
        if (executable == null) return;
        try {
            // "closeWallpaper" is the command that actually destroys a named pop-out window;
            // "removeWallpaper" is accepted but leaves the window running.
            new ProcessBuilder(executable.toString(), "-control", "closeWallpaper", "-location", windowTitle)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start().waitFor(3, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException ignored) {
            // Still close our own window below if the command failed or timed out.
        }
        WinDef.HWND window = findWindow();
        if (window != null) {
            // Scoped to this unique pop-out; never terminate the user's Wallpaper Engine process.
            InputUser32.INSTANCE.PostMessageW(window, 0x0010, new WinDef.WPARAM(0), new WinDef.LPARAM(0));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (findWindow() != null && System.nanoTime() < deadline) {
                try { Thread.sleep(50); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); break; }
            }
            if (findWindow() != null) System.err.println("WallpaperMenu: scene window did not close: " + windowTitle);
        }
    }

}
