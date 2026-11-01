import dev.wallpapermenu.wallpaper.VideoDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/** Run with javac/java; needs the same FFmpeg executable as the mod. */
public class VideoDecoderSmoke {
    public static void main(String[] args) throws Exception {
        Path video = Files.createTempFile("wallpaper-smoke-", ".mp4");
        try {
            Process generator = new ProcessBuilder("ffmpeg", "-nostdin", "-v", "error", "-y",
                    "-f", "lavfi", "-i", "testsrc2=size=160x90:rate=30", "-t", "0.2",
                    "-pix_fmt", "yuv420p", video.toString()).inheritIO().start();
            if (!generator.waitFor(20, TimeUnit.SECONDS)) {
                generator.destroyForcibly();
                throw new AssertionError("Fixture generation timed out");
            }
            if (generator.exitValue() != 0) throw new AssertionError("Fixture generation failed");
            int frames = 0;
            boolean changed = false;
            byte[] first = null;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            try (VideoDecoder decoder = new VideoDecoder(video)) {
                while (frames < 18 && System.nanoTime() < deadline) {
                    if (decoder.failure() != null) throw new AssertionError(decoder.failure());
                    byte[] frame = decoder.pollFrame();
                    if (frame == null) { Thread.sleep(10); continue; }
                    if (frame.length != 960 * 540 * 4) throw new AssertionError("Wrong frame size");
                    if (first == null) first = frame;
                    else changed |= !Arrays.equals(first, frame);
                    frames++;
                }
            }
            if (frames < 18 || !changed) throw new AssertionError("No moving/looping frames: " + frames);
            System.out.println("PASS: 18 RGBA frames from a 6-frame MP4; motion and loop verified; decoder closed");
        } finally {
            Files.deleteIfExists(video);
        }
    }
}
