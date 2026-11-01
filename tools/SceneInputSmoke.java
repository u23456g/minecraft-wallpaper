import dev.wallpapermenu.wallpaper.SceneDecoder;
import dev.wallpapermenu.wallpaper.SceneInputMapping;
import dev.wallpapermenu.wallpaper.WebInputMapping;
import com.sun.jna.platform.win32.User32;
import java.nio.file.Path;

/** Run explicitly against an installed scene; does not change the desktop wallpaper. */
public final class SceneInputSmoke {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        check(SceneInputMapping.virtualKey(257) == 13, "Enter virtual key");
        check(SceneInputMapping.virtualKey(262) == 39, "Right arrow virtual key");
        check(SceneInputMapping.virtualKey(290) == 112, "F1 virtual key");
        check(SceneInputMapping.virtualKey(39) == 222, "Apostrophe virtual key");
        check(SceneInputMapping.virtualKey(-1) == 0, "Unknown key");
        check(SceneInputMapping.point(-960, -540) == 0xfde4fc40, "Signed wheel coordinates");
        check(SceneInputMapping.wheel(-120) == 0xff880000, "Wheel delta");
        check(SceneInputMapping.buttonMask(1) == 2, "Right button mask");
        check(SceneInputMapping.keyFlags(0x14d, true, false, false) == 0xc14d0001L, "Extended key release");
        var mapped = WebInputMapping.map(0, 0, -160, 0, 1280, 720, 1920, 1080).orElseThrow();
        check(mapped.x() == 240 && mapped.y() == 0, "Cover-cropped coordinate mapping");
        System.out.println("PASS: input encoding and cropped coordinate mapping");
        if (args.length == 0) return;
        String sceneTitle;
        try (var scene = new SceneDecoder(Path.of(args[0]), 960, 540, 30, false)) {
            var titleField = SceneDecoder.class.getDeclaredField("windowTitle");
            titleField.setAccessible(true);
            sceneTitle = (String) titleField.get(scene);
            long deadline = System.nanoTime() + 15_000_000_000L;
            byte[] frame = null;
            while (frame == null && System.nanoTime() < deadline) {
                check(scene.failure() == null, "Capture failed: " + scene.failure());
                frame = scene.pollFrame();
                Thread.sleep(20);
            }
            check(frame != null, "No scene frame");
            var foreground = User32.INSTANCE.GetForegroundWindow();
            check(scene.dispatchMouse("mouseMoved", 480, 270, -1, 0), "Move rejected");
            check(scene.dispatchMouse("mousePressed", 480, 270, 0, 0), "Press rejected");
            check(scene.dispatchMouse("mouseMoved", 520, 280, 0, 0), "Drag rejected");
            check(scene.dispatchMouse("mouseReleased", 520, 280, 0, 0), "Release rejected");
            check(scene.dispatchScroll(520, 280, 120, -120), "Wheel rejected");
            check(scene.dispatchKey("keyDown", 65, 30, 0), "Key down rejected");
            check(scene.dispatchKey("keyUp", 65, 30, 0), "Key up rejected");
            check(scene.dispatchText("A\uD83C\uDFB5"), "UTF-16 text rejected");
            scene.clearInput();
            check(java.util.Objects.equals(foreground, User32.INSTANCE.GetForegroundWindow()), "Scene stole foreground focus");
            System.out.println("PASS: real scene HWND accepted move/click/drag/wheel/key/text; foreground unchanged");
            System.out.println("NOTE: PostMessage acceptance does not prove a scene script reacted.");
        }
        check(User32.INSTANCE.FindWindow(null, sceneTitle) == null, "Scene window remains after close");
        System.out.println("PASS: owned scene window absent after close; no host left to play scene audio");
    }
}
