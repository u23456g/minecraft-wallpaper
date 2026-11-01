import dev.wallpapermenu.wallpaper.SceneDecoder;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Explicit live probe: never moves the system cursor or activates the scene. */
public class SceneCursorSmoke {
    public static void main(String[] args) throws Exception {
        WinDef.HWND ownedWindow;
        try (var scene = new SceneDecoder(Path.of(args[0]), 960, 540, 30, false)) {
            var title = SceneDecoder.class.getDeclaredField("windowTitle");
            title.setAccessible(true);
            var window = User32.INSTANCE.FindWindow(null, (String)title.get(scene));
            ownedWindow = window;
            int style = User32.INSTANCE.GetWindowLong(window, -20);
            if ((style & 0x00040000) != 0 || (style & 0x000800A0) != 0x000800A0)
                throw new AssertionError("Host must be layered, click-through, tool-window, not app-window");
            var alpha = new com.sun.jna.ptr.ByteByReference();
            if (!User32.INSTANCE.GetLayeredWindowAttributes(window, new com.sun.jna.ptr.IntByReference(), alpha,
                    new com.sun.jna.ptr.IntByReference()) || alpha.getValue() != 0)
                throw new AssertionError("Host is not fully transparent");
            var foreground = User32.INSTANCE.GetForegroundWindow();
            for (int x : new int[]{240, 720}) {
                if (!scene.dispatchMouse("mouseMoved", x, 140, -1, 0)) throw new AssertionError("Move rejected");
                var point = new WinDef.POINT();
                User32.INSTANCE.GetCursorPos(point);
                var rect = new WinDef.RECT();
                User32.INSTANCE.GetWindowRect(window, rect);
                point.x -= rect.left;
                point.y -= rect.top;
                if (Math.abs(point.x-x)>2 || Math.abs(point.y-140)>2) throw new AssertionError("Native cursor mismatch: " + point);
                Thread.sleep(2500);
                byte[] pixels = scene.pollFrame();
                if (pixels == null) throw new AssertionError("No frame: " + scene.failure());
                var image = new BufferedImage(960,540,BufferedImage.TYPE_INT_RGB);
                for(int y=0;y<540;y++) for(int xx=0;xx<960;xx++) {
                    int i=(y*960+xx)*4;
                    image.setRGB(xx,y,((pixels[i+2]&255)<<16)|((pixels[i+1]&255)<<8)|(pixels[i]&255));
                }
                long sumX = 0, count = 0;
                // Above the cat and below the clock: only the cursor-following bee lives here.
                for (int yy=130; yy<148; yy++) for (int xx=80; xx<880; xx++) {
                    int rgb = image.getRGB(xx, yy);
                    if ((rgb & 255) > 70 && ((rgb >>> 8) & 255) > 70 && ((rgb >>> 16) & 255) > 70) {
                        sumX += xx; count++;
                    }
                }
                if (count == 0 || Math.abs(sumX / (double) count - x) > 30)
                    throw new AssertionError("Bee did not follow target " + x + "; pixels=" + count);
                ImageIO.write(image,"png",Path.of("run","scene-cursor-"+x+".png").toFile());
                System.out.println("PASS: native cursor mapped to " + x + ",140; frame saved");
            }
            if (!java.util.Objects.equals(foreground,User32.INSTANCE.GetForegroundWindow())) throw new AssertionError("Foreground changed");
        }
        if (User32.INSTANCE.IsWindow(ownedWindow)) throw new AssertionError("Owned scene host survives close");
        System.out.println("PASS: zero-alpha host excluded from taskbar; owned window destroyed after close");
    }
}
