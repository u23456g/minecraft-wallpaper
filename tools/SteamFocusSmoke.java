import dev.wallpapermenu.wallpaper.SteamWorkshopDownload;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.ptr.IntByReference;
import javax.swing.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

/** Live warm-client check with a real foreground window owned by the downloader JVM. */
public class SteamFocusSmoke {
    public static void main(String[] args) throws Exception {
        JFrame[] frame = new JFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            frame[0] = new JFrame("Wallpaper Menu — download focus test");
            frame[0].add(new JLabel("Testing Steam download without focus stealing"));
            frame[0].setSize(520, 160);
            frame[0].setLocationRelativeTo(null);
            frame[0].setVisible(true);
        });
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!ownsForeground() && System.nanoTime() < deadline) Thread.sleep(100);
            if (!ownsForeground()) throw new AssertionError("Click test window to run foreground test");
            var download = CompletableFuture.runAsync(() -> {
                try { SteamWorkshopDownload.request(List.of(Path.of(args[0])), args[1]); }
                catch (Exception e) { throw new CompletionException(e); }
            });
            boolean focusLost = false;
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
            while (!download.isDone() && System.nanoTime() < deadline) {
                if (!ownsForeground()) focusLost = true;
                Thread.sleep(50);
            }
            download.get(1, TimeUnit.SECONDS);
            long observeUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (System.nanoTime() < observeUntil) {
                if (!ownsForeground()) focusLost = true;
                Thread.sleep(50);
            }
            if (focusLost) throw new AssertionError("Foreground changed during Steam download");
            System.out.println("PASS: fresh Steam OK; downloader window retained foreground (50ms samples)");
        } finally {
            SwingUtilities.invokeAndWait(() -> frame[0].dispose());
        }
    }
    private static boolean ownsForeground() {
        var pid = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(User32.INSTANCE.GetForegroundWindow(), pid);
        return pid.getValue() == (int) ProcessHandle.current().pid();
    }
}
