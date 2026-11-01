import dev.wallpapermenu.wallpaper.ChromiumWebDecoder;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Real-browser integration check; pass one or more trusted local wallpaper HTML paths. */
public final class WebFpsSwitchSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Pass wallpaper HTML paths");
        for (String path : args) {
            try (var decoder = new ChromiumWebDecoder(Path.of(path), 30)) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
                while (decoder.pollFrame() == null) {
                    if (decoder.failure() != null) throw new AssertionError(decoder.failure());
                    if (System.nanoTime() > deadline) throw new AssertionError("No first frame: " + path);
                    Thread.sleep(5);
                }
                for (int fps : new int[]{30, 120, 30}) {
                    decoder.setFps(fps);
                    // Exclude page startup and switch transients from the measurement window.
                    long warmup = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                    while (System.nanoTime() < warmup) { decoder.pollFrame(); Thread.sleep(2); }
                    int frames = 0;
                    long started = System.nanoTime();
                    while (System.nanoTime() - started < TimeUnit.SECONDS.toNanos(15)) {
                        byte[] frame = decoder.pollFrame();
                        if (frame != null) {
                            if (frame.length != ChromiumWebDecoder.WIDTH * ChromiumWebDecoder.HEIGHT * 4)
                                throw new AssertionError("Incomplete RGBA frame");
                            frames++;
                        }
                        if (decoder.failure() != null) throw new AssertionError(decoder.failure());
                        Thread.sleep(2);
                    }
                    double measured = frames * 1e9 / (System.nanoTime() - started);
                    System.out.printf("%s configured=%d measured=%.1f fps frames=%d failure=%s%n",
                            path, fps, measured, frames, decoder.failure());
                    if (frames < 15) throw new AssertionError("Frame stream stalled after FPS switch");
                    if (fps == 30 && measured > 32) throw new AssertionError("30 FPS cap exceeded");
                }
            }
            System.out.println("PASS: same decoder survived 30 -> 120 -> 30: " + path);
        }
    }
}
